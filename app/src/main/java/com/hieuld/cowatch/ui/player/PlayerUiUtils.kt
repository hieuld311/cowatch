package com.hieuld.cowatch.ui.player

import androidx.media3.common.Player

internal fun Throwable.hasAudioTrackInitializationFailure(): Boolean {
    var current: Throwable? = this

    while (current != null) {
        if (
            current.javaClass.name.contains("AudioSink") ||
            current.message?.contains("Cannot create AudioTrack") == true
        ) {
            return true
        }
        current = current.cause
    }

    return false
}

internal fun formatPlaybackTime(positionMs: Long): String {
    val totalSeconds = (positionMs.coerceAtLeast(0L) / 1_000L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%02d:%02d".format(minutes, seconds)
}

internal fun resolvePlayerTitle(player: Player): String {
    player.mediaMetadata.title
        ?.toString()
        ?.takeIf { it.isNotBlank() }
        ?.let { return it }

    val mediaItem = player.currentMediaItem
    mediaItem?.mediaMetadata?.title
        ?.toString()
        ?.takeIf { it.isNotBlank() }
        ?.let { return it }

    mediaItem?.localConfiguration?.uri?.lastPathSegment
        ?.substringAfterLast('/')
        ?.substringBeforeLast('.')
        ?.takeIf { it.isNotBlank() }
        ?.let { return it }

    return "Untitled video"
}
