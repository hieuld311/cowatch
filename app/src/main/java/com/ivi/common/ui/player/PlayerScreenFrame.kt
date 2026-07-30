package com.ivi.common.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ivi.R
import com.ivi.common.ui.PressStateIconButton

/** Shared player layering; flavors supply only their surface, controls and optional overlays. */
@Composable
public fun PlayerScreenFrame(
    onCloseClick: () -> Unit,
    modifier: Modifier = Modifier,
    videoSurface: @Composable BoxScope.() -> Unit,
    playbackControls: @Composable BoxScope.() -> Unit,
    overlays: @Composable BoxScope.() -> Unit = {}
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        videoSurface()
        playbackControls()
        PressStateIconButton(
            normalDrawable = R.drawable.ico_general_close_n,
            pressedDrawable = R.drawable.ico_general_close_p,
            contentDescription = "Close player",
            onClick = onCloseClick,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
            layoutSize = 64.dp,
            iconSize = 40.dp
        )
        overlays()
    }
}
