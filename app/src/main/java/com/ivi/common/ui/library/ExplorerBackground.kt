package com.ivi.common.ui.library

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.MaterialTheme
import com.ivi.common.domain.AssetVideo
import com.ivi.common.media.ThumbnailLoader
import com.ivi.common.media.ThumbnailProfile
import com.ivi.common.ui.coWatchColorScheme

@Composable
public fun ExplorerBackground(
    video: AssetVideo?,
    thumbnailLoader: ThumbnailLoader,
    @DrawableRes backgroundDrawable: Int,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.background(MaterialTheme.coWatchColorScheme.libraryPlaceholder)) {
        if (video != null) {
            // The selected video supplies the dynamic preview; the provided artwork is its overlay.
            key(video.assetPath) {
                VideoThumbnail(
                    video = video,
                    thumbnailLoader = thumbnailLoader,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    thumbnailProfile = ThumbnailProfile.Background
                )
            }
        }
        Image(
            painter = painterResource(backgroundDrawable),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds
        )
    }
}
