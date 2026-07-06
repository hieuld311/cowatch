package com.hieuld.cowatch.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp

@Composable
internal fun VideoSeekBar(
    positionMs: Long,
    durationMs: Long,
    enabled: Boolean,
    onSeekPreview: (Long) -> Unit,
    onSeekFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    var widthPx by remember { mutableIntStateOf(1) }

    fun positionFromX(x: Float): Long {
        if (durationMs <= 0L || widthPx <= 0) return 0L

        val fraction = (x / widthPx).coerceIn(0f, 1f)
        return (durationMs * fraction).toLong()
    }

    Canvas(
        modifier = modifier
            // 48dp hit area keeps the 6dp visual track easy to touch in a vehicle display.
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
        // Visual track/thumb sizes are density-aware dp, converted to px inside Canvas.
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
