package com.klvw.wallpaper.wear.presentation

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.klvw.wallpaper.wear.comms.WatchPopupItem
import com.klvw.wallpaper.wear.comms.WatchPopupItem.Companion.listFromJson
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

    fun loadConfig() {
        viewModelScope.launch {
            _state.value = WearUiState.Loading
            try {
                val dataItems = Wearable.getDataClient(appContext)
                    .getDataItems(Uri.parse("wear://*/klvw/config"))
                    .await()

                val json = dataItems.firstOrNull()?.let { item ->
                    DataMapItem.fromDataItem(item).dataMap.getString("items")
                }
                dataItems.release()

                if (json == null) {
                    _state.value = WearUiState.Error(
                        "Config not synced yet.\nOpen KLVW on your phone,\nthen re-open this app."
                    )
                    return@launch
                }

                val items = listFromJson(json)
                _state.value = if (items.isEmpty())
                    WearUiState.Error(
                        "No watch items configured.\nOpen KLVW on your phone →\nSettings → KLVW Watch → Add items."
                    )
                else
                    WearUiState.Ready(items)
            } catch (e: Exception) {
                _state.value = WearUiState.Error("Load failed:\n${e.message}")
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
                // Consider it sent — show success without waiting for ack
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
