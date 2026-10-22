package com.dove.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val CanvasDark = Color(0xFF010409)
val SurfaceDark = Color(0xFF0D1117)
val SurfaceSecondaryDark = Color(0xFF161B22)
val BorderDefaultDark = Color(0xFF30363D)
val TextPrimaryDark = Color(0xFFC9D1D9)
val TextSecondaryDark = Color(0xFF8B949E)
val AccentBlue = Color(0xFF58A6FF)
val AccentGreen = Color(0xFF238636)
val AccentRed = Color(0xFFDA3633)

val CanvasLight = Color(0xFFFFFFFF)
val SurfaceLight = Color(0xFFF6F8FA)
val SurfaceSecondaryLight = Color(0xFFEAEEF2)
val BorderDefaultLight = Color(0xFFD0D7DE)
val TextPrimaryLight = Color(0xFF24292F)
val TextSecondaryLight = Color(0xFF57606A)

private val DarkColorScheme = darkColorScheme(
    background = CanvasDark,
    surface = SurfaceDark,
    surfaceVariant = SurfaceSecondaryDark,
    outline = BorderDefaultDark,
    primary = AccentBlue,
    secondary = AccentGreen,
    error = AccentRed,
    onBackground = TextPrimaryDark,
    onSurface = TextPrimaryDark,
    onSurfaceVariant = TextSecondaryDark
)

private val LightColorScheme = lightColorScheme(
    background = CanvasLight,
    surface = SurfaceLight,
    surfaceVariant = SurfaceSecondaryLight,
    outline = BorderDefaultLight,
    primary = AccentBlue,
    secondary = AccentGreen,
    error = AccentRed,
    onBackground = TextPrimaryLight,
    onSurface = TextPrimaryLight,
    onSurfaceVariant = TextSecondaryLight
)

@Composable
fun DoveTheme(
    darkTheme: Boolean = true, // Default to true matte black
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
