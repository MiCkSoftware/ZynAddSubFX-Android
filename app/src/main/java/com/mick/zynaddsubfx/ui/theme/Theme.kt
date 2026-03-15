package com.mick.zynaddsubfx.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = TealPrimary,
    onPrimary = OnPrimary,
    primaryContainer = TealPrimaryContainer,
    onPrimaryContainer = OnSurface,
    secondary = TealSecondary,
    secondaryContainer = TealSecondaryContainer,
    tertiary = TealTertiary,
    background = AppBackground,
    onBackground = OnBackground,
    surface = AppSurface,
    onSurface = OnSurface,
    surfaceVariant = AppSurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    outline = AppOutline
)

@Composable
fun ZynAddSubFXTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = DarkColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
