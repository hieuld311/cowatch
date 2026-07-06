package com.hieuld.cowatch.ui.player

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import com.hieuld.cowatch.domain.display.DisplayInfo
import com.hieuld.cowatch.render.VideoRenderEngine

@Composable
internal fun FrontPlayerScreen(
    player: Player,
    renderEngine: VideoRenderEngine,
    broadcastChecked: Boolean,
    showShareDialog: Boolean,
    displays: List<DisplayInfo>,
    onBroadcastCheckedChange: (Boolean) -> Unit,
    onShareDialogDismiss: () -> Unit,
    onStartSharing: (Set<Int>) -> Unit,
    onBackClick: () -> Unit,
    onPictureInPictureClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            FullPlayerShell(
                player = player,
                renderEngine = renderEngine,
                broadcastChecked = broadcastChecked,
                onBroadcastCheckedChange = onBroadcastCheckedChange,
                onBackClick = onBackClick,
                onPictureInPictureClick = onPictureInPictureClick
            )

            if (showShareDialog) {
                ShareDisplaysDialog(
                    displays = displays,
                    onDismiss = onShareDialogDismiss,
                    onStartSharing = onStartSharing
                )
            }
        }
    }
}

@Composable
private fun FullPlayerShell(
    player: Player,
    renderEngine: VideoRenderEngine,
    broadcastChecked: Boolean,
    onBroadcastCheckedChange: (Boolean) -> Unit,
    onBackClick: () -> Unit,
    onPictureInPictureClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // SurfaceView fills the player; aspect ratio is handled by FrameFanoutRenderEngine.fitViewport().
        HostVideoSurface(
            modifier = Modifier.fillMaxSize(),
            renderEngine = renderEngine
        )

        HostPlaybackControls(
            modifier = Modifier.matchParentSize(),
            player = player,
            broadcastChecked = broadcastChecked,
            onBroadcastCheckedChange = onBroadcastCheckedChange,
            onPictureInPictureClick = onPictureInPictureClick
        )

        IconButton(
            onClick = onBackClick,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 12.dp, end = 12.dp)
                // 64dp hit target mirrors the shared-display close button.
                .size(64.dp)
        ) {
            CloseGlyph(modifier = Modifier.size(36.dp))
        }
    }
}

@Composable
private fun HostVideoSurface(
    renderEngine: VideoRenderEngine,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            SurfaceView(context).apply {
                holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) = Unit

                    override fun surfaceChanged(
                        holder: SurfaceHolder,
                        format: Int,
                        width: Int,
                        height: Int
                    ) {
                        renderEngine.addOutputAsync(
                            outputId = VideoRenderEngine.HOST_OUTPUT_ID,
                            surface = holder.surface,
                            width = width,
                            height = height
                        ) { }
                    }

                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                        renderEngine.removeOutputAsync(VideoRenderEngine.HOST_OUTPUT_ID)
                    }
                })
            }
        }
    )
}
