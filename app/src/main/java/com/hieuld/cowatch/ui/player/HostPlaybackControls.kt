package com.hieuld.cowatch.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.hieuld.cowatch.R
import com.hieuld.cowatch.util.formatPlaybackTime
import kotlinx.coroutines.delay

private const val CONTROLS_AUTO_HIDE_DELAY_MS = 3_000L
private const val PLAYBACK_POSITION_UPDATE_DELAY_MS = 500L

@Composable
internal fun HostPlaybackControls(
    player: Player,
    broadcastChecked: Boolean,
    onBroadcastCheckedChange: (Boolean) -> Unit,
    onPictureInPictureClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val controlsState = rememberPlaybackControlsState(player)

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                controlsState.refresh(updateSlider = !controlsState.isDragging)
            }
        }

        player.addListener(listener)
        controlsState.refresh(updateSlider = true)

        onDispose {
            player.removeListener(listener)
        }
    }

    LaunchedEffect(
        player,
        controlsState.controlsVisible,
        controlsState.isPlaying,
        controlsState.isDragging
    ) {
        if (
            !controlsState.controlsVisible ||
            !controlsState.isPlaying ||
            controlsState.isDragging
        ) {
            return@LaunchedEffect
        }

        while (true) {
            controlsState.updatePlaybackPosition()
            delay(PLAYBACK_POSITION_UPDATE_DELAY_MS)
        }
    }

    LaunchedEffect(
        controlsState.interactionVersion,
        controlsState.controlsVisible,
        controlsState.isDragging
    ) {
        if (controlsState.controlsVisible && !controlsState.isDragging) {
            delay(CONTROLS_AUTO_HIDE_DELAY_MS)
            controlsState.hideControlsIfIdle()
        }
    }

    Box(
        modifier = modifier.clickable(
            indication = null,
            interactionSource = remember { MutableInteractionSource() }
        ) {
            controlsState.showControls()
        }
    ) {
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
                    // 118dp gives the seekbar, title, controls, and time labels stable automotive touch spacing.
                    .height(118.dp)
                    .background(
                        // Dark bottom gradient preserves video readability under the controls.
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0x0017142F),
                                Color(0xCC17142F),
                                Color(0xF217142F)
                            )
                        )
                    )
            ) {
                VideoSeekBar(
                    positionMs = controlsState.sliderPositionMs,
                    durationMs = controlsState.durationMs,
                    onSeekPreview = controlsState::previewSeek,
                    onSeekFinished = controlsState::finishSeek,
                    enabled = controlsState.durationMs > 0L,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                )

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        // 88dp side padding leaves room for edge gestures and keeps controls centered on wide displays.
                        .padding(start = 88.dp, end = 88.dp, bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        PlayerBarText(
                            text = formatPlaybackTime(controlsState.sliderPositionMs),
                            alpha = 0.62f,
                            style = MaterialTheme.typography.labelMedium
                        )
                        PlayerBarText(
                            text = controlsState.videoTitle,
                            alpha = 0.86f,
                            style = MaterialTheme.typography.bodySmall,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        modifier = Modifier.weight(1.2f),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val speedOption = controlsState.speedOption
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .clickable {
                                    controlsState.showControls()
                                    onBroadcastCheckedChange(!broadcastChecked)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            BroadcastGlyph(active = broadcastChecked)
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(
                            onClick = controlsState::cyclePlaybackSpeed,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Image(
                                painter = painterResource(speedOption.iconResId),
                                contentDescription = "Playback speed ${speedOption.label}",
                                modifier = Modifier.size(38.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        MediaControlButton(
                            painter = painterResource(R.drawable.ico_media_prev_p),
                            contentDescription = "Back",
                            size = 34.dp,
                            iconSize = 28.dp,
                            onClick = controlsState::seekBack
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        IconButton(
                            onClick = controlsState::togglePlayback,
                            // Play/pause is the primary control, so it gets the largest hit target in the bar.
                            modifier = Modifier.size(58.dp)
                        ) {
                            Image(
                                painter = mediaControlPainter(controlsState.isPlaying),
                                contentDescription = if (controlsState.isPlaying) "Pause" else "Play",
                                modifier = Modifier.size(38.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        MediaControlButton(
                            painter = painterResource(R.drawable.ico_media_next_p),
                            contentDescription = "Forward",
                            size = 34.dp,
                            iconSize = 28.dp,
                            onClick = controlsState::seekForward
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Box(
                            modifier = Modifier
                                // PiP action is intentionally smaller than transport controls to reduce accidental taps.
                                .size(36.dp)
                                .clickable {
                                    controlsState.showControls()
                                    onPictureInPictureClick()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            PictureInPictureGlyph()
                        }
                    }

                    PlayerBarText(
                        text = formatPlaybackTime(controlsState.durationMs),
                        alpha = 0.62f,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.End
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerBarText(
    text: String,
    alpha: Float,
    style: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier,
    overflow: TextOverflow = TextOverflow.Clip,
    textAlign: TextAlign? = null
) {
    Text(
        text = text,
        modifier = modifier,
        color = Color.White.copy(alpha = alpha),
        style = style,
        fontFamily = FontFamily.Monospace,
        maxLines = 1,
        overflow = overflow,
        textAlign = textAlign
    )
}

@Composable
private fun MediaControlButton(
    painter: Painter,
    contentDescription: String,
    size: Dp,
    iconSize: Dp,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        // Separate button and icon sizes preserve original drawable pixels without Compose tint overlays.
        modifier = Modifier.size(size)
    ) {
        Image(
            painter = painter,
            contentDescription = contentDescription,
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
private fun mediaControlPainter(isPlaying: Boolean): Painter {
    return painterResource(
        id = if (isPlaying) {
            R.drawable.ico_media_pause_p
        } else {
            R.drawable.ico_media_play_l_p
        }
    )
}
