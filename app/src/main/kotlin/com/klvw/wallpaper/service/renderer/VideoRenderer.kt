package com.klvw.wallpaper.service.renderer

import android.content.Context
import android.net.Uri
import android.view.SurfaceHolder
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

class VideoRenderer(private val context: Context) {
    private var player: ExoPlayer? = null
    private var surfaceHolder: SurfaceHolder? = null
    private var currentUri: Uri? = null
    private var intentionalRelease = false

    private val playerListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            if (!intentionalRelease) {
                try { player?.prepare() } catch (_: Exception) {}
            }
        }
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_IDLE && !intentionalRelease) {
                try { player?.prepare() } catch (_: Exception) {}
            }
        }
    }

    fun load(uri: Uri, holder: SurfaceHolder) {
        release()
        intentionalRelease = false
        currentUri = uri
        surfaceHolder = holder
        val exo = ExoPlayer.Builder(context).build()
        exo.setMediaItem(MediaItem.fromUri(uri))
        exo.repeatMode = Player.REPEAT_MODE_ONE
        exo.volume = 0f
        exo.setVideoSurfaceHolder(holder)
        exo.addListener(playerListener)
        exo.prepare()
        exo.playWhenReady = true
        player = exo
    }

    fun detachSurface() {
        try { player?.clearVideoSurfaceHolder(surfaceHolder) } catch (_: Exception) {}
    }

    fun pause() { try { player?.pause() } catch (_: Exception) {} }
    fun resume() { try { player?.play() } catch (_: Exception) {} }

    fun release() {
        intentionalRelease = true
        try { player?.removeListener(playerListener) } catch (_: Exception) {}
        try { player?.stop() } catch (_: Exception) {}
        try { player?.release() } catch (_: Exception) {}
        player = null
        surfaceHolder = null
        currentUri = null
    }
}
