package com.hieuld.cowatch.display

import android.content.Context
import android.hardware.display.DisplayManager
import android.util.Log
import android.view.Display
import com.hieuld.cowatch.render.VideoRenderEngine

class PresentationDisplayManager(
    private val context: Context,
    private val renderEngine: VideoRenderEngine,
    private val onDisplayReady: (displayId: Int) -> Unit,
    private val onDisplayRemoved: (displayId: Int) -> Unit
) {

    private val displayManager: DisplayManager =
        context.getSystemService(DisplayManager::class.java)

    private val presentations = mutableMapOf<Int, SecondaryVideoPresentation>()

    fun show(displayIds: Set<Int>): Set<Int> {
        val displaysById = availableDisplays().associateBy { it.displayId }
        val shownDisplayIds = mutableSetOf<Int>()

        displayIds.forEach { displayId ->
            val display = displaysById[displayId] ?: run {
                Log.w(TAG, "Requested display $displayId is not available.")
                return@forEach
            }
            val presentation = SecondaryVideoPresentation(
                context = context,
                display = display,
                renderEngine = renderEngine,
                onSurfaceReady = onDisplayReady,
                onSurfaceDestroyed = ::handlePresentationSurfaceDestroyed,
                onCloseRequested = ::dismiss
            )

            try {
                presentation.show()
                presentations[displayId] = presentation
                shownDisplayIds += displayId
                Log.i(TAG, "Presentation shown on display $displayId.")
            } catch (_: RuntimeException) {
                Log.e(TAG, "Failed to show presentation on display $displayId.")
                presentation.dismiss()
            }
        }

        return shownDisplayIds
    }

    fun dismissAll() {
        Log.i(TAG, "Dismissing ${presentations.size} presentation(s).")
        presentations.values.toList().forEach { presentation ->
            presentation.dismiss()
        }
        presentations.clear()
    }

    private fun dismiss(displayId: Int) {
        val presentation = presentations.remove(displayId) ?: return
        Log.i(TAG, "Dismissing presentation on display $displayId.")
        presentation.dismiss()
    }

    private fun handlePresentationSurfaceDestroyed(displayId: Int) {
        presentations.remove(displayId)
        onDisplayRemoved(displayId)
    }

    private fun availableDisplays(): List<Display> {
        return displayManager.displays
            .plus(
                displayManager
                    .getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
                    .toList()
            )
            .distinctBy { it.displayId }
    }

    companion object {
        private const val TAG = "PresentationDisplay"
    }
}
