package com.kalaiselvan.ride.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Pure pitch black AMOLED palette for Dark Mode.
 * Pure #000000 base shuts off OLED pixels entirely.
 * Deep ultra-dark surfaces (#0C0E12, #08090C, #10141A) with crisp specular rims
 * maintain clean depth and contrast without gray wash.
 */
val DarkBgDark = Color(0xFF000000)
val DarkBgCard = Color(0xFF0C0E12)
val DarkDialTrackBg = Color(0xFF08090C)
val DarkTextMain = Color(0xFFFFFFFF)
val DarkTextMuted = Color(0xFF8A939E)
val DarkTextSecondary = Color(0xFFAAB4C0)

// Light Palette (Calibrated for high-contrast, premium tactile metallic/slate neumorphic motorcycle cockpit)
// Soft slate-gray base surface enables crisp specular highlights (#FFFFFF) and deep soft drop shadows
val LightBgDark = Color(0xFFDFE4EA)
val LightBgCard = Color(0xFFEAEFF5)
val LightDialTrackBg = Color(0xFFD2D9E2)
val LightTextMain = Color(0xFF101418)
val LightTextMuted = Color(0xFF5A6472)
val LightTextSecondary = Color(0xFF38404A)

// Dynamic Color Holders
data class ThemeColors(
    val bgDark: Color,
    val bgSurface: Color,
    val bgCard: Color,
    val dialTrackBg: Color,
    val textMain: Color,
    val textMuted: Color,
    val textSecondary: Color,
    val podInnerBase: Color,
    val podTrackBorder: Color,
    val shadowDark: Color,
    val shadowLight: Color,
    val chipBg: Color,
    val chipBorder: Color,
    val accentGreen: Color,
    val isLight: Boolean
)

// Apple HIG Inspired Accent Greens
// Dark Mode: Apple System Green #30D158 (calm, crisp, high contrast on dark cockpit)
// Light Mode: Apple System Green #34C759 (refined, polished, non-acidic)
val DarkAccentGreen = Color(0xFF30D158)
val LightAccentGreen = Color(0xFF34C759)

val DarkThemeColors = ThemeColors(
    bgDark = DarkBgDark,
    bgSurface = DarkBgDark,
    bgCard = DarkBgCard,
    dialTrackBg = DarkDialTrackBg,
    textMain = DarkTextMain,
    textMuted = DarkTextMuted,
    textSecondary = DarkTextSecondary,
    podInnerBase = Color(0xFF050608),
    podTrackBorder = Color(0x22FFFFFF),
    shadowDark = Color(0x99000000),
    shadowLight = Color(0x18FFFFFF),
    chipBg = Color(0xFF10141A),
    chipBorder = Color(0xFF1E2632),
    accentGreen = DarkAccentGreen,
    isLight = false
)

val LightThemeColors = ThemeColors(
    bgDark = LightBgDark,
    bgSurface = LightBgDark,
    bgCard = LightBgCard,
    dialTrackBg = LightDialTrackBg,
    textMain = LightTextMain,
    textMuted = LightTextMuted,
    textSecondary = LightTextSecondary,
    podInnerBase = Color(0xFFD2D9E2),
    podTrackBorder = Color(0x28000000),
    shadowDark = Color(0x25000000),
    shadowLight = Color(0xF0FFFFFF),
    chipBg = Color(0xFFD0D8E2),
    chipBorder = Color(0xFFB4C0CE),
    accentGreen = LightAccentGreen,
    isLight = true
)

val LocalThemeColors = compositionLocalOf { DarkThemeColors }

// Dynamic Accessors for UI Composables (transparent drop-in for existing BgDark, TextMain, etc.)
val BgDark: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalThemeColors.current.bgDark

val BgSurface: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalThemeColors.current.bgSurface

val BgCard: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalThemeColors.current.bgCard

val DialTrackBg: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalThemeColors.current.dialTrackBg

val TextMain: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalThemeColors.current.textMain

val TextMuted: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalThemeColors.current.textMuted

val TextSecondary: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalThemeColors.current.textSecondary

val ChipBg: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalThemeColors.current.chipBg

val ChipBorder: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalThemeColors.current.chipBorder

// Universal RPM & Accent Colors (Apple HIG System Green: #30D158)
val AccentGreen = Color(0xFF30D158)
val RpmGreen = AccentGreen
val NeonGreen = AccentGreen
val RpmYellow = Color(0xFFFFCC00)     // #ffcc00 - vibrant tach yellow
val RpmOrange = Color(0xFFFF8800)     // #ff8800 - vibrant tach orange
val RpmRed = Color(0xFFFF003C)        // #ff003c - glowing sport crimson red

val BluetoothBlue = Color(0xFF0088FF) // #0088ff

val NeumorphicLight = Color(0x0DFFFFFF)
val NeumorphicDark = Color(0x66000000)

val CardGradStart = Color(0xFF22262B)
val CardGradEnd = Color(0xFF16181B)
