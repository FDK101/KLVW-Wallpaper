package com.klvw.wallpaper.tile

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.klvw.wallpaper.data.model.FolderType
import com.klvw.wallpaper.data.model.WallpaperTarget
import com.klvw.wallpaper.data.prefs.SettingsPreferences
import com.klvw.wallpaper.data.repository.FolderRepository
import com.klvw.wallpaper.data.repository.WallpaperRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ScreenUnlockReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface UnlockEntryPoint {
        fun prefs(): SettingsPreferences
        fun wallpaperRepository(): WallpaperRepository
        fun folderRepository(): FolderRepository
        fun timerManager(): WallpaperTimerManager
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_USER_PRESENT) return
        val ep = EntryPointAccessors.fromApplication(
            context.applicationContext, UnlockEntryPoint::class.java
        )
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = ep.prefs()
                if (!prefs.appEnabled.first()) return@launch

                val appliedHome = applyIfEnabled(prefs.displayControlHomeImage.first(), prefs.defaultHomeImageFolderUri.first(), FolderType.IMAGE, WallpaperTarget.HOME, ep)
                              || applyIfEnabled(prefs.displayControlHomeVideo.first(), prefs.defaultHomeVideoFolderUri.first(), FolderType.VIDEO, WallpaperTarget.HOME, ep)
                val appliedLock = applyIfEnabled(prefs.displayControlLockImage.first(), prefs.defaultLockImageFolderUri.first(), FolderType.IMAGE, WallpaperTarget.LOCK, ep)
                              || applyIfEnabled(prefs.displayControlLockVideo.first(), prefs.defaultLockVideoFolderUri.first(), FolderType.VIDEO, WallpaperTarget.LOCK, ep)

                if (appliedHome && prefs.displayControlResetHomeTimer.first()) {
                    resetTimersForScreen("home", prefs, ep.timerManager())
                }
                if (appliedLock && prefs.displayControlResetLockTimer.first()) {
                    resetTimersForScreen("lock", prefs, ep.timerManager())
                }

                if (prefs.timerUnlockNotification.first()) {
                    showTimerNotificationIfNeeded(context, prefs, ep.timerManager())
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun applyIfEnabled(
        enabled: Boolean,
        folderUri: String?,
        type: FolderType,
        target: WallpaperTarget,
        ep: UnlockEntryPoint
    ): Boolean {
        if (!enabled || folderUri == null) return false
        val item = ep.folderRepository().getItemsFromFolder(folderUri, type).randomOrNull() ?: return false
        ep.wallpaperRepository().setWallpaper(item, target)
        return true
    }

    private suspend fun showTimerNotificationIfNeeded(
        context: Context,
        prefs: SettingsPreferences,
        timerManager: WallpaperTimerManager
    ) {
        val allKeys = listOf("home_image", "home_video", "lock_image", "lock_video")
        val enabledKeys = allKeys.filter { key ->
            when (key) {
                "home_image" -> prefs.homeImageTimerEnabled.first()
                "home_video" -> prefs.homeVideoTimerEnabled.first()
                "lock_image" -> prefs.lockImageTimerEnabled.first()
                "lock_video" -> prefs.lockVideoTimerEnabled.first()
                else -> false
            }
        }
        if (enabledKeys.isEmpty()) return
        TimerStatusNotificationHelper.show(
            context,
            enabledKeys,
            timerManager.paused.value,
            timerManager.nextFireTimes.value
        )
    }

    private suspend fun resetTimersForScreen(
        screen: String,
        prefs: SettingsPreferences,
        timerManager: WallpaperTimerManager
    ) {
        val imageKey = "${screen}_image"
        val videoKey = "${screen}_video"
        val imageEnabled = when (screen) {
            "home" -> prefs.homeImageTimerEnabled.first()
            else   -> prefs.lockImageTimerEnabled.first()
        }
        val videoEnabled = when (screen) {
            "home" -> prefs.homeVideoTimerEnabled.first()
            else   -> prefs.lockVideoTimerEnabled.first()
        }
        val imageInterval = when (screen) {
            "home" -> prefs.homeImageTimerIntervalMin.first()
            else   -> prefs.lockImageTimerIntervalMin.first()
        }
        val videoInterval = when (screen) {
            "home" -> prefs.homeVideoTimerIntervalMin.first()
            else   -> prefs.lockVideoTimerIntervalMin.first()
        }
        val imagePaused = timerManager.paused.value[imageKey] == true
        val videoPaused = timerManager.paused.value[videoKey] == true

        if (imageEnabled && !imagePaused) timerManager.scheduleAlarm(imageKey, imageInterval)
        if (videoEnabled && !videoPaused) timerManager.scheduleAlarm(videoKey, videoInterval)
    }
}
