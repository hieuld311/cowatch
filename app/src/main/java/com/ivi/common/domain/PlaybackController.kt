package com.ivi.common.domain

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface PlaybackController {
    val pipState: StateFlow<InAppPipState?>
    val playbackState: StateFlow<SessionPlaybackState>
    val playbackCompleted: SharedFlow<Unit>

    fun showFullscreen(source: VideoSource.Asset)
    fun continuePlayback(source: VideoSource.Asset)
    fun enterInAppPip()
    fun exitInAppPip()
    fun togglePlayback()
    fun stop()
}

data class InAppPipState(val source: VideoSource.Asset)

data class SessionPlaybackState(
    val activeSource: VideoSource.Asset? = null,
    val isPlaying: Boolean = false
)
