package com.hieuld.cowatch.ui.library

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hieuld.cowatch.R
import com.hieuld.cowatch.domain.media.AssetVideo

@Composable
internal fun VideoRailCard(
    video: AssetVideo,
    selected: Boolean,
    width: Dp,
    thumbnailHeight: Dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
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
            // Placeholder color and fixed thumbnail height prevent layout jumps while assets decode.
            .background(ExplorerPlaceholderColor)
            .then(
                if (selected) {
                    Modifier.border(
                        width = 2.dp,
                        color = Color.White.copy(alpha = 0.72f)
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
                contentDescription = video.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(10.dp)
                        // 44dp icon footprint keeps selected cards stable and matches the original drawable size.
                        .size(44.dp)
                ) {
                    Image(
                        painter = painterResource(R.drawable.ico_media_play_l_p),
                        contentDescription = "Play",
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(44.dp)
                    )
                }
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
