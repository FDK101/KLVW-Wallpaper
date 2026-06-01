package com.klvw.wallpaper.wear

import android.app.NotificationManager
import android.net.Uri
import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
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
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import javax.inject.Inject

private const val PATH_CONFIG_REQUEST  = "/klvw/config/request"
private const val PATH_CONFIG_RESPONSE = "/klvw/config/response"
private const val PATH_EXECUTE         = "/klvw/execute"
private const val PATH_EXECUTE_ACK     = "/klvw/execute/ack"
private const val TAG = "KLVWWearListener"

@AndroidEntryPoint
class KLVWWearListenerService : WearableListenerService() {

    @Inject lateinit var prefs: SettingsPreferences
    @Inject lateinit var wallpaperRepository: WallpaperRepository
    @Inject lateinit var folderRepository: FolderRepository
    @Inject lateinit var shuffleHistoryManager: ShuffleHistoryManager
    @Inject lateinit var timerManager: WallpaperTimerManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
            PATH_CONFIG_REQUEST -> handleConfigRequest(event.sourceNodeId)
            PATH_EXECUTE        -> handleExecute(event.sourceNodeId, event.data)
        }
    }

    private fun handleConfigRequest(sourceNodeId: String) {
        scope.launch {
            val json = prefs.klvwWatchItemsJson.first()
            sendMessage(sourceNodeId, PATH_CONFIG_RESPONSE, json.toByteArray(Charsets.UTF_8))
        }
    }

    private fun handleExecute(sourceNodeId: String, data: ByteArray) {
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

                sendMessage(sourceNodeId, PATH_EXECUTE_ACK, "ok".toByteArray(Charsets.UTF_8))
            } catch (e: Exception) {
                Log.e(TAG, "Execute failed", e)
            }
        }
    }

    private suspend fun sendMessage(nodeId: String, path: String, data: ByteArray) {
        try {
            Wearable.getMessageClient(this).sendMessage(nodeId, path, data).await()
        } catch (e: Exception) {
            Log.e(TAG, "sendMessage($path) failed", e)
        }
    }
}
