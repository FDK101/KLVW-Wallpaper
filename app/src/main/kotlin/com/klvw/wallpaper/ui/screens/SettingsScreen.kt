package com.klvw.wallpaper.ui.screens

import android.app.Activity.RESULT_OK
import android.app.WallpaperManager
import android.content.Intent
import android.os.Bundle
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.SingletonImageLoader
import com.klvw.wallpaper.aer.AerLockStore
import com.klvw.wallpaper.aer.AerMountActivity
import com.klvw.wallpaper.aer.AerShell
import com.klvw.wallpaper.aer.ui.AerActivity
import com.klvw.wallpaper.tile.KLVWPopupViewModel
import com.klvw.wallpaper.tile.PopupItem
import com.klvw.wallpaper.tile.PujieWatchFacePreset
import com.klvw.wallpaper.wear.WatchPopupItem
import com.klvw.wallpaper.wear.toWatchJsonString
import com.klvw.wallpaper.tile.PujieWatchFacePreset.Companion.toJsonString
import com.klvw.wallpaper.ui.theme.SurfaceGlass
import com.klvw.wallpaper.ui.viewmodel.WallpaperViewModel

@Composable
fun SettingsScreen(viewModel: WallpaperViewModel, popupViewModel: KLVWPopupViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(Modifier.height(8.dp))

        SettingsSection(title = "Performance") {
            SettingToggle(
                icon = Icons.Default.Bolt,
                title = "Vulkan GPU Rendering",
                subtitle = "Use Vulkan for hardware-accelerated image rendering",
                checked = state.useVulkan,
                onCheckedChange = { viewModel.setUseVulkan(it) }
            )
            HorizontalDivider(thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 4.dp))
            val imageLoader = SingletonImageLoader.get(context)
            var cacheCleared by remember { mutableStateOf(false) }
            SettingAction(
                icon = Icons.Default.DeleteSweep,
                title = "Clear Thumbnail Cache",
                subtitle = if (cacheCleared) "Cache cleared" else "Remove cached thumbnails so replaced files show fresh",
                onClick = {
                    imageLoader.memoryCache?.clear()
                    imageLoader.diskCache?.clear()
                    cacheCleared = true
                }
            )
        }

        SettingsSection(title = "Status Bar Icons") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Icon Color", style = MaterialTheme.typography.bodyMedium)
                    Text("Override status bar icon brightness", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("auto" to "Auto", "dark" to "Dark", "light" to "Light").forEach { (value, label) ->
                            FilterChip(
                                selected = state.forceIconColor == value,
                                onClick = { viewModel.setForceIconColor(value) },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            }
        }

        SettingsSection(title = "Live Wallpaper") {
            SettingAction(
                icon = Icons.Default.Tune,
                title = "Activate Live Wallpaper",
                subtitle = "Set KLVW as the live wallpaper service",
                onClick = {
                    try {
                        val intent = Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)
                        context.startActivity(intent)
                    } catch (_: Exception) { }
                }
            )
        }

        SettingsSection(title = "KLVW Popup Menu") {
            KLVWPopupSection(popupViewModel = popupViewModel)
        }

        SettingsSection(title = "KLVW Quick Set Tile") {
            QuickSetTileSection(popupViewModel = popupViewModel)
        }

        SettingsSection(title = "Pujie Watch Faces") {
            PujieWatchFaceSection(popupViewModel = popupViewModel)
        }

        SettingsSection(title = "KLVW Display Control") {
            DisplayControlSection(popupViewModel = popupViewModel)
        }

        SettingsSection(title = "Wallpaper Timers") {
            TimerNotificationSection(popupViewModel = popupViewModel)
            TimerGlobalOffSection(popupViewModel = popupViewModel)
        }

        SettingsSection(title = "KLVW Watch") {
            KLVWWatchSection(popupViewModel = popupViewModel)
        }

        SettingsSection(title = "Aer Private Storage") {
            AerStorageSection(popupViewModel)
        }

        SettingsSection(title = "Popup Appearance") {
            PopupAppearanceSection(popupViewModel = popupViewModel)
        }

        SettingsSection(title = "About") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceGlass)
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Info, contentDescription = null,
                            modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("KLVW Wallpaper", style = MaterialTheme.typography.titleMedium)
                    }
                    Text("Version 1.0.0", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Samsung UI 8 · Android 16 · Vulkan GPU",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text("Tasker Plugin: Add the KLVW action in Tasker → New Task → + → Plugin → KLVW Wallpaper",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = MaterialTheme.typography.bodySmall.lineHeight)
                }
            }
        }

        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun KLVWPopupSection(popupViewModel: KLVWPopupViewModel) {
    val items by popupViewModel.popupItems.collectAsStateWithLifecycle()
    val imageFolders by popupViewModel.imageFolders.collectAsStateWithLifecycle()
    val videoFolders by popupViewModel.videoFolders.collectAsStateWithLifecycle()
    val staticImages by popupViewModel.staticImages.collectAsStateWithLifecycle()
    val popupLayout by popupViewModel.popupLayout.collectAsStateWithLifecycle()
    val popupGridColumns by popupViewModel.popupGridColumns.collectAsStateWithLifecycle()
    val popupWidthFraction by popupViewModel.popupWidthFraction.collectAsStateWithLifecycle()
    val popupAutoScale by popupViewModel.popupAutoScale.collectAsStateWithLifecycle()
    val popupItemSizeDp by popupViewModel.popupItemSizeDp.collectAsStateWithLifecycle()
    val popupScaleIconColor by popupViewModel.popupScaleIconColor.collectAsStateWithLifecycle()
    val popupScaleFolderSelect by popupViewModel.popupScaleFolderSelect.collectAsStateWithLifecycle()
    val popupScaleTimer by popupViewModel.popupScaleTimer.collectAsStateWithLifecycle()
    val popupScaleFolderSelectAll by popupViewModel.popupScaleFolderSelectAll.collectAsStateWithLifecycle()
    val popupScaleDisplayControl by popupViewModel.popupScaleDisplayControl.collectAsStateWithLifecycle()

    var editorItem by remember { mutableStateOf<PopupItem?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OverlayPermissionRow()
        HorizontalDivider(thickness = 0.5.dp)

        // ── Layout & auto-scale (inline) ─────────────────────────────────────
        Text("Item Layout", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = popupLayout == "list",
                    onClick = { popupViewModel.setPopupLayout("list") },
                    label = { Text("List") },
                    leadingIcon = { Icon(Icons.Default.ViewList, null, Modifier.size(16.dp)) }
                )
                FilterChip(
                    selected = popupLayout == "grid",
                    onClick = { popupViewModel.setPopupLayout("grid") },
                    label = { Text("Grid") },
                    leadingIcon = { Icon(Icons.Default.GridView, null, Modifier.size(16.dp)) }
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Checkbox(
                    checked = popupAutoScale,
                    onCheckedChange = { popupViewModel.setPopupAutoScale(it) }
                )
                Text("Auto-scale", style = MaterialTheme.typography.bodySmall)
            }
        }
        if (popupLayout == "grid") {
            Text("Columns", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (2..5).forEach { n ->
                    FilterChip(
                        selected = popupGridColumns == n,
                        onClick = { popupViewModel.setPopupGridColumns(n) },
                        label = { Text("$n") }
                    )
                }
            }
        }

        HorizontalDivider(thickness = 0.5.dp)

        // ── Size & Scaling ────────────────────────────────────────────────────
        Text("Popup Width", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0.60f to "Narrow", 0.78f to "Medium", 0.92f to "Wide").forEach { (frac, lbl) ->
                FilterChip(
                    selected = kotlin.math.abs(popupWidthFraction - frac) < 0.02f,
                    onClick = { popupViewModel.setPopupWidthFraction(frac) },
                    label = { Text(lbl) }
                )
            }
        }
        if (!popupAutoScale) {
            Text("Manual Item Size", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(40 to "XS", 56 to "S", 72 to "M", 88 to "L", 104 to "XL").forEach { (sz, lbl) ->
                    FilterChip(
                        selected = popupItemSizeDp == sz,
                        onClick = { popupViewModel.setPopupItemSizeDp(sz) },
                        label = { Text(lbl) }
                    )
                }
            }
        }

        Text("Apply width to overlays", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Checkbox(checked = popupScaleIconColor, onCheckedChange = { popupViewModel.setPopupScaleIconColor(it) })
            Text("Icon Color Picker", style = MaterialTheme.typography.bodySmall)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Checkbox(checked = popupScaleFolderSelect, onCheckedChange = { popupViewModel.setPopupScaleFolderSelect(it) })
            Text("Folder Selector", style = MaterialTheme.typography.bodySmall)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Checkbox(checked = popupScaleTimer, onCheckedChange = { popupViewModel.setPopupScaleTimer(it) })
            Text("Timer Popup", style = MaterialTheme.typography.bodySmall)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Checkbox(checked = popupScaleFolderSelectAll, onCheckedChange = { popupViewModel.setPopupScaleFolderSelectAll(it) })
            Text("All Folders Popup", style = MaterialTheme.typography.bodySmall)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Checkbox(checked = popupScaleDisplayControl, onCheckedChange = { popupViewModel.setPopupScaleDisplayControl(it) })
            Text("Display Control Popup", style = MaterialTheme.typography.bodySmall)
        }

        HorizontalDivider(thickness = 0.5.dp)

        if (items.isEmpty()) {
            Text(
                "No popup items configured yet. Tap Add Item to build your popup menu.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            items.forEachIndexed { index, item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.label, style = MaterialTheme.typography.bodyMedium)
                        Text(item.actionSubtitle(), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    // Move up
                    if (index > 0) {
                        IconButton(onClick = {
                            val mutable = items.toMutableList()
                            mutable.add(index - 1, mutable.removeAt(index))
                            popupViewModel.saveItems(mutable)
                        }) { Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move up",
                            modifier = Modifier.size(20.dp)) }
                    } else {
                        Spacer(Modifier.size(48.dp))
                    }
                    // Move down
                    if (index < items.lastIndex) {
                        IconButton(onClick = {
                            val mutable = items.toMutableList()
                            mutable.add(index + 1, mutable.removeAt(index))
                            popupViewModel.saveItems(mutable)
                        }) { Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move down",
                            modifier = Modifier.size(20.dp)) }
                    } else {
                        Spacer(Modifier.size(48.dp))
                    }
                    // Edit
                    IconButton(onClick = { editorItem = item; showEditor = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(20.dp))
                    }
                    // Delete
                    IconButton(onClick = { popupViewModel.saveItems(items.filter { it.id != item.id }) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.error)
                    }
                }
                if (index < items.lastIndex) HorizontalDivider(thickness = 0.5.dp)
            }
        }

        Button(
            onClick = { editorItem = null; showEditor = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Add Item")
        }
    }

    if (showEditor) {
        PopupItemEditorDialog(
            existingItem = editorItem,
            imageFolders = imageFolders,
            videoFolders = videoFolders,
            staticImages = staticImages,
            onSave = { saved ->
                val mutable = items.toMutableList()
                val existingIndex = mutable.indexOfFirst { it.id == saved.id }
                if (existingIndex >= 0) mutable[existingIndex] = saved else mutable.add(saved)
                popupViewModel.saveItems(mutable)
                showEditor = false
            },
            onDismiss = { showEditor = false }
        )
    }
}

