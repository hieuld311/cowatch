package com.hieuld.cowatch.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CoWatchColorScheme = darkColorScheme(
    primary = Color(0xFF58A6FF),
    onPrimary = Color(0xFF06192E),
    primaryContainer = Color(0xFF0D419D),
    onPrimaryContainer = Color(0xFFD7E9FF),
    secondary = Color(0xFF7EE787),
    onSecondary = Color(0xFF06210B),
    secondaryContainer = Color(0xFF1F6F3F),
    onSecondaryContainer = Color(0xFFD9FCE0),
    background = Color(0xFF05070A),
    onBackground = Color(0xFFE6EDF3),
    surface = Color(0xFF0D1117),
    onSurface = Color(0xFFE6EDF3),
    surfaceVariant = Color(0xFF161B22),
    onSurfaceVariant = Color(0xFFB7C0CC),
    outline = Color(0xFF30363D),
    error = Color(0xFFFF7B72),
    onError = Color(0xFF330A06)
)

@Composable
fun CoWatchTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CoWatchColorScheme,
        typography = Typography(),
        content = content
    )
}
