package com.hieuld.cowatch.ext

import androidx.media3.common.C
import androidx.media3.common.Player

val Player.normalizedDurationMs: Long
    get() = duration.takeIf { it > 0L && it != C.TIME_UNSET } ?: 0L

val Player.safeCurrentPositionMs: Long
    get() = currentPosition.coerceAtLeast(0L)

fun Player.resolveDisplayTitle(): String {
    mediaMetadata.title
        ?.toString()
        ?.takeIf { it.isNotBlank() }
        ?.let { return it }

    val mediaItem = currentMediaItem
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
