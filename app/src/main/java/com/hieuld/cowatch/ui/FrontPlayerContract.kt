package com.hieuld.cowatch.ui

import android.content.Context
import android.content.Intent
import com.hieuld.cowatch.media.RawVideo
import com.hieuld.cowatch.media.VideoSource

object FrontPlayerContract {
    private const val SOURCE_TYPE_RAW = "raw"
    private const val EXTRA_SOURCE_TYPE = "com.hieuld.cowatch.extra.SOURCE_TYPE"
    private const val EXTRA_VIDEO_RES_ID = "com.hieuld.cowatch.extra.VIDEO_RES_ID"
    private const val EXTRA_VIDEO_RESOURCE_NAME = "com.hieuld.cowatch.extra.VIDEO_RESOURCE_NAME"
    private const val EXTRA_VIDEO_TITLE = "com.hieuld.cowatch.extra.VIDEO_TITLE"

    fun createIntent(context: Context, video: RawVideo): Intent {
        val source = video.toVideoSource()
        return Intent(context, FrontPlayerActivity::class.java)
            .putExtra(EXTRA_SOURCE_TYPE, SOURCE_TYPE_RAW)
            .putExtra(EXTRA_VIDEO_RES_ID, source.resId)
            .putExtra(EXTRA_VIDEO_RESOURCE_NAME, source.resourceName)
            .putExtra(EXTRA_VIDEO_TITLE, source.title)
    }

    fun readVideo(intent: Intent): VideoSource? {
        val sourceType = intent.getStringExtra(EXTRA_SOURCE_TYPE) ?: SOURCE_TYPE_RAW
        if (sourceType != SOURCE_TYPE_RAW) return null

        val resId = intent.getIntExtra(EXTRA_VIDEO_RES_ID, 0)
        if (resId == 0) return null

        val resourceName = intent.getStringExtra(EXTRA_VIDEO_RESOURCE_NAME)
            ?.takeIf { it.isNotBlank() }
            ?: "raw_$resId"

        val title = intent.getStringExtra(EXTRA_VIDEO_TITLE)
            ?.takeIf { it.isNotBlank() }
            ?: "Untitled video"

        return VideoSource.Raw(
            resId = resId,
            resourceName = resourceName,
            title = title
        )
    }
}
