package com.klvw.wallpaper.data.model

import android.net.Uri
import androidx.compose.runtime.Immutable

@Immutable
data class WallpaperItem(
    val uri: Uri,
    val type: FolderType,
    val displayName: String,
    val folderUri: String,
    val lastModified: Long = 0L
)
