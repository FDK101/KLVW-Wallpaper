package com.klvw.wallpaper.wear.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.TimeText
import com.klvw.wallpaper.wear.presentation.screens.ErrorScreen
import com.klvw.wallpaper.wear.presentation.screens.ItemListScreen
import com.klvw.wallpaper.wear.presentation.screens.LoadingScreen

@Composable
fun WearScreen(vm: WearViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.loadConfig() }

    MaterialTheme {
        Scaffold(timeText = { TimeText() }) {
            when (val s = state) {
                is WearUiState.Loading -> LoadingScreen()
                is WearUiState.NoPhone -> ErrorScreen(
                    message = "Phone not connected.\nMake sure KLVW is open.",
                    onRetry = vm::retry
                )
                is WearUiState.Error -> ErrorScreen(
                    message = s.msg,
                    onRetry = vm::retry
                )
                is WearUiState.Ready -> ItemListScreen(
                    items       = s.items,
                    executingId = s.executingId,
                    successId   = s.successId,
                    onTap       = vm::executeItem
                )
            }
        }
    }
}
