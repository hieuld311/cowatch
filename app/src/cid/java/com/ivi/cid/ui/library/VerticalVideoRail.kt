package com.ivi.cid.ui.library

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ivi.common.media.ThumbnailLoader
import com.ivi.common.domain.AssetVideo
import com.ivi.common.ui.PrimaryPlaybackButton
import com.ivi.common.ui.library.VideoFocusCyan
import com.ivi.common.ui.library.VideoThumbnail
import com.ivi.common.ui.library.circularIndex
import com.ivi.common.ui.library.handleFocusedVideoPlay
import com.ivi.common.ui.library.handleVideoCardClick
import com.ivi.common.ui.library.shortestCircularDelta
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
internal fun VerticalVideoRail(
    videos: List<AssetVideo>,
    thumbnailLoader: ThumbnailLoader,
    focusedIndex: Int,
    activeSessionAssetPath: String?,
    isSessionPlaying: Boolean,
    railWidthPx: Float,
    onFocusChanged: (Int) -> Unit,
    onFocusPreviewChanged: (Int) -> Unit,
    onFocusPreviewCleared: () -> Unit,
    onActiveSessionPlaybackToggle: () -> Unit,
    onVideoPlayRequested: (AssetVideo) -> Unit,
    modifier: Modifier = Modifier
) {
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    val settleOffsetPx = remember { Animatable(0f) }
    var isSettling by remember { mutableStateOf(false) }
    var containerHeightPx by remember { mutableIntStateOf(1) }
    val density = LocalDensity.current
    val metrics = rememberVerticalRailMetrics(railWidthPx, density)
    val scope = rememberCoroutineScope()
    val dragSlots = (dragOffsetPx + settleOffsetPx.value) / max(metrics.slotDistancePx, 1f)

    fun focusForOffset(offsetPx: Float): Int {
        if (videos.size <= 1 || abs(offsetPx) < metrics.slotDistancePx * FOCUS_SETTLE_THRESHOLD) {
            return focusedIndex
        }
        val count = max(1, abs(offsetPx / metrics.slotDistancePx).roundToInt())
        return circularIndex(focusedIndex + if (offsetPx < 0f) count else -count, videos.size)
    }

    fun animateFocusTo(targetIndex: Int) {
        if (isSettling || videos.size <= 1) return
        val delta = shortestCircularDelta(focusedIndex, targetIndex, videos.size)
        isSettling = true
        onFocusPreviewChanged(targetIndex)
        scope.launch {
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

    Box(
        modifier = modifier
            .width(with(density) { railWidthPx.toDp() })
            .fillMaxHeight()
            .clipToBounds()
            .onSizeChanged { containerHeightPx = it.height.coerceAtLeast(1) }
            .pointerInput(videos.size, focusedIndex, metrics.slotDistancePx) {
                if (videos.size <= 1) return@pointerInput
                detectVerticalDragGestures(
                    onDragEnd = { animateFocusTo(focusForOffset(dragOffsetPx)) },
                    onDragCancel = { animateFocusTo(focusedIndex) },
                    onVerticalDrag = { change, amount ->
                        if (isSettling) return@detectVerticalDragGestures
                        change.consume()
                        dragOffsetPx += amount
                        onFocusPreviewChanged(focusForOffset(dragOffsetPx))
                    }
                )
            }
    ) {
        verticalRailCards(videos.size, focusedIndex, dragSlots, containerHeightPx, metrics).forEach { card ->
            val video = videos[card.index]
            val isActiveSessionVideo = video.assetPath == activeSessionAssetPath
            key(video.assetPath) {
                val widthPx = metrics.sideWidthPx + (metrics.focusedWidthPx - metrics.sideWidthPx) * card.focus
                val heightPx = metrics.sideHeightPx + (metrics.focusedHeightPx - metrics.sideHeightPx) * card.focus
                VerticalRailCard(
                    video = video,
                    thumbnailLoader = thumbnailLoader,
                    selected = card.selected,
                    isPlaying = isActiveSessionVideo && isSessionPlaying,
                    widthPx = widthPx,
                    heightPx = heightPx,
                    density = density,
                    modifier = Modifier
                        .offset { IntOffset(card.xPx.roundToInt(), card.yPx.roundToInt()) }
                        .scale(0.96f + card.focus * 0.04f),
                    onClick = {
                        handleVideoCardClick(isFocused = card.selected) {
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

@Composable
internal fun rememberVerticalRailMetrics(railWidthPx: Float, density: Density): VerticalRailMetrics {
    return remember(railWidthPx, density) {
        val paddingPx = VERTICAL_RAIL_LEFT_PADDING_PX
        val gapPx = with(density) { 12.dp.toPx() }
        VerticalRailMetrics(
            horizontalPaddingPx = paddingPx,
            focusedWidthPx = FOCUSED_VIDEO_WIDTH_PX,
            focusedHeightPx = FOCUSED_VIDEO_HEIGHT_PX,
            sideWidthPx = NORMAL_VIDEO_WIDTH_PX,
            sideHeightPx = NORMAL_VIDEO_HEIGHT_PX,
            slotDistancePx = (FOCUSED_VIDEO_HEIGHT_PX + NORMAL_VIDEO_HEIGHT_PX) / 2f + gapPx
        )
    }
}

internal data class VerticalRailMetrics(
    val horizontalPaddingPx: Float,
    val focusedWidthPx: Float,
    val focusedHeightPx: Float,
    val sideWidthPx: Float,
    val sideHeightPx: Float,
    val slotDistancePx: Float
)

private data class VerticalRailLayout(
    val index: Int,
    val xPx: Float,
    val yPx: Float,
    val focus: Float,
    val selected: Boolean
)

private fun verticalRailCards(
    videoCount: Int,
    focusedIndex: Int,
    dragSlots: Float,
    containerHeightPx: Int,
    metrics: VerticalRailMetrics
): List<VerticalRailLayout> {
    if (videoCount == 0) return emptyList()
    val layouts = linkedMapOf<Int, VerticalRailLayout>()
    for (relativeSlot in -2..2) {
        val slot = relativeSlot + dragSlots
        if (slot !in -1.65f..1.65f) continue
        val focus = (1f - abs(slot)).coerceIn(0f, 1f)
        val width = metrics.sideWidthPx + (metrics.focusedWidthPx - metrics.sideWidthPx) * focus
        val height = metrics.sideHeightPx + (metrics.focusedHeightPx - metrics.sideHeightPx) * focus
        val layout = VerticalRailLayout(
            index = circularIndex(focusedIndex + relativeSlot, videoCount),
            // Keep focused and normal cards flush to the same left rail edge.
            xPx = metrics.horizontalPaddingPx,
            yPx = containerHeightPx / 2f + slot * metrics.slotDistancePx - height / 2f,
            focus = focus,
            selected = relativeSlot == 0
        )
        val existing = layouts[layout.index]
        if (existing == null || layout.focus > existing.focus) layouts[layout.index] = layout
    }
    return layouts.values.sortedBy { it.focus }
}

@Composable
private fun VerticalRailCard(
    video: AssetVideo,
    thumbnailLoader: ThumbnailLoader,
    selected: Boolean,
    isPlaying: Boolean,
    widthPx: Float,
    heightPx: Float,
    density: Density,
    modifier: Modifier,
    onClick: () -> Unit,
    onPlaybackClick: () -> Unit
) {
    Box(
        modifier = modifier
            .width(with(density) { widthPx.toDp() })
            .height(with(density) { heightPx.toDp() })
            .clip(VideoCardShape)
            .clickable(onClick = onClick)
            .then(if (selected) Modifier.border(1.dp, VideoFocusCyan, VideoCardShape) else Modifier)
    ) {
        VideoThumbnail(
            video = video,
            thumbnailLoader = thumbnailLoader,
            contentDescription = video.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        if (selected) {
            PrimaryPlaybackButton(
                isPlaying = isPlaying,
                onClick = onPlaybackClick,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = 20.dp, y = (-20).dp)
            )
        }
    }
}

private const val FOCUS_SETTLE_THRESHOLD = 0.32f
private const val FOCUS_SETTLE_ANIMATION_DURATION_MS = 320
private val VideoCardShape = RoundedCornerShape(8.dp)
