package com.klvw.wallpaper.wear

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await

object WatchConfigSync {
    private const val PATH = "/klvw/config"
    private const val TAG = "WatchConfigSync"

    suspend fun sync(context: Context, json: String) {
        try {
            val request = PutDataMapRequest.create(PATH).apply {
                dataMap.putString("items", json)
                dataMap.putLong("ts", System.currentTimeMillis())
            }.asPutDataRequest().setUrgent()
            Wearable.getDataClient(context).putDataItem(request).await()
            Log.d(TAG, "Config synced (${json.length} bytes)")
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed", e)
        }
    }
}
