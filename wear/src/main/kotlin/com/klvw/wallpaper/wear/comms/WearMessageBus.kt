package com.klvw.wallpaper.wear.comms

import kotlinx.coroutines.flow.MutableSharedFlow

object WearMessageBus {
    val configResponses = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 1)
    val executeAcks     = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1)
}
