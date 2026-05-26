package com.klvw.wallpaper.tasker

object TaskerBundle {
    const val BUNDLE_KEY_ACTION = "action"
    const val BUNDLE_KEY_FOLDER_URI = "folder_uri"
    const val BUNDLE_KEY_FOLDER_NAME = "folder_name"
    const val BUNDLE_KEY_TARGET = "target"

    const val ACTION_RANDOM_IMAGE = "set_random_image"
    const val ACTION_RANDOM_VIDEO = "set_random_video"
    const val ACTION_STATIC_IMAGE = "set_static_image"
    const val ACTION_RESTORE_PREVIOUS = "restore_previous"
    const val ACTION_SET_ICON_COLOR = "set_icon_color"

    const val BUNDLE_KEY_IMAGE_URI = "image_uri"
    const val BUNDLE_KEY_ICON_COLOR = "icon_color"

    const val ICON_COLOR_AUTO = "auto"
    const val ICON_COLOR_DARK = "dark"
    const val ICON_COLOR_LIGHT = "light"
    const val ICON_COLOR_INTERACTIVE = "__ask__"

    const val TARGET_HOME = "home"
    const val TARGET_LOCK = "lock"
    const val TARGET_BOTH = "both"

    // Sentinel: use the global default folder configured in the KLVW app's Folder Selector.
    const val FOLDER_URI_DEFAULT = "__default__"

    // Variable-to-folder mapping mode (Random Image / Random Video)
    // FOLDER_VAR_VALUE holds the %Variable reference — Tasker substitutes it at fire time.
    // FOLDER_MAPPINGS holds a JSON array: [{"v":"value","u":"content://...uri"}, ...]
    const val BUNDLE_KEY_FOLDER_VAR_VALUE = "folder_var_value"
    const val BUNDLE_KEY_FOLDER_MAPPINGS = "folder_mappings"

    const val PLUGIN_BUNDLE_KEY = "com.twofortyfouram.locale.intent.extra.BUNDLE"
    const val PLUGIN_BLURB_KEY = "com.twofortyfouram.locale.intent.extra.STRING_BLURB"
}
