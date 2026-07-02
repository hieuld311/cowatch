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
import androidx.compose.runtime.DisposableEffect
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
import androidx.media3.common.PlaybackParameters
import kotlinx.coroutines.delay

private const val CONTROLS_AUTO_HIDE_DELAY_MS = 3_000L
private val PLAYBACK_SPEEDS = listOf(1f, 1.5f, 2f)

@Composable
internal fun HostPlaybackControls(
    player: Player,
    broadcastChecked: Boolean,
    onBroadcastCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var durationMs by remember {
        mutableStateOf(player.duration.takeIf { it > 0L } ?: 0L)
    }
    var sliderPositionMs by remember { mutableStateOf(player.currentPosition.coerceAtLeast(0L)) }
    var videoTitle by remember { mutableStateOf(resolvePlayerTitle(player)) }
    var playbackSpeed by remember { mutableStateOf(player.playbackParameters.speed) }
    var isDragging by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var interactionVersion by remember { mutableStateOf(0) }

    fun normalizedDuration(): Long {
        return player.duration.takeIf { it > 0L && it != C.TIME_UNSET } ?: 0L
    }

    fun refreshPlaybackSnapshot(updateSlider: Boolean) {
        isPlaying = player.isPlaying
        durationMs = normalizedDuration()
        val currentPositionMs = player.currentPosition.coerceAtLeast(0L)
        videoTitle = resolvePlayerTitle(player)
        playbackSpeed = player.playbackParameters.speed

        if (updateSlider && !isDragging) {
            sliderPositionMs = currentPositionMs
        }
    }

    fun showControls() {
        refreshPlaybackSnapshot(updateSlider = !isDragging)
        controlsVisible = true
        interactionVersion += 1
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                refreshPlaybackSnapshot(updateSlider = !isDragging)
            }
        }

        player.addListener(listener)
        refreshPlaybackSnapshot(updateSlider = true)

        onDispose {
            player.removeListener(listener)
        }
    }

    LaunchedEffect(player, controlsVisible, isPlaying, isDragging) {
        if (!controlsVisible || !isPlaying || isDragging) return@LaunchedEffect

        while (true) {
            sliderPositionMs = player.currentPosition.coerceAtLeast(0L)
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
                    .height(118.dp)
                    .background(
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
                        .padding(start = 88.dp, end = 88.dp, bottom = 16.dp),
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
                            style = MaterialTheme.typography.bodySmall,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        modifier = Modifier.weight(1.2f),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .clickable {
                                    showControls()
                                    onBroadcastCheckedChange(!broadcastChecked)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            BroadcastGlyph(active = broadcastChecked)
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        PlayerBarText(
                            text = formatSpeed(playbackSpeed),
                            alpha = 0.88f,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable {
                                showControls()
                                val nextSpeed = nextPlaybackSpeed(playbackSpeed)
                                player.playbackParameters = PlaybackParameters(nextSpeed)
                                playbackSpeed = nextSpeed
                            }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        MediaControlButton(
                            painter = painterResource(android.R.drawable.ic_media_rew),
                            contentDescription = "Back",
                            size = 34.dp,
                            iconSize = 22.dp,
                            onClick = {
                                showControls()
                                val target = (player.currentPosition - 5_000L).coerceAtLeast(0L)
                                player.seekTo(target)
                                sliderPositionMs = target
                            }
                        )
                        Spacer(modifier = Modifier.width(10.dp))
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
                                .size(58.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.12f))
                        ) {
                            Icon(
                                painter = mediaControlPainter(isPlaying),
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(38.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        MediaControlButton(
                            painter = painterResource(android.R.drawable.ic_media_ff),
                            contentDescription = "Forward",
                            size = 34.dp,
                            iconSize = 22.dp,
                            onClick = {
                                showControls()
                                val target = player.currentPosition + 5_000L
                                val targetPosition =
                                    if (durationMs > 0L) target.coerceAtMost(durationMs) else target
                                player.seekTo(targetPosition)
                                sliderPositionMs = targetPosition
                            }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Box(
                            modifier = Modifier.size(36.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            PictureInPictureGlyph()
                        }
                    }

                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        PlayerBarText(
                            text = formatPlaybackTime(durationMs),
                            alpha = 0.62f,
                            style = MaterialTheme.typography.labelMedium
                        )
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

private fun nextPlaybackSpeed(currentSpeed: Float): Float {
    val currentIndex = PLAYBACK_SPEEDS.indexOfFirst { speed ->
        kotlin.math.abs(speed - currentSpeed) < 0.05f
    }
    return PLAYBACK_SPEEDS[(currentIndex + 1).floorMod(PLAYBACK_SPEEDS.size)]
}

private fun formatSpeed(speed: Float): String {
    val normalized = PLAYBACK_SPEEDS.minBy { kotlin.math.abs(it - speed) }
    return when (normalized) {
        1f -> "1X"
        1.5f -> "1.5X"
        else -> "2X"
    }
}

private fun Int.floorMod(other: Int): Int {
    return ((this % other) + other) % other
}
