package com.ivi.common.ui.library

import kotlin.math.abs

/** Shared UX: selecting a card changes focus; it never starts playback implicitly. */
inline fun handleVideoCardClick(
    isFocused: Boolean,
    onFocusRequested: () -> Unit
) {
    if (!isFocused) onFocusRequested()
}

/** Shared UX: playback starts only from Play, or toggles the already-active session. */
inline fun handleFocusedVideoPlay(
    isActiveSessionVideo: Boolean,
    onActiveSessionPlaybackToggle: () -> Unit,
    onNewVideoPlayRequested: () -> Unit
) {
    if (isActiveSessionVideo) {
        onActiveSessionPlaybackToggle()
    } else {
        onNewVideoPlayRequested()
    }
}

public fun circularIndex(index: Int, count: Int): Int {
    return ((index % count) + count) % count
}

public fun shortestCircularDelta(from: Int, to: Int, count: Int): Int {
    if (count <= 1) return 0

    val forward = (to - from + count) % count
    val backward = forward - count
    return if (abs(forward) <= abs(backward)) forward else backward
}
