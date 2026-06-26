package com.hieuld.cowatch.display

import android.content.Context
import android.hardware.display.DisplayManager
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
            val display = displaysById[displayId] ?: return@forEach
            val presentation = SecondaryVideoPresentation(
                context = context,
                display = display,
                renderEngine = renderEngine,
                onSurfaceReady = onDisplayReady,
                onSurfaceDestroyed = onDisplayRemoved
            )

            try {
                presentation.show()
                presentations[displayId] = presentation
                shownDisplayIds += displayId
            } catch (_: RuntimeException) {
                presentation.dismiss()
            }
        }

        return shownDisplayIds
    }

    fun dismissAll() {
        presentations.values.toList().forEach { presentation ->
            presentation.dismiss()
        }
        presentations.clear()
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
}
