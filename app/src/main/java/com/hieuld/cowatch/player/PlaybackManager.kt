package com.hieuld.cowatch.player

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object PlaybackManager {

    private val _state = MutableStateFlow(SharedPlaybackState())
    val state: StateFlow<SharedPlaybackState> = _state.asStateFlow()

    fun setMediaIfNeeded(url: String) {
        val current = _state.value

        if (current.mediaUrl == url) return

        _state.value = current.copy(
            mediaUrl = url,
            isPlaying = false,
            positionMs = 0L,
            durationMs = 0L,
            playbackSpeed = 1f,
            startAtElapsedRealtimeMs = null,
            updatedAtElapsedMs = SystemClock.elapsedRealtime()
        )
    }

    fun pauseAt(
        positionMs: Long,
        durationMs: Long = 0L
    ) {
        val current = _state.value

        _state.value = current.copy(
            isPlaying = false,
            positionMs = positionMs,
            durationMs = durationMs,
            startAtElapsedRealtimeMs = null,
            updatedAtElapsedMs = SystemClock.elapsedRealtime()
        )
    }

    fun playAt(positionMs: Long) {
        val current = _state.value

        _state.value = current.copy(
            isPlaying = true,
            positionMs = positionMs,
            startAtElapsedRealtimeMs = null,
            updatedAtElapsedMs = SystemClock.elapsedRealtime()
        )
    }

    fun seekTo(positionMs: Long) {
        val current = _state.value

        _state.value = current.copy(
            positionMs = positionMs,
            startAtElapsedRealtimeMs = null,
            updatedAtElapsedMs = SystemClock.elapsedRealtime()
        )
    }

    fun updatePositionFromHost(
        positionMs: Long,
        durationMs: Long = 0L
    ) {
        val current = _state.value
        val nowMs = SystemClock.elapsedRealtime()

        if (current.hasScheduledStart) return

        _state.value = current.copy(
            positionMs = positionMs,
            durationMs = durationMs,
            updatedAtElapsedMs = nowMs
        )
    }

    fun updateSpeedFromHost(
        speed: Float,
        positionMs: Long
    ) {
        val current = _state.value

        _state.value = current.copy(
            playbackSpeed = speed,
            positionMs = positionMs,
            startAtElapsedRealtimeMs = null,
            updatedAtElapsedMs = SystemClock.elapsedRealtime()
        )
    }

    fun synchronizedStart(
        positionMs: Long,
        startAtElapsedRealtimeMs: Long
    ) {
        val current = _state.value

        _state.value = current.copy(
            isPlaying = true,
            positionMs = positionMs,
            startAtElapsedRealtimeMs = startAtElapsedRealtimeMs,
            updatedAtElapsedMs = SystemClock.elapsedRealtime()
        )
    }
}
