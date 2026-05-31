package com.klvw.wallpaper

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.video.VideoFrameDecoder
import com.klvw.wallpaper.data.prefs.SettingsPreferences
import com.klvw.wallpaper.tile.ScreenUnlockReceiver
import com.klvw.wallpaper.tile.TimerStatusNotificationHelper
import com.klvw.wallpaper.tile.WallpaperTimerManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import okio.Path.Companion.toOkioPath
import javax.inject.Inject

@HiltAndroidApp
class KLVWApplication : Application(), SingletonImageLoader.Factory {

    @Inject lateinit var timerManager: WallpaperTimerManager
    @Inject lateinit var prefs: SettingsPreferences

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
        // ACTION_USER_PRESENT is blocked for manifest receivers on Android 8+ — register dynamically.
        registerReceiver(ScreenUnlockReceiver(), IntentFilter(Intent.ACTION_USER_PRESENT))

        // Track screen state so the notification observer only runs while the screen is on.
        // When the screen is off the user cannot see the notification, and a 1-second ticker
        // running in the dark is one of the main causes of Doze-mode suppression / battery drain.
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

        // Real-time timer notification observer.
        //
        // enabledKeysFlow is derived from prefs and only re-emits when timer settings change
        // (rare), so there are no DataStore reads in the 1-second hot path.
        //
        // flatMapLatest on screenOn:
        //   screen OFF → switches to emptyFlow(), cancels the ticker entirely → Doze can engage
        //   screen ON  → restarts the combine+ticker, notification refreshes within 1 s
        //
        // State-flow changes (pause / resume / reset / timer fire) wake the collector immediately
        // without waiting for the next tick.
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
