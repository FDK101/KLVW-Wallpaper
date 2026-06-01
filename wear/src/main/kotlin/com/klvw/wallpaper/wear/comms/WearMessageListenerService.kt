package com.klvw.wallpaper.wear.comms

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

class WearMessageListenerService : WearableListenerService() {

    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
            WearPaths.CONFIG_RESPONSE ->
                WearMessageBus.configResponses.tryEmit(String(event.data, Charsets.UTF_8))
            WearPaths.EXECUTE_ACK ->
                WearMessageBus.executeAcks.tryEmit(Unit)
        }
    }
}
