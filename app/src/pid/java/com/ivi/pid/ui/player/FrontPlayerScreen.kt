package com.ivi.pid.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.ivi.common.media.SeekFrameProvider
import com.ivi.common.ui.player.FanoutVideoSurface
import com.ivi.common.ui.player.PlayerScreenFrame
import com.ivi.pid.rendering.PidRenderFanout
import com.ivi.pid.rendering.VideoRenderEngine
import com.ivi.pid.sharing.RearTargetState

@Composable
internal fun FrontPlayerScreen(
    player: Player,
    renderFanout: PidRenderFanout,
    activeAssetPath: String?,
    seekFrameProvider: SeekFrameProvider,
    broadcastChecked: Boolean,
    showShareDialog: Boolean,
    targets: List<RearTargetState>,
    hostNotificationText: String?,
    onBroadcastCheckedChange: (Boolean) -> Unit,
    onShareDialogDismiss: () -> Unit,
    onStartSharing: (Set<String>) -> Unit,
    onBackClick: () -> Unit,
    onPictureInPictureClick: () -> Unit
) {
    PlayerScreenFrame(
        onCloseClick = onBackClick,
        videoSurface = {
            FanoutVideoSurface(
                modifier = Modifier.fillMaxSize(),
                onSurfaceAvailable = { generation, surface, width, height ->
                    renderFanout.addOutput(
                        VideoRenderEngine.HOST_OUTPUT_ID,
                        generation,
                        surface,
                        width,
                        height
                    )
                },
                onSurfaceDestroyed = { generation ->
                    renderFanout.removeOutput(VideoRenderEngine.HOST_OUTPUT_ID, generation)
                }
            )
        },
        playbackControls = {
            HostPlaybackControls(
                modifier = Modifier.fillMaxSize(),
                player = player,
                activeAssetPath = activeAssetPath,
                seekFrameProvider = seekFrameProvider,
                broadcastChecked = broadcastChecked,
                onBroadcastCheckedChange = onBroadcastCheckedChange,
                onPictureInPictureClick = onPictureInPictureClick
            )
        },
        overlays = {
            if (showShareDialog) {
                ShareDisplaysDialog(
                    displays = targets,
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
    )
}

@Composable
private fun BoxScope.HostBroadcastNotification(
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
                .background(Color(0xFF25364A), RoundedCornerShape(4.dp))
                .padding(horizontal = 28.dp, vertical = 8.dp)
        )
    }
}
