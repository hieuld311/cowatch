package com.ivi.common.ui.player

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.media3.common.Player
import com.ivi.R
import com.ivi.common.media.SeekFrameProvider
import com.ivi.common.ui.PressStateIconButton
import com.ivi.common.ui.PrimaryPlaybackButton
import com.ivi.common.ui.coWatchColorScheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

public const val PLAYBACK_CONTROL_BAR_HEIGHT_DP = 154

private const val PLAYBACK_POSITION_UPDATE_DELAY_MS = 500L
private const val CONTROLS_AUTO_HIDE_DELAY_MS = 5_000L
private const val REWIND_FRAME_TIMEOUT_MS = 800L
private val CONTROL_BAR_BACKGROUND_HEIGHT = 134.dp
private val CONTROL_BAR_HEIGHT = PLAYBACK_CONTROL_BAR_HEIGHT_DP.dp
private val TIMELINE_HEIGHT = 48.dp
private val TRANSPORT_ICON_SIZE = 40.dp
private val TRANSPORT_SLOT_SIZE = 124.dp
private val PREVIEW_OVERLAY_HEIGHT = 135.dp

@Composable
public fun PlaybackControlBar(
    player: Player,
    activeAssetPath: String?,
    seekFrameProvider: SeekFrameProvider,
    onPictureInPictureClick: () -> Unit,
    onPreviousVideo: () -> Unit,
    onNextVideo: () -> Unit,
    modifier: Modifier = Modifier,
    showVideoTitle: Boolean = false,
    controlsEnabled: Boolean = true,
    @DrawableRes controlBackgroundDrawable: Int = R.drawable.img_media_control_background,
    leadingControl: (@Composable (onInteraction: () -> Unit) -> Unit)? = null,
    trailingControl: (@Composable () -> Unit)? = null
) {
    val controlsState = rememberPlaybackControlsState(player)

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                controlsState.onRenderedFirstFrame()
            }

            override fun onEvents(player: Player, events: Player.Events) {
                controlsState.refresh(updateSlider = !controlsState.isDragging)
            }
        }
        player.addListener(listener)
        controlsState.refresh(updateSlider = true)
        onDispose {
            controlsState.endRewind()
            controlsState.endFastForward()
            player.removeListener(listener)
        }
    }

    LaunchedEffect(player, controlsState.isPlaying, controlsState.isDragging) {
        if (!controlsState.isPlaying || controlsState.isDragging) return@LaunchedEffect
        while (true) {
            controlsState.updatePlaybackPosition()
            delay(PLAYBACK_POSITION_UPDATE_DELAY_MS)
        }
    }

    LaunchedEffect(controlsState.isRewinding) {
        var renderedFrameVersion = controlsState.rewindRenderedFrameVersion
        while (controlsState.isRewinding) {
            val stepStartedAtMs = SystemClock.elapsedRealtime()
            if (controlsState.rewindStep()) {
                withTimeoutOrNull(REWIND_FRAME_TIMEOUT_MS) {
                    snapshotFlow { controlsState.rewindRenderedFrameVersion }
                        .first { it > renderedFrameVersion }
                }
                renderedFrameVersion = controlsState.rewindRenderedFrameVersion
            }
            val remainingDelayMs = (
                PlaybackControlsState.TRANSPORT_HOLD_TICK_DELAY_MS -
                    (SystemClock.elapsedRealtime() - stepStartedAtMs)
                ).coerceAtLeast(0L)
            delay(remainingDelayMs)
        }
    }

    LaunchedEffect(
        controlsState.interactionVersion,
        controlsState.controlsVisible,
        controlsState.isDragging,
        controlsState.isRewinding,
        controlsState.isFastForwarding
    ) {
        if (controlsState.controlsVisible &&
            !controlsState.isDragging &&
            !controlsState.isRewinding &&
            !controlsState.isFastForwarding
        ) {
            delay(CONTROLS_AUTO_HIDE_DELAY_MS)
            controlsState.hideControlsIfIdle()
        }
    }

    Box(
        modifier = modifier.clickable(
            indication = null,
            interactionSource = remember { MutableInteractionSource() }
        ) { controlsState.showControls() }
    ) {
        if (controlsState.isDragging && activeAssetPath != null) {
            SeekFramePreview(
                seekFrameProvider = seekFrameProvider,
                assetPath = activeAssetPath,
                positionMs = controlsState.sliderPositionMs,
                durationMs = controlsState.durationMs,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(bottom = CONTROL_BAR_HEIGHT + 12.dp)
                    .fillMaxWidth()
                    .height(PREVIEW_OVERLAY_HEIGHT)
            )
        }

        if (showVideoTitle) {
            AnimatedVisibility(
                visible = controlsState.controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopStart)
            ) {
                Text(
                    text = controlsState.videoTitle,
                    modifier = Modifier.padding(80.dp),
                    color = MaterialTheme.coWatchColorScheme.contentPrimary,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        AnimatedVisibility(
            visible = controlsState.controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CONTROL_BAR_HEIGHT)
            ) {
                ControlBarBackground(
                    backgroundDrawable = controlBackgroundDrawable,
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    TransportControls(
                        controlsState = controlsState,
                        onPictureInPictureClick = onPictureInPictureClick,
                        onPreviousVideo = onPreviousVideo,
                        onNextVideo = onNextVideo,
                        leadingControl = leadingControl,
                        trailingControl = trailingControl,
                        controlsEnabled = controlsEnabled,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                PlaybackTimeline(
                    positionMs = controlsState.sliderPositionMs,
                    durationMs = controlsState.durationMs,
                    enabled = controlsEnabled && controlsState.durationMs > 0L,
                    onSeekPreview = controlsState::previewSeek,
                    onSeekFinished = controlsState::finishSeek,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .zIndex(10f)
                )
            }
        }
    }
}

@Composable
public fun ReadOnlyPlaybackControlBar(
    visible: Boolean,
    positionMs: Long,
    durationMs: Long,
    title: String,
    isPlaying: Boolean,
    playbackSpeed: Float,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            Text(
                text = title,
                modifier = Modifier.padding(80.dp),
                color = MaterialTheme.coWatchColorScheme.contentPrimary,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CONTROL_BAR_HEIGHT)
            ) {
                ControlBarBackground(
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    ReadOnlyTransportControls(
                        isPlaying = isPlaying,
                        playbackSpeed = playbackSpeed,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                PlaybackTimeline(
                    positionMs = positionMs,
                    durationMs = durationMs,
                    enabled = false,
                    onSeekPreview = {},
                    onSeekFinished = {},
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .zIndex(10f)
                )
            }
        }
    }
}

