package com.hieuld.cowatch.ui.library

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.hieuld.cowatch.domain.media.AssetVideo
import com.hieuld.cowatch.data.media.provider.AssetVideoThumbnailProfile

@Composable
internal fun ExplorerBackground(
    video: AssetVideo?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.background(ExplorerPlaceholderColor)
    ) {
        if (video != null) {
            // Background follows committed focus only, keeping larger thumbnail decode out of drag preview.
            key(video.assetPath) {
                VideoThumbnail(
                    video = video,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    thumbnailProfile = AssetVideoThumbnailProfile.Background
                )
            }
        } else {
            PlaceholderCross(modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun PlaceholderCross(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val color = Color(0x334A5E71)
        drawLine(
            color = color,
            start = Offset.Zero,
            end = Offset(size.width, size.height),
            strokeWidth = 1.dp.toPx()
        )
        drawLine(
            color = color,
            start = Offset(size.width, 0f),
            end = Offset(0f, size.height),
            strokeWidth = 1.dp.toPx()
        )
    }
}
