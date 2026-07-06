package com.hieuld.cowatch.domain.media

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

sealed interface VideoSource {
    val title: String

    fun toMediaItem(packageName: String): MediaItem

    data class Asset(
        val assetPath: String,
        override val title: String
    ) : VideoSource {
        override fun toMediaItem(packageName: String): MediaItem {
            // Media3 can resolve asset:/// URIs through the app APK assets without copying media to storage.
            return MediaItem.Builder()
                .setUri("asset:///$assetPath")
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(title)
                        .build()
                )
                .build()
        }
    }
}