@Composable
private fun QuickSetTileSection(popupViewModel: KLVWPopupViewModel) {
    val homeAction by popupViewModel.quickSetHomeAction.collectAsStateWithLifecycle()
    val lockAction by popupViewModel.quickSetLockAction.collectAsStateWithLifecycle()
    val homeStaticUri by popupViewModel.quickSetHomeStaticUri.collectAsStateWithLifecycle()
    val lockStaticUri by popupViewModel.quickSetLockStaticUri.collectAsStateWithLifecycle()
    val staticImages by popupViewModel.staticImages.collectAsStateWithLifecycle()
    val watchPresetId by popupViewModel.quickSetWatchPresetId.collectAsStateWithLifecycle()
    val pujiePresets by popupViewModel.pujieWatchFaces.collectAsStateWithLifecycle()

    val actions = listOf(
        "random_image" to "Random Image",
        "random_video" to "Random Video",
        "static_image" to "Static Image"
    )
    val isKillSwitch = homeAction == "static_image" && lockAction == "static_image"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Configure what the KLVW Quick Set tile does when tapped.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Home action
        Text("Home Screen", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            actions.forEach { (value, label) ->
                FilterChip(
                    selected = homeAction == value,
                    onClick = { popupViewModel.setQuickSetHomeAction(value) },
                    label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }
        if (homeAction == "static_image") {
            if (staticImages.isEmpty()) {
                Text(
                    "No static images saved. Add a Static Image action in KLVW Popup Menu first.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                staticImages.forEach { img ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = homeStaticUri == img.uri.toString(),
                            onClick = { popupViewModel.setQuickSetHomeStaticUri(img.uri.toString()) }
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(img.displayName, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        HorizontalDivider(thickness = 0.5.dp)

        // Lock action
        Text("Lock Screen", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            actions.forEach { (value, label) ->
                FilterChip(
                    selected = lockAction == value,
                    onClick = { popupViewModel.setQuickSetLockAction(value) },
                    label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }
        if (lockAction == "static_image") {
            if (staticImages.isEmpty()) {
                Text(
                    "No static images saved. Add a Static Image action in KLVW Popup Menu first.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                staticImages.forEach { img ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = lockStaticUri == img.uri.toString(),
                            onClick = { popupViewModel.setQuickSetLockStaticUri(img.uri.toString()) }
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(img.displayName, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        HorizontalDivider(thickness = 0.5.dp)

        // Watch action
        Text("Watch Face", style = MaterialTheme.typography.labelMedium)
        if (pujiePresets.isEmpty()) {
            Text(
                "No Pujie watch faces saved. Add them in the Pujie Watch Faces section below.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = watchPresetId == null,
                    onClick = { popupViewModel.setQuickSetWatchPresetId(null) }
                )
                Spacer(Modifier.width(4.dp))
                Text("None (skip watch face)", style = MaterialTheme.typography.bodySmall)
            }
            pujiePresets.forEach { preset ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = watchPresetId == preset.id,
                        onClick = { popupViewModel.setQuickSetWatchPresetId(preset.id) }
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(preset.displayName, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        if (isKillSwitch) {
            HorizontalDivider(thickness = 0.5.dp)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    "Both screens set to Static Image — this tile acts as a global kill switch. Tapping it will apply static wallpapers and disable all KLVW wallpaper functions until re-enabled via long press.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        Text(
            "For Random Image/Video, the tile uses the default folders set in the Folder Selector popup items.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun PopupItem.actionSubtitle(): String {
    val targetLabel = target.replaceFirstChar { it.uppercase() }
    return when (actionType) {
        "random_image"   -> "$targetLabel · Random Image"
        "random_video"   -> "$targetLabel · Random Video"
        "static_image"   -> "$targetLabel · Static Image"
        "restore"        -> "$targetLabel · Restore Previous"
        "icon_color"     -> "Icon Color · ${iconColor.replaceFirstChar { it.uppercase() }}"
        "folder_select"  -> "$targetLabel · Set ${mediaType.replaceFirstChar { it.uppercase() }} Folder"
        else             -> actionType
    }
}

@Composable
private fun OverlayPermissionRow() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var granted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = Settings.canDrawOverlays(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            if (granted) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
        Column(modifier = Modifier.weight(1f)) {
            Text("Appear on top", style = MaterialTheme.typography.bodyMedium)
            Text(
                if (granted) "Granted — popup can overlay other apps"
                else "Not granted — popup may not show over other apps",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (!granted) {
            TextButton(onClick = {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                )
            }) { Text("Grant") }
        }
    }
}

// ── Popup Appearance ──────────────────────────────────────────────────────────

private fun colorToHex(color: Color): String = "#%08X".format(color.toArgb())
private fun hexToColor(hex: String): Color? = try { Color(android.graphics.Color.parseColor(hex)) } catch (_: Exception) { null }

@Composable
private fun PujieWatchFaceSection(popupViewModel: KLVWPopupViewModel) {
    val context = LocalContext.current
    val presets by popupViewModel.pujieWatchFaces.collectAsStateWithLifecycle()

    val pujieInstalled = remember {
        try { context.packageManager.getPackageInfo("com.pujie.watchfaces", 0); true }
        catch (_: Exception) { false }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val bundle = result.data?.getBundleExtra("com.twofortyfouram.locale.intent.extra.BUNDLE")
                ?: return@rememberLauncherForActivityResult
            val watchFaceName = bundle.getString("watchface")
                ?: return@rememberLauncherForActivityResult
            val receiverClass = bundle.getString("net.dinglisch.android.tasker.extras.ACTION_RUNNER_CLASS")
                ?: "com.pujie.wristwear.pujieblack.tasker.EditPresetActivity\$TaskerWatchFaceReceiver"
            val inputClass = bundle.getString("net.dinglisch.android.tasker.extras.ACTION_INPUT_CLASS") ?: ""
            val newPreset = PujieWatchFacePreset(
                displayName = watchFaceName,
                watchFaceName = watchFaceName,
                receiverClass = receiverClass,
                inputClass = inputClass
            )
            popupViewModel.savePujieWatchFaces(presets + newPreset)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!pujieInstalled) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Warning, contentDescription = null,
                    modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                Text(
                    "Pujie Watch Faces app not found",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        } else {
            if (presets.isEmpty()) {
                Text(
                    "No watch faces added yet. Tap Add Watch Face to configure one.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                presets.forEachIndexed { index, preset ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Watch, contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            preset.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { popupViewModel.savePujieWatchFaces(presets.toMutableList().also { it.removeAt(index) }) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove",
                                tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                HorizontalDivider(thickness = 0.5.dp, modifier = Modifier.padding(vertical = 2.dp))
            }
            OutlinedButton(
                onClick = {
                    launcher.launch(
                        Intent("com.twofortyfouram.locale.intent.action.EDIT_SETTING").apply {
                            setClassName("com.pujie.watchfaces",
                                "com.pujie.wristwear.pujieblack.tasker.EditPresetActivity")
                            // Required by joaomgcd's TaskerPluginLibrary: signals a Tasker-compatible host
                            putExtra("com.twofortyfouram.locale.intent.extra.BUNDLE", Bundle())
                        }
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add Watch Face", style = MaterialTheme.typography.labelMedium)
            }
            Text(
                "Add a 'Pujie Watch Face' item to the popup menu to access these faces from the popup.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DisplayControlSection(popupViewModel: KLVWPopupViewModel) {
    val context = LocalContext.current
    val homeImage by popupViewModel.displayControlHomeImage.collectAsStateWithLifecycle()
    val homeVideo by popupViewModel.displayControlHomeVideo.collectAsStateWithLifecycle()
    val lockImage by popupViewModel.displayControlLockImage.collectAsStateWithLifecycle()
    val lockVideo by popupViewModel.displayControlLockVideo.collectAsStateWithLifecycle()
    val resetHomeTimer by popupViewModel.displayControlResetHomeTimer.collectAsStateWithLifecycle()
    val resetLockTimer by popupViewModel.displayControlResetLockTimer.collectAsStateWithLifecycle()
    val anyHomeEnabled = homeImage || homeVideo
    val anyLockEnabled = lockImage || lockVideo

    // Track permission state — re-evaluated on each recomposition (user may have just granted)
    var phoneStateGranted by remember { mutableStateOf(false) }
    var usageAccessGranted by remember { mutableStateOf(false) }
    var deviceAdminActive by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                phoneStateGranted = androidx.core.content.ContextCompat.checkSelfPermission(
                    context, android.Manifest.permission.READ_PHONE_STATE
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                val appOps = context.getSystemService(android.app.AppOpsManager::class.java)
                usageAccessGranted = appOps.checkOpNoThrow(
                    android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(), context.packageName
                ) == android.app.AppOpsManager.MODE_ALLOWED

                val dpm = context.getSystemService(android.app.admin.DevicePolicyManager::class.java)
                val adminComponent = android.content.ComponentName(context, com.klvw.wallpaper.tile.KLVWDeviceAdminReceiver::class.java)
                deviceAdminActive = dpm.isAdminActive(adminComponent)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val phoneStateRequest = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted -> phoneStateGranted = granted }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Permissions", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        listOf(
            Triple("Phone State", phoneStateGranted) { phoneStateRequest.launch(android.Manifest.permission.READ_PHONE_STATE) },
            Triple("Usage Access", usageAccessGranted) {
                context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            },
            Triple("Device Admin", deviceAdminActive) {
                val dpm = context.getSystemService(android.app.admin.DevicePolicyManager::class.java)
                val adminComponent = android.content.ComponentName(context, com.klvw.wallpaper.tile.KLVWDeviceAdminReceiver::class.java)
                context.startActivity(
                    Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                        putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                        putExtra(android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                            "Required for KLVW Display Control to read device lock state.")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            }
        ).forEach { (label, granted, grant) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (granted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                if (!granted) {
                    TextButton(onClick = grant) { Text("Grant") }
                }
            }
        }

        HorizontalDivider()
        Text("On Unlock — Cycle Wallpaper", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        listOf(
            Triple("home_image", "Home Image", homeImage),
            Triple("home_video", "Home Video", homeVideo),
            Triple("lock_image", "Lock Image", lockImage),
            Triple("lock_video", "Lock Video", lockVideo)
        ).forEach { (key, label, enabled) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Switch(
                    checked = enabled,
                    onCheckedChange = { popupViewModel.setDisplayControl(key, it) },
                    modifier = Modifier.height(24.dp)
                )
            }
        }

        if (anyHomeEnabled || anyLockEnabled) {
            HorizontalDivider()
            Text("Reset timer on unlock", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (anyHomeEnabled) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Checkbox(checked = resetHomeTimer, onCheckedChange = { popupViewModel.setDisplayControlResetHomeTimer(it) })
                    Text("Reset home timer on unlock", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (anyLockEnabled) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Checkbox(checked = resetLockTimer, onCheckedChange = { popupViewModel.setDisplayControlResetLockTimer(it) })
                    Text("Reset lock timer on unlock", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun PopupAppearanceSection(popupViewModel: KLVWPopupViewModel) {
    val bgHex by popupViewModel.popupBgColor.collectAsStateWithLifecycle()
    val primaryHex by popupViewModel.popupPrimaryTextColor.collectAsStateWithLifecycle()
    val secondaryHex by popupViewModel.popupSecondaryTextColor.collectAsStateWithLifecycle()

    val themeSurface = MaterialTheme.colorScheme.surface
    val themeOnSurface = MaterialTheme.colorScheme.onSurface
    val themeOnSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            "Customize the popup menu colors. Leave as Default to follow the system theme.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        ColorPickerRow(
            label = "Background",
            storedHex = bgHex,
            defaultColor = themeSurface.copy(alpha = 0.95f),
            onColorChange = { popupViewModel.setPopupBgColor(it) }
        )
        HorizontalDivider(thickness = 0.5.dp)
        ColorPickerRow(
            label = "Primary Text",
            storedHex = primaryHex,
            defaultColor = themeOnSurface,
            onColorChange = { popupViewModel.setPopupPrimaryTextColor(it) }
        )
        HorizontalDivider(thickness = 0.5.dp)
        ColorPickerRow(
            label = "Secondary Text",
            storedHex = secondaryHex,
            defaultColor = themeOnSurfaceVariant,
            onColorChange = { popupViewModel.setPopupSecondaryTextColor(it) }
        )
    }
}

@Composable
private fun ColorPickerRow(
    label: String,
    storedHex: String?,
    defaultColor: Color,
    onColorChange: (String?) -> Unit
) {
    val resolvedColor = storedHex?.let { hexToColor(it) } ?: defaultColor
    var expanded by remember { mutableStateOf(false) }

    var alpha by remember { mutableFloatStateOf(resolvedColor.alpha * 255f) }
    var red   by remember { mutableFloatStateOf(resolvedColor.red   * 255f) }
    var green by remember { mutableFloatStateOf(resolvedColor.green * 255f) }
    var blue  by remember { mutableFloatStateOf(resolvedColor.blue  * 255f) }

    LaunchedEffect(storedHex) {
        val c = storedHex?.let { hexToColor(it) } ?: defaultColor
        alpha = c.alpha * 255f
        red   = c.red   * 255f
        green = c.green * 255f
        blue  = c.blue  * 255f
    }

    val previewColor = Color(red / 255f, green / 255f, blue / 255f, alpha / 255f)

    fun commitColor() {
        onColorChange(colorToHex(Color(red / 255f, green / 255f, blue / 255f, alpha / 255f)))
    }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                    .background(previewColor)
            )
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            if (storedHex != null) {
                TextButton(
                    onClick = { onColorChange(null) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) { Text("Default", style = MaterialTheme.typography.labelSmall) }
            }
            IconButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        if (expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                @Composable
                fun SliderRow(ch: String, value: Float, onValueChange: (Float) -> Unit) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(ch, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(16.dp))
                        Slider(
                            value = value,
                            onValueChange = onValueChange,
                            onValueChangeFinished = { commitColor() },
                            valueRange = 0f..255f,
                            modifier = Modifier.weight(1f)
                        )
                        Text(value.toInt().toString(), style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(28.dp))
                    }
                }
                SliderRow("A", alpha) { alpha = it }
                SliderRow("R", red)   { red   = it }
                SliderRow("G", green) { green = it }
                SliderRow("B", blue)  { blue  = it }
            }
        }
    }
}

@Composable
private fun TimerNotificationSection(popupViewModel: KLVWPopupViewModel) {
    val enabled by popupViewModel.timerUnlockNotification.collectAsStateWithLifecycle()
    val onlyWhenRunning by popupViewModel.timerNotificationOnlyWhenRunning.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Timer status on unlock", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Show a notification with remaining time when you unlock your screen. " +
                    "Expand it to pause or reset individual timers.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = { popupViewModel.setTimerUnlockNotification(it) },
                modifier = Modifier.height(24.dp)
            )
        }
        if (enabled) {
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 34.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Only show when running", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Hide notification while all timers are paused.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = onlyWhenRunning,
                    onCheckedChange = { popupViewModel.setTimerNotificationOnlyWhenRunning(it) },
                    modifier = Modifier.height(24.dp)
                )
            }
        }
    }
}

@Composable
private fun TimerGlobalOffSection(popupViewModel: KLVWPopupViewModel) {
    val pauseOnGlobalOff by popupViewModel.pauseTimersOnGlobalOff.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.PauseCircle, contentDescription = null, modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Pause timers on Global Off", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "When you press the Global Off QS button, all running timers are paused " +
                    "automatically. Resume them from the timer notification.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = pauseOnGlobalOff,
                onCheckedChange = { popupViewModel.setPauseTimersOnGlobalOff(it) },
                modifier = Modifier.height(24.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        content()
    }
}

@Composable
private fun SettingToggle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = onClick) { Text("Open") }
    }
}

// ── KLVW Watch ────────────────────────────────────────────────────────────────

@Composable
private fun KLVWWatchSection(popupViewModel: KLVWPopupViewModel) {
    val watchJson by popupViewModel.watchItemsJson.collectAsStateWithLifecycle()
    val imageFolders by popupViewModel.imageFolders.collectAsStateWithLifecycle()
    val videoFolders by popupViewModel.videoFolders.collectAsStateWithLifecycle()
    val globalOffVibrate by popupViewModel.watchGlobalOffVibrate.collectAsStateWithLifecycle()
    var items by remember(watchJson) {
        mutableStateOf(WatchPopupItem.listFromJson(watchJson))
    }
    var showEditor by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<WatchPopupItem?>(null) }
    var showSideload by remember { mutableStateOf(false) }

    fun save(updated: List<WatchPopupItem>) {
        items = updated
        popupViewModel.saveWatchItems(updated.toWatchJsonString())
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Configure actions for your Galaxy Watch. The watch app fetches this list when opened.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No watch items yet. Tap \"Add Watch Item\" to get started.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items.forEachIndexed { index, item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = when (item.actionType) {
                            "random_image" -> Icons.Default.Shuffle
                            "random_video" -> Icons.Default.VideoLibrary
                            "restore"      -> Icons.Default.Restore
                            "global_off"   -> Icons.Default.PowerSettingsNew
                            "timers"       -> Icons.Default.Timer
                            else           -> Icons.Default.TouchApp
                        },
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = if (item.actionType == "global_off")
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.label, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            buildString {
                                append(item.actionType.replace("_", " "))
                                if (item.target.isNotBlank() &&
                                    item.actionType != "global_off" &&
                                    item.actionType != "restore" &&
                                    item.actionType != "timers")
                                    append(" · ${item.target}")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { save(items.toMutableList().also { it.add(index - 1, it.removeAt(index)) }) },
                        enabled = index > 0) {
                        Icon(Icons.Default.KeyboardArrowUp, null, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = { save(items.toMutableList().also { it.add(index + 1, it.removeAt(index)) }) },
                        enabled = index < items.lastIndex) {
                        Icon(Icons.Default.KeyboardArrowDown, null, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = { editingItem = item; showEditor = true }) {
                        Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = { save(items.filter { it.id != item.id }) }) {
                        Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Vibrate on Global Off", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Vibrate the phone when Global Off is triggered from the watch",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = globalOffVibrate,
                onCheckedChange = { popupViewModel.setWatchGlobalOffVibrate(it) }
            )
        }

        Button(
            onClick = { editingItem = null; showEditor = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Add Watch Item")
        }

        WatchSideloadCard(expanded = showSideload, onToggle = { showSideload = !showSideload })
    }

    if (showEditor) {
        WatchItemEditorDialog(
            initial    = editingItem,
            imageFolders = imageFolders.map { it.uri },
            videoFolders = videoFolders.map { it.uri },
            onSave = { newItem ->
                save(
                    if (editingItem != null) items.map { if (it.id == editingItem!!.id) newItem else it }
                    else items + newItem
                )
                showEditor = false
                editingItem = null
            },
            onDismiss = { showEditor = false; editingItem = null }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WatchItemEditorDialog(
    initial: WatchPopupItem?,
    imageFolders: List<String>,
    videoFolders: List<String>,
    onSave: (WatchPopupItem) -> Unit,
    onDismiss: () -> Unit
) {
    var label      by remember(initial) { mutableStateOf(initial?.label ?: "") }
    var actionType by remember(initial) { mutableStateOf(initial?.actionType ?: "random_image") }
    var target     by remember(initial) { mutableStateOf(initial?.target ?: "home") }
    var folderUri  by remember(initial) { mutableStateOf(initial?.folderUri ?: "") }
    var labelError by remember { mutableStateOf(false) }

    val watchActions = listOf("random_image", "random_video", "restore", "global_off", "timers")
    val actionLabels = mapOf(
        "random_image" to "Random Image",
        "random_video" to "Random Video",
        "restore"      to "Restore",
        "global_off"   to "Global Off",
        "timers"       to "Timers"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial != null) "Edit Watch Item" else "Add Watch Item") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it; labelError = false },
                    label = { Text("Label") },
                    isError = labelError,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Action Type", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    watchActions.forEach { type ->
                        FilterChip(
                            selected = actionType == type,
                            onClick  = {
                                actionType = type
                                if (type == "restore" || type == "global_off" || type == "timers") {
                                    target    = "home"
                                    folderUri = ""
                                }
                            },
                            label = { Text(actionLabels[type] ?: type) }
                        )
                    }
                }

                if (actionType == "random_image" || actionType == "random_video") {
                    Text("Apply To", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("home", "lock", "both").forEach { t ->
                            FilterChip(
                                selected = target == t,
                                onClick  = { target = t },
                                label    = { Text(t.replaceFirstChar { it.uppercase() }) }
                            )
                        }
                    }

                    val folders = if (actionType == "random_image") imageFolders else videoFolders
                    if (folders.isNotEmpty()) {
                        Text("Source Folder (optional)", style = MaterialTheme.typography.labelMedium)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            FilterChip(
                                selected = folderUri.isBlank(),
                                onClick  = { folderUri = "" },
                                label    = { Text("Default folder") }
                            )
                            folders.forEach { uri ->
                                val name = Uri.parse(uri).lastPathSegment?.substringAfterLast(':') ?: uri
                                FilterChip(
                                    selected = folderUri == uri,
                                    onClick  = { folderUri = uri },
                                    label    = { Text(name, maxLines = 1) }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (label.isBlank()) { labelError = true; return@TextButton }
                onSave(
                    WatchPopupItem(
                        id         = initial?.id ?: java.util.UUID.randomUUID().toString(),
                        label      = label.trim(),
                        actionType = actionType,
                        target     = target,
                        folderUri  = folderUri
                    )
                )
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun WatchSideloadCard(expanded: Boolean, onToggle: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Watch, null, modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text("Install Watch App", modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium)
            Icon(
                if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HorizontalDivider()
                Spacer(Modifier.height(4.dp))
                Text("To sideload KLVW Watch onto your Galaxy Watch:",
                    style = MaterialTheme.typography.bodySmall)
                Text("1. On the watch: Settings → Developer options → Wireless debugging → Enable",
                    style = MaterialTheme.typography.bodySmall)
                Text("2. On your PC: pair and connect via ADB",
                    style = MaterialTheme.typography.bodySmall)
                androidx.compose.foundation.text.selection.SelectionContainer {
                    Text(
                        "   adb pair <ip>:<pairing-port>\n   adb connect <ip>:<port>",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
                Text("3. Install the watch APK:",
                    style = MaterialTheme.typography.bodySmall)
                androidx.compose.foundation.text.selection.SelectionContainer {
                    Text(
                        "   adb install -r wear/build/outputs/apk/debug/wear-debug.apk",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
                Text("4. Open \"KLVW Watch\" from the watch app drawer.",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun AerStorageSection(popupViewModel: KLVWPopupViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isMounted by remember { mutableStateOf(AerLockStore.isMounted) }
    val aerUnmountOnLock by popupViewModel.aerUnmountOnLock.collectAsStateWithLifecycle()
    val aerAutoUnmountMinutes by popupViewModel.aerAutoUnmountMinutes.collectAsStateWithLifecycle()
    var showAutoUnmountDialog by remember { mutableStateOf(false) }
    var autoUnmountInput by remember { mutableStateOf("") }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) isMounted = AerLockStore.isMounted
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                if (isMounted) Icons.Default.LockOpen else Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (isMounted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(modifier = Modifier.weight(1f)) {
                Text("Status", style = MaterialTheme.typography.bodyMedium)
                Text(
                    if (isMounted) "Mounted — external apps can see files" else "Unmounted — private from external apps",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isMounted) {
                OutlinedButton(onClick = {
                    AerLockStore.unmount(context)
                    isMounted = false
                }) { Text("Unmount") }
            } else {
                Button(onClick = {
                    context.startActivity(Intent(context, AerMountActivity::class.java))
                }) { Text("Mount") }
            }
        }
        HorizontalDivider(thickness = 0.5.dp)
        SettingToggle(
            icon = Icons.Default.ScreenLockPortrait,
            title = "Unmount on device lock",
            subtitle = "Lock Aer automatically when the screen turns off",
            checked = aerUnmountOnLock,
            onCheckedChange = { popupViewModel.setAerUnmountOnLock(it) }
        )
        HorizontalDivider(thickness = 0.5.dp)
        SettingAction(
            icon = Icons.Default.Timer,
            title = "Unmount after",
            subtitle = if (aerAutoUnmountMinutes > 0)
                "Automatically locks after $aerAutoUnmountMinutes minute${if (aerAutoUnmountMinutes != 1) "s" else ""}"
            else
                "Disabled — tap to set a timeout",
            onClick = {
                autoUnmountInput = if (aerAutoUnmountMinutes > 0) aerAutoUnmountMinutes.toString() else ""
                showAutoUnmountDialog = true
            }
        )
        HorizontalDivider(thickness = 0.5.dp)
        SettingAction(
            icon = Icons.Default.FolderOpen,
            title = "Open in Files App",
            subtitle = "Browse and play media in the system file manager",
            onClick = { AerShell.open(context) }
        )
        HorizontalDivider(thickness = 0.5.dp)
        SettingAction(
            icon = Icons.Default.FolderSpecial,
            title = "Manage Aer Storage",
            subtitle = "Import files and assign images as wallpapers",
            onClick = { context.startActivity(Intent(context, AerActivity::class.java)) }
        )
        HorizontalDivider(thickness = 0.5.dp)
        Text(
            "Files in Aer are stored in app-private storage. Open in Files App to browse with the system file manager and play videos with the default player. Use Manage to import media.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = MaterialTheme.typography.bodySmall.lineHeight
        )
    }

    if (showAutoUnmountDialog) {
        AlertDialog(
            onDismissRequest = { showAutoUnmountDialog = false },
            title = { Text("Unmount after") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Enter timeout in minutes (0 or blank to disable).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = autoUnmountInput,
                        onValueChange = { if (it.all { c -> c.isDigit() } && it.length <= 4) autoUnmountInput = it },
                        label = { Text("Minutes") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val minutes = autoUnmountInput.toIntOrNull() ?: 0
                    popupViewModel.setAerAutoUnmountMinutes(minutes)
                    showAutoUnmountDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showAutoUnmountDialog = false }) { Text("Cancel") }
            }
        )
    }
}
