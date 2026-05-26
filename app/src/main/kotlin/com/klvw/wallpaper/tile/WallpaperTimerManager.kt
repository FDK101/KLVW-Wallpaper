package com.klvw.wallpaper.tile

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.klvw.wallpaper.data.prefs.SettingsPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WallpaperTimerManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: SettingsPreferences
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _paused = MutableStateFlow(
        mapOf(
            "home_image" to false,
            "home_video" to false,
            "lock_image" to false,
            "lock_video" to false
        )
    )
    val paused: StateFlow<Map<String, Boolean>> = _paused.asStateFlow()

    private val _nextFireTimes = MutableStateFlow<Map<String, Long>>(emptyMap())
    val nextFireTimes: StateFlow<Map<String, Long>> = _nextFireTimes.asStateFlow()

    init {
        launchTimer("home_image")
        launchTimer("home_video")
        launchTimer("lock_image")
        launchTimer("lock_video")
    }

    private fun launchTimer(key: String) {
        scope.launch {
            combine(
                enabledFlow(key),
                intervalFlow(key),
                _paused.map { it[key] == true }
            ) { enabled, intervalMin, paused ->
                Triple(enabled, intervalMin, paused)
            }.collectLatest { (enabled, intervalMin, paused) ->
                if (!enabled || paused) {
                    cancelAlarm(key)
                    return@collectLatest
                }
                scheduleAlarm(key, intervalMin)
                // Suspend until collectLatest cancels this block on next pref change
                awaitCancellation()
            }
        }
    }

    fun scheduleAlarm(key: String, intervalMin: Int) {
        val fireAt = System.currentTimeMillis() + intervalMin * 60_000L
        context.getSystemService(AlarmManager::class.java).setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP, fireAt, buildPendingIntent(key)
        )
        _nextFireTimes.update { it + (key to fireAt) }
    }

    fun cancelAlarm(key: String) {
        context.getSystemService(AlarmManager::class.java).cancel(buildPendingIntent(key))
        _nextFireTimes.update { it - key }
    }

    private fun buildPendingIntent(key: String): PendingIntent {
        val requestCode = when (key) {
            "home_image" -> 3001
            "home_video" -> 3002
            "lock_image" -> 3003
            "lock_video" -> 3004
            else -> throw IllegalArgumentException("Unknown timer key: $key")
        }
        return PendingIntent.getBroadcast(
            context, requestCode,
            Intent(context, WallpaperTimerReceiver::class.java).apply {
                action = ACTION_TIMER_FIRE
                putExtra(EXTRA_TIMER_KEY, key)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    fun pause(key: String) = _paused.update { it + (key to true) }
    fun resume(key: String) = _paused.update { it + (key to false) }

    private fun enabledFlow(key: String): Flow<Boolean> = when (key) {
        "home_image" -> prefs.homeImageTimerEnabled
        "home_video" -> prefs.homeVideoTimerEnabled
        "lock_image" -> prefs.lockImageTimerEnabled
        "lock_video" -> prefs.lockVideoTimerEnabled
        else -> throw IllegalArgumentException("Unknown timer key: $key")
    }

    private fun intervalFlow(key: String): Flow<Int> = when (key) {
        "home_image" -> prefs.homeImageTimerIntervalMin
        "home_video" -> prefs.homeVideoTimerIntervalMin
        "lock_image" -> prefs.lockImageTimerIntervalMin
        "lock_video" -> prefs.lockVideoTimerIntervalMin
        else -> throw IllegalArgumentException("Unknown timer key: $key")
    }

    companion object {
        const val ACTION_TIMER_FIRE = "com.klvw.wallpaper.TIMER_FIRE"
        const val EXTRA_TIMER_KEY = "timer_key"
    }
}
