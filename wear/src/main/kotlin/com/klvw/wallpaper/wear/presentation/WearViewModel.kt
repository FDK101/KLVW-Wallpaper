package com.klvw.wallpaper.wear.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.android.gms.wearable.Wearable
import com.klvw.wallpaper.wear.comms.WatchPopupItem
import com.klvw.wallpaper.wear.comms.WatchPopupItem.Companion.listFromJson
import com.klvw.wallpaper.wear.comms.WearMessageBus
import com.klvw.wallpaper.wear.comms.WearPaths
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

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

class WearViewModel(private val appContext: Context) : ViewModel() {

    private val _state = MutableStateFlow<WearUiState>(WearUiState.Loading)
    val state: StateFlow<WearUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            WearMessageBus.configResponses.collect { json ->
                val items = listFromJson(json)
                _state.value = if (items.isEmpty())
                    WearUiState.Error("No watch items configured.\nOpen KLVW on your phone →\nSettings → KLVW Watch → Add items.")
                else
                    WearUiState.Ready(items)
            }
        }
    }

    fun loadConfig() {
        viewModelScope.launch {
            _state.value = WearUiState.Loading
            try {
                val nodes = Wearable.getNodeClient(appContext).connectedNodes.await()
                val phone = nodes.firstOrNull()
                if (phone == null) {
                    _state.value = WearUiState.NoPhone
                    return@launch
                }
                Wearable.getMessageClient(appContext)
                    .sendMessage(phone.id, WearPaths.CONFIG_REQUEST, ByteArray(0))
                    .await()
                // Response arrives via WearMessageListenerService → WearMessageBus
            } catch (e: Exception) {
                _state.value = WearUiState.Error("Could not reach phone:\n${e.message}")
            }
        }
    }

    fun executeItem(item: WatchPopupItem) {
        val currentReady = _state.value as? WearUiState.Ready ?: return
        viewModelScope.launch {
            _state.value = currentReady.copy(executingId = item.id, successId = null)
            try {
                val nodes = Wearable.getNodeClient(appContext).connectedNodes.await()
                val phone = nodes.firstOrNull()
                if (phone == null) {
                    _state.value = WearUiState.NoPhone
                    return@launch
                }
                Wearable.getMessageClient(appContext)
                    .sendMessage(
                        phone.id,
                        WearPaths.EXECUTE,
                        item.toJson().toString().toByteArray(Charsets.UTF_8)
                    ).await()
                _state.value = currentReady.copy(executingId = null, successId = item.id)
                delay(1_000L)
                _state.value = currentReady.copy(executingId = null, successId = null)
            } catch (e: Exception) {
                _state.value = WearUiState.Error("Action failed:\n${e.message}")
            }
        }
    }

    fun retry() = loadConfig()
}

class WearViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        WearViewModel(context.applicationContext) as T
}
