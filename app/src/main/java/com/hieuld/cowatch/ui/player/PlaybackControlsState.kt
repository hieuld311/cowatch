package com.hieuld.cowatch.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.media3.common.Player
import androidx.media3.common.PlaybackParameters
import com.hieuld.cowatch.ext.normalizedDurationMs
import com.hieuld.cowatch.ext.safeCurrentPositionMs

@Composable
internal fun rememberPlaybackControlsState(player: Player): PlaybackControlsState {
    return remember(player) { PlaybackControlsState(player) }
}

internal class PlaybackControlsState(
    private val player: Player
) {
    var isPlaying by mutableStateOf(player.isPlaying)
        private set
    var durationMs by mutableLongStateOf(player.normalizedDurationMs)
        private set
    var sliderPositionMs by mutableLongStateOf(player.safeCurrentPositionMs)
        private set
    var videoTitle by mutableStateOf(resolvePlayerTitle(player))
        private set
    var playbackSpeed by mutableFloatStateOf(player.playbackParameters.speed)
        private set
    var isDragging by mutableStateOf(false)
        private set
    var controlsVisible by mutableStateOf(true)
        private set
    var interactionVersion by mutableIntStateOf(0)
        private set

    val speedOption: PlaybackSpeedOption
        get() = playbackSpeedOption(playbackSpeed)

    fun refresh(updateSlider: Boolean) {
        isPlaying = player.isPlaying
        durationMs = player.normalizedDurationMs
        videoTitle = resolvePlayerTitle(player)
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
        showControls()
        isDragging = true
        sliderPositionMs = positionMs
    }

    fun finishSeek() {
        player.seekTo(sliderPositionMs)
        isDragging = false
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
        if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
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
