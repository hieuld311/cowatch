package com.hieuld.cowatch.display

import android.app.Presentation
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import android.widget.FrameLayout
import com.hieuld.cowatch.render.VideoRenderEngine

class SecondaryVideoPresentation(
    context: Context,
    display: Display,
    private val renderEngine: VideoRenderEngine,
    private val onSurfaceReady: (displayId: Int) -> Unit,
    private val onSurfaceDestroyed: (displayId: Int) -> Unit
) : Presentation(context, display) {

    private val displayId = display.displayId
    private val mainHandler = Handler(Looper.getMainLooper())
    private var surfaceGeneration = 0
    private var outputRegistered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val surfaceView = SurfaceView(context).apply {
            holder.addCallback(object : SurfaceHolder.Callback {
                override fun surfaceCreated(holder: SurfaceHolder) = Unit

                override fun surfaceChanged(
                    holder: SurfaceHolder,
                    format: Int,
                    width: Int,
                    height: Int
                ) {
                    val generation = nextSurfaceGeneration()
                    Log.d(TAG, "Surface changed on display $displayId: ${width}x$height.")
                    renderEngine.addOutputAsync(
                        outputId = displayId,
                        surface = holder.surface,
                        width = width,
                        height = height
                    ) { outputAdded ->
                        mainHandler.post {
                            if (generation != surfaceGeneration) return@post

                            if (outputAdded) {
                                outputRegistered = true
                                Log.i(TAG, "Surface ready on display $displayId.")
                                onSurfaceReady(displayId)
                            } else {
                                Log.w(TAG, "Surface rejected on display $displayId.")
                                onSurfaceDestroyed(displayId)
                            }
                        }
                    }
                }

                override fun surfaceDestroyed(holder: SurfaceHolder) {
                    Log.i(TAG, "Surface destroyed on display $displayId.")
                    unregisterOutput()
                }
            })
        }

        setContentView(
            FrameLayout(context).apply {
                setBackgroundColor(android.graphics.Color.BLACK)
                addView(
                    surfaceView,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
            }
        )
    }

    override fun onStop() {
        Log.i(TAG, "Presentation stopped on display $displayId.")
        unregisterOutput()
        super.onStop()
    }

    private fun nextSurfaceGeneration(): Int {
        surfaceGeneration += 1
        outputRegistered = false
        return surfaceGeneration
    }

    private fun unregisterOutput() {
        surfaceGeneration += 1
        renderEngine.removeOutputAsync(displayId)

        if (!outputRegistered) return
        outputRegistered = false
        onSurfaceDestroyed(displayId)
    }

    companion object {
        private const val TAG = "SecondaryPresentation"
    }
}
