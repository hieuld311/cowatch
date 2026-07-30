package com.ivi.common.ui.player

import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import java.util.concurrent.atomic.AtomicLong

/**
 * A plain SurfaceView whose buffers are produced by the PID fanout renderer.
 * Compose remains in the Rear process, so titles and controls can stay above this surface.
 */
@Composable
public fun FanoutVideoSurface(
    onSurfaceAvailable: (generation: Long, surface: Surface, width: Int, height: Int) -> Unit,
    onSurfaceDestroyed: (generation: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val availableCallback = rememberUpdatedState(onSurfaceAvailable)
    val destroyedCallback = rememberUpdatedState(onSurfaceDestroyed)
    val token = androidx.compose.runtime.remember { SurfaceGenerationToken() }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            SurfaceView(context).apply {
                setZOrderOnTop(false)
                holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) {
                        token.generation = nextSurfaceGeneration.incrementAndGet()
                    }

                    override fun surfaceChanged(
                        holder: SurfaceHolder,
                        format: Int,
                        width: Int,
                        height: Int
                    ) {
                        availableCallback.value(
                            token.generation.takeIf { it != 0L }
                                ?: nextSurfaceGeneration.incrementAndGet().also {
                                    token.generation = it
                                },
                            holder.surface,
                            width.coerceAtLeast(1),
                            height.coerceAtLeast(1)
                        )
                    }

                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                        val generation = token.generation
                        if (generation != 0L) destroyedCallback.value(generation)
                        token.generation = 0L
                    }
                })
            }
        }
    )

    DisposableEffect(Unit) {
        onDispose {
            val generation = token.generation
            if (generation != 0L) destroyedCallback.value(generation)
            token.generation = 0L
        }
    }
}

private class SurfaceGenerationToken(var generation: Long = 0L)

private val nextSurfaceGeneration = AtomicLong(0L)
