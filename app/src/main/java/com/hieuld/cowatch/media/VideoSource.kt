package com.hieuld.cowatch.media

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

sealed interface VideoSource {
    val title: String

    fun toMediaItem(packageName: String): MediaItem

    data class Raw(
        val resId: Int,
        val resourceName: String,
        override val title: String
    ) : VideoSource {
        override fun toMediaItem(packageName: String): MediaItem {
            return MediaItem.Builder()
                .setUri("android.resource://$packageName/$resId")
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(title)
                        .build()
                )
                .build()
        }
    }
}
