package com.hieuld.cowatch.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp

@Composable
internal fun FullscreenGlyph(isFullscreen: Boolean) {
    Canvas(modifier = Modifier.size(24.dp)) {
        val color = Color.White.copy(alpha = 0.86f)
        val strokeWidth = 2.dp.toPx()
        val arm = 7.dp.toPx()
        val inset = 2.dp.toPx()
        val right = size.width - inset
        val bottom = size.height - inset

        if (isFullscreen) {
            val leftInner = inset + arm
            val rightInner = right - arm
            val topInner = inset + arm
            val bottomInner = bottom - arm

            drawLine(color, Offset(leftInner, inset), Offset(leftInner, topInner), strokeWidth)
            drawLine(color, Offset(leftInner, topInner), Offset(inset, topInner), strokeWidth)
            drawLine(color, Offset(rightInner, inset), Offset(rightInner, topInner), strokeWidth)
            drawLine(color, Offset(rightInner, topInner), Offset(right, topInner), strokeWidth)
            drawLine(color, Offset(leftInner, bottom), Offset(leftInner, bottomInner), strokeWidth)
            drawLine(color, Offset(leftInner, bottomInner), Offset(inset, bottomInner), strokeWidth)
            drawLine(color, Offset(rightInner, bottom), Offset(rightInner, bottomInner), strokeWidth)
            drawLine(color, Offset(rightInner, bottomInner), Offset(right, bottomInner), strokeWidth)
            return@Canvas
        }

        drawLine(color, Offset(inset, inset), Offset(inset + arm, inset), strokeWidth)
        drawLine(color, Offset(inset, inset), Offset(inset, inset + arm), strokeWidth)
        drawLine(color, Offset(right, inset), Offset(right - arm, inset), strokeWidth)
        drawLine(color, Offset(right, inset), Offset(right, inset + arm), strokeWidth)
        drawLine(color, Offset(inset, bottom), Offset(inset + arm, bottom), strokeWidth)
        drawLine(color, Offset(inset, bottom), Offset(inset, bottom - arm), strokeWidth)
        drawLine(color, Offset(right, bottom), Offset(right - arm, bottom), strokeWidth)
        drawLine(color, Offset(right, bottom), Offset(right, bottom - arm), strokeWidth)
    }
}

@Composable
internal fun BackGlyph() {
    Canvas(modifier = Modifier.size(20.dp)) {
        val color = Color(0xFFC9D1D9)
        val strokeWidth = 2.dp.toPx()
        val centerY = size.height / 2f
        val startX = 3.dp.toPx()
        val endX = size.width - 3.dp.toPx()
        val head = 6.dp.toPx()

        drawLine(
            color = color,
            start = Offset(startX, centerY),
            end = Offset(endX, centerY),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(startX, centerY),
            end = Offset(startX + head, centerY - head),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(startX, centerY),
            end = Offset(startX + head, centerY + head),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}
