package com.klvw.wallpaper.wear.presentation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.klvw.wallpaper.wear.comms.BtConfigClient
import com.klvw.wallpaper.wear.comms.WatchPopupItem
import com.klvw.wallpaper.wear.comms.WatchPopupItem.Companion.listFromJson
import com.klvw.wallpaper.wear.comms.WatchTimerInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface WearUiState {
    object Loading : WearUiState
    object NoPhone : WearUiState
    data class Error(val msg: String) : WearUiState
    data class Ready(
        val items: List<WatchPopupItem>,
        val executingId: String? = null,
        val successId: String? = null
    ) : WearUiState
}

sealed interface TimerUiState {
    object Loading : TimerUiState
    data class Error(val msg: String) : TimerUiState
    data class Ready(
        val timers: List<WatchTimerInfo>,
        val actionKey: String? = null
    ) : TimerUiState
}

sealed interface WatchScreen {
    object Main : WatchScreen
    object Timers : WatchScreen
    data class ConfirmRestore(val item: WatchPopupItem) : WatchScreen
}

class WearViewModel(private val appContext: Context) : ViewModel() {

    private val _state = MutableStateFlow<WearUiState>(WearUiState.Loading)
    val state: StateFlow<WearUiState> = _state.asStateFlow()

    private val _timerState = MutableStateFlow<TimerUiState>(TimerUiState.Loading)
    val timerState: StateFlow<TimerUiState> = _timerState.asStateFlow()

    private val _screen = MutableStateFlow<WatchScreen>(WatchScreen.Main)
    val screen: StateFlow<WatchScreen> = _screen.asStateFlow()

    fun loadConfig() {
        viewModelScope.launch {
            _state.value = WearUiState.Loading
            try {
                val json = BtConfigClient.getConfig(appContext)
                if (json != null) {
                    applyJson(json)
                } else {
                    val btGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                        ContextCompat.checkSelfPermission(
                            appContext, Manifest.permission.BLUETOOTH_CONNECT
                        ) == PackageManager.PERMISSION_GRANTED
                    _state.value = WearUiState.Error(
                        "Phone did not respond.\nOpen KLVW on phone.\nBT:${if (btGranted) "ok" else "DENIED"}"
                    )
                }
            } catch (e: Exception) {
                _state.value = WearUiState.Error("Load failed:\n${e.message}")
            }
        }
    }

    fun executeItem(item: WatchPopupItem) {
        when (item.actionType) {
            "timers" -> {
                _screen.value = WatchScreen.Timers
                loadTimers()
                return
            }
            "restore" -> {
                _screen.value = WatchScreen.ConfirmRestore(item)
                return
            }
        }
        sendExec(item)
    }

    fun confirmRestore(item: WatchPopupItem) {
        _screen.value = WatchScreen.Main
        sendExec(item)
    }

    fun cancelConfirm() {
        _screen.value = WatchScreen.Main
    }

    private fun sendExec(item: WatchPopupItem) {
        val currentReady = _state.value as? WearUiState.Ready ?: return
        viewModelScope.launch {
            _state.value = currentReady.copy(executingId = item.id, successId = null)
            try {
                val ok = BtConfigClient.execute(appContext, item.toJson().toString())
                if (ok) {
                    _state.value = currentReady.copy(executingId = null, successId = item.id)
                    delay(1_000L)
                    _state.value = currentReady.copy(executingId = null, successId = null)
                } else {
                    _state.value = WearUiState.Error("Action failed.\nOpen KLVW on phone.")
                }
            } catch (e: Exception) {
                _state.value = WearUiState.Error("Action failed:\n${e.message}")
            }
        }
    }

    fun loadTimers() {
        viewModelScope.launch {
            _timerState.value = TimerUiState.Loading
            try {
                val json = BtConfigClient.getTimers(appContext)
                if (json != null) {
                    val timers = WatchTimerInfo.listFromJson(json)
                    _timerState.value = if (timers.isEmpty())
                        TimerUiState.Error("No timers enabled.\nEnable timers in KLVW on your phone.")
                    else
                        TimerUiState.Ready(timers)
                } else {
                    _timerState.value = TimerUiState.Error("Phone did not respond.\nOpen KLVW on phone.")
                }
            } catch (e: Exception) {
                _timerState.value = TimerUiState.Error("Load failed:\n${e.message}")
            }
        }
    }

    fun timerAction(key: String, action: String) {
        val current = _timerState.value as? TimerUiState.Ready ?: return
        viewModelScope.launch {
            _timerState.value = current.copy(actionKey = key)
            try {
                val json = BtConfigClient.timerAction(appContext, key, action)
                if (json != null && json != "ERROR") {
                    _timerState.value = TimerUiState.Ready(WatchTimerInfo.listFromJson(json))
                } else {
                    _timerState.value = TimerUiState.Error("Action failed.\nOpen KLVW on phone.")
                }
            } catch (e: Exception) {
                _timerState.value = TimerUiState.Error("Action failed:\n${e.message}")
            }
        }
    }

    fun navigateBack() {
        _screen.value = WatchScreen.Main
    }

    fun retry() = loadConfig()

    private fun applyJson(json: String) {
        val items = listFromJson(json)
        _state.value = if (items.isEmpty())
            WearUiState.Error(
                "No watch items configured.\nOpen KLVW on your phone →\nSettings → KLVW Watch → Add items."
            )
        else
            WearUiState.Ready(items)
    }
}

class WearViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        WearViewModel(context.applicationContext) as T
}
