package com.hieuld.cowatch.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Player
import kotlinx.coroutines.delay

private const val CONTROLS_AUTO_HIDE_DELAY_MS = 3_000L

@Composable
internal fun HostPlaybackControls(
    player: Player,
    isFullscreen: Boolean,
    onFullscreenToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var durationMs by remember {
        mutableStateOf(player.duration.takeIf { it > 0L } ?: 0L)
    }
    var positionMs by remember { mutableStateOf(player.currentPosition.coerceAtLeast(0L)) }
    var sliderPositionMs by remember { mutableStateOf(positionMs) }
    var videoTitle by remember { mutableStateOf(resolvePlayerTitle(player)) }
    var isDragging by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var interactionVersion by remember { mutableStateOf(0) }

    fun showControls() {
        controlsVisible = true
        interactionVersion += 1
    }

    LaunchedEffect(player) {
        while (true) {
            isPlaying = player.isPlaying
            durationMs = player.duration.takeIf { it > 0L && it != C.TIME_UNSET } ?: 0L
            positionMs = player.currentPosition.coerceAtLeast(0L)
            videoTitle = resolvePlayerTitle(player)

            if (!isDragging) {
                sliderPositionMs = positionMs
            }

            delay(250L)
        }
    }

    LaunchedEffect(interactionVersion, controlsVisible, isDragging) {
        if (controlsVisible && !isDragging) {
            delay(CONTROLS_AUTO_HIDE_DELAY_MS)
            controlsVisible = false
        }
    }

    Box(
        modifier = modifier.clickable(
            indication = null,
            interactionSource = remember { MutableInteractionSource() }
        ) {
            showControls()
        }
    ) {
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(112.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0x8017142F),
                                Color(0xE617142F),
                                Color(0xFF17142F)
                            )
                        )
                    )
            ) {
                VideoSeekBar(
                    positionMs = sliderPositionMs,
                    durationMs = durationMs,
                    onSeekPreview = { position ->
                        showControls()
                        isDragging = true
                        sliderPositionMs = position
                    },
                    onSeekFinished = {
                        player.seekTo(sliderPositionMs)
                        isDragging = false
                        showControls()
                    },
                    enabled = durationMs > 0L,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                )

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        PlayerBarText(
                            text = formatPlaybackTime(sliderPositionMs),
                            alpha = 0.62f,
                            style = MaterialTheme.typography.labelMedium
                        )
                        PlayerBarText(
                            text = videoTitle,
                            alpha = 0.86f,
                            style = MaterialTheme.typography.bodyLarge,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MediaControlButton(
                            painter = painterResource(android.R.drawable.ic_media_rew),
                            contentDescription = "Back",
                            size = 48.dp,
                            iconSize = 30.dp,
                            onClick = {
                                showControls()
                                player.seekTo((player.currentPosition - 5_000L).coerceAtLeast(0L))
                            }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        IconButton(
                            onClick = {
                                showControls()
                                if (player.isPlaying) {
                                    player.pause()
                                } else {
                                    player.play()
                                }
                            },
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.10f))
                        ) {
                            Icon(
                                painter = mediaControlPainter(isPlaying),
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(42.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        MediaControlButton(
                            painter = painterResource(android.R.drawable.ic_media_ff),
                            contentDescription = "Forward",
                            size = 48.dp,
                            iconSize = 30.dp,
                            onClick = {
                                showControls()
                                val target = player.currentPosition + 5_000L
                                player.seekTo(
                                    if (durationMs > 0L) target.coerceAtMost(durationMs) else target
                                )
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PlayerBarText(
                            text = formatPlaybackTime(durationMs),
                            alpha = 0.62f,
                            style = MaterialTheme.typography.labelMedium
                        )
                        Spacer(modifier = Modifier.width(28.dp))
                        PlayerBarText(
                            text = "1.0X",
                            alpha = 0.88f,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        IconButton(
                            onClick = {
                                showControls()
                                onFullscreenToggle()
                            },
                            modifier = Modifier.size(44.dp)
                        ) {
                            FullscreenGlyph(isFullscreen = isFullscreen)
                        }
                    }
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
    fontWeight: FontWeight? = null,
    overflow: TextOverflow = TextOverflow.Clip
) {
    Text(
        text = text,
        modifier = modifier,
        color = Color.White.copy(alpha = alpha),
        style = style,
        fontFamily = FontFamily.Monospace,
        fontWeight = fontWeight,
        maxLines = 1,
        overflow = overflow
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
        modifier = Modifier.size(size)
    ) {
        Icon(
            painter = painter,
            contentDescription = contentDescription,
            tint = Color.White.copy(alpha = 0.82f),
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
private fun VideoSeekBar(
    positionMs: Long,
    durationMs: Long,
    enabled: Boolean,
    onSeekPreview: (Long) -> Unit,
    onSeekFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    var widthPx by remember { mutableStateOf(1) }

    fun positionFromX(x: Float): Long {
        if (durationMs <= 0L || widthPx <= 0) return 0L

        val fraction = (x / widthPx).coerceIn(0f, 1f)
        return (durationMs * fraction).toLong()
    }

    Canvas(
        modifier = modifier
            .height(48.dp)
            .onSizeChanged { size -> widthPx = size.width.coerceAtLeast(1) }
            .pointerInput(enabled, durationMs) {
                if (!enabled) return@pointerInput

                detectTapGestures { offset ->
                    onSeekPreview(positionFromX(offset.x))
                    onSeekFinished()
                }
            }
            .pointerInput(enabled, durationMs) {
                if (!enabled) return@pointerInput

                detectDragGestures(
                    onDragStart = { offset ->
                        onSeekPreview(positionFromX(offset.x))
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        onSeekPreview(positionFromX(change.position.x))
                    },
                    onDragEnd = onSeekFinished,
                    onDragCancel = onSeekFinished
                )
            }
    ) {
        val trackY = size.height / 2f
        val progress = if (durationMs > 0L) {
            (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
        val progressX = size.width * progress
        val strokeWidth = 6.dp.toPx()
        val thumbRadius = 12.dp.toPx()

        drawLine(
            color = Color(0xFF777777),
            start = Offset(0f, trackY),
            end = Offset(size.width, trackY),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color(0xFF111111),
            start = Offset(0f, trackY),
            end = Offset(progressX, trackY),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        drawCircle(
            color = Color.White,
            radius = thumbRadius,
            center = Offset(progressX, trackY)
        )
    }
}

@Composable
private fun mediaControlPainter(isPlaying: Boolean): Painter {
    return painterResource(
        id = if (isPlaying) {
            android.R.drawable.ic_media_pause
        } else {
            android.R.drawable.ic_media_play
        }
    )
}
