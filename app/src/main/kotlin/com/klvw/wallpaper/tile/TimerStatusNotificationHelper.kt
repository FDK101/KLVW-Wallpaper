package com.klvw.wallpaper.tile

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.klvw.wallpaper.R

object TimerStatusNotificationHelper {

    const val NOTIFICATION_ID = 7001
    const val CHANNEL_ID = "timer_status"

    const val ACTION_TIMER_ACTION = "com.klvw.wallpaper.TIMER_NOTIFICATION_ACTION"
    const val EXTRA_ACTION = "timer_notification_action"
    const val EXTRA_TIMER_KEY = "timer_notification_key"
    const val EXTRA_TIMER_KEYS = "timer_notification_keys"
    const val VALUE_PAUSE = "pause"
    const val VALUE_RESET = "reset"

    fun show(
        context: Context,
        enabledKeys: List<String>,
        pausedMap: Map<String, Boolean>,
        nextFireTimes: Map<String, Long>
    ) {
        val now = System.currentTimeMillis()

        // Build per-timer status lines
        val lines = enabledKeys.map { key ->
            val paused = pausedMap[key] == true
            val fireAt = nextFireTimes[key]
            val status = when {
                paused -> "Paused"
                fireAt != null -> formatRemaining(fireAt - now)
                else -> "—"
            }
            "${timerLabel(key)}: $status"
        }

        val contentText = if (enabledKeys.size == 1) lines.first()
                          else "${enabledKeys.size} timers running"

        // Active (non-paused) keys are the ones that can be paused
        val pausableKeys = enabledKeys.filter { pausedMap[it] != true }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_timer_notification)
            .setContentTitle("KLVW Timers")
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n")))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .also { builder ->
                if (pausableKeys.isNotEmpty()) {
                    builder.addAction(
                        android.R.drawable.ic_media_pause,
                        "Pause",
                        buildActionIntent(context, VALUE_PAUSE, pausableKeys, reqCode = 8001)
                    )
                }
                builder.addAction(
                    android.R.drawable.ic_menu_rotate,
                    "Reset",
                    buildActionIntent(context, VALUE_RESET, enabledKeys, reqCode = 8002)
                )
            }
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private fun buildActionIntent(
        context: Context,
        action: String,
        keys: List<String>,
        reqCode: Int
    ): PendingIntent {
        return if (keys.size == 1) {
            // Single timer — fire broadcast directly to TimerActionReceiver
            PendingIntent.getBroadcast(
                context, reqCode,
                Intent(ACTION_TIMER_ACTION, null, context, TimerActionReceiver::class.java).apply {
                    putExtra(EXTRA_ACTION, action)
                    putExtra(EXTRA_TIMER_KEY, keys.first())
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        } else {
            // Multiple timers — open picker activity
            PendingIntent.getActivity(
                context, reqCode,
                Intent(context, TimerActionPickerActivity::class.java).apply {
                    putExtra(EXTRA_ACTION, action)
                    putStringArrayListExtra(EXTRA_TIMER_KEYS, ArrayList(keys))
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
    }

    fun timerLabel(key: String) = when (key) {
        "home_image" -> "Home Image"
        "home_video" -> "Home Video"
        "lock_image" -> "Lock Image"
        "lock_video" -> "Lock Video"
        else -> key
    }

    fun formatRemaining(ms: Long): String {
        if (ms <= 0) return "now"
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return when {
            h > 0 -> "${h}h ${m}m"
            m > 0 -> "${m}m ${s}s"
            else -> "${s}s"
        }
    }
}
