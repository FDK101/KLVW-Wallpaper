package com.klvw.wallpaper.tile

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.klvw.wallpaper.data.prefs.SettingsPreferences
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class TimerActionReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ActionEntryPoint {
        fun timerManager(): WallpaperTimerManager
        fun prefs(): SettingsPreferences
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TimerStatusNotificationHelper.ACTION_TIMER_ACTION) return
        val action = intent.getStringExtra(TimerStatusNotificationHelper.EXTRA_ACTION) ?: return
        val key = intent.getStringExtra(TimerStatusNotificationHelper.EXTRA_TIMER_KEY) ?: return

        val ep = EntryPointAccessors.fromApplication(
            context.applicationContext, ActionEntryPoint::class.java
        )
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                applyAction(action, key, ep.timerManager(), ep.prefs())
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        suspend fun applyAction(
            action: String,
            key: String,
            timerManager: WallpaperTimerManager,
            prefs: SettingsPreferences
        ) {
            when (action) {
                TimerStatusNotificationHelper.VALUE_PAUSE -> timerManager.pause(key)
                TimerStatusNotificationHelper.VALUE_RESET -> {
                    val interval = intervalForKey(prefs, key)
                    timerManager.resume(key)
                    timerManager.scheduleAlarm(key, interval)
                }
            }
        }

        private suspend fun intervalForKey(prefs: SettingsPreferences, key: String) = when (key) {
            "home_image" -> prefs.homeImageTimerIntervalMin.first()
            "home_video" -> prefs.homeVideoTimerIntervalMin.first()
            "lock_image" -> prefs.lockImageTimerIntervalMin.first()
            "lock_video" -> prefs.lockVideoTimerIntervalMin.first()
            else -> 60
        }
    }
}
