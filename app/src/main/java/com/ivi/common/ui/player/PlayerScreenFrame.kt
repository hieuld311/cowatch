package com.ivi.common.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivi.R
import com.ivi.common.ui.PressStateIconButton
import com.ivi.common.ui.coWatchColorScheme

/** Shared player layering; flavors supply only their surface, controls and optional overlays. */
@Composable
public fun PlayerScreenFrame(
    onCloseClick: () -> Unit,
    modifier: Modifier = Modifier,
    closeButtonVisible: Boolean = true,
    closeBoxSize: Dp = 72.dp,
    closePaddingTop: Dp = 77.dp,
    closePaddingEnd: Dp = 48.dp,
    closeBoxContentPadding: Dp = 10.dp,
    closeLayoutSize: Dp = 52.dp,
    closeIconSize: Dp = 40.dp,
    videoSurface: @Composable BoxScope.() -> Unit,
    playbackControls: @Composable BoxScope.() -> Unit,
    overlays: @Composable BoxScope.() -> Unit = {}
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.coWatchColorScheme.playerCanvas)
    ) {
        videoSurface()
        playbackControls()
        AnimatedVisibility(
            visible = closeButtonVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            Box(
                modifier = Modifier
                    .padding(top = closePaddingTop, end = closePaddingEnd)
                    .size(closeBoxSize)
                    .padding(closeBoxContentPadding),
                contentAlignment = Alignment.Center
            ) {
                PressStateIconButton(
                    normalDrawable = R.drawable.ico_general_close_n,
                    pressedDrawable = R.drawable.ico_general_close_p,
                    contentDescription = "Close player",
                    onClick = onCloseClick,
                    layoutSize = closeLayoutSize,
                    iconSize = closeIconSize
                )
            }
        }
        overlays()
    }
}
