package com.klvw.wallpaper.wear.presentation.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.klvw.wallpaper.wear.comms.WatchPopupItem

@Composable
fun ItemListScreen(
    items: List<WatchPopupItem>,
    executingId: String?,
    successId: String?,
    onTap: (WatchPopupItem) -> Unit
) {
    val listState = rememberScalingLazyListState()

    ScalingLazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 28.dp, bottom = 20.dp, start = 8.dp, end = 8.dp)
    ) {
        item {
            ListHeader {
                Text("KLVW", style = MaterialTheme.typography.title3)
            }
        }
        items(items) { item ->
            val isExecuting = executingId == item.id
            val isSuccess   = successId   == item.id

            Chip(
                modifier = Modifier.fillMaxWidth(),
                onClick  = { if (!isExecuting && !isSuccess) onTap(item) },
                enabled  = !isExecuting && !isSuccess,
                colors   = if (item.actionType == "global_off")
                    ChipDefaults.primaryChipColors(backgroundColor = MaterialTheme.colors.error)
                else
                    ChipDefaults.primaryChipColors(),
                label    = { Text(item.label) },
                secondaryLabel = {
                    Text(
                        item.actionType.replace("_", " "),
                        style = MaterialTheme.typography.caption2
                    )
                },
                icon = {
                    when {
                        isExecuting -> CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        isSuccess   -> Icon(Icons.Default.Check, null, tint = Color(0xFF4CAF50))
                        else        -> Icon(actionIcon(item.actionType), null)
                    }
                }
            )
        }
    }
}

private fun actionIcon(actionType: String): ImageVector = when (actionType) {
    "random_image" -> Icons.Default.Shuffle
    "random_video" -> Icons.Default.VideoLibrary
    "restore"      -> Icons.Default.Restore
    "global_off"   -> Icons.Default.PowerSettingsNew
    else           -> Icons.Default.Shuffle
}
