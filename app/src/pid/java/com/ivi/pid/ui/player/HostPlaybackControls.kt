package com.ivi.pid.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.ivi.R
import com.ivi.common.media.SeekFrameProvider
import com.ivi.common.ui.rememberPressAnimationState
import com.ivi.common.ui.player.PlaybackControlBar

@Composable
internal fun HostPlaybackControls(
    player: Player,
    activeAssetPath: String?,
    seekFrameProvider: SeekFrameProvider,
    broadcastChecked: Boolean,
    onBroadcastCheckedChange: (Boolean) -> Unit,
    onPictureInPictureClick: () -> Unit,
    onPreviousVideo: () -> Unit,
    onNextVideo: () -> Unit,
    modifier: Modifier = Modifier
) {
    PlaybackControlBar(
        player = player,
        activeAssetPath = activeAssetPath,
        seekFrameProvider = seekFrameProvider,
        onPictureInPictureClick = onPictureInPictureClick,
        onPreviousVideo = onPreviousVideo,
        onNextVideo = onNextVideo,
        modifier = modifier,
        showVideoTitle = true,
        controlBackgroundDrawable = R.drawable.img_media_passenger_control_background,
        leadingControl = { onInteraction ->

                        val press = rememberPressAnimationState("BroadcastIconScale")
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(40.dp)
                    .clip(CircleShape)
                    .graphicsLayer {
                        scaleX = press.scale
                        scaleY = press.scale
                    }
                    .clickable(
                        interactionSource = press.interactionSource,
                        indication = null
                    ) {
                        onInteraction()
                        onBroadcastCheckedChange(!broadcastChecked)
                    },
                contentAlignment = Alignment.Center
            ) {
                BroadcastGlyph(
                    active = broadcastChecked,
                    pressed = press.isPressed
                )
            }
        }
    )
}
