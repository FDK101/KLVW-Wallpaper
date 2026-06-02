package com.klvw.wallpaper

import android.app.Application
import android.app.NotificationManager
import android.app.NotificationChannel
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.PowerManager
import android.util.Log
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.video.VideoFrameDecoder
import com.klvw.wallpaper.data.model.FolderType
import com.klvw.wallpaper.data.model.WallpaperItem
import com.klvw.wallpaper.data.model.WallpaperTarget
import com.klvw.wallpaper.data.prefs.SettingsPreferences
import com.klvw.wallpaper.data.prefs.ShuffleHistoryManager
import com.klvw.wallpaper.data.repository.FolderRepository
import com.klvw.wallpaper.data.repository.WallpaperRepository
import com.klvw.wallpaper.tile.ScreenUnlockReceiver
import com.klvw.wallpaper.tile.TimerStatusNotificationHelper
import com.klvw.wallpaper.tile.WallpaperTimerManager
import com.klvw.wallpaper.wear.BtConfigServer
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import okio.Path.Companion.toOkioPath
import org.json.JSONObject
import javax.inject.Inject

private const val TAG = "KLVWApp"

@HiltAndroidApp
class KLVWApplication : Application(), SingletonImageLoader.Factory {

    @Inject lateinit var timerManager: WallpaperTimerManager
    @Inject lateinit var prefs: SettingsPreferences
    @Inject lateinit var wallpaperRepository: WallpaperRepository
    @Inject lateinit var folderRepository: FolderRepository
    @Inject lateinit var shuffleHistoryManager: ShuffleHistoryManager

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("klvw_debug", "KLVW Debug", NotificationManager.IMPORTANCE_HIGH)
        )
        nm.createNotificationChannel(
            NotificationChannel("timer_status", "Timer Status", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows running timer status on screen unlock"
            }
        )
        registerReceiver(ScreenUnlockReceiver(), IntentFilter(Intent.ACTION_USER_PRESENT))

        val powerManager = getSystemService(PowerManager::class.java)
        val screenOn = MutableStateFlow(powerManager?.isInteractive == true)
        registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                screenOn.value = intent.action == Intent.ACTION_SCREEN_ON
            }
        }, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        })

        // Start Bluetooth RFCOMM server — watch connects directly over classic BT.
        BtConfigServer(this, prefs, timerManager, appScope) { data ->
            handleWatchExecute(data)
        }.start()

        appScope.launch {
            val enabledKeysFlow = combine(
                prefs.homeImageTimerEnabled,
                prefs.homeVideoTimerEnabled,
                prefs.lockImageTimerEnabled,
                prefs.lockVideoTimerEnabled
            ) { hi, hv, li, lv ->
                listOfNotNull(
                    "home_image".takeIf { hi },
                    "home_video".takeIf { hv },
                    "lock_image".takeIf { li },
                    "lock_video".takeIf { lv }
                )
            }

            screenOn.flatMapLatest { isOn ->
                if (!isOn) {
                    emptyFlow()
                } else {
                    val ticker = flow { while (true) { emit(Unit); delay(1_000L) } }
                    combine(
                        enabledKeysFlow,
                        timerManager.paused,
                        timerManager.nextFireTimes,
                        ticker
                    ) { enabled, paused, fires, _ ->
                        Triple(enabled, paused, fires)
                    }
                }
            }.collect { triple ->
                val (enabled, paused, fires) = triple
                if (enabled.isEmpty()) return@collect
                val notifMgr = getSystemService(NotificationManager::class.java) ?: return@collect
                if (notifMgr.activeNotifications.none {
                        it.id == TimerStatusNotificationHelper.NOTIFICATION_ID
                    }) return@collect
                TimerStatusNotificationHelper.show(applicationContext, enabled, paused, fires)
            }
        }
    }

    private suspend fun handleWatchExecute(data: ByteArray) {
        try {
            val obj        = JSONObject(String(data, Charsets.UTF_8))
            val actionType = obj.optString("actionType")
            val target     = obj.optString("target", "home")
            val folderUri  = obj.optString("folderUri", "")

            val wallpaperTarget = when (target) {
                "lock" -> WallpaperTarget.LOCK
                "both" -> WallpaperTarget.BOTH
                else   -> WallpaperTarget.HOME
            }

            when (actionType) {
                "random_image" -> {
                    val uri = folderUri.ifBlank {
                        when (target) {
                            "lock" -> prefs.defaultLockImageFolderUri.first()
                            else   -> prefs.defaultHomeImageFolderUri.first()
                        }
                    } ?: return
                    val item = shuffleHistoryManager.pickNext(
                        folderRepository.getItemsFromFolder(uri, FolderType.IMAGE),
                        uri, FolderType.IMAGE
                    ) ?: return
                    wallpaperRepository.setWallpaper(item, wallpaperTarget)
                }
                "random_video" -> {
                    val uri = folderUri.ifBlank {
                        when (target) {
                            "lock" -> prefs.defaultLockVideoFolderUri.first()
                            else   -> prefs.defaultHomeVideoFolderUri.first()
                        }
                    } ?: return
                    val item = shuffleHistoryManager.pickNext(
                        folderRepository.getItemsFromFolder(uri, FolderType.VIDEO),
                        uri, FolderType.VIDEO
                    ) ?: return
                    wallpaperRepository.setWallpaper(item, wallpaperTarget)
                }
                "restore" -> {
                    prefs.setAppEnabled(true)
                    prefs.prevHomeWallpaperUri.first()?.let { uri ->
                        val vid = prefs.prevHomeIsVideo.first()
                        wallpaperRepository.setWallpaper(
                            WallpaperItem(Uri.parse(uri),
                                if (vid) FolderType.VIDEO else FolderType.IMAGE, "", ""),
                            WallpaperTarget.HOME
                        )
                    }
                    prefs.prevLockWallpaperUri.first()?.let { uri ->
                        val vid = prefs.prevLockIsVideo.first()
                        wallpaperRepository.setWallpaper(
                            WallpaperItem(Uri.parse(uri),
                                if (vid) FolderType.VIDEO else FolderType.IMAGE, "", ""),
                            WallpaperTarget.LOCK
                        )
                    }
                    val pausedKeys = prefs.globalOffPausedTimers.first()
                    if (pausedKeys.isNotEmpty()) {
                        pausedKeys.forEach { key -> timerManager.resume(key) }
                        prefs.setGlobalOffPausedTimers(emptySet())
                    }
                }
                "global_off" -> {
                    // Apply quickSet static wallpapers first (records prevHomeWallpaperUri
                    // so a subsequent Restore can go back, matching QS tile behaviour).
                    prefs.quickSetHomeStaticUri.first()?.let { uri ->
                        wallpaperRepository.setWallpaper(
                            WallpaperItem(Uri.parse(uri), FolderType.IMAGE, "", ""),
                            WallpaperTarget.HOME
                        )
                    }
                    prefs.quickSetLockStaticUri.first()?.let { uri ->
                        wallpaperRepository.setWallpaper(
                            WallpaperItem(Uri.parse(uri), FolderType.IMAGE, "", ""),
                            WallpaperTarget.LOCK
                        )
                    }
                    prefs.setAppEnabled(false)
                    if (prefs.pauseTimersOnGlobalOff.first()) {
                        val keys = listOf("home_image", "home_video", "lock_image", "lock_video")
                            .filter { key ->
                                when (key) {
                                    "home_image" -> prefs.homeImageTimerEnabled.first()
                                    "home_video" -> prefs.homeVideoTimerEnabled.first()
                                    "lock_image" -> prefs.lockImageTimerEnabled.first()
                                    "lock_video" -> prefs.lockVideoTimerEnabled.first()
                                    else -> false
                                }
                            }
                            .filter { key -> timerManager.paused.value[key] != true }
                        keys.forEach { key -> timerManager.pause(key) }
                        prefs.setGlobalOffPausedTimers(keys.toSet())
                        getSystemService(NotificationManager::class.java)
                            ?.cancel(TimerStatusNotificationHelper.NOTIFICATION_ID)
                    }
                    if (prefs.watchGlobalOffVibrate.first()) {
                        getSystemService(android.os.Vibrator::class.java)
                            ?.vibrate(android.os.VibrationEffect.createOneShot(
                                300L, android.os.VibrationEffect.DEFAULT_AMPLITUDE
                            ))
                    }
                }
                else -> Log.w(TAG, "Unknown watch action: $actionType")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Watch execute failed", e)
        }
    }

    override fun newImageLoader(context: android.content.Context): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.20)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("coil_thumbnails").toOkioPath())
                    .maxSizeBytes(150L * 1024 * 1024)
                    .build()
            }
            .build()
    }
}
