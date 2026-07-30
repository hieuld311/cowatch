package com.ivi.rear.ui

import android.content.Context
import android.content.Intent
import com.ivi.common.domain.AssetVideo
import com.ivi.common.domain.VideoSource

object RearNavigation {
    private const val EXTRA_PATH = "com.ivi.rear.extra.ASSET_PATH"
    private const val EXTRA_TITLE = "com.ivi.rear.extra.TITLE"

    fun playerIntent(context: Context, video: AssetVideo): Intent =
        playerIntent(context, video.toVideoSource())

    fun playerIntent(context: Context, source: VideoSource.Asset): Intent =
        Intent(context, FrontPlayerActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_PATH, source.assetPath)
            .putExtra(EXTRA_TITLE, source.title)

    fun libraryIntent(context: Context): Intent = Intent(context, VideoLibraryActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    fun readSource(intent: Intent): VideoSource.Asset? {
        val path = intent.getStringExtra(EXTRA_PATH)?.takeIf(String::isNotBlank) ?: return null
        return VideoSource.Asset(path, intent.getStringExtra(EXTRA_TITLE) ?: "Untitled video")
    }
}
