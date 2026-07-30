package com.ivi.common.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import com.ivi.common.playback.PlayerSurfaceController

@Composable
fun Media3PlayerSurface(
    playerSurfaceController: PlayerSurfaceController,
    modifier: Modifier = Modifier,
    onPlayerViewReady: (PlayerView?) -> Unit = {}
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            PlayerView(context).apply { useController = false }.also { view ->
                playerSurfaceController.attach(view)
                onPlayerViewReady(view)
            }
        },
        update = {},
        onRelease = { view ->
            playerSurfaceController.detach(view)
            onPlayerViewReady(null)
        }
    )
}
