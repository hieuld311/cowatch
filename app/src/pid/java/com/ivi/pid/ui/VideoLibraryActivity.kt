package com.ivi.pid.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivi.common.media.ThumbnailLoader
import com.ivi.common.ui.CoWatchTheme
import com.ivi.common.ui.requestUsbVideoPermissionIfNeeded
import com.ivi.common.ui.showTransparentLibraryStatusBar
import com.ivi.common.playback.Media3PlaybackController
import com.ivi.common.ui.pidlibrary.VideoLibraryScreen
import com.ivi.pid.viewmodel.VideoLibraryViewModel
import com.ivi.common.ui.player.FanoutVideoSurface
import com.ivi.pid.rendering.PidRenderFanout
import com.ivi.pid.rendering.VideoRenderEngine
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class VideoLibraryActivity : ComponentActivity() {
    @Inject
    lateinit var thumbnailLoader: ThumbnailLoader

    @Inject
    lateinit var playbackSession: Media3PlaybackController

    @Inject
    lateinit var renderFanout: PidRenderFanout

    private val viewModel: VideoLibraryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.showTransparentLibraryStatusBar()
        requestUsbVideoPermissionIfNeeded()

        setContent {
            CoWatchTheme {
                // Activity stays as navigation shell; media catalog and PiP state live in the ViewModel.
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                VideoLibraryScreen(
                    videos = uiState.videos,
                    thumbnailLoader = thumbnailLoader,
                    playerSurfaceController = playbackSession,
                    pipState = uiState.pipState,
                    activeSessionAssetPath = uiState.playbackState.activeSource?.assetPath,
                    isSessionPlaying = uiState.playbackState.isPlaying,
                    onPipSelected = {
                        uiState.pipState?.source?.let { source ->
                            startActivity(FrontPlayerContract.createIntent(this, source))
                        }
                    },
                    onPipPlaybackToggle = viewModel::togglePipPlayback,
                    onFocusedPlaybackToggle = viewModel::togglePipPlayback,
                    onPipClose = viewModel::closePipPlayback,
                    onFocusedVideoPlay = { video ->
                        startActivity(FrontPlayerContract.createIntent(this, video))
                    },
                    pipVideoSurface = {
                        FanoutVideoSurface(
                            modifier = Modifier.fillMaxSize(),
                            onSurfaceAvailable = { generation, surface, width, height ->
                                renderFanout.addOutput(
                                    VideoRenderEngine.LIBRARY_PIP_OUTPUT_ID,
                                    generation,
                                    surface,
                                    width,
                                    height
                                )
                            },
                            onSurfaceDestroyed = { generation ->
                                renderFanout.removeOutput(
                                    VideoRenderEngine.LIBRARY_PIP_OUTPUT_ID,
                                    generation
                                )
                            }
                        )
                    }
                )
            }
        }
    }
}
