package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val EvDarkColorScheme = darkColorScheme(
    primary = EvMagenta,
    onPrimary = EvTextWhite,
    primaryContainer = EvDeepMagenta,
    onPrimaryContainer = EvMagentaLight,
    secondary = EvPurple,
    onSecondary = EvTextWhite,
    tertiary = EvMagentaSoft,
    background = EvBg,
    onBackground = EvTextWhite,
    surface = EvSurface1,
    onSurface = EvTextWhite,
    surfaceVariant = EvSurface2,
    onSurfaceVariant = EvTextMuted,
    outline = EvBorder,
    outlineVariant = EvBorderStrong
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = EvDarkColorScheme,
        typography = Typography,
        content = content
    )
}
