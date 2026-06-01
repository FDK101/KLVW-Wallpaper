package com.klvw.wallpaper.wear

import org.json.JSONArray
import org.json.JSONObject

data class WatchPopupItem(
    val id: String,
    val label: String,
    val actionType: String,
    val target: String = "home",
    val folderUri: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("label", label)
        put("actionType", actionType)
        put("target", target)
        put("folderUri", folderUri)
    }

    companion object {
        fun fromJson(o: JSONObject) = WatchPopupItem(
            id         = o.optString("id"),
            label      = o.optString("label"),
            actionType = o.optString("actionType"),
            target     = o.optString("target", "home"),
            folderUri  = o.optString("folderUri", "")
        )

        fun listFromJson(json: String): List<WatchPopupItem> = try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
        } catch (_: Exception) { emptyList() }
    }
}

fun List<WatchPopupItem>.toWatchJsonString(): String =
    JSONArray().also { arr -> forEach { arr.put(it.toJson()) } }.toString()
