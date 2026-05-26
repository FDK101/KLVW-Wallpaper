package com.klvw.wallpaper.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.klvw.wallpaper.data.db.FolderEntity
import com.klvw.wallpaper.data.model.WallpaperItem
import com.klvw.wallpaper.tile.*
import java.util.UUID

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PopupItemEditorDialog(
    existingItem: PopupItem?,
    imageFolders: List<FolderEntity>,
    videoFolders: List<FolderEntity>,
    staticImages: List<WallpaperItem>,
    onSave: (PopupItem) -> Unit,
    onDismiss: () -> Unit
) {
    var label by remember { mutableStateOf(existingItem?.label ?: "") }
    var actionType by remember { mutableStateOf(existingItem?.actionType ?: POPUP_ACTION_RANDOM_IMAGE) }
    var target by remember { mutableStateOf(existingItem?.target ?: "home") }
    var folderUri by remember { mutableStateOf(existingItem?.folderUri ?: "") }
    var imageUri by remember { mutableStateOf(existingItem?.imageUri ?: "") }
    var iconColor by remember { mutableStateOf(existingItem?.iconColor ?: "auto") }
    var mediaType by remember { mutableStateOf(existingItem?.mediaType ?: "image") }

    val isVideo = actionType == POPUP_ACTION_RANDOM_VIDEO
    val folders = if (isVideo) videoFolders else imageFolders

    val canSave = label.isNotBlank() && when (actionType) {
        POPUP_ACTION_STATIC_IMAGE -> imageUri.isNotBlank()
        else -> true
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existingItem == null) "Add Item" else "Edit Item") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Label
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Action type
                Text("Action Type", style = MaterialTheme.typography.labelLarge)
                val actionTypes = listOf(
                    POPUP_ACTION_RANDOM_IMAGE      to "Random Image",
                    POPUP_ACTION_RANDOM_VIDEO      to "Random Video",
                    POPUP_ACTION_STATIC_IMAGE      to "Static Image",
                    POPUP_ACTION_RESTORE           to "Restore Previous",
                    POPUP_ACTION_ICON_COLOR        to "Icon Color",
                    POPUP_ACTION_SELECT_FILE       to "Select File",
                    POPUP_ACTION_TIMER             to "Timer Control",
                    POPUP_ACTION_FOLDER_SELECT_ALL to "All Folders",
                    POPUP_ACTION_DISPLAY_CONTROL   to "Display Control"
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    actionTypes.forEach { (value, lbl) ->
                        FilterChip(
                            selected = actionType == value,
                            onClick = { actionType = value },
                            label = { Text(lbl, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                // Action-specific options
                when (actionType) {
                    POPUP_ACTION_RANDOM_IMAGE, POPUP_ACTION_RANDOM_VIDEO -> {
                        Text("Source Folder", style = MaterialTheme.typography.labelLarge)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = folderUri.isEmpty(), onClick = { folderUri = "" })
                            Spacer(Modifier.width(4.dp))
                            Text("Use default folder", style = MaterialTheme.typography.bodySmall)
                        }
                        folders.forEach { folder ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = folderUri == folder.uri,
                                    onClick = { folderUri = folder.uri }
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(folder.displayName, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    POPUP_ACTION_STATIC_IMAGE -> {
                        Text("Image", style = MaterialTheme.typography.labelLarge)
                        if (staticImages.isEmpty()) {
                            Text(
                                "No static images saved.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            staticImages.forEach { img ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(
                                        selected = imageUri == img.uri.toString(),
                                        onClick = { imageUri = img.uri.toString() }
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(img.displayName, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                    POPUP_ACTION_ICON_COLOR -> {
                        Text("Color", style = MaterialTheme.typography.labelLarge)
                        listOf("auto" to "Auto", "dark" to "Dark", "light" to "Light").forEach { (v, lbl) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = iconColor == v, onClick = { iconColor = v })
                                Spacer(Modifier.width(4.dp))
                                Text(lbl, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                val noTargetActions = setOf(
                    POPUP_ACTION_ICON_COLOR,
                    POPUP_ACTION_SELECT_FILE,
                    POPUP_ACTION_FOLDER_SELECT_ALL,
                    POPUP_ACTION_TIMER,
                    POPUP_ACTION_DISPLAY_CONTROL
                )
                if (actionType !in noTargetActions) {
                    Text("Apply To", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("home" to "Home", "lock" to "Lock", "both" to "Both").forEach { (v, lbl) ->
                            FilterChip(
                                selected = target == v,
                                onClick = { target = v },
                                label = { Text(lbl) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (!canSave) return@Button
                    onSave(
                        PopupItem(
                            id = existingItem?.id ?: UUID.randomUUID().toString(),
                            label = label.trim(),
                            actionType = actionType,
                            target = target,
                            folderUri = folderUri,
                            imageUri = imageUri,
                            iconColor = iconColor,
                            mediaType = mediaType
                        )
                    )
                },
                enabled = canSave
            ) { Text("Save") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
