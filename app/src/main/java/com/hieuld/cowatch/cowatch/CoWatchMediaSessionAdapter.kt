package com.hieuld.cowatch.cowatch

import android.content.Context
import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import com.hieuld.cowatch.player.SharedPlaybackState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

object CoWatchMediaSessionAdapter {

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null

    private var activeCoWatchSessionId: String = ""
    private val versionGenerator = AtomicLong(0L)

    private val _state = MutableStateFlow(SharedPlaybackState())
    val state: StateFlow<SharedPlaybackState> = _state.asStateFlow()

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            exportFromPlayer(player)
        }
    }

    fun attach(
        context: Context,
        exoPlayer: ExoPlayer
    ) {
        if (player === exoPlayer && mediaSession != null) {
            exportFromPlayer(exoPlayer)
            return
        }

        release()

        player = exoPlayer

        mediaSession = MediaSession.Builder(context, exoPlayer)
            .setId("cowatch_host_media_session")
            .build()

        exoPlayer.addListener(playerListener)
        exportFromPlayer(exoPlayer)
    }

    fun release() {
        player?.removeListener(playerListener)
        mediaSession?.release()

        mediaSession = null
        player = null
        activeCoWatchSessionId = ""
    }

    fun beginCoWatchSession(sessionId: String) {
        activeCoWatchSessionId = sessionId

        val current = _state.value
        publish(
            current.copy(
                sessionId = sessionId,
                version = nextVersion(),
                updatedAtElapsedMs = SystemClock.elapsedRealtime()
            )
        )
    }

    fun endCoWatchSession() {
        activeCoWatchSessionId = ""

        val current = _state.value
        publish(
            current.copy(
                sessionId = "",
                startAtElapsedRealtimeMs = null,
                version = nextVersion(),
                updatedAtElapsedMs = SystemClock.elapsedRealtime()
            )
        )
    }

    fun setMedia(
        mediaUri: String,
        title: String = "CoWatch Media"
    ) {
        val exoPlayer = player ?: return

        val item = MediaItem.Builder()
            .setUri(mediaUri)
            .setMediaId(mediaUri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .build()
            )
            .build()

        exoPlayer.setMediaItem(item)
        exoPlayer.prepare()

        exportFromPlayer(exoPlayer)
    }

    fun play() {
        val exoPlayer = player ?: return
        exoPlayer.play()
        exportFromPlayer(exoPlayer)
    }

    fun pause() {
        val exoPlayer = player ?: return
        exoPlayer.pause()
        exportFromPlayer(exoPlayer)
    }

    fun seekTo(positionMs: Long) {
        val exoPlayer = player ?: return
        exoPlayer.seekTo(positionMs)
        exportFromPlayer(exoPlayer)
    }

    fun setPlaybackSpeed(speed: Float) {
        val exoPlayer = player ?: return
        exoPlayer.setPlaybackSpeed(speed)
        exportFromPlayer(exoPlayer)
    }

    fun publishHostSnapshot() {
        val exoPlayer = player ?: return
        exportFromPlayer(exoPlayer)
    }

    fun publishSynchronizedStart(
        positionMs: Long,
        startAtElapsedRealtimeMs: Long
    ) {
        val current = _state.value
        val exoPlayer = player

        publish(
            current.copy(
                sessionId = activeCoWatchSessionId.ifBlank { current.sessionId },
                version = nextVersion(),
                isPlaying = true,
                positionMs = positionMs,
                durationMs = exoPlayer?.duration
                    ?.takeIf { it > 0L }
                    ?: current.durationMs,
                playbackSpeed = exoPlayer?.playbackParameters?.speed
                    ?: current.playbackSpeed,
                startAtElapsedRealtimeMs = startAtElapsedRealtimeMs,
                updatedAtElapsedMs = SystemClock.elapsedRealtime()
            )
        )
    }

    fun clearScheduledStart() {
        val current = _state.value
        val exoPlayer = player ?: return

        if (current.startAtElapsedRealtimeMs == null) return

        publish(
            current.copy(
                version = nextVersion(),
                isPlaying = exoPlayer.isPlaying,
                positionMs = exoPlayer.currentPosition.coerceAtLeast(0L),
                durationMs = exoPlayer.duration.takeIf { it > 0L }
                    ?: current.durationMs,
                playbackSpeed = exoPlayer.playbackParameters.speed,
                startAtElapsedRealtimeMs = null,
                updatedAtElapsedMs = SystemClock.elapsedRealtime()
            )
        )
    }

    private fun exportFromPlayer(player: Player) {
        val current = _state.value
        val mediaItem = player.currentMediaItem

        val durationMs = player.duration
            .takeIf { it > 0L }
            ?: current.durationMs

        publish(
            current.copy(
                sessionId = activeCoWatchSessionId.ifBlank { current.sessionId },
                version = nextVersion(),
                mediaUri = mediaItem
                    ?.localConfiguration
                    ?.uri
                    ?.toString()
                    .orEmpty(),
                mediaId = mediaItem?.mediaId.orEmpty(),
                title = mediaItem
                    ?.mediaMetadata
                    ?.title
                    ?.toString()
                    .orEmpty(),
                isPlaying = if (current.hasScheduledStart) {
                    current.isPlaying
                } else {
                    player.isPlaying
                },
                positionMs = if (current.hasScheduledStart) {
                    current.positionMs
                } else {
                    player.currentPosition.coerceAtLeast(0L)
                },
                durationMs = durationMs,
                playbackSpeed = player.playbackParameters.speed,
                updatedAtElapsedMs = SystemClock.elapsedRealtime(),
                startAtElapsedRealtimeMs = current.startAtElapsedRealtimeMs
            )
        )
    }

    private fun publish(state: SharedPlaybackState) {
        val current = _state.value
        if (state.version < current.version) return
        _state.value = state
    }

    private fun nextVersion(): Long {
        return versionGenerator.incrementAndGet()
    }
}