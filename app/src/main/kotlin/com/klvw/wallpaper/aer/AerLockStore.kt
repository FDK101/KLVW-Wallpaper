package com.klvw.wallpaper.aer

import android.content.Context
import android.provider.DocumentsContract
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object AerLockStore {

    private const val PREFS_NAME = "aer_lock_store"
    private const val KEY_LOCKED = "is_locked"

    @Volatile private var _isLocked: Boolean = true

    private val listeners = mutableListOf<(Boolean) -> Unit>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var autoUnmountJob: Job? = null

    val isLocked: Boolean get() = _isLocked
    val isMounted: Boolean get() = !_isLocked

    fun init(context: Context) {
        _isLocked = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_LOCKED, true)
    }

    fun mount(context: Context) = unlock(context)
    fun unmount(context: Context) = lock(context)

    fun unlock(context: Context) {
        _isLocked = false
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_LOCKED, false).apply()
        notifyListeners(false)
        notifyRootsChanged(context)
    }

    fun lock(context: Context) {
        autoUnmountJob?.cancel()
        autoUnmountJob = null
        _isLocked = true
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_LOCKED, true).apply()
        notifyListeners(true)
        notifyRootsChanged(context)
    }

    fun scheduleAutoUnmount(context: Context, delayMs: Long) {
        autoUnmountJob?.cancel()
        if (delayMs <= 0L) return
        autoUnmountJob = scope.launch {
            delay(delayMs)
            lock(context)
        }
    }

    fun addListener(listener: (Boolean) -> Unit) {
        synchronized(listeners) { listeners.add(listener) }
    }

    fun removeListener(listener: (Boolean) -> Unit) {
        synchronized(listeners) { listeners.remove(listener) }
    }

    private fun notifyListeners(isLocked: Boolean) {
        synchronized(listeners) { listeners.toList() }.forEach { it(isLocked) }
    }

    fun notifyRootsChanged(context: Context) {
        context.contentResolver.notifyChange(
            DocumentsContract.buildRootsUri(AerDocumentsProvider.AUTHORITY), null
        )
        context.contentResolver.notifyChange(
            DocumentsContract.buildChildDocumentsUri(AerDocumentsProvider.AUTHORITY, "root"), null
        )
    }
}
