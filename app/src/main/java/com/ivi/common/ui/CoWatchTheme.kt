package com.ivi.common.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/*
 * Archived Material palette. CoWatch UI colors are now owned exclusively by
 * DarkCoWatchColors and LightCoWatchColors below; retain this mapping as design reference only.
private val DarkMaterialColors = darkColorScheme(
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

private val LightMaterialColors = lightColorScheme(
    primary = Color(0xFF007A4D),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF79F8BE),
    onPrimaryContainer = Color(0xFF002113),
    secondary = Color(0xFF476354),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC9E8D5),
    onSecondaryContainer = Color(0xFF042014),
    background = Color(0xFFF7FAFC),
    onBackground = Color(0xFF17212B),
    surface = Color(0xFFFBFCFE),
    onSurface = Color(0xFF17212B),
    surfaceVariant = Color(0xFFDEE6EE),
    onSurfaceVariant = Color(0xFF3F4A55),
    outline = Color(0xFF6F7883),
    error = Color(0xFFBA1A1A),
    onError = Color.White
)
*/

/** App-specific colors that have no direct Material 3 semantic equivalent. */
@Immutable
public data class CoWatchColorScheme(
    val librarySurface: Color,
    val contentPrimary: Color,
    val libraryPlaceholder: Color,
    val libraryTitle: Color,
    val focusedVideoOutline: Color,
    val playerCanvas: Color,
    val playerScrim: Color,
    val seekPreviewOutline: Color,
    val dialogSurface: Color,
    val dialogDivider: Color,
    val dialogAccent: Color,
    val dialogNormalText: Color,
    val dialogDisabledText: Color,
    val dialogAction: Color,
    val dialogActionContent: Color,
    val dialogDisabledAction: Color,
    val broadcastAction: Color,
    val broadcastNotificationSurface: Color,
    val broadcastNotificationText: Color
)

private val DarkCoWatchColors = CoWatchColorScheme(
    librarySurface = Color(0xFF142231),
    contentPrimary = Color(0xFFE6EDF3),
    libraryPlaceholder = Color(0xFF70879B),
    libraryTitle = Color.White,
    focusedVideoOutline = Color(0xFF05F0EC),
    playerCanvas = Color.Black,
    playerScrim = Color.Black.copy(alpha = 0.68f),
    seekPreviewOutline = Color.White.copy(alpha = 0.7f),
    dialogSurface = Color(0xFF25263B),
    dialogDivider = Color.White.copy(alpha = 0.08f),
    dialogAccent = Color(0xFF00F9EC),
    dialogNormalText = Color(0xFFC7CADA),
    dialogDisabledText = Color(0xFF838497),
    dialogAction = Color(0xFF62636F),
    dialogActionContent = Color.White,
    dialogDisabledAction = Color(0xFF4A4B56),
    broadcastAction = Color(0xFF00F9EC),
    broadcastNotificationSurface = Color(0xFF25364A),
    broadcastNotificationText = Color(0xFFDCE8F2)
)

private val LightCoWatchColors = CoWatchColorScheme(
    librarySurface = Color(0xFFEAF1F6),
    contentPrimary = Color(0xFF17212B),
    libraryPlaceholder = Color(0xFFB7C8D6),
    libraryTitle = Color(0xFF17212B),
    focusedVideoOutline = Color(0xFF00A86B),
    playerCanvas = Color.Black,
    playerScrim = Color.Black.copy(alpha = 0.68f),
    seekPreviewOutline = Color.White.copy(alpha = 0.7f),
    dialogSurface = Color(0xFFFBFCFE),
    dialogDivider = Color(0xFF17212B).copy(alpha = 0.10f),
    dialogAccent = Color(0xFF00A86B),
    dialogNormalText = Color(0xFF3F4A55),
    dialogDisabledText = Color(0xFF77808A),
    dialogAction = Color(0xFF64748B),
    dialogActionContent = Color.White,
    dialogDisabledAction = Color(0xFFCBD5E1),
    broadcastAction = Color(0xFF00A86B),
    broadcastNotificationSurface = Color(0xFFE0F2E9),
    broadcastNotificationText = Color(0xFF15382A)
)

private val LocalCoWatchColorScheme = staticCompositionLocalOf { DarkCoWatchColors }

/** Access the CoWatch-owned app palette from a composable. */
public val MaterialTheme.coWatchColorScheme: CoWatchColorScheme
    @Composable get() = LocalCoWatchColorScheme.current

/**
 * Central typography factory. Supply a FontFamily from an external font library here when it is
 * introduced; individual screens should only consume MaterialTheme.typography styles.
 */
public fun coWatchTypography(fontFamily: FontFamily = FontFamily.Monospace): Typography {
    val base = Typography()
    fun themed(style: TextStyle, weight: FontWeight? = null): TextStyle = style.copy(
        fontFamily = fontFamily,
        fontWeight = weight ?: style.fontWeight
    )
    return Typography(
        displayLarge = themed(base.displayLarge),
        displayMedium = themed(base.displayMedium),
        displaySmall = themed(base.displaySmall),
        headlineLarge = themed(base.headlineLarge),
        headlineMedium = themed(base.headlineMedium),
        headlineSmall = themed(base.headlineSmall, FontWeight.Bold),
        titleLarge = themed(base.titleLarge),
        titleMedium = themed(base.titleMedium),
        titleSmall = themed(base.titleSmall),
        bodyLarge = themed(base.bodyLarge),
        bodyMedium = themed(base.bodyMedium),
        bodySmall = themed(base.bodySmall, FontWeight.Bold),
        labelLarge = themed(base.labelLarge),
        labelMedium = themed(base.labelMedium),
        labelSmall = themed(base.labelSmall)
    )
}

@Composable
public fun CoWatchTheme(
    fontFamily: FontFamily = FontFamily.Monospace,
    content: @Composable () -> Unit
) {
    val darkTheme = androidx.compose.foundation.isSystemInDarkTheme()
    // MaterialTheme requires a ColorScheme; CoWatch UI does not read this default palette.
    val materialColors = if (darkTheme) darkColorScheme() else lightColorScheme()
    val appColors = if (darkTheme) DarkCoWatchColors else LightCoWatchColors
    val typography = remember(fontFamily) { coWatchTypography(fontFamily) }

    CompositionLocalProvider(LocalCoWatchColorScheme provides appColors) {
        MaterialTheme(
            colorScheme = materialColors,
            typography = typography,
            content = content
        )
    }
}
