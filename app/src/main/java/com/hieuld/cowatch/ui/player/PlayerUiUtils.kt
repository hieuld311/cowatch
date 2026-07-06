package com.hieuld.cowatch.ui.player

import androidx.media3.common.Player
import com.hieuld.cowatch.ext.resolveDisplayTitle
import com.hieuld.cowatch.util.formatDurationClock

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
    return formatDurationClock(positionMs)
}

internal fun resolvePlayerTitle(player: Player): String {
    return player.resolveDisplayTitle()
}
