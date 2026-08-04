package com.ivi.common.ui.pidlibrary

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ivi.R
import com.ivi.common.domain.AssetVideo
import com.ivi.common.media.ThumbnailLoader
import com.ivi.common.ui.library.circularIndex
import com.ivi.common.ui.library.handleFocusedVideoPlay
import com.ivi.common.ui.library.handleVideoCardClick
import com.ivi.common.ui.library.shortestCircularDelta
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
public fun VideoRail(
    videos: List<AssetVideo>,
    thumbnailLoader: ThumbnailLoader,
    focusedIndex: Int,
    activeSessionAssetPath: String?,
    isSessionPlaying: Boolean,
    onFocusChanged: (Int) -> Unit,
    onFocusPreviewChanged: (Int) -> Unit,
    onFocusPreviewCleared: () -> Unit,
    onActiveSessionPlaybackToggle: () -> Unit,
    onVideoPlayRequested: (AssetVideo) -> Unit
) {
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    val settleOffsetPx = remember { Animatable(0f) }
    var isSettling by remember { mutableStateOf(false) }
    var containerWidthPx by remember { mutableIntStateOf(1) }
    val metrics = rememberRailMetrics(videos.size, containerWidthPx)
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val dragSlots = (dragOffsetPx + settleOffsetPx.value) / max(metrics.slotDistancePx, 1f)

    fun focusForOffset(offsetPx: Float): Int = focusedIndexForOffset(
        focusedIndex = focusedIndex,
        offsetPx = offsetPx,
        slotDistancePx = metrics.slotDistancePx,
        itemCount = videos.size
    )

    fun animateFocusTo(targetIndex: Int) {
        if (isSettling || videos.size <= 1) return

        val delta = shortestCircularDelta(focusedIndex, targetIndex, videos.size)
        onFocusPreviewChanged(targetIndex)
        isSettling = true
        coroutineScope.launch {
            settleOffsetPx.animateTo(
                targetValue = -delta * metrics.slotDistancePx - dragOffsetPx,
                animationSpec = tween(FOCUS_SETTLE_ANIMATION_DURATION_MS, easing = FastOutSlowInEasing)
            )
            if (targetIndex != focusedIndex) onFocusChanged(targetIndex)
            dragOffsetPx = 0f
            settleOffsetPx.snapTo(0f)
            onFocusPreviewCleared()
            isSettling = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = with(density) { VIDEO_RAIL_HORIZONTAL_PADDING_PX.toDp() },
                end = with(density) { VIDEO_RAIL_HORIZONTAL_PADDING_PX.toDp() },
                bottom = with(density) { RAIL_BOTTOM_PADDING_PX.toDp() }
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(metrics.railHeight)
                .clipToBounds()
                .onSizeChanged { containerWidthPx = it.width.coerceAtLeast(1) }
                .pointerInput(videos.size, focusedIndex, metrics.slotDistancePx) {
                    if (videos.size <= 1) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragEnd = { animateFocusTo(focusForOffset(dragOffsetPx)) },
                        onDragCancel = { animateFocusTo(focusedIndex) },
                        onHorizontalDrag = { change, dragAmount ->
                            if (isSettling) return@detectHorizontalDragGestures
                            change.consume()
                            dragOffsetPx += dragAmount
                            onFocusPreviewChanged(focusForOffset(dragOffsetPx))
                        }
                    )
                }
        ) {
            visibleRailCards(videos.size, focusedIndex, dragSlots, metrics).forEach { card ->
                val video = videos[card.index]
                val isActiveSessionVideo = video.assetPath == activeSessionAssetPath
                key(video.assetPath) {
                    val widthPx = metrics.sideWidthPx +
                        (metrics.focusedWidthPx - metrics.sideWidthPx) * card.focusProgress
                    val heightPx = metrics.sideHeightPx +
                        (metrics.focusedHeightPx - metrics.sideHeightPx) * card.focusProgress
                    VideoRailCard(
                        video = video,
                        thumbnailLoader = thumbnailLoader,
                        selected = card.selectedSlot,
                        isPlaying = isActiveSessionVideo && isSessionPlaying,
                        width = with(density) { widthPx.toDp() },
                        thumbnailHeight = with(density) { heightPx.toDp() },
                        modifier = Modifier.offset {
                            IntOffset(card.xPx.roundToInt(), metrics.topPaddingPx.roundToInt())
                        },
                        onClick = {
                            handleVideoCardClick(isFocused = card.selectedSlot) {
                                animateFocusTo(card.index)
                            }
                        },
                        onPlaybackClick = {
                            handleFocusedVideoPlay(
                                isActiveSessionVideo = isActiveSessionVideo,
                                onActiveSessionPlaybackToggle = onActiveSessionPlaybackToggle
                            ) {
                                if (!isSettling) onVideoPlayRequested(video)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
public fun VideoRailProgress(
    focusedIndex: Int,
    videoCount: Int,
    modifier: Modifier = Modifier
) {
    val targetProgress = if (videoCount == 0) 0f else (focusedIndex + 1f) / videoCount
    val progress by animateFloatAsState(
        targetValue = targetProgress.coerceIn(0f, 1f),
        animationSpec = tween(
            durationMillis = RAIL_PROGRESS_ANIMATION_DURATION_MS,
            easing = FastOutSlowInEasing
        ),
        label = "PID video rail progress"
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(RAIL_PROGRESS_HEIGHT)
    ) {
        Image(
            painter = painterResource(R.drawable.img_general_progress_bar_track),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(),
            contentScale = ContentScale.FillBounds
        )
        Image(
            painter = painterResource(R.drawable.img_general_progress_bar_filled_track),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .drawWithContent {
                    // Reveal the full-width filled artwork instead of rescaling it as focus changes.
                    clipRect(right = size.width * progress) {
                        this@drawWithContent.drawContent()
                    }
                },
            contentScale = ContentScale.FillBounds
        )
    }
}

@Composable
public fun rememberRailMetrics(videoCount: Int, containerWidthPx: Int): RailMetrics {
    val density = LocalDensity.current
    return remember(videoCount, containerWidthPx, density) {
        val topPaddingPx = with(density) { 12.dp.toPx() }
        val cardGapPx = with(density) { 16.dp.toPx() }
        val sideCardsOnScreen = ceil(
            (containerWidthPx - FOCUSED_VIDEO_WIDTH_PX)
                .coerceAtLeast(0f) / (NORMAL_VIDEO_WIDTH_PX + cardGapPx)
        ).toInt().coerceAtMost((videoCount - 1).coerceAtLeast(0))

        RailMetrics(
            railHeight = with(density) { (topPaddingPx + FOCUSED_VIDEO_HEIGHT_PX).toDp() },
            topPaddingPx = topPaddingPx,
            startPaddingPx = 0f,
            sideWidthPx = NORMAL_VIDEO_WIDTH_PX,
            sideHeightPx = NORMAL_VIDEO_HEIGHT_PX,
            focusedWidthPx = FOCUSED_VIDEO_WIDTH_PX,
            focusedHeightPx = FOCUSED_VIDEO_HEIGHT_PX,
            slotDistancePx = NORMAL_VIDEO_WIDTH_PX + cardGapPx,
            focusedStepPx = FOCUSED_VIDEO_WIDTH_PX + cardGapPx,
            visibleSlots = max(1, sideCardsOnScreen + 1)
        )
    }
}

public data class RailMetrics(
    val railHeight: Dp,
    val topPaddingPx: Float,
    val startPaddingPx: Float,
    val sideWidthPx: Float,
    val sideHeightPx: Float,
    val focusedWidthPx: Float,
    val focusedHeightPx: Float,
    val slotDistancePx: Float,
    val focusedStepPx: Float,
    val visibleSlots: Int
)

private data class RailCardLayout(
    val index: Int,
    val xPx: Float,
    val focusProgress: Float,
    val selectedSlot: Boolean
)

private fun focusedIndexForOffset(
    focusedIndex: Int,
    offsetPx: Float,
    slotDistancePx: Float,
    itemCount: Int
): Int {
    if (itemCount <= 1 || abs(offsetPx) < slotDistancePx * FOCUS_SETTLE_THRESHOLD) return focusedIndex
    val slotCount = max(1, abs(offsetPx / max(slotDistancePx, 1f)).roundToInt())
    return circularIndex(focusedIndex + if (offsetPx < 0f) slotCount else -slotCount, itemCount)
}

private fun visibleRailCards(
    videoCount: Int,
    focusedIndex: Int,
    dragSlots: Float,
    metrics: RailMetrics
): List<RailCardLayout> {
    if (videoCount == 0) return emptyList()

    val firstRelativeSlot = floor(-dragSlots - 2f).toInt()
    val lastRelativeSlot = ceil(metrics.visibleSlots - dragSlots + 2f).toInt()
    val layouts = linkedMapOf<Int, RailCardLayout>()
    for (relativeSlot in firstRelativeSlot..lastRelativeSlot) {
        val slot = relativeSlot + dragSlots
        if (slot < -1.15f || slot > metrics.visibleSlots + 0.35f) continue

        val layout = RailCardLayout(
            index = circularIndex(focusedIndex + relativeSlot, videoCount),
            xPx = metrics.xForSlot(slot),
            focusProgress = (1f - abs(slot)).coerceIn(0f, 1f),
            selectedSlot = relativeSlot == 0
        )
        val current = layouts[layout.index]
        if (current == null || layout.focusProgress > current.focusProgress) layouts[layout.index] = layout
    }
    return layouts.values.sortedBy { it.focusProgress }
}

private fun RailMetrics.xForSlot(slot: Float): Float = startPaddingPx + when {
    slot <= 0f -> slot * focusedStepPx
    slot <= 1f -> slot * focusedStepPx
    else -> focusedStepPx + (slot - 1f) * slotDistancePx
}

public const val FOCUSED_VIDEO_WIDTH_PX = 604f
public const val FOCUSED_VIDEO_HEIGHT_PX = 340f
public const val NORMAL_VIDEO_WIDTH_PX = 396f
public const val NORMAL_VIDEO_HEIGHT_PX = 223f
public const val VIDEO_RAIL_HORIZONTAL_PADDING_PX = 55f
private const val RAIL_BOTTOM_PADDING_PX = 40f
private const val FOCUS_SETTLE_THRESHOLD = 0.32f
private const val FOCUS_SETTLE_ANIMATION_DURATION_MS = 320
private const val RAIL_PROGRESS_ANIMATION_DURATION_MS = 420
private val RAIL_PROGRESS_HEIGHT = 6.dp
