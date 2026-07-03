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

    fun addOutputAsync(
        outputId: Int,
        surface: Surface,
        width: Int,
        height: Int,
        onResult: (Boolean) -> Unit
    ) {
        onResult(addOutput(outputId, surface, width, height))
    }

    fun removeOutput(outputId: Int)

    fun removeOutputAsync(outputId: Int) {
        removeOutput(outputId)
    }

    fun setVideoSize(
        width: Int,
        height: Int,
        pixelWidthHeightRatio: Float = 1f
    )

    fun release()
}
