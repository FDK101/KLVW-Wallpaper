package com.klvw.wallpaper.wear.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.klvw.wallpaper.wear.comms.WatchTimerInfo
import com.klvw.wallpaper.wear.presentation.TimerUiState
import kotlinx.coroutines.delay

@Composable
fun TimerListScreen(
    state: TimerUiState,
    onAction: (key: String, action: String) -> Unit,
    onRetry: () -> Unit
) {
    when (state) {
        is TimerUiState.Loading -> LoadingScreen()
        is TimerUiState.Error   -> ErrorScreen(message = state.msg, onRetry = onRetry)
        is TimerUiState.Ready   -> TimerReadyScreen(
            timers    = state.timers,
            actionKey = state.actionKey,
            onAction  = onAction
        )
    }
}

@Composable
private fun TimerReadyScreen(
    timers: List<WatchTimerInfo>,
    actionKey: String?,
    onAction: (key: String, action: String) -> Unit
) {
    var tickMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000L)
            tickMs = System.currentTimeMillis()
        }
    }

    val listState = rememberScalingLazyListState()

    ScalingLazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 28.dp, bottom = 24.dp, start = 24.dp, end = 24.dp)
    ) {
        item {
            ListHeader {
                Text("Timers", style = MaterialTheme.typography.title3)
            }
        }
        items(timers, key = { it.key }) { timer ->
            val isActing = actionKey == timer.key
            val anyActing = actionKey != null
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Chip(
                    modifier = Modifier.fillMaxWidth(),
                    onClick  = {},
                    colors   = ChipDefaults.secondaryChipColors(),
                    label    = {
                        Text(timer.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    secondaryLabel = {
                        Text(
                            text = when {
                                isActing      -> "…"
                                timer.paused  -> "Paused"
                                else          -> formatCountdown(timer.nextFireMs - tickMs)
                            },
                            style = MaterialTheme.typography.caption2,
                            maxLines = 1
                        )
                    },
                    icon = {
                        if (isActing)
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        else
                            Icon(Icons.Default.Timer, contentDescription = null)
                    }
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CompactChip(
                        modifier = Modifier.weight(1f),
                        onClick  = { onAction(timer.key, if (timer.paused) "resume" else "pause") },
                        enabled  = !anyActing,
                        colors   = if (timer.paused)
                            ChipDefaults.primaryChipColors()
                        else
                            ChipDefaults.secondaryChipColors(),
                        label    = {
                            Text(
                                if (timer.paused) "Resume" else "Pause",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                    CompactChip(
                        modifier = Modifier.weight(1f),
                        onClick  = { onAction(timer.key, "reset") },
                        enabled  = !timer.paused && !anyActing,
                        colors   = ChipDefaults.secondaryChipColors(),
                        label    = {
                            Text("Reset", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    )
                }
            }
        }
    }
}

private fun formatCountdown(remainingMs: Long): String {
    if (remainingMs <= 0L) return "0:00"
    val secs = remainingMs / 1000L
    val h = secs / 3600
    val m = (secs % 3600) / 60
    val s = secs % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s)
    else "%d:%02d".format(m, s)
}
