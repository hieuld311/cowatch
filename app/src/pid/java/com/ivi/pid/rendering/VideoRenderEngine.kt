package com.ivi.pid.rendering

import android.view.Surface

interface VideoRenderEngine {
    val inputSurface: Surface

    fun addOutputAsync(
        outputId: Int,
        surfaceGeneration: Long? = null,
        surface: Surface,
        width: Int,
        height: Int,
        releaseSurfaceOnRemoval: Boolean = false,
        onResult: (Boolean) -> Unit = {}
    )

    /**
     * Removes an output only when its generation still matches. This prevents a queued callback
     * from a destroyed SurfaceView removing the replacement surface that reuses the same output id.
     */
    fun removeOutputAsync(outputId: Int, surfaceGeneration: Long? = null)

    /** Hides the prior decoder texture until the current media item has rendered its first frame. */
    fun waitForFirstFrameAsync()

    /** Makes the latest input texture visible after the player confirms the first frame. */
    fun allowFramesAsync()
    fun setVideoSize(width: Int, height: Int, pixelWidthHeightRatio: Float = 1f)
    fun release()

    companion object {
        const val HOST_OUTPUT_ID: Int = Int.MIN_VALUE
        const val LIBRARY_PIP_OUTPUT_ID: Int = Int.MIN_VALUE + 1
        const val REAR_LEFT_OUTPUT_ID: Int = 1
        const val REAR_RIGHT_OUTPUT_ID: Int = 2
    }
}
