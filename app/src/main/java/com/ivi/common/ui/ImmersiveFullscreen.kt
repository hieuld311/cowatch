package com.ivi.common.ui

import android.graphics.Color
import android.view.Window
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

fun Window.enterImmersiveFullscreen() {
    WindowCompat.setDecorFitsSystemWindows(this, false)
    WindowInsetsControllerCompat(this, decorView).apply {
        hide(WindowInsetsCompat.Type.systemBars())
        systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}

/** Keeps the status bar visible while allowing a Library background to draw behind it. */
@Suppress("DEPRECATION")
fun Window.showTransparentLibraryStatusBar() {
    WindowCompat.setDecorFitsSystemWindows(this, false)
    clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
    addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
    statusBarColor = Color.TRANSPARENT
    isStatusBarContrastEnforced = false
    WindowInsetsControllerCompat(this, decorView).apply {
        show(WindowInsetsCompat.Type.statusBars())
        isAppearanceLightStatusBars = false
    }
}
