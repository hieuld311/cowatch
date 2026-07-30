package com.ivi.pid.rendering

import android.view.Surface

interface VideoRenderEngine {
    val inputSurface: Surface

    fun addOutputAsync(
        outputId: Int,
        surface: Surface,
        width: Int,
        height: Int,
        releaseSurfaceOnRemoval: Boolean = false,
        onResult: (Boolean) -> Unit = {}
    )

    fun removeOutputAsync(outputId: Int)
    fun setVideoSize(width: Int, height: Int, pixelWidthHeightRatio: Float = 1f)
    fun release()

    companion object {
        const val HOST_OUTPUT_ID: Int = Int.MIN_VALUE
        const val LIBRARY_PIP_OUTPUT_ID: Int = Int.MIN_VALUE + 1
        const val REAR_LEFT_OUTPUT_ID: Int = 1
        const val REAR_RIGHT_OUTPUT_ID: Int = 2
    }
}
