package com.klvw.wallpaper.tile

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class PopupItem(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val actionType: String,
    val target: String = "home",
    val folderUri: String = "",
    val imageUri: String = "",
    val iconColor: String = "auto",
    val mediaType: String = "image"
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("label", label)
        put("actionType", actionType)
        put("target", target)
        put("folderUri", folderUri)
        put("imageUri", imageUri)
        put("iconColor", iconColor)
        put("mediaType", mediaType)
    }

    companion object {
        fun fromJson(obj: JSONObject) = PopupItem(
            id = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
            label = obj.optString("label"),
            actionType = obj.optString("actionType"),
            target = obj.optString("target", "home"),
            folderUri = obj.optString("folderUri", ""),
            imageUri = obj.optString("imageUri", ""),
            iconColor = obj.optString("iconColor", "auto"),
            mediaType = obj.optString("mediaType", "image")
        )
    }
}

const val POPUP_ACTION_RANDOM_IMAGE = "random_image"
const val POPUP_ACTION_RANDOM_VIDEO = "random_video"
const val POPUP_ACTION_STATIC_IMAGE = "static_image"
const val POPUP_ACTION_RESTORE = "restore"
const val POPUP_ACTION_ICON_COLOR = "icon_color"
const val POPUP_ACTION_FOLDER_SELECT = "folder_select"
const val POPUP_ACTION_SELECT_FILE = "select_file"
const val POPUP_ACTION_TIMER = "timer"
const val POPUP_ACTION_FOLDER_SELECT_ALL = "folder_select_all"
const val POPUP_ACTION_DISPLAY_CONTROL = "display_control"

fun List<PopupItem>.toJsonString(): String =
    JSONArray().also { arr -> forEach { arr.put(it.toJson()) } }.toString()

fun popupItemsFromJson(json: String): List<PopupItem> = try {
    val arr = JSONArray(json)
    (0 until arr.length()).map { PopupItem.fromJson(arr.getJSONObject(it)) }
} catch (_: Exception) { emptyList() }
