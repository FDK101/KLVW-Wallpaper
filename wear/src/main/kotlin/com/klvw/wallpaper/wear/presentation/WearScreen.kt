package com.klvw.wallpaper.wear.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.TimeText
import com.klvw.wallpaper.wear.presentation.screens.ConfirmRestoreScreen
import com.klvw.wallpaper.wear.presentation.screens.ErrorScreen
import com.klvw.wallpaper.wear.presentation.screens.ItemListScreen
import com.klvw.wallpaper.wear.presentation.screens.LoadingScreen
import com.klvw.wallpaper.wear.presentation.screens.TimerListScreen

@Composable
fun WearScreen(vm: WearViewModel) {
    val state       by vm.state.collectAsStateWithLifecycle()
    val screen      by vm.screen.collectAsStateWithLifecycle()
    val timerState  by vm.timerState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.loadConfig() }

    MaterialTheme {
        Scaffold(timeText = { TimeText() }) {
            when (val s = screen) {
                is WatchScreen.Timers -> {
                    BackHandler { vm.navigateBack() }
                    TimerListScreen(
                        state    = timerState,
                        onAction = vm::timerAction,
                        onRetry  = vm::loadTimers
                    )
                }
                is WatchScreen.ConfirmRestore -> {
                    BackHandler { vm.cancelConfirm() }
                    ConfirmRestoreScreen(
                        item       = s.item,
                        onConfirmed = vm::confirmRestore,
                        onCancel   = vm::cancelConfirm
                    )
                }
                is WatchScreen.Main -> when (val ms = state) {
                    is WearUiState.Loading -> LoadingScreen()
                    is WearUiState.NoPhone -> ErrorScreen(
                        message = "Phone not connected.\nMake sure KLVW is open.",
                        onRetry = vm::retry
                    )
                    is WearUiState.Error -> ErrorScreen(
                        message = ms.msg,
                        onRetry = vm::retry
                    )
                    is WearUiState.Ready -> ItemListScreen(
                        items       = ms.items,
                        executingId = ms.executingId,
                        successId   = ms.successId,
                        onTap       = vm::executeItem
                    )
                }
            }
        }
    }
}