@Composable
private fun ControlBarBackground(
    @DrawableRes backgroundDrawable: Int = R.drawable.img_media_control_background,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(CONTROL_BAR_BACKGROUND_HEIGHT)
            .paint(
                painter = painterResource(backgroundDrawable),
                contentScale = ContentScale.FillBounds
            ),
        content = content
    )
}

@Composable
private fun PlaybackTimeline(
    positionMs: Long,
    durationMs: Long,
    enabled: Boolean,
    onSeekPreview: (Long) -> Unit,
    onSeekFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(TIMELINE_HEIGHT)
    ) {
        VideoSeekBar(
            positionMs = positionMs,
            durationMs = durationMs,
            onSeekPreview = onSeekPreview,
            onSeekFinished = onSeekFinished,
            enabled = enabled,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(SEEK_BAR_VISUAL_HEIGHT)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 28.dp)
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ControlTime(formatDurationClock(positionMs))
            ControlTime(
                text = formatDurationClock(durationMs),
                textAlign = TextAlign.End
            )
        }
    }
}

@Composable
private fun TransportControls(
    controlsState: PlaybackControlsState,
    onPictureInPictureClick: () -> Unit,
    onPreviousVideo: () -> Unit,
    onNextVideo: () -> Unit,
    leadingControl: (@Composable (onInteraction: () -> Unit) -> Unit)?,
    trailingControl: (@Composable () -> Unit)?,
    controlsEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    TransportControlsLayout(
        modifier = modifier,
        leftControls = {
            if (leadingControl != null) {
                TransportControlSlot {
                    leadingControl(controlsState::showControls)
                }
            }
            val speed = controlsState.speedOption
            PressStateIconButton(
                normalDrawable = speed.iconResId,
                pressedDrawable = speed.pressedIconResId,
                contentDescription = "Playback speed ${speed.label}",
                layoutSize = TRANSPORT_SLOT_SIZE,
                iconSize = TRANSPORT_ICON_SIZE,
                onClick = controlsState::cyclePlaybackSpeed,
                enabled = controlsEnabled
            )
            PressStateIconButton(
                normalDrawable = R.drawable.ico_media_prev_n,
                pressedDrawable = R.drawable.ico_media_prev_p,
                contentDescription = "Previous video",
                layoutSize = TRANSPORT_SLOT_SIZE,
                iconSize = TRANSPORT_ICON_SIZE,
                onClick = onPreviousVideo,
                enabled = controlsEnabled,
                onLongPressStart = controlsState::beginRewind,
                onLongPressEnd = controlsState::endRewind
            )
        },
        centerControl = {
            PrimaryPlaybackButton(
            isPlaying = controlsState.isPlaying,
            onClick = controlsState::togglePlayback,
            buttonSize = TRANSPORT_SLOT_SIZE,
            enabled = controlsEnabled
            )
        },
        rightControls = {
            PressStateIconButton(
                normalDrawable = R.drawable.ico_media_next_n,
                pressedDrawable = R.drawable.ico_media_next_p,
                contentDescription = "Next video",
                layoutSize = TRANSPORT_SLOT_SIZE,
                iconSize = TRANSPORT_ICON_SIZE,
                onClick = onNextVideo,
                enabled = controlsEnabled,
                onLongPressStart = controlsState::beginFastForward,
                onLongPressEnd = controlsState::endFastForward
            )
            PressStateIconButton(
                normalDrawable = R.drawable.ico_media_collapse_n,
                pressedDrawable = R.drawable.ico_media_collapse_p,
                contentDescription = "Collapse player",
                layoutSize = TRANSPORT_SLOT_SIZE,
                iconSize = TRANSPORT_ICON_SIZE,
                onClick = {
                    controlsState.showControls()
                    onPictureInPictureClick()
                },
                enabled = controlsEnabled
            )
            if (trailingControl != null) {
                TransportControlSlot {
                    trailingControl()
                }
            }
        }
    )
}

