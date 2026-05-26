package com.klvw.wallpaper.data.repository

import android.net.Uri
import com.klvw.wallpaper.data.db.StaticImageDao
import com.klvw.wallpaper.data.db.StaticImageEntity
import com.klvw.wallpaper.data.model.FolderType
import com.klvw.wallpaper.data.model.WallpaperItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StaticImageRepository @Inject constructor(
    private val staticImageDao: StaticImageDao
) {
    fun getAll(): Flow<List<WallpaperItem>> = staticImageDao.getAll().map { entities ->
        entities.map { WallpaperItem(Uri.parse(it.uri), FolderType.IMAGE, it.displayName, "") }
    }

    suspend fun add(uri: Uri, displayName: String): Long {
        val existing = staticImageDao.findByUri(uri.toString())
        if (existing != null) return existing.id
        return staticImageDao.insert(StaticImageEntity(uri = uri.toString(), displayName = displayName))
    }

    suspend fun remove(uri: Uri) {
        val entity = staticImageDao.findByUri(uri.toString()) ?: return
        staticImageDao.delete(entity)
    }

    suspend fun findByDisplayName(name: String): WallpaperItem? =
        staticImageDao.findByDisplayName(name)
            ?.let { WallpaperItem(Uri.parse(it.uri), FolderType.IMAGE, it.displayName, "") }
}
