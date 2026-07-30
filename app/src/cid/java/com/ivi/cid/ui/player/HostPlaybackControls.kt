package com.ivi.cid.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.media3.common.Player
import com.ivi.common.media.SeekFrameProvider
import com.ivi.common.ui.player.PlaybackControlBar

@Composable
internal fun HostPlaybackControls(
    player: Player,
    activeAssetPath: String?,
    seekFrameProvider: SeekFrameProvider,
    onPictureInPictureClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    PlaybackControlBar(
        player = player,
        activeAssetPath = activeAssetPath,
        seekFrameProvider = seekFrameProvider,
        onPictureInPictureClick = onPictureInPictureClick,
        modifier = modifier
    )
}
