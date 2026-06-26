package com.hieuld.cowatch.player

import android.os.SystemClock

data class SharedPlaybackState(
    val sessionId: String = "",
    val version: Long = 0L,

    val mediaUri: String = "",
    val mediaId: String = "",
    val title: String = "",

    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playbackSpeed: Float = 1f,

    val updatedAtElapsedMs: Long = SystemClock.elapsedRealtime(),
    val startAtElapsedRealtimeMs: Long? = null
) {
    val hasScheduledStart: Boolean
        get() = startAtElapsedRealtimeMs != null
}
