package com.klvw.wallpaper.service.renderer

import android.graphics.Bitmap
import android.util.Log
import android.view.Surface

object VulkanBridge {
    private const val TAG = "VulkanBridge"
    private var available = false

    init {
        available = try {
            System.loadLibrary("klvw_vulkan")
            true
        } catch (e: UnsatisfiedLinkError) {
            Log.w(TAG, "Vulkan native library not available, falling back to Canvas")
            false
        }
    }

    fun isAvailable() = available

    fun init(surface: Surface, width: Int, height: Int): Boolean {
        if (!available) return false
        return try {
            nativeInit(surface, width, height)
        } catch (e: Exception) {
            Log.e(TAG, "Vulkan init failed", e)
            available = false
            false
        }
    }

    fun setImage(bitmap: Bitmap): Boolean {
        if (!available) return false
        return try {
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            val bytes = ByteArray(width * height * 4)
            for (i in pixels.indices) {
                val p = pixels[i]
                bytes[i * 4 + 0] = ((p shr 16) and 0xFF).toByte() // R
                bytes[i * 4 + 1] = ((p shr 8) and 0xFF).toByte()  // G
                bytes[i * 4 + 2] = (p and 0xFF).toByte()           // B
                bytes[i * 4 + 3] = ((p shr 24) and 0xFF).toByte() // A
            }
            nativeSetImage(bytes, width, height)
        } catch (e: Exception) {
            Log.e(TAG, "Vulkan setImage failed", e)
            false
        }
    }

    fun render(): Boolean {
        if (!available) return false
        return try {
            nativeRender()
        } catch (e: Exception) {
            false
        }
    }

    fun destroy() {
        if (!available) return
        try { nativeDestroy() } catch (_: Exception) {}
    }

    private external fun nativeInit(surface: Surface, width: Int, height: Int): Boolean
    private external fun nativeSetImage(pixels: ByteArray, width: Int, height: Int): Boolean
    private external fun nativeRender(): Boolean
    private external fun nativeDestroy()
}
