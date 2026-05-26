package com.klvw.wallpaper.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.klvw.wallpaper.data.model.FolderType

@Database(entities = [FolderEntity::class, StaticImageEntity::class], version = 2, exportSchema = false)
@TypeConverters(AppDatabase.Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun folderDao(): FolderDao
    abstract fun staticImageDao(): StaticImageDao

    class Converters {
        @TypeConverter
        fun fromFolderType(value: FolderType): String = value.name

        @TypeConverter
        fun toFolderType(value: String): FolderType = FolderType.valueOf(value)
    }
}
