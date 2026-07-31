package com.ivi.common.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.ivi.common.domain.VideoSource

fun VideoSource.Asset.toMediaItem(): MediaItem {
    return MediaItem.Builder()
        .setUri(if (isPackagedAsset) "asset:///$assetPath" else assetPath)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .build()
        )
        .build()
}
