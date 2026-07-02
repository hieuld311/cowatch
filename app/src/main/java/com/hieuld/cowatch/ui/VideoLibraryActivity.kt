package com.hieuld.cowatch.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.hieuld.cowatch.media.RawVideoRepository
import com.hieuld.cowatch.ui.library.VideoLibraryScreen

class VideoLibraryActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val videos = RawVideoRepository.listVideos()

        setContent {
            CoWatchTheme {
                VideoLibraryScreen(
                    videos = videos,
                    onVideoSelected = { video ->
                        startActivity(FrontPlayerContract.createIntent(this, video))
                    }
                )
            }
        }
    }
}
