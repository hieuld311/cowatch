package com.ivi.common.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.media3.common.Player
import androidx.media3.common.PlaybackParameters
import com.ivi.common.playback.normalizedDurationMs
import com.ivi.common.playback.safeCurrentPositionMs
import com.ivi.common.playback.togglePlayback
import com.ivi.common.playback.resolveDisplayTitle

@Composable
public fun rememberPlaybackControlsState(player: Player): PlaybackControlsState {
    return remember(player) { PlaybackControlsState(player) }
}

@Stable
public class PlaybackControlsState(
    private val player: Player
) {
    var isPlaying by mutableStateOf(player.isPlaying)
        private set
    var durationMs by mutableLongStateOf(player.normalizedDurationMs)
        private set
    var sliderPositionMs by mutableLongStateOf(player.safeCurrentPositionMs)
        private set
    var videoTitle by mutableStateOf(player.resolveDisplayTitle())
        private set
    var playbackSpeed by mutableFloatStateOf(player.playbackParameters.speed)
        private set
    var isDragging by mutableStateOf(false)
        private set
    var controlsVisible by mutableStateOf(true)
        private set
    var interactionVersion by mutableIntStateOf(0)
        private set
    private var resumePlaybackAfterSeek = false

    val speedOption: PlaybackSpeedOption
        get() = playbackSpeedOption(playbackSpeed)

    fun refresh(updateSlider: Boolean) {
        isPlaying = player.isPlaying
        durationMs = player.normalizedDurationMs
        videoTitle = player.resolveDisplayTitle()
        playbackSpeed = player.playbackParameters.speed

        if (updateSlider && !isDragging) {
            sliderPositionMs = player.safeCurrentPositionMs
        }
    }

    fun showControls() {
        refresh(updateSlider = !isDragging)
        controlsVisible = true
        interactionVersion += 1
    }

    fun hideControlsIfIdle() {
        if (!isDragging) {
            controlsVisible = false
        }
    }

    fun updatePlaybackPosition() {
        sliderPositionMs = player.safeCurrentPositionMs
    }

    fun previewSeek(positionMs: Long) {
        if (!isDragging) {
            // Freeze playback once at the start of the gesture. Slider movement below is UI-only.
            resumePlaybackAfterSeek = player.playWhenReady
            isDragging = true
            player.pause()
        }
        showControls()
        sliderPositionMs = positionMs.coerceIn(0L, durationMs.coerceAtLeast(0L))
    }

    fun finishSeek() {
        if (!isDragging) return

        player.seekTo(sliderPositionMs)
        isDragging = false
        if (resumePlaybackAfterSeek) {
            player.play()
        }
        resumePlaybackAfterSeek = false
        showControls()
    }

    fun cyclePlaybackSpeed() {
        showControls()
        val nextSpeedOption = nextPlaybackSpeedOption(playbackSpeed)
        player.playbackParameters = PlaybackParameters(nextSpeedOption.speed)
        playbackSpeed = nextSpeedOption.speed
    }

    fun seekBack() {
        showControls()
        val target = (player.safeCurrentPositionMs - SEEK_STEP_MS).coerceAtLeast(0L)
        player.seekTo(target)
        sliderPositionMs = target
    }

    fun togglePlayback() {
        showControls()
        player.togglePlayback()
    }

    fun seekForward() {
        showControls()
        val target = player.safeCurrentPositionMs + SEEK_STEP_MS
        val targetPosition = if (durationMs > 0L) {
            target.coerceAtMost(durationMs)
        } else {
            target
        }
        player.seekTo(targetPosition)
        sliderPositionMs = targetPosition
    }

    companion object {
        private const val SEEK_STEP_MS = 5_000L
    }
}
