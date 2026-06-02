package com.klvw.wallpaper.wear.comms

import org.json.JSONArray
import org.json.JSONObject

data class WatchTimerInfo(
    val key: String,
    val label: String,
    val intervalMin: Int,
    val nextFireMs: Long,
    val paused: Boolean
) {
    companion object {
        fun listFromJson(json: String): List<WatchTimerInfo> = try {
            val arr = JSONArray(json)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                WatchTimerInfo(
                    key         = o.optString("key"),
                    label       = o.optString("label"),
                    intervalMin = o.optInt("intervalMin", 60),
                    nextFireMs  = o.optLong("nextFireMs", 0L),
                    paused      = o.optBoolean("paused", false)
                )
            }
        } catch (_: Exception) { emptyList() }
    }
}
