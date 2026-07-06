package com.hieuld.cowatch.ui.library

import androidx.compose.ui.graphics.Color

// Dark blue-gray base keeps bright video thumbnails readable without using pure black across the library.
internal val ExplorerSurfaceColor = Color(0xFF142231)
// Placeholder color appears before thumbnail decode, so it stays neutral and does not compete with media.
internal val ExplorerPlaceholderColor = Color(0xFF70879B)
// Alpha rail background separates cards from the image while preserving focused-video context.
internal val RailBackgroundColor = Color(0xAA2A4154)
// Title color is tuned for dark media backgrounds and the placeholder surface.
internal val ExplorerTitleColor = Color(0xFFD6E3EF)
