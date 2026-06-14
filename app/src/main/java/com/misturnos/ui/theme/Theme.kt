package com.misturnos.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Green,
    onPrimary = Surface,
    primaryContainer = GreenContainer,
    onPrimaryContainer = OnGreenContainer,
    secondary = GreenDark,
    onSecondary = Surface,
    background = Background,
    onBackground = Ink,
    surface = Surface,
    onSurface = Ink,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = InkMuted,
    outline = Outline,
)

private val DarkColors = darkColorScheme(
    primary = Green,
    onPrimary = Color(0xFF06150E),
    primaryContainer = OnGreenContainer,
    onPrimaryContainer = GreenContainer,
    secondary = Green,
    background = Color(0xFF121712),
    onBackground = Color(0xFFE3E8E4),
    surface = Color(0xFF1B211D),
    onSurface = Color(0xFFE3E8E4),
    surfaceVariant = Color(0xFF2A322C),
    onSurfaceVariant = Color(0xFFB5BFB8),
)

@Composable
fun MisTurnosTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