@Composable
private fun ReadOnlyTransportControls(
    isPlaying: Boolean,
    playbackSpeed: Float,
    modifier: Modifier = Modifier
) {
    val speed = playbackSpeedOption(playbackSpeed)
    TransportControlsLayout(
        modifier = modifier,
        leftControls = {
            PressStateIconButton(
                normalDrawable = speed.iconResId,
                pressedDrawable = speed.pressedIconResId,
                contentDescription = "Playback speed ${speed.label}",
                layoutSize = TRANSPORT_SLOT_SIZE,
                iconSize = TRANSPORT_ICON_SIZE,
                onClick = {},
                enabled = false,
                forcePressedVisual = true
            )
            PressStateIconButton(
                normalDrawable = R.drawable.ico_media_prev_n,
                pressedDrawable = R.drawable.ico_media_prev_p,
                contentDescription = "Back",
                layoutSize = TRANSPORT_SLOT_SIZE,
                iconSize = TRANSPORT_ICON_SIZE,
                onClick = {},
                enabled = false,
                forcePressedVisual = true
            )
        },
        centerControl = {
            PrimaryPlaybackButton(
                isPlaying = isPlaying,
                onClick = {},
                buttonSize = TRANSPORT_SLOT_SIZE,
                enabled = false,
                forcePressedVisual = true
            )
        },
        rightControls = {
            PressStateIconButton(
                normalDrawable = R.drawable.ico_media_next_n,
                pressedDrawable = R.drawable.ico_media_next_p,
                contentDescription = "Forward",
                layoutSize = TRANSPORT_SLOT_SIZE,
                iconSize = TRANSPORT_ICON_SIZE,
                onClick = {},
                enabled = false,
                forcePressedVisual = true
            )
            PressStateIconButton(
                normalDrawable = R.drawable.ico_media_collapse_n,
                pressedDrawable = R.drawable.ico_media_collapse_p,
                contentDescription = "Collapse player",
                layoutSize = TRANSPORT_SLOT_SIZE,
                iconSize = TRANSPORT_ICON_SIZE,
                onClick = {},
                enabled = false,
                forcePressedVisual = true
            )
        }
    )
}

@Composable
private fun TransportControlsLayout(
    leftControls: @Composable RowScope.() -> Unit,
    centerControl: @Composable () -> Unit,
    rightControls: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(TRANSPORT_SLOT_SIZE)
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth(0.5f)
                .padding(end = TRANSPORT_SLOT_SIZE / 2),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
            content = leftControls
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(TRANSPORT_SLOT_SIZE),
            contentAlignment = Alignment.Center
        ) {
            centerControl()
        }
        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxWidth(0.5f)
                .padding(start = TRANSPORT_SLOT_SIZE / 2),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
            content = rightControls
        )
    }
}

@Composable
private fun TransportControlSlot(
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit
) {
    Box(
        modifier = Modifier.size(TRANSPORT_SLOT_SIZE),
        contentAlignment = Alignment.Center,
        content = content
    )
}

@Composable
private fun ControlTime(text: String, textAlign: TextAlign = TextAlign.Start) {
    Text(
        text = text,
        color = MaterialTheme.coWatchColorScheme.contentPrimary.copy(alpha = 0.7f),
        style = MaterialTheme.typography.labelSmall,
        textAlign = textAlign,
        modifier = Modifier.width(52.dp)
    )
}
