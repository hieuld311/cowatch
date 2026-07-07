package com.hieuld.cowatch.ui.player

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
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
    hostNotificationText: String?,
    onBroadcastCheckedChange: (Boolean) -> Unit,
    onShareDialogDismiss: () -> Unit,
    onStartSharing: (Set<Int>) -> Unit,
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

        if (showShareDialog) {
            ShareDisplaysDialog(
                displays = displays,
                onDismiss = onShareDialogDismiss,
                onStartSharing = onStartSharing
            )
        }

        HostBroadcastNotification(
            text = hostNotificationText,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 10.dp)
        )
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

@Composable
private fun HostBroadcastNotification(
    text: String?,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = text != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Text(
            text = text.orEmpty(),
            color = Color(0xFFDCE8F2),
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .background(
                    color = Color(0xFF25364A),
                    shape = RoundedCornerShape(4.dp)
                )
                .padding(horizontal = 28.dp, vertical = 8.dp)
        )
    }
}
