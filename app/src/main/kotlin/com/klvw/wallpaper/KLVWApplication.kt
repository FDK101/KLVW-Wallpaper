package com.klvw.wallpaper

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.IntentFilter
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.video.VideoFrameDecoder
import com.klvw.wallpaper.tile.ScreenUnlockReceiver
import com.klvw.wallpaper.tile.WallpaperTimerManager
import dagger.hilt.android.HiltAndroidApp
import okio.Path.Companion.toOkioPath
import javax.inject.Inject

@HiltAndroidApp
class KLVWApplication : Application(), SingletonImageLoader.Factory {

    @Inject lateinit var timerManager: WallpaperTimerManager

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("klvw_debug", "KLVW Debug", NotificationManager.IMPORTANCE_HIGH)
        )
        nm.createNotificationChannel(
            NotificationChannel("timer_status", "Timer Status", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows running timer status on screen unlock"
            }
        )
        // ACTION_USER_PRESENT is blocked for manifest receivers on Android 8+ — register dynamically.
        // The live wallpaper service keeps the process alive whenever the wallpaper is active.
        registerReceiver(ScreenUnlockReceiver(), IntentFilter(Intent.ACTION_USER_PRESENT))
    }

    override fun newImageLoader(context: android.content.Context): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.20)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("coil_thumbnails").toOkioPath())
                    .maxSizeBytes(150L * 1024 * 1024)
                    .build()
            }
            .build()
    }
}
