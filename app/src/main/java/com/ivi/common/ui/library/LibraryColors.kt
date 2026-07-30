package com.ivi.common.ui.library

import androidx.compose.ui.graphics.Color

// Dark blue-gray base keeps bright video thumbnails readable without using pure black across the library.
public val ExplorerSurfaceColor = Color(0xFF142231)
// Placeholder color appears before thumbnail decode, so it stays neutral and does not compete with media.
public val ExplorerPlaceholderColor = Color(0xFF70879B)
// A light scrim keeps the full-screen video image from competing with the foreground rail.
public val ExplorerBackgroundScrimColor = Color.Black.copy(alpha = 0.4f)
// Title color is tuned for dark media backgrounds and the placeholder surface.
public val ExplorerTitleColor = Color.White
public val VideoFocusCyan = Color(0xFF05F0EC)
