package com.klvw.wallpaper.tasker

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.klvw.wallpaper.tile.IconColorTileConfig
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.klvw.wallpaper.data.model.FolderType
import com.klvw.wallpaper.data.model.WallpaperItem
import com.klvw.wallpaper.data.model.WallpaperTarget
import com.klvw.wallpaper.data.prefs.SettingsPreferences
import com.klvw.wallpaper.data.repository.FolderRepository
import com.klvw.wallpaper.data.repository.StaticImageRepository
import com.klvw.wallpaper.data.repository.WallpaperRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import javax.inject.Inject

@AndroidEntryPoint
class TaskerActionFire : BroadcastReceiver() {

    @Inject lateinit var folderRepository: FolderRepository
    @Inject lateinit var wallpaperRepository: WallpaperRepository
    @Inject lateinit var staticImageRepository: StaticImageRepository
    @Inject lateinit var prefs: SettingsPreferences

    private var ctx: Context? = null
    private var notifId = 1000

    private fun debugNotify(msg: String) {
        val c = ctx ?: return
        val nm = c.getSystemService(NotificationManager::class.java)
        val n = NotificationCompat.Builder(c, "klvw_debug")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("KLVW Debug")
            .setContentText(msg)
            .setStyle(NotificationCompat.BigTextStyle().bigText(msg))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        nm.notify(notifId++, n)
    }

    override fun onReceive(context: Context, intent: Intent) {
        ctx = context
        val bundle = intent.getBundleExtra(TaskerBundle.PLUGIN_BUNDLE_KEY) ?: return

        val action = bundle.getString(TaskerBundle.BUNDLE_KEY_ACTION) ?: return
        val targetStr = bundle.getString(TaskerBundle.BUNDLE_KEY_TARGET) ?: TaskerBundle.TARGET_HOME

        val target = when (targetStr.lowercase()) {
            TaskerBundle.TARGET_LOCK -> WallpaperTarget.LOCK
            TaskerBundle.TARGET_BOTH -> WallpaperTarget.BOTH
            else -> WallpaperTarget.HOME
        }

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (action) {
                    TaskerBundle.ACTION_SET_ICON_COLOR -> {
                        val color = bundle.getString(TaskerBundle.BUNDLE_KEY_ICON_COLOR) ?: return@launch
                        if (color == TaskerBundle.ICON_COLOR_INTERACTIVE) {
                            context.startActivity(
                                Intent(context, IconColorTileConfig::class.java)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        } else {
                            prefs.setForceIconColor(color.lowercase())
                        }
                    }
                    TaskerBundle.ACTION_RESTORE_PREVIOUS -> {
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
                    else -> {
                        val item: WallpaperItem? = when (action) {
                            TaskerBundle.ACTION_STATIC_IMAGE -> {
                                val rawUri = bundle.getString(TaskerBundle.BUNDLE_KEY_IMAGE_URI) ?: return@launch
                                if (rawUri.startsWith("content://")) {
                                    WallpaperItem(Uri.parse(rawUri), FolderType.IMAGE, "", "")
                                } else {
                                    staticImageRepository.findByDisplayName(rawUri.trim())
                                }
                            }
                            TaskerBundle.ACTION_RANDOM_VIDEO -> {
                                resolveFolder(bundle, FolderType.VIDEO) ?: return@launch
                            }
                            else -> {
                                resolveFolder(bundle, FolderType.IMAGE) ?: return@launch
                            }
                        }
                        item?.let { wallpaperRepository.setWallpaper(it, target) }
                    }
                }
            } catch (e: Exception) {
                debugNotify("Error: ${e.javaClass.simpleName}: ${e.message}")
            } finally {
                pending.finish()
            }
        }
    }

    // Resolves the folder for random image/video actions.
    // Mapping mode: matches FOLDER_VAR_VALUE against FOLDER_MAPPINGS to find the folder URI.
    // Direct mode: FOLDER_URI holds the content URI directly (or blank = all folders).
    // Returns null to skip the action (no match found).
    private suspend fun resolveFolder(bundle: android.os.Bundle, type: FolderType): WallpaperItem? {
        val mappingsJson = bundle.getString(TaskerBundle.BUNDLE_KEY_FOLDER_MAPPINGS)
        if (!mappingsJson.isNullOrEmpty()) {
            val varValue = bundle.getString(TaskerBundle.BUNDLE_KEY_FOLDER_VAR_VALUE)?.trim() ?: run {
                debugNotify("Variable value missing in bundle")
                return null
            }
            val mappings = parseMappings(mappingsJson)
            val folderUri = mappings.find { it.first.equals(varValue, ignoreCase = true) }?.second
            if (folderUri == null) {
                debugNotify("No match for \"$varValue\" — configured keys: ${mappings.map { "\"${it.first}\"" }}")
                return null
            }
            return folderRepository.getItemsFromFolder(folderUri, type).randomOrNull()
        }

        val folderUri = bundle.getString(TaskerBundle.BUNDLE_KEY_FOLDER_URI)?.trim() ?: ""
        val targetStr = bundle.getString(TaskerBundle.BUNDLE_KEY_TARGET) ?: TaskerBundle.TARGET_HOME
        return when {
            folderUri == TaskerBundle.FOLDER_URI_DEFAULT -> {
                val defaultUri = when {
                    type == FolderType.IMAGE && targetStr == TaskerBundle.TARGET_LOCK ->
                        prefs.defaultLockImageFolderUri.first()
                    type == FolderType.IMAGE ->
                        prefs.defaultHomeImageFolderUri.first()
                    type == FolderType.VIDEO && targetStr == TaskerBundle.TARGET_LOCK ->
                        prefs.defaultLockVideoFolderUri.first()
                    else ->
                        prefs.defaultHomeVideoFolderUri.first()
                }
                if (defaultUri != null) {
                    folderRepository.getItemsFromFolder(defaultUri, type).randomOrNull()
                } else {
                    debugNotify("No global default folder configured for ${type.name} / $targetStr — set one in KLVW → Folder Selector")
                    null
                }
            }
            folderUri.isBlank() -> {
                folderRepository.getFoldersByType(type).first()
                    .flatMap { folderRepository.getItemsFromFolder(it.uri, type) }
                    .randomOrNull()
            }
            folderUri.startsWith("content://") -> folderRepository.getItemsFromFolder(folderUri, type).randomOrNull()
            else -> {
                // Variable was substituted to a display name — resolve to URI first
                val folder = folderRepository.findFolderByDisplayName(folderUri, type)
                if (folder == null) {
                    debugNotify("No folder found with name \"$folderUri\"")
                    null
                } else {
                    folderRepository.getItemsFromFolder(folder.uri, type).randomOrNull()
                }
            }
        }
    }

    private fun parseMappings(json: String): List<Pair<String, String>> = try {
        val arr = JSONArray(json)
        (0 until arr.length()).mapNotNull { i ->
            val obj = arr.getJSONObject(i)
            val v = obj.optString("v")
            val u = obj.optString("u")
            if (v.isNotEmpty() && u.isNotEmpty()) v to u else null
        }
    } catch (_: Exception) { emptyList() }
}
