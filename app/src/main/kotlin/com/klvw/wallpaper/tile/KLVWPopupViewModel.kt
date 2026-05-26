package com.klvw.wallpaper.tile

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.klvw.wallpaper.data.db.FolderEntity
import com.klvw.wallpaper.data.model.FolderType
import com.klvw.wallpaper.data.model.WallpaperItem
import com.klvw.wallpaper.data.model.WallpaperTarget
import com.klvw.wallpaper.data.prefs.SettingsPreferences
import com.klvw.wallpaper.data.repository.FolderRepository
import com.klvw.wallpaper.data.repository.StaticImageRepository
import com.klvw.wallpaper.data.repository.WallpaperRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class KLVWPopupViewModel @Inject constructor(
    private val prefs: SettingsPreferences,
    private val folderRepository: FolderRepository,
    private val wallpaperRepository: WallpaperRepository,
    private val staticImageRepository: StaticImageRepository,
    private val timerManager: WallpaperTimerManager
) : ViewModel() {

    val popupItems: StateFlow<List<PopupItem>> = prefs.popupItemsJson
        .map { popupItemsFromJson(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val imageFolders: StateFlow<List<FolderEntity>> = folderRepository
        .getFoldersByType(FolderType.IMAGE)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val videoFolders: StateFlow<List<FolderEntity>> = folderRepository
        .getFoldersByType(FolderType.VIDEO)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val staticImages: StateFlow<List<WallpaperItem>> = staticImageRepository
        .getAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val defaultHomeImageFolderUri: StateFlow<String?> = prefs.defaultHomeImageFolderUri
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val defaultHomeVideoFolderUri: StateFlow<String?> = prefs.defaultHomeVideoFolderUri
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val defaultLockImageFolderUri: StateFlow<String?> = prefs.defaultLockImageFolderUri
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val defaultLockVideoFolderUri: StateFlow<String?> = prefs.defaultLockVideoFolderUri
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val popupBgColor: StateFlow<String?> = prefs.popupBgColor
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val popupPrimaryTextColor: StateFlow<String?> = prefs.popupPrimaryTextColor
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val popupSecondaryTextColor: StateFlow<String?> = prefs.popupSecondaryTextColor
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val popupLayout: StateFlow<String> = prefs.popupLayout
        .stateIn(viewModelScope, SharingStarted.Eagerly, "list")
    val popupGridColumns: StateFlow<Int> = prefs.popupGridColumns
        .stateIn(viewModelScope, SharingStarted.Eagerly, 3)
    val popupWidthFraction: StateFlow<Float> = prefs.popupWidthFraction
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0.92f)
    val popupAutoScale: StateFlow<Boolean> = prefs.popupAutoScale
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val popupItemSizeDp: StateFlow<Int> = prefs.popupItemSizeDp
        .stateIn(viewModelScope, SharingStarted.Eagerly, 72)
    val popupScaleIconColor: StateFlow<Boolean> = prefs.popupScaleIconColor
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val popupScaleFolderSelect: StateFlow<Boolean> = prefs.popupScaleFolderSelect
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val popupScaleTimer: StateFlow<Boolean> = prefs.popupScaleTimer
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val popupScaleFolderSelectAll: StateFlow<Boolean> = prefs.popupScaleFolderSelectAll
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val appEnabled: StateFlow<Boolean> = prefs.appEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val quickSetHomeAction: StateFlow<String> = prefs.quickSetHomeAction
        .stateIn(viewModelScope, SharingStarted.Eagerly, "random_image")
    val quickSetLockAction: StateFlow<String> = prefs.quickSetLockAction
        .stateIn(viewModelScope, SharingStarted.Eagerly, "random_image")
    val quickSetHomeStaticUri: StateFlow<String?> = prefs.quickSetHomeStaticUri
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val quickSetLockStaticUri: StateFlow<String?> = prefs.quickSetLockStaticUri
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    // Timer state
    val timerPaused: StateFlow<Map<String, Boolean>> = timerManager.paused
    val timerNextFireTimes: StateFlow<Map<String, Long>> = timerManager.nextFireTimes
    // Display Control
    val displayControlHomeImage: StateFlow<Boolean> = prefs.displayControlHomeImage
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val displayControlHomeVideo: StateFlow<Boolean> = prefs.displayControlHomeVideo
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val displayControlLockImage: StateFlow<Boolean> = prefs.displayControlLockImage
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val displayControlLockVideo: StateFlow<Boolean> = prefs.displayControlLockVideo
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val popupScaleDisplayControl: StateFlow<Boolean> = prefs.popupScaleDisplayControl
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val displayControlResetHomeTimer: StateFlow<Boolean> = prefs.displayControlResetHomeTimer
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val displayControlResetLockTimer: StateFlow<Boolean> = prefs.displayControlResetLockTimer
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val homeImageTimerEnabled: StateFlow<Boolean> = prefs.homeImageTimerEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val homeImageTimerIntervalMin: StateFlow<Int> = prefs.homeImageTimerIntervalMin
        .stateIn(viewModelScope, SharingStarted.Eagerly, 60)
    val homeVideoTimerEnabled: StateFlow<Boolean> = prefs.homeVideoTimerEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val homeVideoTimerIntervalMin: StateFlow<Int> = prefs.homeVideoTimerIntervalMin
        .stateIn(viewModelScope, SharingStarted.Eagerly, 60)
    val lockImageTimerEnabled: StateFlow<Boolean> = prefs.lockImageTimerEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val lockImageTimerIntervalMin: StateFlow<Int> = prefs.lockImageTimerIntervalMin
        .stateIn(viewModelScope, SharingStarted.Eagerly, 60)
    val lockVideoTimerEnabled: StateFlow<Boolean> = prefs.lockVideoTimerEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val lockVideoTimerIntervalMin: StateFlow<Int> = prefs.lockVideoTimerIntervalMin
        .stateIn(viewModelScope, SharingStarted.Eagerly, 60)

    val pujieWatchFaces: StateFlow<List<PujieWatchFacePreset>> = prefs.pujieWatchFacesJson
        .map { PujieWatchFacePreset.fromJsonArray(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun firePujiePreset(preset: PujieWatchFacePreset, context: Context) {
        viewModelScope.launch {
            if (!prefs.appEnabled.first()) return@launch
            val bundle = Bundle().apply {
                putString("presetName", preset.presetName)
                putString("presetType", preset.presetType)
            }
            val intent = Intent("com.twofortyfouram.locale.intent.action.FIRE_SETTING").apply {
                setClassName(
                    "com.pujie.watchfaces",
                    "com.joaomgcd.taskerpluginlibrary.action.BroadcastReceiverAction"
                )
                putExtra("com.twofortyfouram.locale.intent.extra.BUNDLE", bundle)
            }
            context.sendBroadcast(intent)
        }
    }

    fun savePujieWatchFaces(presets: List<PujieWatchFacePreset>) {
        viewModelScope.launch {
            with(PujieWatchFacePreset) { prefs.setPujieWatchFacesJson(presets.toJsonString()) }
        }
    }

    fun setIconColor(color: String) {
        viewModelScope.launch { prefs.setForceIconColor(color) }
    }

    fun setPopupBgColor(hex: String?) { viewModelScope.launch { prefs.setPopupBgColor(hex) } }
    fun setPopupPrimaryTextColor(hex: String?) { viewModelScope.launch { prefs.setPopupPrimaryTextColor(hex) } }
    fun setPopupSecondaryTextColor(hex: String?) { viewModelScope.launch { prefs.setPopupSecondaryTextColor(hex) } }
    fun setPopupLayout(layout: String) { viewModelScope.launch { prefs.setPopupLayout(layout) } }
    fun setPopupGridColumns(columns: Int) { viewModelScope.launch { prefs.setPopupGridColumns(columns) } }
    fun setPopupWidthFraction(f: Float) { viewModelScope.launch { prefs.setPopupWidthFraction(f) } }
    fun setPopupAutoScale(auto: Boolean) { viewModelScope.launch { prefs.setPopupAutoScale(auto) } }
    fun setPopupItemSizeDp(dp: Int) { viewModelScope.launch { prefs.setPopupItemSizeDp(dp) } }
    fun setPopupScaleIconColor(enabled: Boolean) { viewModelScope.launch { prefs.setPopupScaleIconColor(enabled) } }
    fun setPopupScaleFolderSelect(enabled: Boolean) { viewModelScope.launch { prefs.setPopupScaleFolderSelect(enabled) } }
    fun setPopupScaleTimer(enabled: Boolean) { viewModelScope.launch { prefs.setPopupScaleTimer(enabled) } }
    fun setPopupScaleFolderSelectAll(enabled: Boolean) { viewModelScope.launch { prefs.setPopupScaleFolderSelectAll(enabled) } }
    fun setDisplayControl(key: String, enabled: Boolean) { viewModelScope.launch { prefs.setDisplayControl(key, enabled) } }
    fun setDisplayControlResetHomeTimer(enabled: Boolean) { viewModelScope.launch { prefs.setDisplayControlResetHomeTimer(enabled) } }
    fun setDisplayControlResetLockTimer(enabled: Boolean) { viewModelScope.launch { prefs.setDisplayControlResetLockTimer(enabled) } }
    fun setPopupScaleDisplayControl(enabled: Boolean) { viewModelScope.launch { prefs.setPopupScaleDisplayControl(enabled) } }
    // Quick Set tile config
    fun setQuickSetHomeAction(action: String) { viewModelScope.launch { prefs.setQuickSetHomeAction(action) } }
    fun setQuickSetLockAction(action: String) { viewModelScope.launch { prefs.setQuickSetLockAction(action) } }
    fun setQuickSetHomeStaticUri(uri: String?) { viewModelScope.launch { prefs.setQuickSetHomeStaticUri(uri) } }
    fun setQuickSetLockStaticUri(uri: String?) { viewModelScope.launch { prefs.setQuickSetLockStaticUri(uri) } }
    // Timer controls
    fun pauseTimer(key: String) = timerManager.pause(key)
    fun resumeTimer(key: String) = timerManager.resume(key)
    fun setTimerEnabled(key: String, enabled: Boolean) { viewModelScope.launch { prefs.setTimerEnabled(key, enabled) } }
    fun setTimerInterval(key: String, minutes: Int) { viewModelScope.launch { prefs.setTimerInterval(key, minutes) } }

    fun saveItems(items: List<PopupItem>) {
        viewModelScope.launch { prefs.setPopupItemsJson(items.toJsonString()) }
    }

    suspend fun executeItem(item: PopupItem, context: Context) {
        val enabled = prefs.appEnabled.first()
        if (!enabled && item.actionType != POPUP_ACTION_ICON_COLOR) return
        val target = when (item.target) {
            "lock" -> WallpaperTarget.LOCK
            "both" -> WallpaperTarget.BOTH
            else   -> WallpaperTarget.HOME
        }
        when (item.actionType) {
            POPUP_ACTION_RANDOM_IMAGE -> {
                val folderUri = item.folderUri.ifBlank {
                    when (item.target) {
                        "lock" -> prefs.defaultLockImageFolderUri.first()
                        else   -> prefs.defaultHomeImageFolderUri.first()
                    }
                } ?: return
                val wallpaperItem = folderRepository.getItemsFromFolder(folderUri, FolderType.IMAGE).randomOrNull() ?: return
                wallpaperRepository.setWallpaper(wallpaperItem, target)
            }
            POPUP_ACTION_RANDOM_VIDEO -> {
                val folderUri = item.folderUri.ifBlank {
                    when (item.target) {
                        "lock" -> prefs.defaultLockVideoFolderUri.first()
                        else   -> prefs.defaultHomeVideoFolderUri.first()
                    }
                } ?: return
                val wallpaperItem = folderRepository.getItemsFromFolder(folderUri, FolderType.VIDEO).randomOrNull() ?: return
                wallpaperRepository.setWallpaper(wallpaperItem, target)
            }
            POPUP_ACTION_STATIC_IMAGE -> {
                if (item.imageUri.isBlank()) return
                wallpaperRepository.setWallpaper(
                    WallpaperItem(Uri.parse(item.imageUri), FolderType.IMAGE, "", ""),
                    target
                )
            }
            POPUP_ACTION_RESTORE -> {
                val prevHomeUri = prefs.prevHomeWallpaperUri.first()
                val prevHomeIsVid = prefs.prevHomeIsVideo.first()
                val prevLockUri = prefs.prevLockWallpaperUri.first()
                val prevLockIsVid = prefs.prevLockIsVideo.first()
                when (target) {
                    WallpaperTarget.HOME -> prevHomeUri?.let {
                        wallpaperRepository.setWallpaper(
                            WallpaperItem(Uri.parse(it), if (prevHomeIsVid) FolderType.VIDEO else FolderType.IMAGE, "", ""),
                            WallpaperTarget.HOME
                        )
                    }
                    WallpaperTarget.LOCK -> prevLockUri?.let {
                        wallpaperRepository.setWallpaper(
                            WallpaperItem(Uri.parse(it), if (prevLockIsVid) FolderType.VIDEO else FolderType.IMAGE, "", ""),
                            WallpaperTarget.LOCK
                        )
                    }
                    WallpaperTarget.BOTH -> {
                        prevHomeUri?.let {
                            wallpaperRepository.setWallpaper(
                                WallpaperItem(Uri.parse(it), if (prevHomeIsVid) FolderType.VIDEO else FolderType.IMAGE, "", ""),
                                WallpaperTarget.HOME
                            )
                        }
                        prevLockUri?.let {
                            wallpaperRepository.setWallpaper(
                                WallpaperItem(Uri.parse(it), if (prevLockIsVid) FolderType.VIDEO else FolderType.IMAGE, "", ""),
                                WallpaperTarget.LOCK
                            )
                        }
                    }
                }
            }
            POPUP_ACTION_ICON_COLOR -> {
                prefs.setForceIconColor(item.iconColor)
            }
        }
    }

    suspend fun onFolderSelected(mediaType: String, target: String, folderUri: String) {
        prefs.setDefaultFolderUri(target, mediaType, folderUri)
    }

    suspend fun getItemsFromFolder(uri: String, type: FolderType): List<WallpaperItem> =
        folderRepository.getItemsFromFolder(uri, type)

    suspend fun setFileWallpaper(item: WallpaperItem, targetStr: String) {
        val target = when (targetStr) {
            "lock" -> WallpaperTarget.LOCK
            "both" -> WallpaperTarget.BOTH
            else   -> WallpaperTarget.HOME
        }
        wallpaperRepository.setWallpaper(item, target)
    }
}
