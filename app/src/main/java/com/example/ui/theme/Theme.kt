package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.data.model.ReadingTheme

@Immutable
data class FolioPalette(
    val theme: ReadingTheme,
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val ink: Color,
    val inkMuted: Color,
    val accent: Color,
    val accentShadow: Color,
    val accentSoft: Color,
    val border: Color,
    val borderShadow: Color,
    val onAccent: Color = Color.White
)

fun paletteFor(theme: ReadingTheme): FolioPalette = when (theme) {
    ReadingTheme.LIGHT -> FolioPalette(
        theme = theme,
        isDark = false,
        background = LightBg,
        surface = LightSurface,
        surfaceAlt = LightSurfaceAlt,
        ink = LightInk,
        inkMuted = LightInkMuted,
        accent = CobaltPrimary,
        accentShadow = CobaltShadow,
        accentSoft = CobaltSoftLight,
        border = LightBorder,
        borderShadow = LightBorderShadow
    )
    ReadingTheme.SEPIA -> FolioPalette(
        theme = theme,
        isDark = false,
        background = SepiaBg,
        surface = SepiaSurface,
        surfaceAlt = SepiaSurfaceAlt,
        ink = SepiaInk,
        inkMuted = SepiaInkMuted,
        accent = SepiaAccent,
        accentShadow = SepiaAccentShadow,
        accentSoft = Color(0xFFE7DCCB),
        border = SepiaBorder,
        borderShadow = SepiaBorderShadow
    )
    ReadingTheme.SAGE -> FolioPalette(
        theme = theme,
        isDark = false,
        background = SageBg,
        surface = SageSurface,
        surfaceAlt = SageSurfaceAlt,
        ink = SageInk,
        inkMuted = SageInkMuted,
        accent = SageAccent,
        accentShadow = SageAccentShadow,
        accentSoft = Color(0xFFD9EBE1),
        border = SageBorder,
        borderShadow = SageBorderShadow
    )
    ReadingTheme.LAVENDER -> FolioPalette(
        theme = theme,
        isDark = false,
        background = LavenderBg,
        surface = LavenderSurface,
        surfaceAlt = LavenderSurfaceAlt,
        ink = LavenderInk,
        inkMuted = LavenderInkMuted,
        accent = LavenderAccent,
        accentShadow = LavenderAccentShadow,
        accentSoft = Color(0xFFE5DDF8),
        border = LavenderBorder,
        borderShadow = LavenderBorderShadow
    )
    ReadingTheme.PEACH -> FolioPalette(
        theme = theme,
        isDark = false,
        background = PeachBg,
        surface = PeachSurface,
        surfaceAlt = PeachSurfaceAlt,
        ink = PeachInk,
        inkMuted = PeachInkMuted,
        accent = PeachAccent,
        accentShadow = PeachAccentShadow,
        accentSoft = Color(0xFFF8E1D5),
        border = PeachBorder,
        borderShadow = PeachBorderShadow
    )
    ReadingTheme.SKY -> FolioPalette(
        theme = theme,
        isDark = false,
        background = SkyBg,
        surface = SkySurface,
        surfaceAlt = SkySurfaceAlt,
        ink = SkyInk,
        inkMuted = SkyInkMuted,
        accent = SkyAccent,
        accentShadow = SkyAccentShadow,
        accentSoft = Color(0xFFD9EBFA),
        border = SkyBorder,
        borderShadow = SkyBorderShadow
    )
    ReadingTheme.ROSE -> FolioPalette(
        theme = theme,
        isDark = false,
        background = RoseBg,
        surface = RoseSurface,
        surfaceAlt = RoseSurfaceAlt,
        ink = RoseInk,
        inkMuted = RoseInkMuted,
        accent = RoseAccent,
        accentShadow = RoseAccentShadow,
        accentSoft = Color(0xFFF7DCE6),
        border = RoseBorder,
        borderShadow = RoseBorderShadow
    )
    ReadingTheme.LEMON -> FolioPalette(
        theme = theme,
        isDark = false,
        background = LemonBg,
        surface = LemonSurface,
        surfaceAlt = LemonSurfaceAlt,
        ink = LemonInk,
        inkMuted = LemonInkMuted,
        accent = LemonAccent,
        accentShadow = LemonAccentShadow,
        accentSoft = Color(0xFFF5ECC8),
        border = LemonBorder,
        borderShadow = LemonBorderShadow
    )
    ReadingTheme.NAVY_MID -> FolioPalette(
        theme = theme,
        isDark = true,
        background = NavyMidBg,
        surface = NavyMidSurface,
        surfaceAlt = NavyMidSurfaceAlt,
        ink = NavyMidInk,
        inkMuted = NavyMidInkMuted,
        accent = NavyMidAccent,
        accentShadow = NavyMidAccentShadow,
        accentSoft = Color(0xFF233B66),
        border = NavyMidBorder,
        borderShadow = NavyMidBorderShadow
    )
    ReadingTheme.MIDNIGHT -> FolioPalette(
        theme = theme,
        isDark = true,
        background = MidnightBg,
        surface = MidnightSurface,
        surfaceAlt = MidnightSurfaceAlt,
        ink = MidnightInk,
        inkMuted = MidnightInkMuted,
        accent = MidnightAccent,
        accentShadow = MidnightAccentShadow,
        accentSoft = Color(0xFF1A2B4D),
        border = MidnightBorder,
        borderShadow = MidnightBorderShadow
    )
    ReadingTheme.DARK -> FolioPalette(
        theme = theme,
        isDark = true,
        background = DarkBg,
        surface = DarkSurface,
        surfaceAlt = DarkSurfaceAlt,
        ink = DarkInk,
        inkMuted = DarkInkMuted,
        accent = CobaltBright,
        accentShadow = CobaltShadow,
        accentSoft = CobaltSoftDark,
        border = DarkBorder,
        borderShadow = DarkBorderShadow
    )
    ReadingTheme.AMOLED -> FolioPalette(
        theme = theme,
        isDark = true,
        background = AmoledBg,
        surface = AmoledSurface,
        surfaceAlt = AmoledSurfaceAlt,
        ink = AmoledInk,
        inkMuted = AmoledInkMuted,
        accent = CobaltBright,
        accentShadow = CobaltShadow,
        accentSoft = Color(0xFF102042),
        border = AmoledBorder,
        borderShadow = AmoledBorderShadow
    )
}

