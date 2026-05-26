package com.klvw.wallpaper.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.klvw.wallpaper.data.model.FolderType

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uri: String,
    val displayName: String,
    val type: FolderType,
    val addedAt: Long = System.currentTimeMillis(),
    val lastUsed: Long = 0L
)
