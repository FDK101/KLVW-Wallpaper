package com.klvw.wallpaper.util

import android.content.Context
import android.net.Uri
import com.klvw.wallpaper.data.model.FolderType
import com.klvw.wallpaper.data.model.WallpaperItem
import com.klvw.wallpaper.data.repository.FolderRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaUtils @Inject constructor(
    @ApplicationContext private val context: Context,
    private val folderRepository: FolderRepository
) {
    suspend fun getRandomItem(folderUri: String, type: FolderType): WallpaperItem? {
        return folderRepository.getItemsFromFolder(folderUri, type).randomOrNull()
    }

    suspend fun getAllItems(folderUris: List<String>, type: FolderType): List<WallpaperItem> {
        return folderUris.flatMap { folderRepository.getItemsFromFolder(it, type) }
    }
}
