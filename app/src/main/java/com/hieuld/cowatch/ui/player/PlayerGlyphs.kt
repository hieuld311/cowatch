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
internal fun PictureInPictureGlyph() {
    Canvas(modifier = Modifier.size(24.dp)) {
        val color = Color.White.copy(alpha = 0.86f)
        val strokeWidth = 2.dp.toPx()
        val inset = 3.dp.toPx()
        val outerRight = size.width - inset
        val outerBottom = size.height - inset
        val innerWidth = 8.dp.toPx()
        val innerHeight = 5.dp.toPx()
        val innerRight = outerRight - 2.dp.toPx()
        val innerBottom = outerBottom - 2.dp.toPx()

        drawLine(color, Offset(inset, inset), Offset(outerRight, inset), strokeWidth)
        drawLine(color, Offset(inset, inset), Offset(inset, outerBottom), strokeWidth)
        drawLine(color, Offset(outerRight, inset), Offset(outerRight, outerBottom), strokeWidth)
        drawLine(color, Offset(inset, outerBottom), Offset(outerRight, outerBottom), strokeWidth)

        drawLine(
            color,
            Offset(innerRight - innerWidth, innerBottom - innerHeight),
            Offset(innerRight, innerBottom - innerHeight),
            strokeWidth
        )
        drawLine(
            color,
            Offset(innerRight - innerWidth, innerBottom - innerHeight),
            Offset(innerRight - innerWidth, innerBottom),
            strokeWidth
        )
        drawLine(color, Offset(innerRight, innerBottom - innerHeight), Offset(innerRight, innerBottom), strokeWidth)
        drawLine(color, Offset(innerRight - innerWidth, innerBottom), Offset(innerRight, innerBottom), strokeWidth)
    }
}

@Composable
internal fun BroadcastGlyph(active: Boolean) {
    Canvas(modifier = Modifier.size(24.dp)) {
        val color = if (active) {
            Color(0xFF2DE7F0)
        } else {
            Color.White.copy(alpha = 0.86f)
        }
        val strokeWidth = 2.dp.toPx()
        val screenLeft = 4.dp.toPx()
        val screenTop = 7.dp.toPx()
        val screenRight = size.width - 4.dp.toPx()
        val screenBottom = size.height - 5.dp.toPx()

        drawLine(color, Offset(screenLeft, screenTop), Offset(screenRight, screenTop), strokeWidth)
        drawLine(color, Offset(screenLeft, screenTop), Offset(screenLeft, screenBottom), strokeWidth)
        drawLine(color, Offset(screenRight, screenTop), Offset(screenRight, screenBottom), strokeWidth)
        drawLine(color, Offset(screenLeft, screenBottom), Offset(screenRight, screenBottom), strokeWidth)

        val waveCenter = Offset(size.width / 2f, 4.dp.toPx())
        drawLine(
            color = color,
            start = Offset(waveCenter.x - 7.dp.toPx(), waveCenter.y),
            end = Offset(waveCenter.x - 3.dp.toPx(), waveCenter.y + 3.dp.toPx()),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(waveCenter.x + 7.dp.toPx(), waveCenter.y),
            end = Offset(waveCenter.x + 3.dp.toPx(), waveCenter.y + 3.dp.toPx()),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}

@Composable
internal fun CloseGlyph(modifier: Modifier = Modifier.size(22.dp)) {
    Canvas(modifier = modifier) {
        val color = Color.White.copy(alpha = 0.82f)
        val strokeWidth = 2.dp.toPx()
        val inset = 4.dp.toPx()

        drawLine(
            color = color,
            start = Offset(inset, inset),
            end = Offset(size.width - inset, size.height - inset),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(size.width - inset, inset),
            end = Offset(inset, size.height - inset),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}
