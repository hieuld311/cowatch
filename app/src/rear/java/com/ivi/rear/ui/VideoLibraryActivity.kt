package com.ivi.rear.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivi.common.ui.CoWatchTheme
import com.ivi.common.ui.requestUsbVideoPermissionIfNeeded
import com.ivi.common.ui.showTransparentLibraryStatusBar
import com.ivi.common.ui.pidlibrary.VideoLibraryScreen
import com.ivi.rear.viewmodel.VideoLibraryViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class VideoLibraryActivity : ComponentActivity() {
    private val viewModel: VideoLibraryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.showTransparentLibraryStatusBar()
        requestUsbVideoPermissionIfNeeded()
        setContent {
            CoWatchTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                val pendingRequest by viewModel.shareClient.pendingShareRequest.collectAsStateWithLifecycle()
                LaunchedEffect(Unit) {
                    withFrameNanos { }
                    display?.displayId?.let(viewModel.shareClient::markReceiverUiReady)
                }
                Box(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
                    VideoLibraryScreen(
                        videos = state.videos,
                        thumbnailLoader = viewModel.thumbnailLoader,
                        playerSurfaceController = viewModel.playbackController,
                        pipState = state.pipState,
                        activeSessionAssetPath = state.playbackState.activeSource?.assetPath,
                        isSessionPlaying = state.playbackState.isPlaying,
                        onPipSelected = {
                            if (!state.sharedMode) state.pipState?.source?.let {
                                startActivity(RearNavigation.playerIntent(this@VideoLibraryActivity, it))
                            }
                        },
                        onPipPlaybackToggle = viewModel::togglePlayback,
                        onFocusedPlaybackToggle = viewModel::togglePlayback,
                        onPipClose = viewModel::closePip,
                        onFocusedVideoPlay = { video ->
                            if (!state.sharedMode) {
                                startActivity(RearNavigation.playerIntent(this@VideoLibraryActivity, video))
                            }
                        }
                    )
                    pendingRequest?.let { request ->
                        ReceiverBroadcastDialog(
                            request = request,
                            onShown = viewModel.shareClient::onPendingRequestDialogShown,
                            onDismiss = viewModel.shareClient::dismissPendingRequest,
                            onAccept = viewModel.shareClient::acceptPendingRequest
                        )
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        display?.displayId?.let(viewModel.shareClient::updateDisplay)
    }
}
