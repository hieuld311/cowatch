package com.hieuld.cowatch.render

import android.view.Surface

interface VideoRenderEngine {
    // ExoPlayer renders into this single input surface; the engine fans frames out to registered outputs.
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

    companion object {
        // Fullscreen host and library PiP use separate IDs so SurfaceView teardown cannot remove the wrong output.
        const val HOST_OUTPUT_ID: Int = Int.MIN_VALUE
        const val LIBRARY_PIP_OUTPUT_ID: Int = Int.MIN_VALUE + 1
    }
}
