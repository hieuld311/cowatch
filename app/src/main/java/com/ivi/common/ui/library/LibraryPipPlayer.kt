package com.ivi.common.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ivi.R
import com.ivi.common.ui.PressStateIconButton
import com.ivi.common.ui.PrimaryPlaybackButton

/** Shared PiP controls and behavior; each flavor supplies only its video-surface implementation. */
@Composable
fun LibraryPipPlayer(
    isPlaying: Boolean,
    onExpand: () -> Unit,
    onPlaybackToggle: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    videoSurface: @Composable BoxScope.() -> Unit
) {
    Box(modifier = modifier) {
        videoSurface()

        PrimaryPlaybackButton(
            isPlaying = isPlaying,
            onClick = onPlaybackToggle,
            modifier = Modifier.align(Alignment.Center)
        )

        PressStateIconButton(
            normalDrawable = R.drawable.ico_media_expend_n,
            pressedDrawable = R.drawable.ico_media_expend_p,
            contentDescription = "Expand player",
            onClick = onExpand,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp),
            layoutSize = 40.dp,
            iconSize = 40.dp
        )

        PressStateIconButton(
            normalDrawable = R.drawable.ico_general_close_n,
            pressedDrawable = R.drawable.ico_general_close_p,
            contentDescription = "Close PiP player",
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp),
            layoutSize = 40.dp,
            iconSize = 40.dp
        )
    }
}
