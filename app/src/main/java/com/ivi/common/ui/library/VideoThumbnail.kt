package com.ivi.common.ui.library

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.ivi.common.domain.AssetVideo
import com.ivi.common.media.ThumbnailLoader
import com.ivi.common.media.ThumbnailProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
public fun VideoThumbnail(
    video: AssetVideo,
    thumbnailLoader: ThumbnailLoader,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    thumbnailProfile: ThumbnailProfile = ThumbnailProfile.Rail
) {
    // assetPath is stable across builds and is used for both Compose state and bitmap cache keys.
    val thumbnail by produceState<Bitmap?>(
        initialValue = thumbnailLoader.getCached(video.assetPath, thumbnailProfile),
        key1 = video.assetPath,
        key2 = thumbnailProfile,
        key3 = thumbnailLoader
    ) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                thumbnailLoader.getOrLoad(video, thumbnailProfile)
            }
        }
    }

    val imageBitmap = remember(thumbnail) { thumbnail?.asImageBitmap() }

    Box(modifier = modifier.background(ExplorerPlaceholderColor)) {
        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap,
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
