package com.hieuld.cowatch.ui.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

internal const val CARD_SCALE_MIN = 0.96f
internal const val CARD_SCALE_RANGE = 0.04f

// Focused card is 1.5x side-card width; derived thumbnail heights stay 16:9.
private const val FOCUSED_CARD_SCALE = 1.5f
// Drag must pass about one third of a slot before focus commits.
private const val FOCUS_SETTLE_THRESHOLD = 0.32f

@Composable
internal fun rememberRailMetrics(
    videoCount: Int,
    containerWidthPx: Int
): RailMetrics {
    val density = LocalDensity.current
    return remember(videoCount, containerWidthPx, density) {
        // dp values define visual spacing; px values drive drag math and PiP sizing.
        val horizontalPadding = 16.dp
        val topPadding = 12.dp
        val cardGap = 16.dp
        val startPaddingPx = with(density) { horizontalPadding.toPx() }
        val topPaddingPx = with(density) { topPadding.toPx() }
        val cardGapPx = with(density) { cardGap.toPx() }
        val sideCardsOnScreen = minOf(5, (videoCount - 1).coerceAtLeast(0))
        val availableWidthPx = (containerWidthPx - startPaddingPx * 2f - cardGapPx * sideCardsOnScreen)
            .coerceAtLeast(1f)
        val sideWidthPx = if (sideCardsOnScreen > 0) {
            availableWidthPx / (sideCardsOnScreen + FOCUSED_CARD_SCALE)
        } else {
            availableWidthPx
        }
        val focusedWidthPx = sideWidthPx * FOCUSED_CARD_SCALE
        // All exhibition thumbnails are displayed at 16:9 regardless of source dimensions.
        val sideHeightPx = sideWidthPx * 9f / 16f
        val focusedHeightPx = focusedWidthPx * 9f / 16f

        RailMetrics(
            railHeight = with(density) { (focusedHeightPx + topPaddingPx + 24.dp.toPx()).toDp() },
            topPaddingPx = topPaddingPx,
            startPaddingPx = startPaddingPx,
            sideWidthPx = sideWidthPx,
            sideHeightPx = sideHeightPx,
            focusedWidthPx = focusedWidthPx,
            focusedHeightPx = focusedHeightPx,
            slotDistancePx = sideWidthPx + cardGapPx,
            focusedStepPx = focusedWidthPx + cardGapPx,
            visibleSlots = max(1, sideCardsOnScreen + 1)
        )
    }
}

@Composable
internal fun rememberVisibleRailCards(
    videoCount: Int,
    focusedIndex: Int,
    dragSlots: Float,
    metrics: RailMetrics
): List<RailCardLayout> {
    return remember(videoCount, focusedIndex, dragSlots, metrics) {
        List(videoCount) { index ->
            railCardLayout(
                index = index,
                focusedIndex = focusedIndex,
                itemCount = videoCount,
                dragSlots = dragSlots,
                metrics = metrics
            )
        }
            .filterNotNull()
            .sortedBy { it.focusProgress }
    }
}

internal data class RailMetrics(
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

internal data class RailCardLayout(
    val index: Int,
    val xPx: Float,
    val focusProgress: Float
)

internal fun calculateFocusedIndexForDragOffset(
    focusedIndex: Int,
    offsetPx: Float,
    slotDistancePx: Float,
    itemCount: Int
): Int {
    if (itemCount <= 1) return focusedIndex

    val minDragPx = slotDistancePx * FOCUS_SETTLE_THRESHOLD
    if (abs(offsetPx) < minDragPx) return focusedIndex

    val slots = offsetPx / max(slotDistancePx, 1f)
    val draggedSlotCount = max(1, abs(slots).roundToInt())
    val focusDelta = if (slots < 0f) draggedSlotCount else -draggedSlotCount
    return circularIndex(focusedIndex + focusDelta, itemCount)
}

internal fun railCardLayout(
    index: Int,
    focusedIndex: Int,
    itemCount: Int,
    dragSlots: Float,
    metrics: RailMetrics
): RailCardLayout? {
    val baseSlot = circularIndex(index - focusedIndex, itemCount).toFloat() + dragSlots
    val slot = visibleCircularSlot(
        baseSlot = baseSlot,
        itemCount = itemCount,
        visibleSlots = metrics.visibleSlots
    ) ?: return null

    return RailCardLayout(
        index = index,
        xPx = metrics.xForSlot(slot),
        focusProgress = focusProgressFromSlot(slot)
    )
}

private fun visibleCircularSlot(
    baseSlot: Float,
    itemCount: Int,
    visibleSlots: Int
): Float? {
    val firstCandidate = baseSlot - itemCount
    if (firstCandidate.isVisibleRailSlot(visibleSlots)) return firstCandidate

    if (baseSlot.isVisibleRailSlot(visibleSlots)) return baseSlot

    val lastCandidate = baseSlot + itemCount
    return if (lastCandidate.isVisibleRailSlot(visibleSlots)) lastCandidate else null
}

private fun Float.isVisibleRailSlot(visibleSlots: Int): Boolean {
    return this >= -1.15f && this <= visibleSlots + 0.35f
}

private fun RailMetrics.xForSlot(slot: Float): Float {
    return startPaddingPx + when {
        slot <= 0f -> slot * focusedStepPx
        slot <= 1f -> slot * focusedStepPx
        else -> focusedStepPx + (slot - 1f) * slotDistancePx
    }
}

private fun focusProgressFromSlot(slot: Float): Float {
    return (1f - abs(slot)).coerceIn(0f, 1f)
}

private fun circularIndex(index: Int, size: Int): Int {
    if (size <= 0) return 0
    return ((index % size) + size) % size
}
