package com.ivi.common.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.media3.common.Player
import com.ivi.R
import com.ivi.common.media.SeekFrameProvider
import com.ivi.common.ui.PressStateIconButton
import com.ivi.common.ui.PrimaryPlaybackButton
import com.ivi.common.ui.coWatchColorScheme
import kotlinx.coroutines.delay

public const val PLAYBACK_CONTROL_BAR_HEIGHT_DP = 154

private const val CONTROLS_AUTO_HIDE_DELAY_MS = 5_000L
private val CONTROL_BAR_BACKGROUND_HEIGHT = 134.dp
private val DEFAULT_CONTROL_BAR_HEIGHT = PLAYBACK_CONTROL_BAR_HEIGHT_DP.dp
private val TRANSPORT_ICON_SIZE = 48.dp
private val TRANSPORT_SLOT_SIZE = 136.dp
private val CONTROL_BAR_BACKGROUND_TOP_GAP = 20.dp
private val CONTROL_BAR_BACKGROUND_BLUR_RADIUS = 32.dp
private val PREVIEW_OVERLAY_HEIGHT = 135.dp
private val TITLE_BACKGROUND_COLOR = Color.Black.copy(alpha = 0.1f)

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
    titlePaddingTop: Dp = 75.dp,
    titlePaddingStart: Dp = 48.dp,
    titleColor: Color = Color.White,
    titleBackgroundColor: Color = TITLE_BACKGROUND_COLOR,
    titleCornerRadius: Dp = 4.dp,
    titleContentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    titleFontSize: TextUnit = 44.sp,
    controlBarHeight: Dp = DEFAULT_CONTROL_BAR_HEIGHT,
    transportSlotSize: Dp = TRANSPORT_SLOT_SIZE,
    transportIconSize: Dp = TRANSPORT_ICON_SIZE,
    controlsEnabled: Boolean = true,
    @DrawableRes controlBackgroundDrawable: Int = R.drawable.img_media_control_background,
    leadingControl: (@Composable (onInteraction: () -> Unit) -> Unit)? = null,
    trailingControl: (@Composable () -> Unit)? = null,
    onControlsVisibilityChanged: (Boolean) -> Unit = {}
) {
    val controlsState = rememberPlaybackControlsState(player)

    LaunchedEffect(controlsState.controlsVisible) {
        onControlsVisibilityChanged(controlsState.controlsVisible)
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
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

    LaunchedEffect(
        player,
        controlsState.isPlaying,
        controlsState.isRewinding,
        controlsState.isFastForwarding,
        controlsState.isDragging,
        controlsState.controlsVisible
    ) {
        if (!controlsState.controlsVisible ||
            controlsState.isDragging ||
            (!controlsState.isPlaying &&
                !controlsState.isRewinding &&
                !controlsState.isFastForwarding)
        ) {
            return@LaunchedEffect
        }

        var previousFrameNanos = withFrameNanos { it }
        while (true) {
            val frameNanos = withFrameNanos { it }
            if (controlsState.isRewinding) {
                controlsState.advanceRewindPosition(frameNanos - previousFrameNanos)
            } else {
                controlsState.updatePlaybackPosition()
            }
            previousFrameNanos = frameNanos
        }
    }

    LaunchedEffect(controlsState.isRewinding) {
        while (controlsState.isRewinding) {
            delay(PlaybackControlsState.TRANSPORT_HOLD_TICK_DELAY_MS)
            controlsState.rewindStep()
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
                    .padding(bottom = controlBarHeight + 12.dp)
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
                Box(
                    modifier = Modifier
                        .padding(top = titlePaddingTop, start = titlePaddingStart)
                        .background(titleBackgroundColor, RoundedCornerShape(titleCornerRadius))
                        .padding(titleContentPadding)
                ) {
                    Text(
                        text = controlsState.videoTitle,
                        color = titleColor,
                        style = MaterialTheme.typography.headlineSmall,
                        fontSize = titleFontSize,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
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
                    .height(controlBarHeight)
            ) {
                ControlBarBackground(
                    backgroundDrawable = controlBackgroundDrawable,
                    height = (controlBarHeight - CONTROL_BAR_BACKGROUND_TOP_GAP)
                        .coerceAtLeast(CONTROL_BAR_BACKGROUND_HEIGHT),
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
                        slotSize = transportSlotSize,
                        iconSize = transportIconSize,
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
                    .height(DEFAULT_CONTROL_BAR_HEIGHT)
            ) {
                ControlBarBackground(
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
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
    height: Dp = CONTROL_BAR_BACKGROUND_HEIGHT,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit = {}
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .blur(CONTROL_BAR_BACKGROUND_BLUR_RADIUS)
                .paint(
                    painter = painterResource(backgroundDrawable),
                    contentScale = ContentScale.FillBounds
                )
        )
        content()
    }
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
    Column(
        modifier = modifier
            .fillMaxWidth()
    ) {
        VideoSeekBar(
            positionMs = positionMs,
            durationMs = durationMs,
            onSeekPreview = onSeekPreview,
            onSeekFinished = onSeekFinished,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(SEEK_BAR_VISUAL_HEIGHT)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 32.dp, end = 32.dp, top = 15.dp),
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
    slotSize: Dp = TRANSPORT_SLOT_SIZE,
    iconSize: Dp = TRANSPORT_ICON_SIZE,
    modifier: Modifier = Modifier
) {
    TransportControlsLayout(
        modifier = modifier,
        slotSize = slotSize,
        leftControls = {
            if (leadingControl != null) {
                TransportControlSlot(slotSize = slotSize) {
                    leadingControl(controlsState::showControls)
                }
            }
            val speed = controlsState.speedOption
            PressStateIconButton(
                normalDrawable = speed.iconResId,
                pressedDrawable = speed.pressedIconResId,
                contentDescription = "Playback speed ${speed.label}",
                layoutSize = slotSize,
                iconSize = iconSize,
                onClick = controlsState::cyclePlaybackSpeed,
                enabled = controlsEnabled
            )
            PressStateIconButton(
                normalDrawable = R.drawable.ico_media_prev_n,
                pressedDrawable = R.drawable.ico_media_prev_p,
                contentDescription = "Previous video",
                layoutSize = slotSize,
                iconSize = iconSize,
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
            buttonSize = slotSize,
            enabled = controlsEnabled
            )
        },
        rightControls = {
            PressStateIconButton(
                normalDrawable = R.drawable.ico_media_next_n,
                pressedDrawable = R.drawable.ico_media_next_p,
                contentDescription = "Next video",
                layoutSize = slotSize,
                iconSize = iconSize,
                onClick = onNextVideo,
                enabled = controlsEnabled,
                onLongPressStart = controlsState::beginFastForward,
                onLongPressEnd = controlsState::endFastForward
            )
            PressStateIconButton(
                normalDrawable = R.drawable.ico_media_collapse_n,
                pressedDrawable = R.drawable.ico_media_collapse_p,
                contentDescription = "Collapse player",
                layoutSize = slotSize,
                iconSize = iconSize,
                onClick = {
                    controlsState.showControls()
                    onPictureInPictureClick()
                },
                enabled = controlsEnabled
            )
            if (trailingControl != null) {
                TransportControlSlot(slotSize = slotSize) {
                    trailingControl()
                }
            }
        }
    )
}

@Composable
private fun TransportControlsLayout(
    leftControls: @Composable RowScope.() -> Unit,
    centerControl: @Composable () -> Unit,
    rightControls: @Composable RowScope.() -> Unit,
    slotSize: Dp = TRANSPORT_SLOT_SIZE,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(slotSize)
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth(0.5f)
                .padding(end = slotSize / 2),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
            content = leftControls
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(slotSize),
            contentAlignment = Alignment.Center
        ) {
            centerControl()
        }
        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxWidth(0.5f)
                .padding(start = slotSize / 2),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
            content = rightControls
        )
    }
}

@Composable
private fun TransportControlSlot(
    slotSize: Dp = TRANSPORT_SLOT_SIZE,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit
) {
    Box(
        modifier = Modifier.size(slotSize),
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
        fontSize = 22.sp,
        maxLines = 1,
        overflow = TextOverflow.Visible,
        textAlign = textAlign,
        modifier = Modifier.width(100.dp)
    )
}
