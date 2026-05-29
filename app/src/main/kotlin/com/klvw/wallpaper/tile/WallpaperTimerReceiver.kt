package com.klvw.wallpaper.tile

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.klvw.wallpaper.data.model.FolderType
import com.klvw.wallpaper.data.model.WallpaperTarget
import com.klvw.wallpaper.data.prefs.SettingsPreferences
import com.klvw.wallpaper.data.prefs.ShuffleHistoryManager
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

class WallpaperTimerReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface TimerEntryPoint {
        fun prefs(): SettingsPreferences
        fun wallpaperRepository(): WallpaperRepository
        fun folderRepository(): FolderRepository
        fun timerManager(): WallpaperTimerManager
        fun shuffleHistoryManager(): ShuffleHistoryManager
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            WallpaperTimerManager.ACTION_TIMER_FIRE -> handleTimerFire(context, intent)
            Intent.ACTION_BOOT_COMPLETED -> { /* WallpaperTimerManager.init() reschedules alarms on process start */ }
        }
    }

    private fun handleTimerFire(context: Context, intent: Intent) {
        val key = intent.getStringExtra(WallpaperTimerManager.EXTRA_TIMER_KEY) ?: return
        val ep = EntryPointAccessors.fromApplication(
            context.applicationContext, TimerEntryPoint::class.java
        )
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val timerManager = ep.timerManager()
                val paused = timerManager.paused.value[key] == true
                if (!paused && ep.prefs().appEnabled.first()) {
                    val (type, target) = keyToTypeTarget(key)
                    val uri = defaultFolderUri(ep.prefs(), type, target)
                    if (uri != null) {
                        val allItems = ep.folderRepository().getItemsFromFolder(uri, type)
                    val item = ep.shuffleHistoryManager().pickNext(allItems, uri, type)
                        if (item != null) ep.wallpaperRepository().setWallpaper(item, target)
                    }
                }
                // Reschedule next firing — launchTimer's collectLatest will cancel it if disabled/paused
                val intervalMin = intervalForKey(ep.prefs(), key)
                timerManager.scheduleAlarm(key, intervalMin)
                // Refresh the notification so the countdown resets to the new fire time
                TimerStatusNotificationHelper.refreshIfVisible(context, ep.prefs(), timerManager)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun keyToTypeTarget(key: String): Pair<FolderType, WallpaperTarget> = when (key) {
        "home_image" -> FolderType.IMAGE to WallpaperTarget.HOME
        "home_video" -> FolderType.VIDEO to WallpaperTarget.HOME
        "lock_image" -> FolderType.IMAGE to WallpaperTarget.LOCK
        "lock_video" -> FolderType.VIDEO to WallpaperTarget.LOCK
        else -> throw IllegalArgumentException("Unknown timer key: $key")
    }

    private suspend fun defaultFolderUri(
        prefs: SettingsPreferences, type: FolderType, target: WallpaperTarget
    ): String? = when {
        type == FolderType.IMAGE && target == WallpaperTarget.HOME -> prefs.defaultHomeImageFolderUri.first()
        type == FolderType.IMAGE && target == WallpaperTarget.LOCK -> prefs.defaultLockImageFolderUri.first()
        type == FolderType.VIDEO && target == WallpaperTarget.HOME -> prefs.defaultHomeVideoFolderUri.first()
        type == FolderType.VIDEO && target == WallpaperTarget.LOCK -> prefs.defaultLockVideoFolderUri.first()
        else -> null
    }

    private suspend fun intervalForKey(prefs: SettingsPreferences, key: String): Int = when (key) {
        "home_image" -> prefs.homeImageTimerIntervalMin.first()
        "home_video" -> prefs.homeVideoTimerIntervalMin.first()
        "lock_image" -> prefs.lockImageTimerIntervalMin.first()
        "lock_video" -> prefs.lockVideoTimerIntervalMin.first()
        else -> 60
    }
}
