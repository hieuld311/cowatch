package com.hieuld.cowatch.display

import android.app.Presentation
import android.content.Context
import android.os.Bundle
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
                    val outputAdded = renderEngine.addOutput(
                        outputId = displayId,
                        surface = holder.surface,
                        width = width,
                        height = height
                    )

                    if (outputAdded) {
                        onSurfaceReady(displayId)
                    } else {
                        onSurfaceDestroyed(displayId)
                    }
                }

                override fun surfaceDestroyed(holder: SurfaceHolder) {
                    renderEngine.removeOutput(displayId)
                    onSurfaceDestroyed(displayId)
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
        renderEngine.removeOutput(displayId)
        onSurfaceDestroyed(displayId)
        super.onStop()
    }
}
