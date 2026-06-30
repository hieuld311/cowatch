package com.hieuld.cowatch.render

import android.view.Surface

interface VideoRenderEngine {
    val inputSurface: Surface

    fun addOutput(
        outputId: Int,
        surface: Surface,
        width: Int,
        height: Int
    ): Boolean

    fun removeOutput(outputId: Int)

    fun setVideoSize(
        width: Int,
        height: Int,
        pixelWidthHeightRatio: Float = 1f
    )

    fun release()
}
