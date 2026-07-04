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
import com.hieuld.cowatch.media.RawVideo
import com.hieuld.cowatch.media.RawVideoThumbnailCache
import com.hieuld.cowatch.media.RawVideoThumbnailProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun VideoThumbnail(
    video: RawVideo,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    thumbnailProfile: RawVideoThumbnailProfile = RawVideoThumbnailProfile.Rail
) {
    val context = LocalContext.current.applicationContext
    val thumbnail by produceState<Bitmap?>(
        initialValue = RawVideoThumbnailCache.getCached(video.resId, thumbnailProfile),
        key1 = video.resId,
        key2 = thumbnailProfile,
        key3 = context
    ) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                RawVideoThumbnailCache.getOrLoad(context, video, thumbnailProfile)
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
