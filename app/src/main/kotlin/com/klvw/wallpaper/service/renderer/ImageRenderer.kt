package com.klvw.wallpaper.service.renderer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.view.SurfaceHolder
import com.klvw.wallpaper.util.DisplayUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

class ImageRenderer(
    private val context: Context,
    private val displayUtils: DisplayUtils
) {
    private var bitmap: Bitmap? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    suspend fun load(uri: Uri, holder: SurfaceHolder) {
        val metrics = displayUtils.getScreenMetrics()
        val bmp = withContext(Dispatchers.IO) { decodeBitmap(uri, metrics.widthPx, metrics.heightPx) }
        coroutineContext.ensureActive()
        bitmap = bmp
        if (bmp != null) drawOnCanvas(holder)
    }

    fun render(holder: SurfaceHolder) {
        drawOnCanvas(holder)
    }

    private fun drawOnCanvas(holder: SurfaceHolder) {
        val bmp = bitmap ?: return
        // Software canvas path.
        val canvas: Canvas = holder.lockCanvas() ?: return
        try {
            val metrics = displayUtils.getScreenMetrics()
            val matrix = Matrix()
            val scaleX = metrics.widthPx.toFloat() / bmp.width
            val scaleY = metrics.heightPx.toFloat() / bmp.height
            val scale = maxOf(scaleX, scaleY)
            val dx = (metrics.widthPx - bmp.width * scale) / 2f
            val dy = (metrics.heightPx - bmp.height * scale) / 2f
            matrix.setScale(scale, scale)
            matrix.postTranslate(dx, dy)
            canvas.drawBitmap(bmp, matrix, paint)
        } finally {
            holder.unlockCanvasAndPost(canvas)
        }
    }

    private fun decodeBitmap(uri: Uri, targetW: Int, targetH: Int): Bitmap? {
        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            opts.inSampleSize = calcSampleSize(opts.outWidth, opts.outHeight, targetW, targetH)
            opts.inJustDecodeBounds = false
            opts.inPreferredConfig = Bitmap.Config.ARGB_8888
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        } catch (_: Exception) { null }
    }

    private fun calcSampleSize(sw: Int, sh: Int, dw: Int, dh: Int): Int {
        var s = 1
        var w = sw / 2; var h = sh / 2
        while (w >= dw && h >= dh) { s *= 2; w /= 2; h /= 2 }
        return s
    }

    fun release() {
        bitmap?.recycle()
        bitmap = null
    }
}
