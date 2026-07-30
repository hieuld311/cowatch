package com.ivi.pid.rendering

import android.view.Surface
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import com.ivi.common.ipc.ScreenRole
import com.ivi.common.playback.Media3PlaybackController
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PidRenderFanout @Inject constructor(
    private val playbackController: Media3PlaybackController
) {
    @Volatile
    private var rearOutputLostListener: ((role: String, reason: String) -> Unit)? = null
    private val localOutputGenerations = mutableMapOf<Int, Long>()
    private val engine: VideoRenderEngine = FrameFanoutRenderEngine { outputId, reason ->
        roleForOutputId(outputId)?.let { role -> rearOutputLostListener?.invoke(role, reason) }
    }

    init {
        playbackController.exoPlayer.addListener(object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                engine.setVideoSize(
                    videoSize.width,
                    videoSize.height,
                    videoSize.pixelWidthHeightRatio
                )
            }
        })
        ensureAttached()
    }

    fun addOutput(
        outputId: Int,
        surfaceGeneration: Long,
        surface: Surface,
        width: Int,
        height: Int,
        onResult: (Boolean) -> Unit = {}
    ) {
        ensureAttached()
        localOutputGenerations[outputId] = surfaceGeneration
        engine.addOutputAsync(
            outputId = outputId,
            surface = surface,
            width = width,
            height = height,
            onResult = onResult
        )
    }

    fun removeOutput(outputId: Int, surfaceGeneration: Long? = null) {
        if (surfaceGeneration != null && localOutputGenerations[outputId] != surfaceGeneration) return
        localOutputGenerations.remove(outputId)
        engine.removeOutputAsync(outputId)
    }

    fun addRearOutput(
        role: String,
        surface: Surface,
        width: Int,
        height: Int,
        onResult: (Boolean) -> Unit
    ) {
        ensureAttached()
        engine.addOutputAsync(
            outputId = outputId(role),
            surface = surface,
            width = width,
            height = height,
            releaseSurfaceOnRemoval = true,
            onResult = onResult
        )
    }

    fun removeRearOutput(role: String) = engine.removeOutputAsync(outputId(role))

    fun setRearOutputLostListener(listener: (role: String, reason: String) -> Unit) {
        rearOutputLostListener = listener
    }

    private fun ensureAttached() {
        playbackController.attachExternalVideoSurface(engine.inputSurface)
    }

    private fun outputId(role: String): Int = when (role) {
        ScreenRole.REAR_LEFT -> VideoRenderEngine.REAR_LEFT_OUTPUT_ID
        ScreenRole.REAR_RIGHT -> VideoRenderEngine.REAR_RIGHT_OUTPUT_ID
        else -> error("Unsupported render role: $role")
    }

    private fun roleForOutputId(outputId: Int): String? = when (outputId) {
        VideoRenderEngine.REAR_LEFT_OUTPUT_ID -> ScreenRole.REAR_LEFT
        VideoRenderEngine.REAR_RIGHT_OUTPUT_ID -> ScreenRole.REAR_RIGHT
        else -> null
    }
}
