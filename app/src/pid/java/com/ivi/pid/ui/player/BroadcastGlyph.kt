package com.ivi.pid.ui.player

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.ivi.R

/** PID-only control; all non-broadcast media buttons live in com.ivi.common. */
@Composable
internal fun BroadcastGlyph(
    active: Boolean,
    pressed: Boolean,
    modifier: Modifier = Modifier
) {
    val drawable = when {
        pressed -> R.drawable.ico_media_boardcast_p
        active -> R.drawable.ico_media_boardcast_s
        else -> R.drawable.ico_media_boardcast_n
    }
    Crossfade(
        targetState = drawable,
        animationSpec = tween(90),
        label = "BroadcastGlyphCrossfade"
    ) { icon ->
        Image(
            painter = painterResource(icon),
            contentDescription = "Broadcast",
            modifier = modifier.fillMaxSize()
        )
    }
}
