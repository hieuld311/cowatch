package com.ivi.common.playback

import android.content.Context
import android.view.Surface
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionResult
import androidx.media3.ui.PlayerView
import com.ivi.common.domain.VideoSource
import com.ivi.common.domain.InAppPipState
import com.ivi.common.domain.PlaybackController
import com.ivi.common.domain.SessionPlaybackState
import com.ivi.common.playback.PlayerSurfaceController
import com.ivi.common.playback.togglePlayback
import com.ivi.common.playback.toMediaItem
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class Media3PlaybackController @Inject constructor(
    @ApplicationContext context: Context
) : PlaybackController, PlayerSurfaceController {
    private val appContext = context.applicationContext
    private var sessionPlayer: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private var activePlayerView: PlayerView? = null
    private var externalVideoSurface: Surface? = null
    @Volatile
    private var sharedMode = false
    private val _pipState = MutableStateFlow<InAppPipState?>(null)
    private val _playbackState = MutableStateFlow(SessionPlaybackState())
    private val _playbackEnded = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override val pipState: StateFlow<InAppPipState?> = _pipState.asStateFlow()
    override val playbackState: StateFlow<SessionPlaybackState> = _playbackState.asStateFlow()
    override val playbackEnded: SharedFlow<Unit> = _playbackEnded
    var currentSource: VideoSource.Asset? = null
        private set

    val exoPlayer: ExoPlayer
        get() = getOrCreatePlayer()

    // One ExoPlayer stays alive across fullscreen playback and app-scoped PiP.
    @OptIn(UnstableApi::class)
    private fun getOrCreatePlayer(): ExoPlayer {
        val renderersFactory = DefaultRenderersFactory(appContext)
            .setEnableDecoderFallback(true)
            .setMediaCodecSelector(nonQualcommAacCodecSelector)

        return sessionPlayer ?: ExoPlayer.Builder(appContext, renderersFactory).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                false
            )
            volume = 1f
        }.also { createdPlayer ->
            sessionPlayer = createdPlayer
            createdPlayer.addListener(sessionPlayerListener)
            // The session owns platform/media-control integration; PlayerView renders directly.
            mediaSession = MediaSession.Builder(appContext, createdPlayer)
                .setCallback(object : MediaSession.Callback {
                    override fun onPlayerCommandRequest(
                        session: MediaSession,
                        controller: MediaSession.ControllerInfo,
                        playerCommand: Int
                    ): Int {
                        return if (sharedMode) {
                            SessionResult.RESULT_ERROR_PERMISSION_DENIED
                        } else {
                            SessionResult.RESULT_SUCCESS
                        }
                    }
                })
                .build()
        }
    }

    // Returning from PiP to the same asset must not restart the decoder or lose playback position.
    override fun showFullscreen(source: VideoSource.Asset) {
        val player = getOrCreatePlayer()
        val sameSource = currentSource?.assetPath == source.assetPath
        val returningFromPip = _pipState.value != null

        exitInAppPip()

        if (returningFromPip && sameSource && player.currentMediaItem != null) {
            return
        }

        startSource(source)
    }

    private fun startSource(source: VideoSource.Asset) {
        val player = getOrCreatePlayer()
        currentSource = source
        publishPlaybackState()
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
            .build()
        player.setMediaItem(source.toMediaItem())
        player.prepare()
        player.play()
    }

    // PiP state is an app-level preview state, not Android system PiP.
    override fun enterInAppPip() {
        val source = currentSource ?: return
        if (sessionPlayer == null) return
        _pipState.value = InAppPipState(
            source = source
        )
    }

    override fun exitInAppPip() {
        _pipState.value = null
    }

    override fun togglePlayback() {
        if (sharedMode) return
        sessionPlayer?.togglePlayback()
    }

    fun setSharedMode(enabled: Boolean) {
        sharedMode = enabled
    }

    /** Releases Rear media resources while preserving the process-level player and MediaSession. */
    fun suspendForSharedRendering(): LocalPlaybackSnapshot {
        val source = currentSource
        val player = sessionPlayer
        val snapshot = LocalPlaybackSnapshot(
            source = source,
            positionMs = player?.currentPosition?.coerceAtLeast(0L) ?: 0L,
            wasPlaying = player?.isPlaying == true,
            playbackSpeed = player?.playbackParameters?.speed ?: 1f,
            destination = when {
                source == null -> LocalPlaybackDestination.LIBRARY_IDLE
                _pipState.value != null -> LocalPlaybackDestination.LIBRARY_PIP
                else -> LocalPlaybackDestination.FULLSCREEN
            }
        )
        stopPlaybackInternal(notifyEnded = false)
        return snapshot
    }

    fun restoreAfterSharedRendering(snapshot: LocalPlaybackSnapshot): LocalPlaybackDestination {
        sharedMode = false
        val source = snapshot.source
        if (source == null) {
            stopPlaybackInternal(notifyEnded = false)
            return snapshot.destination
        }

        val player = getOrCreatePlayer()
        currentSource = source
        player.setMediaItem(source.toMediaItem(), snapshot.positionMs)
        player.setPlaybackSpeed(snapshot.playbackSpeed)
        player.prepare()
        player.playWhenReady = snapshot.wasPlaying
        _pipState.value = if (snapshot.destination == LocalPlaybackDestination.LIBRARY_PIP) {
            InAppPipState(source)
        } else {
            null
        }
        publishPlaybackState()
        return snapshot.destination
    }

    private val sessionPlayerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            publishPlaybackState()
        }
    }

    private fun publishPlaybackState() {
        _playbackState.value = SessionPlaybackState(
            activeSource = currentSource,
            isPlaying = sessionPlayer?.isPlaying == true
        )
    }

    /**
     * Moves the single session player between fullscreen and in-app PiP surfaces without passing
     * through a no-surface state. This keeps video attached while audio/playback continues.
     */
    @OptIn(UnstableApi::class)
    override fun attach(targetView: PlayerView) {
        val sessionPlayer = sessionPlayer ?: return
        val previousView = activePlayerView
        if (previousView === targetView) return

        externalVideoSurface = null
        PlayerView.switchTargetView(sessionPlayer, previousView, targetView)
        activePlayerView = targetView
    }

    override fun detach(targetView: PlayerView) {
        if (activePlayerView !== targetView) return

        targetView.player = null
        activePlayerView = null
    }

    /** Routes the PID player into the fanout engine instead of a PlayerView-owned surface. */
    fun attachExternalVideoSurface(surface: Surface) {
        val player = getOrCreatePlayer()
        if (externalVideoSurface === surface) return
        activePlayerView?.player = null
        activePlayerView = null
        player.setVideoSurface(surface)
        externalVideoSurface = surface
    }

    fun detachExternalVideoSurface(surface: Surface) {
        if (externalVideoSurface !== surface) return
        sessionPlayer?.clearVideoSurface(surface)
        externalVideoSurface = null
    }

    // Stop releases active media/codec resources but keeps one stable process-level player/session.
    override fun stop() {
        stopPlaybackInternal(notifyEnded = true)
    }

    private fun stopPlaybackInternal(notifyEnded: Boolean) {
        activePlayerView?.player = null
        activePlayerView = null
        sessionPlayer?.clearVideoSurface()
        externalVideoSurface = null
        sessionPlayer?.stop()
        sessionPlayer?.clearMediaItems()
        currentSource = null
        _playbackState.value = SessionPlaybackState()
        _pipState.value = null
        if (notifyEnded) _playbackEnded.tryEmit(Unit)
    }

    fun releaseProcessResources() {
        stopPlaybackInternal(notifyEnded = false)
        mediaSession?.release()
        mediaSession = null
        sessionPlayer?.release()
        sessionPlayer = null
    }

    private val nonQualcommAacCodecSelector = MediaCodecSelector {
        mimeType,
        requiresSecureDecoder,
        requiresTunnelingDecoder ->
        val decoders = MediaCodecSelector.DEFAULT.getDecoderInfos(
            mimeType,
            requiresSecureDecoder,
            requiresTunnelingDecoder
        )

        if (mimeType != MimeTypes.AUDIO_AAC) {
            return@MediaCodecSelector decoders
        }

        decoders.filterNot { decoder ->
            decoder.name.contains("qti", ignoreCase = true) ||
                    decoder.name.contains("qualcomm", ignoreCase = true)
        }.ifEmpty { decoders }
    }
}

data class LocalPlaybackSnapshot(
    val source: VideoSource.Asset?,
    val positionMs: Long,
    val wasPlaying: Boolean,
    val playbackSpeed: Float,
    val destination: LocalPlaybackDestination
)

enum class LocalPlaybackDestination {
    FULLSCREEN,
    LIBRARY_PIP,
    LIBRARY_IDLE
}
