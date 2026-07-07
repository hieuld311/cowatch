package com.hieuld.cowatch.display.presentation

import android.app.Presentation
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.hieuld.cowatch.render.VideoRenderEngine
import kotlin.math.min
import kotlin.math.roundToInt

class SecondaryVideoPresentation(
    context: Context,
    display: Display,
    private val videoTitle: String,
    private val renderEngine: VideoRenderEngine,
    private val onBroadcastAccepted: (displayId: Int) -> Unit,
    private val onBroadcastDismissed: (displayId: Int) -> Unit,
    private val onSurfaceReady: (displayId: Int) -> Unit,
    private val onSurfaceDestroyed: (displayId: Int) -> Unit,
    private val onCloseRequested: (displayId: Int) -> Unit
) : Presentation(context, display) {

    private val displayId = display.displayId
    private val mainHandler = Handler(Looper.getMainLooper())
    private var surfaceGeneration = 0
    private var outputRegistered = false
    private var closeRequested = false
    private var requestResolved = false
    private var accepted = false
    private var latestSurfaceHolder: SurfaceHolder? = null
    private var latestSurfaceWidth = 0
    private var latestSurfaceHeight = 0
    private var requestPanel: View? = null
    private var countdownText: TextView? = null
    private var countdownSeconds = REQUEST_TIMEOUT_SECONDS

    private val countdownRunnable = object : Runnable {
        override fun run() {
            if (requestResolved) return

            countdownSeconds -= 1
            updateCountdownText()

            if (countdownSeconds <= 0) {
                acceptBroadcastRequest()
            } else {
                mainHandler.postDelayed(this, 1_000L)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Shared display video is full-screen; only close/release control is shown on top.
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
                    latestSurfaceHolder = holder
                    latestSurfaceWidth = width
                    latestSurfaceHeight = height
                    registerOutputIfAccepted(generation)
                }

                override fun surfaceDestroyed(holder: SurfaceHolder) {
                    Log.i(TAG, "Surface destroyed on display $displayId.")
                    latestSurfaceHolder = null
                    latestSurfaceWidth = 0
                    latestSurfaceHeight = 0
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
                            if (!accepted) {
                                dismissBroadcastRequest()
                            } else {
                                closeRequested = true
                                onCloseRequested(displayId)
                            }
                        }
                    },
                    FrameLayout.LayoutParams(
                        // 64dp hit target remains usable on rear/dashboard displays at automotive density.
                        64.dpToPx(),
                        64.dpToPx(),
                        Gravity.TOP or Gravity.END
                    ).apply {
                        // Match the front-player top-right close padding for consistent spatial placement.
                        topMargin = 12.dpToPx()
                        marginEnd = 12.dpToPx()
                    }
                )
                addView(
                    createRequestPanel(),
                    FrameLayout.LayoutParams(
                        344.dpToPx(),
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.CENTER
                    )
                )
            }
        )
        startRequestCountdown()
    }

    override fun onStop() {
        Log.i(TAG, "Presentation stopped on display $displayId.")
        mainHandler.removeCallbacks(countdownRunnable)
        unregisterOutput()
        super.onStop()
    }

    private fun nextSurfaceGeneration(): Int {
        surfaceGeneration += 1
        outputRegistered = false
        return surfaceGeneration
    }

    private fun registerOutputIfAccepted(generation: Int = surfaceGeneration) {
        if (!accepted) return

        val holder = latestSurfaceHolder ?: return
        if (latestSurfaceWidth <= 0 || latestSurfaceHeight <= 0 || !holder.surface.isValid) {
            return
        }

        renderEngine.addOutputAsync(
            outputId = displayId,
            surface = holder.surface,
            width = latestSurfaceWidth,
            height = latestSurfaceHeight
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

    private fun unregisterOutput() {
        surfaceGeneration += 1
        renderEngine.removeOutput(displayId)

        if (!outputRegistered) return
        outputRegistered = false
        closeRequested = false
        onSurfaceDestroyed(displayId)
    }

    private fun createRequestPanel(): View {
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(24.dpToPx(), 12.dpToPx(), 24.dpToPx(), 12.dpToPx())
            background = roundedBackground(0xFF152537.toInt(), 4.dpToPx().toFloat())
        }

        panel.addView(
            TextView(context).apply {
                text = "Accept video broadcast request?"
                setTextColor(Color.rgb(207, 219, 232))
                textSize = 13f
                typeface = Typeface.MONOSPACE
                gravity = Gravity.CENTER
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        panel.addView(
            TextView(context).apply {
                text = videoTitle
                setTextColor(Color.rgb(207, 219, 232))
                textSize = 12f
                typeface = Typeface.MONOSPACE
                gravity = Gravity.CENTER
                maxLines = 1
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 24.dpToPx()
            }
        )

        countdownText = TextView(context).apply {
            setTextColor(Color.rgb(207, 219, 232))
            textSize = 11f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
        }
        updateCountdownText()
        panel.addView(
            countdownText,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 4.dpToPx()
            }
        )

        panel.addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                addView(
                    requestButton("Dismiss").apply {
                        setOnClickListener { dismissBroadcastRequest() }
                    },
                    LinearLayout.LayoutParams(
                        78.dpToPx(),
                        34.dpToPx()
                    ).apply {
                        marginEnd = 8.dpToPx()
                    }
                )
                addView(
                    requestButton("Accept").apply {
                        setOnClickListener { acceptBroadcastRequest() }
                    },
                    LinearLayout.LayoutParams(
                        78.dpToPx(),
                        34.dpToPx()
                    )
                )
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 20.dpToPx()
            }
        )

        requestPanel = panel
        return panel
    }

    private fun requestButton(text: String): Button {
        return Button(context).apply {
            this.text = text
            setTextColor(Color.rgb(220, 232, 242))
            textSize = 11f
            typeface = Typeface.MONOSPACE
            isAllCaps = false
            minHeight = 0
            minWidth = 0
            setPadding(0, 0, 0, 0)
            background = roundedBackground(0xFF2A4666.toInt(), 3.dpToPx().toFloat())
        }
    }

    private fun startRequestCountdown() {
        countdownSeconds = REQUEST_TIMEOUT_SECONDS
        updateCountdownText()
        mainHandler.postDelayed(countdownRunnable, 1_000L)
    }

    private fun updateCountdownText() {
        countdownText?.text = "Accepting the video broadcast in $countdownSeconds seconds"
    }

    private fun acceptBroadcastRequest() {
        if (requestResolved) return

        requestResolved = true
        accepted = true
        mainHandler.removeCallbacks(countdownRunnable)
        requestPanel?.visibility = View.GONE
        Log.i(TAG, "Broadcast accepted on display $displayId.")
        onBroadcastAccepted(displayId)
        registerOutputIfAccepted(surfaceGeneration)
    }

    private fun dismissBroadcastRequest() {
        if (requestResolved) return

        requestResolved = true
        accepted = false
        mainHandler.removeCallbacks(countdownRunnable)
        Log.i(TAG, "Broadcast dismissed on display $displayId.")
        onBroadcastDismissed(displayId)
    }

    private fun roundedBackground(
        color: Int,
        radius: Float
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius
        }
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
        private const val REQUEST_TIMEOUT_SECONDS = 10
    }
}
