package com.ivi.common.playback

import androidx.media3.ui.PlayerView

/** Media3 boundary used only by Android player surfaces. */
interface PlayerSurfaceController {
    fun attach(targetView: PlayerView)
    fun detach(targetView: PlayerView)
}