val LocalFolioPalette = staticCompositionLocalOf { paletteFor(ReadingTheme.LIGHT) }

val FolioShapes = Shapes(
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(34.dp)
)

@Composable
fun FolioTheme(
    readingTheme: ReadingTheme = ReadingTheme.LIGHT,
    useSystemFont: Boolean = false,
    content: @Composable () -> Unit
) {
    val palette = paletteFor(readingTheme)
    val typography = remember(useSystemFont) { buildFolioTypography(useSystemFont) }
    val colorScheme = if (palette.isDark) {
        darkColorScheme(
            primary = palette.accent,
            onPrimary = palette.onAccent,
            primaryContainer = palette.accentSoft,
            onPrimaryContainer = palette.ink,
            secondary = EmeraldAccent,
            onSecondary = Color.White,
            background = palette.background,
            onBackground = palette.ink,
            surface = palette.surface,
            onSurface = palette.ink,
            surfaceVariant = palette.surfaceAlt,
            onSurfaceVariant = palette.inkMuted,
            outline = palette.border,
            error = CoralAccent
        )
    } else {
        lightColorScheme(
            primary = palette.accent,
            onPrimary = palette.onAccent,
            primaryContainer = palette.accentSoft,
            onPrimaryContainer = palette.ink,
            secondary = EmeraldAccent,
            onSecondary = Color.White,
            background = palette.background,
            onBackground = palette.ink,
            surface = palette.surface,
            onSurface = palette.ink,
            surfaceVariant = palette.surfaceAlt,
            onSurfaceVariant = palette.inkMuted,
            outline = palette.border,
            error = CoralAccent
        )
    }

    CompositionLocalProvider(LocalFolioPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = FolioShapes,
            content = content
        )
    }
}
