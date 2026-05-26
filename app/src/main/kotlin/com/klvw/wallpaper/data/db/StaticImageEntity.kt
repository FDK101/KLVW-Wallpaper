package com.klvw.wallpaper.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "static_images")
data class StaticImageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uri: String,
    val displayName: String,
    val addedAt: Long = System.currentTimeMillis()
)
