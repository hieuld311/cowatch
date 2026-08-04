package com.ivi.common.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivi.R

@Composable
public fun PrimaryPlaybackButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    buttonSize: Dp = 124.dp,
    iconSize: Dp = 50.dp,
    enabled: Boolean = true,
    forcePressedVisual: Boolean = false
) {
    val press = rememberPressAnimationState("PrimaryPlaybackButtonScale")
    val showPressed = enabled && (forcePressedVisual || press.isPressed)
    val normalIconDrawable = if (isPlaying) {
        R.drawable.ico_media_pause_n
    } else {
        R.drawable.ico_media_play_l_n
    }
    val iconDrawable = when {
        !enabled -> disabledDrawableOrNull(normalIconDrawable) ?: normalIconDrawable
        isPlaying && showPressed -> R.drawable.ico_media_pause_p
        isPlaying -> R.drawable.ico_media_pause_n
        showPressed -> R.drawable.ico_media_play_l_p
        else -> R.drawable.ico_media_play_l_n
    }
    val backgroundDrawable = when {
        !enabled -> R.drawable.img_button_play_background_d
        showPressed -> R.drawable.img_button_play_background_p
        else -> R.drawable.img_button_play_background_n
    }

    Box(
        modifier = modifier
            .size(buttonSize)
            .graphicsLayer {
                scaleX = if (showPressed) 0.98f else press.scale
                scaleY = if (showPressed) 0.98f else press.scale
            },
        contentAlignment = Alignment.Center
    ) {
        Crossfade(
            targetState = backgroundDrawable,
            animationSpec = tween(90),
            label = "PrimaryPlaybackBackground"
        ) { drawableRes ->
            Image(
                painter = painterResource(drawableRes),
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
        }

        Box(
            modifier = Modifier
                .size(iconSize)
                .clickable(
                    interactionSource = press.interactionSource,
                    indication = null,
                    enabled = enabled,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Crossfade(
                targetState = iconDrawable,
                animationSpec = tween(90),
                label = "PrimaryPlaybackGlyph"
            ) { drawableRes ->
                Image(
                    painter = painterResource(drawableRes),
                    contentDescription = if (isPlaying) "Pause video" else "Play video",
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
