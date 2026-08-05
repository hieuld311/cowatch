package com.ivi.common.media

import android.graphics.Bitmap
import com.ivi.common.domain.AssetVideo

interface ThumbnailLoader {
    fun getCached(
        assetPath: String,
        profile: ThumbnailProfile = ThumbnailProfile.Rail
    ): Bitmap?

    suspend fun getOrLoad(
        video: AssetVideo,
        profile: ThumbnailProfile = ThumbnailProfile.Rail
    ): Bitmap?

    suspend fun warmPersistentCache(
        videos: List<AssetVideo>,
        profile: ThumbnailProfile = ThumbnailProfile.Background
    )
}

enum class ThumbnailProfile(
    val width: Int,
    val height: Int,
    val persistentDiskCache: Boolean
) {
    Rail(width = 426, height = 240, persistentDiskCache = false),
    Background(width = 1280, height = 720, persistentDiskCache = true),
    // Compact JPEG source for launcher/media-session artwork. Keeping this below 0.5 MP
    // avoids sending a full playback frame through MediaSession binder metadata.
    LauncherArtwork(width = 480, height = 270, persistentDiskCache = true)
}
