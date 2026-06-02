package com.klvw.wallpaper.wear.comms

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.util.UUID

private const val TAG = "BtConfigClient"
private val SERVICE_UUID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890")
private const val CONNECT_TIMEOUT_MS = 8_000L
private const val READ_TIMEOUT_MS = 10_000L

object BtConfigClient {

    suspend fun getConfig(context: Context): String? =
        exchange(context, "GET_CONFIG")

    suspend fun execute(context: Context, json: String): Boolean =
        exchange(context, "EXEC:${json.replace('\n', ' ').replace('\r', ' ')}") == "OK"

    suspend fun getTimers(context: Context): String? =
        exchange(context, "GET_TIMERS")

    suspend fun timerAction(context: Context, key: String, action: String): String? =
        exchange(context, "TIMER_ACTION:$key:$action")

    private suspend fun exchange(context: Context, command: String): String? =
        withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED
            ) {
                Log.w(TAG, "BLUETOOTH_CONNECT not granted")
                return@withContext null
            }

            val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
                ?: return@withContext null

            @Suppress("MissingPermission")
            val bonded = adapter.bondedDevices
                ?.filter {
                    it.type == BluetoothDevice.DEVICE_TYPE_CLASSIC ||
                    it.type == BluetoothDevice.DEVICE_TYPE_DUAL
                }
                ?: return@withContext null

            for (device in bonded) {
                try {
                    @Suppress("MissingPermission")
                    val socket = device.createInsecureRfcommSocketToServiceRecord(SERVICE_UUID)

                    // connect() blocks indefinitely if the phone isn't listening; close the socket
                    // from a watchdog coroutine so the IO thread can never get stuck in D-state.
                    val connectScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
                    val connectWatchdog = connectScope.launch {
                        delay(CONNECT_TIMEOUT_MS)
                        try { socket.close() } catch (_: Exception) {}
                    }
                    try {
                        socket.connect()
                        connectWatchdog.cancel()
                    } catch (e: Exception) {
                        connectWatchdog.cancel()
                        try { socket.close() } catch (_: Exception) {}
                        continue
                    }

                    // readLine() can also block; close socket on timeout.
                    val readScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
                    val readWatchdog = readScope.launch {
                        delay(READ_TIMEOUT_MS)
                        try { socket.close() } catch (_: Exception) {}
                    }
                    try {
                        val result = socket.use { s ->
                            val reader = BufferedReader(InputStreamReader(s.inputStream, Charsets.UTF_8))
                            val writer = PrintWriter(s.outputStream, true, Charsets.UTF_8)
                            writer.println(command)
                            reader.readLine()
                        }
                        readWatchdog.cancel()
                        if (result != null) return@withContext result
                    } catch (_: Exception) {
                        readWatchdog.cancel()
                    }
                } catch (_: Exception) {
                }
            }
            null
        }
}
