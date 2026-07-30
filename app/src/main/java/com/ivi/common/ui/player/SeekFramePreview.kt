package com.ivi.common.ui.player

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.ivi.common.media.SeekFrameProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private const val PREVIEW_REQUEST_BUCKET_MS = 500L
private val PreviewWidth = 240.dp
private val PreviewHeight = 135.dp
private val PreviewShape = RoundedCornerShape(8.dp)

@Composable
public fun SeekFramePreview(
    seekFrameProvider: SeekFrameProvider,
    assetPath: String,
    positionMs: Long,
    durationMs: Long,
    modifier: Modifier = Modifier
) {
    val latestPositionMs by rememberUpdatedState(positionMs)
    var frame by remember(assetPath) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(assetPath, seekFrameProvider) {
        frame = null
        val decoder = withContext(Dispatchers.IO) {
            seekFrameProvider.open(assetPath)
        } ?: return@LaunchedEffect

        try {
            snapshotFlow { latestPositionMs }
                .map { requestedMs ->
                    (requestedMs.coerceAtLeast(0L) / PREVIEW_REQUEST_BUCKET_MS) *
                        PREVIEW_REQUEST_BUCKET_MS
                }
                .distinctUntilChanged()
                .collectLatest { requestedMs ->
                    val decodedFrame = withContext(Dispatchers.IO) {
                        decoder.frameAt(requestedMs)
                    }
                    if (decodedFrame != null) frame = decodedFrame
                }
        } finally {
            withContext(NonCancellable + Dispatchers.IO) { decoder.close() }
        }
    }

    val imageBitmap = remember(frame) { frame?.asImageBitmap() }
    val progress = if (durationMs > 0L) {
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    BoxWithConstraints(modifier = modifier) {
        val bubbleWidth = if (imageBitmap == null) 88.dp else PreviewWidth
        val bubbleHeight = if (imageBitmap == null) 44.dp else PreviewHeight
        val availableWidth = (maxWidth - bubbleWidth).coerceAtLeast(0.dp)
        val desiredOffset = maxWidth * progress - bubbleWidth / 2f
        val previewOffset = desiredOffset.coerceIn(0.dp, availableWidth)

        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = previewOffset)
                .width(bubbleWidth)
                .height(bubbleHeight)
                .clip(PreviewShape)
                .background(Color.Black)
                .border(1.dp, Color.White.copy(alpha = 0.7f), PreviewShape)
        ) {
            if (imageBitmap != null) {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = "Seek preview",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Text(
                text = formatDurationClock(positionMs),
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .align(if (imageBitmap == null) Alignment.Center else Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.68f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
    }
}
