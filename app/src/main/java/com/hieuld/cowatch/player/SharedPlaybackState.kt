package com.hieuld.cowatch.player

import android.os.SystemClock

data class SharedPlaybackState(
    val mediaUrl: String = "",
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playbackSpeed: Float = 1f,
    val updatedAtElapsedMs: Long = SystemClock.elapsedRealtime(),
    val startAtElapsedRealtimeMs: Long? = null
) {
    fun expectedPositionAt(elapsedRealtimeMs: Long): Long {
        val scheduledStartAt = startAtElapsedRealtimeMs ?: return positionMs
        val baseElapsedMs = if (updatedAtElapsedMs > scheduledStartAt) {
            updatedAtElapsedMs
        } else {
            scheduledStartAt
        }
        val elapsedSinceBaseMs = elapsedRealtimeMs - baseElapsedMs

        if (elapsedSinceBaseMs <= 0L) return positionMs

        return positionMs + (elapsedSinceBaseMs * playbackSpeed).toLong()
    }

    val hasSharedTimeline: Boolean
        get() = startAtElapsedRealtimeMs != null
}
