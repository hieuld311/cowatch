package com.hieuld.cowatch.ui

import android.content.Context
import android.content.Intent
import com.hieuld.cowatch.domain.media.AssetVideo
import com.hieuld.cowatch.domain.media.VideoSource

object FrontPlayerContract {
    private const val SOURCE_TYPE_ASSET = "asset"
    private const val EXTRA_SOURCE_TYPE = "com.hieuld.cowatch.extra.SOURCE_TYPE"
    private const val EXTRA_VIDEO_ASSET_PATH = "com.hieuld.cowatch.extra.VIDEO_ASSET_PATH"
    private const val EXTRA_VIDEO_TITLE = "com.hieuld.cowatch.extra.VIDEO_TITLE"

    // Keep all player Intent extras centralized so Activity recreation and future source types stay stable.
    fun createIntent(context: Context, video: AssetVideo): Intent {
        val source = video.toVideoSource()
        return Intent(context, FrontPlayerActivity::class.java)
            .putExtra(EXTRA_SOURCE_TYPE, SOURCE_TYPE_ASSET)
            .putExtra(EXTRA_VIDEO_ASSET_PATH, source.assetPath)
            .putExtra(EXTRA_VIDEO_TITLE, source.title)
    }

    // Rebuild the source from primitive Intent data; never pass media/session objects between activities.
    fun readVideo(intent: Intent): VideoSource? {
        val sourceType = intent.getStringExtra(EXTRA_SOURCE_TYPE) ?: SOURCE_TYPE_ASSET
        if (sourceType != SOURCE_TYPE_ASSET) return null

        val assetPath = intent.getStringExtra(EXTRA_VIDEO_ASSET_PATH)
            ?.takeIf { it.isNotBlank() }
            ?: return null

        val title = intent.getStringExtra(EXTRA_VIDEO_TITLE)
            ?.takeIf { it.isNotBlank() }
            ?: "Untitled video"

        return VideoSource.Asset(
            assetPath = assetPath,
            title = title
        )
    }
}
