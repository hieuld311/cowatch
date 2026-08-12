package com.ivi.common.ui.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ivi.R
import com.ivi.common.ui.PressStateIconButton
import com.ivi.common.ui.PrimaryPlaybackButton
import kotlinx.coroutines.delay

private const val PIP_CONTROLS_AUTO_HIDE_DELAY_MS = 5_000L

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
    var controlsVisible by remember { mutableStateOf(true) }
    var interactionVersion by remember { mutableIntStateOf(0) }

    fun showControls() {
        controlsVisible = true
        interactionVersion += 1
    }

    LaunchedEffect(interactionVersion) {
        delay(PIP_CONTROLS_AUTO_HIDE_DELAY_MS)
        controlsVisible = false
    }

    Box(modifier = modifier) {
        videoSurface()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = ::showControls
                )
        )

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                PrimaryPlaybackButton(
                    isPlaying = isPlaying,
                    onClick = {
                        showControls()
                        onPlaybackToggle()
                    },
                    modifier = Modifier.align(Alignment.Center)
                )

                PressStateIconButton(
                    normalDrawable = R.drawable.ico_media_expend_n,
                    pressedDrawable = R.drawable.ico_media_expend_p,
                    contentDescription = "Expand player",
                    onClick = {
                        showControls()
                        onExpand()
                    },
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
                    onClick = {
                        showControls()
                        onClose()
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp),
                    layoutSize = 40.dp,
                    iconSize = 40.dp
                )
            }
        }
    }
}
