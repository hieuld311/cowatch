package com.hieuld.cowatch.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import com.hieuld.cowatch.ui.library.VideoLibraryScreen
import com.hieuld.cowatch.ui.theme.CoWatchTheme
import com.hieuld.cowatch.viewmodel.VideoLibraryViewModel

class VideoLibraryActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val viewModel = ViewModelProvider(this)[VideoLibraryViewModel::class.java]

        setContent {
            CoWatchTheme {
                // Activity stays as navigation shell; media catalog and PiP state live in the ViewModel.
                val uiState by viewModel.uiState.collectAsState()
                VideoLibraryScreen(
                    videos = uiState.videos,
                    pipState = uiState.pipState,
                    onPipSelected = {
                        uiState.pipState?.source?.let { source ->
                            startActivity(FrontPlayerContract.createIntent(this, source))
                        }
                    },
                    onVideoSelected = { video ->
                        startActivity(FrontPlayerContract.createIntent(this, video))
                    }
                )
            }
        }
    }
}
