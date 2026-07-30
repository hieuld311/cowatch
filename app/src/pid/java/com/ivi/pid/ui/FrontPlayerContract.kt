package com.ivi.pid.ui

import android.content.Context
import android.content.Intent
import com.ivi.common.domain.AssetVideo
import com.ivi.common.domain.VideoSource

object FrontPlayerContract {
    private const val SOURCE_TYPE_ASSET = "asset"
    private const val EXTRA_SOURCE_TYPE = "com.ivi.pid.extra.SOURCE_TYPE"
    private const val EXTRA_VIDEO_ASSET_PATH = "com.ivi.pid.extra.VIDEO_ASSET_PATH"
    private const val EXTRA_VIDEO_TITLE = "com.ivi.pid.extra.VIDEO_TITLE"

    // Keep all player Intent extras centralized so Activity recreation and future source types stay stable.
    fun createIntent(context: Context, video: AssetVideo): Intent {
        return createIntent(context, video.toVideoSource())
    }

    fun createIntent(context: Context, source: VideoSource.Asset): Intent {
        return Intent(context, FrontPlayerActivity::class.java)
            // Reuse a stopped fullscreen player instance when returning from app-scoped PiP.
            .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
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
