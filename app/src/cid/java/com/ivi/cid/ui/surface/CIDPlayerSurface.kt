package com.ivi.cid.ui.surface

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import com.ivi.common.playback.PlayerSurfaceController

@Composable
internal fun CIDPlayerSurface(
    playerSurfaceController: PlayerSurfaceController,
    modifier: Modifier = Modifier,
    onPlayerViewReady: (PlayerView?) -> Unit = {}
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            PlayerView(context).apply {
                useController = false
            }.also { playerView ->
                playerSurfaceController.attach(playerView)
                onPlayerViewReady(playerView)
            }
        },
        update = { },
        onRelease = { playerView ->
            playerSurfaceController.detach(playerView)
            onPlayerViewReady(null)
        }
    )
}
