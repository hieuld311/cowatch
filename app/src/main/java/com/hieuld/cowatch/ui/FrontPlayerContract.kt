package com.hieuld.cowatch.ui

import android.content.Context
import android.content.Intent
import com.hieuld.cowatch.media.RawVideo

object FrontPlayerContract {
    private const val EXTRA_VIDEO_RES_ID = "com.hieuld.cowatch.extra.VIDEO_RES_ID"
    private const val EXTRA_VIDEO_TITLE = "com.hieuld.cowatch.extra.VIDEO_TITLE"

    fun createIntent(context: Context, video: RawVideo): Intent {
        return Intent(context, FrontPlayerActivity::class.java)
            .putExtra(EXTRA_VIDEO_RES_ID, video.resId)
            .putExtra(EXTRA_VIDEO_TITLE, video.title)
    }

    fun readVideo(intent: Intent): SelectedVideo? {
        val resId = intent.getIntExtra(EXTRA_VIDEO_RES_ID, 0)
        if (resId == 0) return null

        val title = intent.getStringExtra(EXTRA_VIDEO_TITLE)
            ?.takeIf { it.isNotBlank() }
            ?: "Untitled video"

        return SelectedVideo(resId = resId, title = title)
    }
}

data class SelectedVideo(
    val resId: Int,
    val title: String
)
