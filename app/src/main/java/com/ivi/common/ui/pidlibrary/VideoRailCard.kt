package com.ivi.common.ui.pidlibrary

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivi.common.domain.AssetVideo
import com.ivi.common.media.ThumbnailLoader
import com.ivi.common.ui.PrimaryPlaybackButton
import com.ivi.common.ui.library.ExplorerPlaceholderColor
import com.ivi.common.ui.library.VideoFocusCyan
import com.ivi.common.ui.library.VideoThumbnail

@Composable
public fun VideoRailCard(
    video: AssetVideo,
    thumbnailLoader: ThumbnailLoader,
    selected: Boolean,
    isPlaying: Boolean,
    width: Dp,
    thumbnailHeight: Dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onPlaybackClick: () -> Unit
) {
    Column(
        modifier = modifier
            .width(width)
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.Top
    ) {
        val thumbnailModifier = Modifier
            .fillMaxWidth()
            .height(thumbnailHeight)
            .clip(VideoCardShape)
            // Placeholder color and fixed thumbnail height prevent layout jumps while assets decode.
            .background(ExplorerPlaceholderColor)
            .then(
                if (selected) {
                    Modifier.border(
                        width = 1.dp,
                        color = VideoFocusCyan,
                        shape = VideoCardShape
                    )
                } else {
                    Modifier
                }
            )

        Box(
            modifier = thumbnailModifier
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
                )
            }
        }

        if (!selected) {
            Text(
                text = video.title,
                modifier = Modifier.padding(top = 8.dp),
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private val VideoCardShape = RoundedCornerShape(8.dp)
