package com.hieuld.cowatch.data.display.repository

import android.content.Context
import android.hardware.display.DisplayManager
import com.hieuld.cowatch.domain.display.DisplayInfo

class DisplayRepository(
    context: Context
) {

    private val displayManager: DisplayManager =
        context.getSystemService(DisplayManager::class.java)

    // Returns dynamic secondary-display candidates; display IDs are never hardcoded.
    fun getShareTargets(currentDisplayId: Int): List<DisplayInfo> {
        val presentationDisplays =
            displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)

        return presentationDisplays
            .distinctBy { it.displayId }
            .filter { it.displayId != currentDisplayId }
            .map {
                DisplayInfo(
                    displayId = it.displayId,
                    name = it.name ?: "Display ${it.displayId}",
                    flags = it.flags,
                    state = it.state
                )
            }
    }
}
