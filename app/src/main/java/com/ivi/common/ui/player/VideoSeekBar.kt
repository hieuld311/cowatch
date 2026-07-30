package com.ivi.common.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ivi.R
import kotlin.math.roundToInt

public val SEEK_BAR_VISUAL_HEIGHT = 40.dp

private val SEEK_HANDLE_SIZE = 40.dp
private val SEEK_TOUCH_HEIGHT = 16.dp

@Composable
public fun VideoSeekBar(
    positionMs: Long,
    durationMs: Long,
    enabled: Boolean,
    onSeekPreview: (Long) -> Unit,
    onSeekFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    var widthPx by remember { mutableIntStateOf(1) }
    val density = LocalDensity.current
    val handleSizePx = with(density) { SEEK_HANDLE_SIZE.toPx() }
    val handleRadiusPx = handleSizePx / 2f
    val progress = if (durationMs > 0L) {
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val progressXPx = widthPx * progress
    val handleCenterXPx = progressXPx

    fun positionFromX(x: Float): Long {
        if (durationMs <= 0L || widthPx <= 0) return 0L

        val fraction = (x / widthPx).coerceIn(0f, 1f)
        return (durationMs * fraction).toLong()
    }

    Box(
        modifier = modifier
            .height(SEEK_BAR_VISUAL_HEIGHT)
            .onSizeChanged { widthPx = it.width.coerceAtLeast(1) }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val trackY = size.height / 2f

            drawLine(
                color = Color(0xFF2B2D4A),
                start = Offset(0f, trackY),
                end = Offset(size.width, trackY),
                strokeWidth = 3.dp.toPx()
            )
            drawLine(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color(0xFF00E7FF), Color(0xFF8D3DFF)),
                    startX = 0f,
                    endX = size.width
                ),
                start = Offset(0f, trackY),
                end = Offset(progressXPx, trackY),
                strokeWidth = 3.dp.toPx()
            )
        }

        Image(
            painter = painterResource(R.drawable.img_general_slider_handle_n),
            contentDescription = null,
            modifier = Modifier
                .size(SEEK_HANDLE_SIZE)
                .offset {
                    IntOffset(
                        x = (handleCenterXPx - handleRadiusPx).roundToInt(),
                        y = 0
                    )
                }
        )

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(SEEK_TOUCH_HEIGHT)
                .pointerInput(enabled, durationMs, widthPx) {
                    if (!enabled) return@pointerInput

                    detectTapGestures { offset ->
                        onSeekPreview(positionFromX(offset.x))
                        onSeekFinished()
                    }
                }
                .pointerInput(enabled, durationMs, widthPx) {
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
        )
    }
}
