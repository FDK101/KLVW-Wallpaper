package com.klvw.wallpaper.tile

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.klvw.wallpaper.data.model.FolderType
import com.klvw.wallpaper.data.model.WallpaperItem
import com.klvw.wallpaper.data.model.WallpaperTarget
import com.klvw.wallpaper.data.prefs.SettingsPreferences
import com.klvw.wallpaper.data.repository.FolderRepository
import com.klvw.wallpaper.data.repository.WallpaperRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class KLVWQuickSetActivity : ComponentActivity() {

    @Inject lateinit var prefs: SettingsPreferences
    @Inject lateinit var wallpaperRepository: WallpaperRepository
    @Inject lateinit var folderRepository: FolderRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            if (prefs.appEnabled.first()) {
                val homeAction = prefs.quickSetHomeAction.first()
                val lockAction = prefs.quickSetLockAction.first()
                applyAction(homeAction, WallpaperTarget.HOME)
                applyAction(lockAction, WallpaperTarget.LOCK)
                if (homeAction == "static_image" && lockAction == "static_image") {
                    prefs.setAppEnabled(false)
                }
            }
            finish()
        }
    }

    private suspend fun applyAction(action: String, target: WallpaperTarget) {
        when (action) {
            "random_image" -> {
                val uri = resolveDefaultFolderUri(FolderType.IMAGE, target) ?: return
                val item = folderRepository.getItemsFromFolder(uri, FolderType.IMAGE).randomOrNull() ?: return
                wallpaperRepository.setWallpaper(item, target)
            }
            "random_video" -> {
                val uri = resolveDefaultFolderUri(FolderType.VIDEO, target) ?: return
                val item = folderRepository.getItemsFromFolder(uri, FolderType.VIDEO).randomOrNull() ?: return
                wallpaperRepository.setWallpaper(item, target)
            }
            "static_image" -> {
                val uri = when (target) {
                    WallpaperTarget.HOME -> prefs.quickSetHomeStaticUri.first()
                    WallpaperTarget.LOCK -> prefs.quickSetLockStaticUri.first()
                    else -> null
                } ?: return
                wallpaperRepository.setWallpaper(
                    WallpaperItem(Uri.parse(uri), FolderType.IMAGE, "", ""),
                    target
                )
            }
        }
    }

    private suspend fun resolveDefaultFolderUri(type: FolderType, target: WallpaperTarget): String? = when {
        type == FolderType.IMAGE && target == WallpaperTarget.HOME -> prefs.defaultHomeImageFolderUri.first()
        type == FolderType.IMAGE && target == WallpaperTarget.LOCK -> prefs.defaultLockImageFolderUri.first()
        type == FolderType.VIDEO && target == WallpaperTarget.HOME -> prefs.defaultHomeVideoFolderUri.first()
        type == FolderType.VIDEO && target == WallpaperTarget.LOCK -> prefs.defaultLockVideoFolderUri.first()
        else -> null
    }
}
