package com.klvw.wallpaper.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface StaticImageDao {
    @Query("SELECT * FROM static_images ORDER BY addedAt DESC")
    fun getAll(): Flow<List<StaticImageEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(image: StaticImageEntity): Long

    @Delete
    suspend fun delete(image: StaticImageEntity)

    @Query("SELECT * FROM static_images WHERE uri = :uri LIMIT 1")
    suspend fun findByUri(uri: String): StaticImageEntity?

    @Query("SELECT * FROM static_images WHERE displayName = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByDisplayName(name: String): StaticImageEntity?
}
