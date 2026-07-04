package com.hieuld.cowatch.display

import android.app.Presentation
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.hieuld.cowatch.render.VideoRenderEngine
import kotlin.math.min
import kotlin.math.roundToInt

class SecondaryVideoPresentation(
    context: Context,
    display: Display,
    private val renderEngine: VideoRenderEngine,
    private val onSurfaceReady: (displayId: Int) -> Unit,
    private val onSurfaceDestroyed: (displayId: Int) -> Unit,
    private val onCloseRequested: (displayId: Int) -> Unit
) : Presentation(context, display) {

    private val displayId = display.displayId
    private val mainHandler = Handler(Looper.getMainLooper())
    private var surfaceGeneration = 0
    private var outputRegistered = false
    private var closeRequested = false

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
                addView(
                    CloseDisplayButton(context).apply {
                        contentDescription = "Close shared display"
                        setOnClickListener {
                            Log.i(TAG, "Close requested on display $displayId.")
                            closeRequested = true
                            onCloseRequested(displayId)
                        }
                    },
                    FrameLayout.LayoutParams(
                        64.dpToPx(),
                        64.dpToPx(),
                        Gravity.TOP or Gravity.END
                    ).apply {
                        topMargin = 12.dpToPx()
                        marginEnd = 12.dpToPx()
                    }
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
        renderEngine.removeOutput(displayId)

        if (!outputRegistered && !closeRequested) return
        outputRegistered = false
        closeRequested = false
        onSurfaceDestroyed(displayId)
    }

    private fun Int.dpToPx(): Int {
        return (this * context.resources.displayMetrics.density).roundToInt()
    }

    private class CloseDisplayButton(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(209, 255, 255, 255)
            strokeCap = Paint.Cap.ROUND
            strokeWidth = 2.dpToPx()
        }

        init {
            isClickable = true
            isFocusable = true
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val glyphSize = min(width, height).toFloat().coerceAtMost(36.dpToPx())
            val left = (width - glyphSize) / 2f
            val top = (height - glyphSize) / 2f
            val right = left + glyphSize
            val bottom = top + glyphSize
            val inset = 4.dpToPx()

            canvas.drawLine(
                left + inset,
                top + inset,
                right - inset,
                bottom - inset,
                paint
            )
            canvas.drawLine(
                right - inset,
                top + inset,
                left + inset,
                bottom - inset,
                paint
            )
        }

        private fun Int.dpToPx(): Float {
            return this * resources.displayMetrics.density
        }
    }

    companion object {
        private const val TAG = "SecondaryPresentation"
    }
}
