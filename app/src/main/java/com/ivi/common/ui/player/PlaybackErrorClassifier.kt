package com.ivi.common.ui.player

public fun Throwable.hasAudioTrackInitializationFailure(): Boolean {
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
