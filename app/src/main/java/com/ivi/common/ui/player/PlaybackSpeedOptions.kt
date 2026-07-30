package com.ivi.common.ui.player

import com.ivi.R
import kotlin.math.abs

public val PLAYBACK_SPEED_OPTIONS = listOf(
    PlaybackSpeedOption(
        speed = 1f,
        iconResId = R.drawable.ico_media_1x_n,
        pressedIconResId = R.drawable.ico_media_1x_p,
        label = "1X"
    ),
    PlaybackSpeedOption(
        speed = 1.5f,
        iconResId = R.drawable.ico_media_1_5x_n,
        pressedIconResId = R.drawable.ico_media_1_5x_p,
        label = "1.5X"
    ),
    PlaybackSpeedOption(
        speed = 2f,
        iconResId = R.drawable.ico_media_2x_n,
        pressedIconResId = R.drawable.ico_media_2x_p,
        label = "2X"
    )
)

public fun playbackSpeedOption(speed: Float): PlaybackSpeedOption {
    return PLAYBACK_SPEED_OPTIONS.minBy { option ->
        abs(option.speed - speed)
    }
}

public fun nextPlaybackSpeedOption(speed: Float): PlaybackSpeedOption {
    val currentOption = playbackSpeedOption(speed)
    val currentIndex = PLAYBACK_SPEED_OPTIONS.indexOf(currentOption)
    return PLAYBACK_SPEED_OPTIONS[(currentIndex + 1).floorMod(PLAYBACK_SPEED_OPTIONS.size)]
}

private fun Int.floorMod(other: Int): Int {
    return ((this % other) + other) % other
}

public data class PlaybackSpeedOption(
    val speed: Float,
    val iconResId: Int,
    val pressedIconResId: Int,
    val label: String
)
