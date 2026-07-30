package com.ivi.pid.sharing

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RearDisplayResolver @Inject constructor(
    @ApplicationContext context: Context
) {
    private val displayManager = context.getSystemService(DisplayManager::class.java)

    fun resolveAvailableDisplayId(role: String): Int? {
        val displayId = RearDisplayConfig.displayId(role) ?: return null
        val display = displayManager.getDisplay(displayId) ?: return null
        if (!display.isValid || !display.isOnForSharing()) return null
        return display.displayId
    }

    private fun Display.isOnForSharing(): Boolean = state == Display.STATE_ON
}
