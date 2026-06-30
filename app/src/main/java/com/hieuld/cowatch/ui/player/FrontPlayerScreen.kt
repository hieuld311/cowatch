package com.hieuld.cowatch.ui.player

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import com.hieuld.cowatch.display.DisplayInfo
import com.hieuld.cowatch.render.VideoRenderEngine

private const val HOST_RENDER_OUTPUT_ID = Int.MIN_VALUE

@Composable
internal fun FrontPlayerScreen(
    player: Player,
    renderEngine: VideoRenderEngine,
    broadcastChecked: Boolean,
    showShareDialog: Boolean,
    isFullscreen: Boolean,
    displays: List<DisplayInfo>,
    onBroadcastCheckedChange: (Boolean) -> Unit,
    onShareDialogDismiss: () -> Unit,
    onStartSharing: (Set<Int>) -> Unit,
    onFullscreenToggle: () -> Unit,
    onBackClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (!isFullscreen) {
                    BroadcastTopBar(
                        checked = broadcastChecked,
                        onCheckedChange = onBroadcastCheckedChange,
                        onBackClick = onBackClick,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color.Black)
                ) {
                    HostVideoSurface(
                        modifier = Modifier.fillMaxSize(),
                        renderEngine = renderEngine
                    )

                    HostPlaybackControls(
                        modifier = Modifier.matchParentSize(),
                        player = player,
                        isFullscreen = isFullscreen,
                        onFullscreenToggle = onFullscreenToggle
                    )
                }
            }

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
                        renderEngine.addOutput(
                            outputId = HOST_RENDER_OUTPUT_ID,
                            surface = holder.surface,
                            width = width,
                            height = height
                        )
                    }

                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                        renderEngine.removeOutput(HOST_RENDER_OUTPUT_ID)
                    }
                })
            }
        }
    )
}
