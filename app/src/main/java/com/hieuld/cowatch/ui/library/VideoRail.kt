package com.hieuld.cowatch.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import com.hieuld.cowatch.media.RawVideo
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
internal fun VideoRail(
    videos: List<RawVideo>,
    focusedIndex: Int,
    onFocusChanged: (Int) -> Unit,
    onFocusPreviewChanged: (Int) -> Unit,
    onFocusPreviewCleared: () -> Unit,
    onVideoSelected: (RawVideo) -> Unit
) {
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    var containerWidthPx by remember { mutableIntStateOf(1) }
    val metrics = rememberRailMetrics(
        videoCount = videos.size,
        containerWidthPx = containerWidthPx
    )
    val density = LocalDensity.current
    val dragSlots = dragOffsetPx / max(metrics.slotDistancePx, 1f)

    fun focusedIndexForDragOffset(offsetPx: Float): Int {
        return calculateFocusedIndexForDragOffset(
            focusedIndex = focusedIndex,
            offsetPx = offsetPx,
            slotDistancePx = metrics.slotDistancePx,
            itemCount = videos.size
        )
    }

    fun settleFocus() {
        if (videos.size > 1) {
            val nextFocusedIndex = focusedIndexForDragOffset(dragOffsetPx)
            if (nextFocusedIndex != focusedIndex) {
                onFocusChanged(nextFocusedIndex)
            }
        }
        dragOffsetPx = 0f
        onFocusPreviewCleared()
    }

    fun resetDrag() {
        dragOffsetPx = 0f
        onFocusPreviewCleared()
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(metrics.railHeight)
            .background(RailBackgroundColor)
            .onSizeChanged { containerWidthPx = it.width.coerceAtLeast(1) }
            .pointerInput(videos.size, focusedIndex, metrics.slotDistancePx) {
                if (videos.size <= 1) return@pointerInput

                detectHorizontalDragGestures(
                    onDragEnd = { settleFocus() },
                    onDragCancel = { resetDrag() },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        val nextOffsetPx = dragOffsetPx + dragAmount
                        dragOffsetPx = nextOffsetPx
                        onFocusPreviewChanged(focusedIndexForDragOffset(nextOffsetPx))
                    }
                )
            }
    ) {
        if (videos.isEmpty()) return@Box

        val cards = videos.indices.mapNotNull { index ->
            railCardLayout(
                index = index,
                focusedIndex = focusedIndex,
                itemCount = videos.size,
                dragSlots = dragSlots,
                metrics = metrics
            )
        }.sortedBy { it.focusProgress }

        cards.forEach { card ->
            val video = videos[card.index]
            key(video.resId) {
                val widthPx = metrics.sideWidthPx +
                    (metrics.focusedWidthPx - metrics.sideWidthPx) * card.focusProgress
                val heightPx = metrics.sideHeightPx +
                    (metrics.focusedHeightPx - metrics.sideHeightPx) * card.focusProgress
                val selected = card.index == focusedIndex
                val cardScale = CARD_SCALE_MIN + card.focusProgress * CARD_SCALE_RANGE

                VideoRailCard(
                    video = video,
                    selected = selected,
                    width = with(density) { widthPx.toDp() },
                    thumbnailHeight = with(density) { heightPx.toDp() },
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                card.xPx.roundToInt(),
                                metrics.topPaddingPx.roundToInt()
                            )
                        }
                        .scale(cardScale)
                        .graphicsLayer { shadowElevation = if (selected) 14f else 0f },
                    onClick = {
                        if (selected) {
                            onVideoSelected(video)
                        } else {
                            onFocusChanged(card.index)
                        }
                    }
                )
            }
        }
    }
}
