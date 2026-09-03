package com.ivi.cid.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import com.ivi.common.playback.PlayerSurfaceController
import com.ivi.common.media.SeekFrameProvider
import com.ivi.common.ui.player.PlayerScreenFrame
import com.ivi.cid.ui.surface.CIDPlayerSurface

@Composable
internal fun FrontPlayerScreen(
    player: Player,
    playerSurfaceController: PlayerSurfaceController,
    seekFrameProvider: SeekFrameProvider,
    activeAssetPath: String?,
    onPlayerViewReady: (PlayerView?) -> Unit,
    onCloseClick: () -> Unit,
    onPictureInPictureClick: () -> Unit,
    onPreviousVideo: () -> Unit,
    onNextVideo: () -> Unit
) {
    var controlsVisible by remember { mutableStateOf(true) }

    PlayerScreenFrame(
        onCloseClick = onCloseClick,
        closeButtonVisible = controlsVisible,
        videoSurface = {
            CIDPlayerSurface(
                modifier = Modifier.matchParentSize(),
                playerSurfaceController = playerSurfaceController,
                onPlayerViewReady = onPlayerViewReady
            )
        },
        playbackControls = {
            HostPlaybackControls(
                player = player,
                seekFrameProvider = seekFrameProvider,
                activeAssetPath = activeAssetPath,
                onPictureInPictureClick = onPictureInPictureClick,
                onPreviousVideo = onPreviousVideo,
                onNextVideo = onNextVideo,
                onControlsVisibilityChanged = { controlsVisible = it },
                modifier = Modifier.matchParentSize()
            )
        }
    )
}
