package com.klvw.wallpaper.wear

import android.app.NotificationManager
import android.net.Uri
import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.klvw.wallpaper.data.model.FolderType
import com.klvw.wallpaper.data.model.WallpaperItem
import com.klvw.wallpaper.data.model.WallpaperTarget
import com.klvw.wallpaper.data.prefs.SettingsPreferences
import com.klvw.wallpaper.data.prefs.ShuffleHistoryManager
import com.klvw.wallpaper.data.repository.FolderRepository
import com.klvw.wallpaper.data.repository.WallpaperRepository
import com.klvw.wallpaper.tile.TimerStatusNotificationHelper
import com.klvw.wallpaper.tile.WallpaperTimerManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject

private const val PATH_EXECUTE = "/klvw/execute"
private const val TAG = "KLVWWearListener"

class KLVWWearListenerService : WearableListenerService() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WearListenerEntryPoint {
        fun settingsPreferences(): SettingsPreferences
        fun wallpaperRepository(): WallpaperRepository
        fun folderRepository(): FolderRepository
        fun shuffleHistoryManager(): ShuffleHistoryManager
        fun wallpaperTimerManager(): WallpaperTimerManager
    }

    private val ep: WearListenerEntryPoint by lazy {
        EntryPointAccessors.fromApplication(applicationContext, WearListenerEntryPoint::class.java)
    }

    private val prefs               get() = ep.settingsPreferences()
    private val wallpaperRepository get() = ep.wallpaperRepository()
    private val folderRepository    get() = ep.folderRepository()
    private val shuffleHistoryManager get() = ep.shuffleHistoryManager()
    private val timerManager        get() = ep.wallpaperTimerManager()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onMessageReceived(event: MessageEvent) {
        Log.d(TAG, "onMessageReceived: ${event.path}")
        if (event.path == PATH_EXECUTE) handleExecute(event.data)
    }

    private fun handleExecute(data: ByteArray) {
        scope.launch {
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
                        } ?: return@launch
                        val item = shuffleHistoryManager.pickNext(
                            folderRepository.getItemsFromFolder(uri, FolderType.IMAGE),
                            uri, FolderType.IMAGE
                        ) ?: return@launch
                        wallpaperRepository.setWallpaper(item, wallpaperTarget)
                    }
                    "random_video" -> {
                        val uri = folderUri.ifBlank {
                            when (target) {
                                "lock" -> prefs.defaultLockVideoFolderUri.first()
                                else   -> prefs.defaultHomeVideoFolderUri.first()
                            }
                        } ?: return@launch
                        val item = shuffleHistoryManager.pickNext(
                            folderRepository.getItemsFromFolder(uri, FolderType.VIDEO),
                            uri, FolderType.VIDEO
                        ) ?: return@launch
                        wallpaperRepository.setWallpaper(item, wallpaperTarget)
                    }
                    "restore" -> {
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
                    }
                    "global_off" -> {
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
                    }
                    else -> Log.w(TAG, "Unknown watch action: $actionType")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Execute failed", e)
            }
        }
    }
}
