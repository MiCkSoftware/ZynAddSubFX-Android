package com.mick.zynaddsubfx.ui.theme

import androidx.compose.ui.graphics.Color

val TealPrimary = Color(0xFF33C8C8)
val TealPrimaryContainer = Color(0xFF0A2D33)
val TealSecondary = Color(0xFF7AC1C8)
val TealSecondaryContainer = Color(0xFF1D2B30)
val TealTertiary = Color(0xFF5BE4D7)

val AppBackground = Color(0xFF061116)
val AppSurface = Color(0xFF0E1C22)
val AppSurfaceVariant = Color(0xFF152930)
val AppOutline = Color(0xFF2C4D56)

val OnPrimary = Color(0xFF001F24)
val OnBackground = Color(0xFFD8F6F6)
val OnSurface = Color(0xFFD8F6F6)
val OnSurfaceVariant = Color(0xFF9AB9BF)

const val LedDefaultHue = 180f
const val LedFxHue = 42f
const val LedStereoHue = 32f

data class LedColors(
    val surface: Color,
    val border: Color,
    val glow: Color,
    val content: Color,
)

fun ledColors(enabled: Boolean, hue: Float = LedDefaultHue): LedColors = if (enabled) {
    LedColors(
        surface = Color.hsv(hue, .76f, .27f),
        border = Color.hsv(hue, .72f, .82f),
        glow = Color.hsv(hue, .68f, .92f),
        content = Color.hsv(hue, .10f, 1f),
    )
} else {
    LedColors(
        surface = Color.hsv(hue, .12f, .20f),
        border = Color.hsv(hue, .18f, .38f),
        glow = Color.hsv(hue, .14f, .36f),
        content = Color.hsv(hue, .10f, .74f),
    )
}
