package com.klvw.wallpaper.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.klvw.wallpaper.data.db.FolderEntity
import com.klvw.wallpaper.data.model.FolderType
import com.klvw.wallpaper.ui.theme.SurfaceGlass
import com.klvw.wallpaper.ui.viewmodel.WallpaperViewModel
import com.klvw.wallpaper.util.PermissionUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FolderManagerScreen(viewModel: WallpaperViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var addingType by remember { mutableStateOf<FolderType?>(null) }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { picked ->
            PermissionUtils.persistFolderPermission(context, picked)
            addingType?.let { type -> viewModel.addFolder(picked, type) }
            addingType = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            AddFolderButton(
                label = "Add Image Folder",
                icon = Icons.Default.Image,
                modifier = Modifier.weight(1f),
                onClick = { addingType = FolderType.IMAGE; folderPicker.launch(null) }
            )
            AddFolderButton(
                label = "Add Video Folder",
                icon = Icons.Default.VideoFile,
                modifier = Modifier.weight(1f),
                onClick = { addingType = FolderType.VIDEO; folderPicker.launch(null) }
            )
        }

        if (state.imageFolders.isNotEmpty()) {
            FolderSection(
                title = "Image Folders",
                folders = state.imageFolders,
                onRemove = { viewModel.removeFolder(it) }
            )
        }

        if (state.videoFolders.isNotEmpty()) {
            FolderSection(
                title = "Video Folders",
                folders = state.videoFolders,
                onRemove = { viewModel.removeFolder(it) }
            )
        }

        if (state.imageFolders.isEmpty() && state.videoFolders.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceGlass),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Folder, contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("No folders added yet", color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        if (state.imageFolders.isNotEmpty() || state.videoFolders.isNotEmpty()) {
            DefaultFoldersSection(
                imageFolders = state.imageFolders,
                videoFolders = state.videoFolders,
                defaultHomeImageUri = state.defaultHomeImageFolderUri,
                defaultLockImageUri = state.defaultLockImageFolderUri,
                defaultHomeVideoUri = state.defaultHomeVideoFolderUri,
                defaultLockVideoUri = state.defaultLockVideoFolderUri,
                onSetDefault = { target, mediaType, uri -> viewModel.setDefaultFolder(target, mediaType, uri) }
            )
        }

        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun DefaultFoldersSection(
    imageFolders: List<FolderEntity>,
    videoFolders: List<FolderEntity>,
    defaultHomeImageUri: String?,
    defaultLockImageUri: String?,
    defaultHomeVideoUri: String?,
    defaultLockVideoUri: String?,
    onSetDefault: (target: String, mediaType: String, uri: String?) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Default Folders",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Text(
            "Used by popup items and Tasker actions when no folder is specified",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        listOf(
            Triple("home", "image", defaultHomeImageUri) to "Home — Images",
            Triple("lock", "image", defaultLockImageUri) to "Lock — Images",
            Triple("home", "video", defaultHomeVideoUri) to "Home — Videos",
            Triple("lock", "video", defaultLockVideoUri) to "Lock — Videos",
        ).forEach { (triple, label) ->
            val (target, mediaType, currentUri) = triple
            val folders = if (mediaType == "image") imageFolders else videoFolders
            DefaultFolderRow(
                label = label,
                folders = folders,
                currentUri = currentUri,
                onSelect = { uri -> onSetDefault(target, mediaType, uri) }
            )
        }
    }
}

@Composable
private fun DefaultFolderRow(
    label: String,
    folders: List<FolderEntity>,
    currentUri: String?,
    onSelect: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val currentFolder = folders.find { it.uri == currentUri }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceGlass)
            .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                currentFolder?.displayName ?: "Not set",
                style = MaterialTheme.typography.bodySmall,
                color = if (currentFolder != null) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box {
            TextButton(
                onClick = { if (folders.isNotEmpty()) expanded = true },
                enabled = folders.isNotEmpty()
            ) {
                Text(if (currentFolder != null) "Change" else "Set",
                    style = MaterialTheme.typography.labelMedium)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                if (currentFolder != null) {
                    DropdownMenuItem(
                        text = { Text("Clear", color = MaterialTheme.colorScheme.error) },
                        onClick = { onSelect(null); expanded = false }
                    )
                    HorizontalDivider()
                }
                folders.forEach { folder ->
                    DropdownMenuItem(
                        text = { Text(folder.displayName) },
                        onClick = { onSelect(folder.uri); expanded = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun AddFolderButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun FolderSection(
    title: String,
    folders: List<FolderEntity>,
    onRemove: (FolderEntity) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        folders.forEach { folder ->
            FolderItem(folder = folder, onRemove = { onRemove(folder) })
        }
    }
}

@Composable
private fun FolderItem(folder: FolderEntity, onRemove: () -> Unit) {
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceGlass)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (folder.type == FolderType.IMAGE) Icons.Default.Image else Icons.Default.VideoFile,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(folder.displayName, style = MaterialTheme.typography.bodyMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "Added ${dateFormat.format(Date(folder.addedAt))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onRemove, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Default.Delete, contentDescription = "Remove",
                tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
        }
    }
}
