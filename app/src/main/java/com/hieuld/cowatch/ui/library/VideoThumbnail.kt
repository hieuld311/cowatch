package com.hieuld.cowatch.ui.library

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
import androidx.compose.ui.platform.LocalContext
import com.hieuld.cowatch.domain.media.AssetVideo
import com.hieuld.cowatch.data.media.provider.AssetVideoThumbnailCache
import com.hieuld.cowatch.data.media.provider.AssetVideoThumbnailProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun VideoThumbnail(
    video: AssetVideo,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    thumbnailProfile: AssetVideoThumbnailProfile = AssetVideoThumbnailProfile.Rail
) {
    val context = LocalContext.current.applicationContext
    // assetPath is stable across builds and is used for both Compose state and bitmap cache keys.
    val thumbnail by produceState<Bitmap?>(
        initialValue = AssetVideoThumbnailCache.getCached(video.assetPath, thumbnailProfile),
        key1 = video.assetPath,
        key2 = thumbnailProfile,
        key3 = context
    ) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                AssetVideoThumbnailCache.getOrLoad(context, video, thumbnailProfile)
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
