package com.ivi.common.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
public fun PressStateIconButton(
    @DrawableRes normalDrawable: Int,
    @DrawableRes pressedDrawable: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    layoutSize: Dp = 124.dp,
    iconSize: Dp = 40.dp,
    enabled: Boolean = true,
    forcePressedVisual: Boolean = false
) {
    val press = rememberPressAnimationState("PressStateIconScale")
    val showPressed = forcePressedVisual || press.isPressed

    Box(
        modifier = modifier
            .size(layoutSize),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(iconSize)
                .graphicsLayer {
                    scaleX = if (forcePressedVisual) 0.98f else press.scale
                    scaleY = if (forcePressedVisual) 0.98f else press.scale
                }
                .clickable(
                    interactionSource = press.interactionSource,
                    indication = null,
                    enabled = enabled,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Crossfade(
                targetState = showPressed,
                animationSpec = tween(90),
                label = "PressStateIconCrossfade"
            ) { pressed ->
                Image(
                    painter = painterResource(if (pressed) pressedDrawable else normalDrawable),
                    contentDescription = contentDescription,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

public data class PressAnimationState(
    val interactionSource: MutableInteractionSource,
    val isPressed: Boolean,
    val scale: Float
)

@Composable
public fun rememberPressAnimationState(label: String): PressAnimationState {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = tween(durationMillis = if (isPressed) 80 else 140),
        label = label
    )
    return PressAnimationState(interactionSource, isPressed, scale)
}
