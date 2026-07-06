package com.hieuld.cowatch.session

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.hieuld.cowatch.domain.media.VideoSource
import com.hieuld.cowatch.render.FrameFanoutRenderEngine
import com.hieuld.cowatch.render.VideoRenderEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AppPlaybackSession {
    private var player: ExoPlayer? = null
    private var renderEngine: FrameFanoutRenderEngine? = null
    private var attachedRenderEngine: VideoRenderEngine? = null
    private val _pipState = MutableStateFlow<InAppPipState?>(null)

    val pipState: StateFlow<InAppPipState?> = _pipState.asStateFlow()
    var currentSource: VideoSource.Asset? = null
        private set

    // One ExoPlayer is shared across fullscreen, library PiP, and shared-display sessions.
    fun getPlayer(context: Context): ExoPlayer {
        return player ?: ExoPlayer.Builder(context.applicationContext).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true
            )
            volume = 1f
        }.also { player = it }
    }

    // The render engine survives fullscreen -> library PiP so external shared displays keep receiving frames.
    fun getRenderEngine(): FrameFanoutRenderEngine {
        return renderEngine ?: FrameFanoutRenderEngine()
            .also {
                renderEngine = it
                attachRenderEngine(it)
            }
    }

    fun attachRenderEngine(renderEngine: VideoRenderEngine) {
        attachedRenderEngine = renderEngine
        player?.setVideoSurface(renderEngine.inputSurface)
    }

    fun detachRenderEngine(renderEngine: VideoRenderEngine) {
        if (attachedRenderEngine !== renderEngine) return

        player?.clearVideoSurface()
        attachedRenderEngine = null
    }

    // Fullscreen entry from library starts a new source and clears app-scoped PiP state.
    fun play(context: Context, source: VideoSource.Asset) {
        exitInAppPip()
        startSource(context, source)
    }

    // Returning from PiP to the same asset must not restart the decoder or lose playback position.
    fun showFullscreen(context: Context, source: VideoSource.Asset) {
        val player = getPlayer(context)
        val sameSource = currentSource?.assetPath == source.assetPath
        val returningFromPip = _pipState.value != null

        exitInAppPip()

        if (returningFromPip && sameSource && player.currentMediaItem != null) {
            return
        }

        startSource(context, source)
    }

    private fun startSource(context: Context, source: VideoSource.Asset) {
        val player = getPlayer(context)
        currentSource = source
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
            .build()
        player.setMediaItem(source.toMediaItem(context.packageName))
        player.prepare()
        player.play()
    }

    // PiP state is an app-level preview state, not Android system PiP.
    fun enterInAppPip() {
        val source = currentSource ?: return
        val player = player ?: return
        _pipState.value = InAppPipState(
            source = source,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            isPlaying = player.isPlaying
        )
    }

    fun exitInAppPip() {
        _pipState.value = null
    }

    // Stop is the app-session boundary: it releases media, PiP state, and render resources together.
    fun stop() {
        player?.stop()
        player?.clearMediaItems()
        currentSource = null
        _pipState.value = null
        releaseRenderEngine()
    }

    fun release() {
        stop()
        player?.release()
        player = null
        attachedRenderEngine = null
    }

    private fun releaseRenderEngine() {
        player?.clearVideoSurface()
        attachedRenderEngine = null
        renderEngine?.release()
        renderEngine = null
    }
}

data class InAppPipState(
    val source: VideoSource.Asset,
    val positionMs: Long,
    val isPlaying: Boolean
)
