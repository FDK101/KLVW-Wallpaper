package com.klvw.wallpaper.service

import android.graphics.Bitmap
import android.graphics.Color

internal fun isLightBitmap(bitmap: Bitmap): Boolean {
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    var totalLuminance = 0.0
    for (px in pixels) {
        val r = Color.red(px) / 255.0
        val g = Color.green(px) / 255.0
        val b = Color.blue(px) / 255.0
        totalLuminance += 0.2126 * r + 0.7152 * g + 0.0722 * b
    }
    return totalLuminance / pixels.size >= 0.5
}
