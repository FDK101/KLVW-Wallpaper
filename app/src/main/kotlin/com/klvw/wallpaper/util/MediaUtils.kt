package com.klvw.wallpaper.util

import android.content.Context
import android.net.Uri
import com.klvw.wallpaper.data.model.FolderType
import com.klvw.wallpaper.data.model.WallpaperItem
import com.klvw.wallpaper.data.prefs.ShuffleHistoryManager
import com.klvw.wallpaper.data.repository.FolderRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaUtils @Inject constructor(
    @ApplicationContext private val context: Context,
    private val folderRepository: FolderRepository,
    private val shuffleHistoryManager: ShuffleHistoryManager
) {
    suspend fun getRandomItem(folderUri: String, type: FolderType): WallpaperItem? {
        val items = folderRepository.getItemsFromFolder(folderUri, type)
        return shuffleHistoryManager.pickNext(items, folderUri, type)
    }

    suspend fun getAllItems(folderUris: List<String>, type: FolderType): List<WallpaperItem> {
        return folderUris.flatMap { folderRepository.getItemsFromFolder(it, type) }
    }
}
