package com.io.ab.music.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Extended theme modes — AMOLED dark variants + Light pastel variants + Midnight
enum class ThemeMode {
    SYSTEM, LIGHT, DARK,
    // Dark AMOLED variants
    AMOLED, AMOLED_CYAN, AMOLED_PINK, AMOLED_GOLD, AMOLED_GREEN, AMOLED_ORANGE,
    // New dark: Midnight Navy, Violet, Turquoise
    AMOLED_MIDNIGHT, AMOLED_VIOLET, AMOLED_TURQUOISE,
    // Pure Black & White AMOLED
    AMOLED_BW,
    // Neon Depth — Purple → Pink gradient theme
    AMOLED_NEON_DEPTH,
    // Light pastel variants
    LIGHT_LAVENDER, LIGHT_MINT, LIGHT_CORAL, LIGHT_SUNRISE, LIGHT_OCEAN
}

/** CompositionLocal so any Composable can read the active ThemeMode for neumorphism. */
val LocalThemeMode = compositionLocalOf { ThemeMode.SYSTEM }

/** CompositionLocal — true when the user has enabled a custom manual wallpaper.
 *  Screens/bars read this to drop their opaque background and let the wallpaper
 *  show through (tab bar, scaffolds, status/nav bar already transparent). */
val LocalWallpaperActive = compositionLocalOf { false }

// ── Dark Color Scheme ──────────────────────────────────────────────────────────
private val DarkColorScheme = darkColorScheme(
    primary                = Purple80,
    onPrimary              = Purple10,
    primaryContainer       = Purple30,
    onPrimaryContainer     = Purple90,
    secondary              = NeonBlue80,
    onSecondary            = NeonBlue10,
    secondaryContainer     = NeonBlue20,
    onSecondaryContainer   = NeonBlue90,
    tertiary               = Teal80,
    onTertiary             = Teal10,
    tertiaryContainer      = Teal40,
    onTertiaryContainer    = Teal90,
    background             = DarkBackground,
    onBackground           = Color(0xFFE8E3EF),
    surface                = DarkSurface,
    onSurface              = Color(0xFFE8E3EF),
    surfaceVariant         = DarkSurfaceVariant,
    onSurfaceVariant       = Color(0xFFCAC4D4),
    surfaceTint            = Purple80,
    outline                = Color(0xFF958F9E),
    outlineVariant         = Color(0xFF4A4458),
    inverseSurface         = Color(0xFFE8E3EF),
    inverseOnSurface       = Color(0xFF1C1B22),
    inversePrimary         = Purple40,
    error                  = Error,
    onError                = Rose10,
    errorContainer         = Color(0xFF93000A),
    onErrorContainer       = Color(0xFFFFDAD6),
    scrim                  = Color(0xFF000000)
)

// ── Light Color Scheme (Neumorphic base) ─────────────────────────────────────
private val LightColorScheme = lightColorScheme(
    primary                = Purple40,
    onPrimary              = Color.White,
    primaryContainer       = Purple90,
    onPrimaryContainer     = Purple10,
    secondary              = NeonBlue40,
    onSecondary            = Color.White,
    secondaryContainer     = NeonBlue90,
    onSecondaryContainer   = NeonBlue10,
    tertiary               = Teal40,
    onTertiary             = Color.White,
    tertiaryContainer      = Teal90,
    onTertiaryContainer    = Teal10,
    background             = LightBackground,       // 0xFFEFEFF4 — neumorphic grey
    onBackground           = Color(0xFF1C1A22),
    surface                = LightSurface,          // 0xFFE8E8EC — neumorphic surface
    onSurface              = Color(0xFF1C1A22),
    surfaceVariant         = LightSurfaceVar,
    onSurfaceVariant       = Color(0xFF4A4458),
    outline                = Color(0xFF7B7489),
    inverseSurface         = Color(0xFF312E38),
    inverseOnSurface       = Color(0xFFF4EFF7),
    inversePrimary         = Purple80
)

// ── AMOLED Purple/Violet (default) ────────────────────────────────────────────
private val AmoledColorScheme = darkColorScheme(
    primary                = NeonPurple,         // BB86FC — soft Material purple
    onPrimary              = AmoledBlack,
    primaryContainer       = Color(0xFF1A0033),
    onPrimaryContainer     = Purple90,
    secondary              = NeonBlue,           // 03DAC6 — teal complement
    onSecondary            = AmoledBlack,
    secondaryContainer     = Color(0xFF003330),
    onSecondaryContainer   = Teal90,
    tertiary               = NeonPink,           // FF4081
    onTertiary             = AmoledBlack,
    tertiaryContainer      = Color(0xFF3D0020),
    onTertiaryContainer    = Rose80,
    background             = AmoledBlack,
    onBackground           = AmoledOnSurface,
    surface                = AmoledSurface,
    onSurface              = AmoledOnSurface,
    surfaceVariant         = AmoledSurfaceVar,
    onSurfaceVariant       = Color(0xFFCAC4D0),
    outline                = Color(0xFF5A5467),
    outlineVariant         = Color(0xFF1E1E1E),
    inverseSurface         = AmoledOnSurface,
    inverseOnSurface       = AmoledBlack,
    inversePrimary         = Purple40,
    error                  = Error,
    onError                = Rose10,
    scrim                  = Color(0xFF000000)
)

// ── AMOLED Cyan/Blue ──────────────────────────────────────────────────────────
private val AmoledCyanColorScheme = darkColorScheme(
    primary                = NeonCyan,           // 00E5FF — electric cyan
    onPrimary              = AmoledBlack,
    primaryContainer       = Color(0xFF002B33),
    onPrimaryContainer     = Color(0xFFB3F6FF),
    secondary              = Color(0xFF7C4DFF),  // Deep violet
    onSecondary            = AmoledBlack,
    secondaryContainer     = Color(0xFF1C0066),
    onSecondaryContainer   = Indigo90,
    tertiary               = Color(0xFF64FFDA),  // Mint
    onTertiary             = AmoledBlack,
    tertiaryContainer      = Color(0xFF003326),
    onTertiaryContainer    = Teal90,
    background             = AmoledBlack,
    onBackground           = Color(0xFFE0FEFF),
    surface                = AmoledSurface,
    onSurface              = Color(0xFFE0FEFF),
    surfaceVariant         = AmoledSurfaceVar,
    onSurfaceVariant       = Color(0xFFB8D8DC),
    outline                = Color(0xFF4A7A80),
    outlineVariant         = Color(0xFF1A2A2C),
    inverseSurface         = Color(0xFFE0FEFF),
    inverseOnSurface       = AmoledBlack,
    inversePrimary         = Color(0xFF006070),
    error                  = Error,
    onError                = Rose10,
    scrim                  = Color(0xFF000000)
)

// ── AMOLED Pink/Rose ──────────────────────────────────────────────────────────
private val AmoledPinkColorScheme = darkColorScheme(
    primary                = NeonPink,           // FF4081 — hot pink
    onPrimary              = AmoledBlack,
    primaryContainer       = Color(0xFF3D0020),
    onPrimaryContainer     = Color(0xFFFFB3C6),
    secondary              = Color(0xFFFF6D00),  // Orange
    onSecondary            = AmoledBlack,
    secondaryContainer     = Color(0xFF3D1800),
    onSecondaryContainer   = Color(0xFFFFDBCC),
    tertiary               = NeonPurple,
    onTertiary             = AmoledBlack,
    tertiaryContainer      = Color(0xFF1A0033),
    onTertiaryContainer    = Purple90,
    background             = AmoledBlack,
    onBackground           = Color(0xFFFFE8EE),
    surface                = AmoledSurface,
    onSurface              = Color(0xFFFFE8EE),
    surfaceVariant         = AmoledSurfaceVar,
    onSurfaceVariant       = Color(0xFFDDBBC4),
    outline                = Color(0xFF804060),
    outlineVariant         = Color(0xFF2A0E18),
    inverseSurface         = Color(0xFFFFE8EE),
    inverseOnSurface       = AmoledBlack,
    inversePrimary         = Rose40,
    error                  = Color(0xFFFF6B6B),
    onError                = AmoledBlack,
    scrim                  = Color(0xFF000000)
)

// ── AMOLED Gold/Amber ─────────────────────────────────────────────────────────
private val AmoledGoldColorScheme = darkColorScheme(
    primary                = NeonGold,           // FFD54F — amber gold
    onPrimary              = Color(0xFF1A1000),
    primaryContainer       = Color(0xFF2E1F00),
    onPrimaryContainer     = Color(0xFFFFEFB3),
    secondary              = NeonOrange,         // FF6D00
    onSecondary            = Color(0xFF1A1000),
    secondaryContainer     = Color(0xFF3D1800),
    onSecondaryContainer   = Color(0xFFFFDBCC),
    tertiary               = Color(0xFFFFEB3B),  // Yellow
    onTertiary             = Color(0xFF1A1600),
    tertiaryContainer      = Color(0xFF2A2200),
    onTertiaryContainer    = Color(0xFFFFF9C4),
    background             = AmoledBlack,
    onBackground           = Color(0xFFFFF8E1),
    surface                = AmoledSurface,
    onSurface              = Color(0xFFFFF8E1),
    surfaceVariant         = AmoledSurfaceVar,
    onSurfaceVariant       = Color(0xFFDDD5B0),
    outline                = Color(0xFF806A30),
    outlineVariant         = Color(0xFF2A2210),
    inverseSurface         = Color(0xFFFFF8E1),
    inverseOnSurface       = AmoledBlack,
    inversePrimary         = Color(0xFF7A6200),
    error                  = Error,
    onError                = Rose10,
    scrim                  = Color(0xFF000000)
)

// ── AMOLED Green/Matrix ───────────────────────────────────────────────────────
private val AmoledGreenColorScheme = darkColorScheme(
    primary                = NeonGreen,          // 69FF47 — matrix green
    onPrimary              = Color(0xFF001200),
    primaryContainer       = Color(0xFF002800),
    onPrimaryContainer     = Color(0xFFB7FFAB),
    secondary              = NeonCyan,           // 00E5FF — electric cyan
    onSecondary            = AmoledBlack,
    secondaryContainer     = Color(0xFF002B33),
    onSecondaryContainer   = Color(0xFFB3F6FF),
    tertiary               = Color(0xFFA5FF82),  // Light green
    onTertiary             = Color(0xFF001200),
    tertiaryContainer      = Color(0xFF001E00),
    onTertiaryContainer    = Color(0xFFC2FFAA),
    background             = AmoledBlack,
    onBackground           = Color(0xFFE8FFE4),
    surface                = AmoledSurface,
    onSurface              = Color(0xFFE8FFE4),
    surfaceVariant         = AmoledSurfaceVar,
    onSurfaceVariant       = Color(0xFFB8D8B0),
    outline                = Color(0xFF3A6A30),
    outlineVariant         = Color(0xFF162A10),
    inverseSurface         = Color(0xFFE8FFE4),
    inverseOnSurface       = AmoledBlack,
    inversePrimary         = Color(0xFF006900),
    error                  = Error,
    onError                = Rose10,
    scrim                  = Color(0xFF000000)
)

// ── AMOLED Orange/Sunset ──────────────────────────────────────────────────────
private val AmoledOrangeColorScheme = darkColorScheme(
    primary                = NeonOrange,         // FF6D00 — deep orange
    onPrimary              = Color(0xFF1A0900),
    primaryContainer       = Color(0xFF3D1800),
    onPrimaryContainer     = Color(0xFFFFDBCC),
    secondary              = Color(0xFFFFEB3B),  // Yellow
    onSecondary            = Color(0xFF1A1600),
    secondaryContainer     = Color(0xFF2A2200),
    onSecondaryContainer   = Color(0xFFFFF9C4),
    tertiary               = NeonPink,
    onTertiary             = AmoledBlack,
    tertiaryContainer      = Color(0xFF3D0020),
    onTertiaryContainer    = Color(0xFFFFB3C6),
    background             = AmoledBlack,
    onBackground           = Color(0xFFFFF3E0),
    surface                = AmoledSurface,
    onSurface              = Color(0xFFFFF3E0),
    surfaceVariant         = AmoledSurfaceVar,
    onSurfaceVariant       = Color(0xFFDDC8A8),
    outline                = Color(0xFF804020),
    outlineVariant         = Color(0xFF2A1400),
    inverseSurface         = Color(0xFFFFF3E0),
    inverseOnSurface       = AmoledBlack,
    inversePrimary         = Color(0xFF7A3200),
    error                  = Color(0xFFFF6B6B),
    onError                = AmoledBlack,
    scrim                  = Color(0xFF000000)
)

// ── AMOLED Midnight Navy ──────────────────────────────────────────────────────
private val AmoledMidnightColorScheme = darkColorScheme(
    primary                = Color(0xFF7EB8FF),
    onPrimary              = Color(0xFF001D35),
    primaryContainer       = Color(0xFF002B4D),
    onPrimaryContainer     = Color(0xFFCDE5FF),
    secondary              = Color(0xFF9D80FF),
    onSecondary            = AmoledBlack,
    secondaryContainer     = Color(0xFF1C0066),
    onSecondaryContainer   = Color(0xFFDFD0FF),
    tertiary               = NeonTurquoise,
    onTertiary             = AmoledBlack,
    tertiaryContainer      = Color(0xFF00323A),
    onTertiaryContainer    = Color(0xFFB2EFFF),
    background             = AmoledBlack,
    onBackground           = Color(0xFFE4F0FF),
    surface                = AmoledSurface,
    onSurface              = Color(0xFFE4F0FF),
    surfaceVariant         = AmoledSurfaceVar,
    onSurfaceVariant       = Color(0xFFB5C8DC),
    outline                = Color(0xFF3A5068),
    outlineVariant         = Color(0xFF0E1C28),
    inverseSurface         = Color(0xFFE4F0FF),
    inverseOnSurface       = AmoledBlack,
    inversePrimary         = Color(0xFF004A80),
    error                  = Error,
    onError                = Rose10,
    scrim                  = Color(0xFF000000)
)

// ── AMOLED Violet / Deep Purple ───────────────────────────────────────────────
private val AmoledVioletColorScheme = darkColorScheme(
    primary                = NeonViolet,
    onPrimary              = AmoledBlack,
    primaryContainer       = Color(0xFF230047),
    onPrimaryContainer     = Color(0xFFDFB4FF),
    secondary              = Color(0xFFCF6BFF),
    onSecondary            = AmoledBlack,
    secondaryContainer     = Color(0xFF2D0063),
    onSecondaryContainer   = Color(0xFFEDD5FF),
    tertiary               = NeonIndigo,
    onTertiary             = Color.White,
    tertiaryContainer      = Color(0xFF1A1C5C),
    onTertiaryContainer    = Color(0xFFD0D5FF),
    background             = AmoledBlack,
    onBackground           = Color(0xFFEEE0FF),
    surface                = AmoledSurface,
    onSurface              = Color(0xFFEEE0FF),
    surfaceVariant         = AmoledSurfaceVar,
    onSurfaceVariant       = Color(0xFFCFC4DC),
    outline                = Color(0xFF5A4870),
    outlineVariant         = Color(0xFF1C1228),
    inverseSurface         = Color(0xFFEEE0FF),
    inverseOnSurface       = AmoledBlack,
    inversePrimary         = Color(0xFF5C007A),
    error                  = Error,
    onError                = Rose10,
    scrim                  = Color(0xFF000000)
)

// ── AMOLED Turquoise / Teal ───────────────────────────────────────────────────
private val AmoledTurquoiseColorScheme = darkColorScheme(
    primary                = NeonTurquoise,
    onPrimary              = AmoledBlack,
    primaryContainer       = Color(0xFF003540),
    onPrimaryContainer     = Color(0xFFB2F0FF),
    secondary              = Color(0xFF80DEEA),
    onSecondary            = Color(0xFF001F24),
    secondaryContainer     = Color(0xFF003038),
    onSecondaryContainer   = Color(0xFFC8F5FF),
    tertiary               = NeonGreen,
    onTertiary             = Color(0xFF001200),
    tertiaryContainer      = Color(0xFF002800),
    onTertiaryContainer    = Color(0xFFB7FFAB),
    background             = AmoledBlack,
    onBackground           = Color(0xFFDEF9FF),
    surface                = AmoledSurface,
    onSurface              = Color(0xFFDEF9FF),
    surfaceVariant         = AmoledSurfaceVar,
    onSurfaceVariant       = Color(0xFFB0D0D8),
    outline                = Color(0xFF2A6470),
    outlineVariant         = Color(0xFF0A2028),
    inverseSurface         = Color(0xFFDEF9FF),
    inverseOnSurface       = AmoledBlack,
    inversePrimary         = Color(0xFF007080),
    error                  = Error,
    onError                = Rose10,
    scrim                  = Color(0xFF000000)
)

// ── Light Lavender ────────────────────────────────────────────────────────────
private val LightLavenderColorScheme = lightColorScheme(
    primary                = PastelLavender,
    onPrimary              = Color.White,
    primaryContainer       = Color(0xFFE8E0FF),
    onPrimaryContainer     = Color(0xFF2C0078),
    secondary              = Color(0xFF625B8A),
    onSecondary            = Color.White,
    secondaryContainer     = Color(0xFFE8DFFF),
    onSecondaryContainer   = Color(0xFF1E1548),
    tertiary               = Color(0xFF7E4E9E),
    onTertiary             = Color.White,
    tertiaryContainer      = Color(0xFFF3DAFF),
    onTertiaryContainer    = Color(0xFF2E0957),
    background             = LightAmoledBg,
    onBackground           = Color(0xFF1B1B2F),
    surface                = LightAmoledSurface,
    onSurface              = Color(0xFF1B1B2F),
    surfaceVariant         = LightAmoledSurfaceVar,
    onSurfaceVariant       = Color(0xFF494066),
    outline                = Color(0xFF7B7494),
    inverseSurface         = Color(0xFF312E42),
    inverseOnSurface       = Color(0xFFF4EFF7),
    inversePrimary         = Color(0xFFCBB3FF)
)

// ── Light Mint ────────────────────────────────────────────────────────────────
private val LightMintColorScheme = lightColorScheme(
    primary                = PastelMint,
    onPrimary              = Color(0xFF001F15),
    primaryContainer       = Color(0xFFD0FFEC),
    onPrimaryContainer     = Color(0xFF00391F),
    secondary              = Color(0xFF3D6657),
    onSecondary            = Color.White,
    secondaryContainer     = Color(0xFFBFEDD8),
    onSecondaryContainer   = Color(0xFF002117),
    tertiary               = Color(0xFF00799F),
    onTertiary             = Color.White,
    tertiaryContainer      = Color(0xFFBFE9FF),
    onTertiaryContainer    = Color(0xFF001E2F),
    background             = Color(0xFFF2FDF7),
    onBackground           = Color(0xFF0A1C14),
    surface                = Color(0xFFE8F8F0),
    onSurface              = Color(0xFF0A1C14),
    surfaceVariant         = Color(0xFFDCF0E6),
    onSurfaceVariant       = Color(0xFF3B5047),
    outline                = Color(0xFF6B9080),
    inverseSurface         = Color(0xFF1E342A),
    inverseOnSurface       = Color(0xFFEBF5EE),
    inversePrimary         = Color(0xFF7ADDB2)
)

// ── Light Coral ───────────────────────────────────────────────────────────────
private val LightCoralColorScheme = lightColorScheme(
    primary                = PastelCoral,
    onPrimary              = Color.White,
    primaryContainer       = Color(0xFFFFDDD5),
    onPrimaryContainer     = Color(0xFF3E0012),
    secondary              = Color(0xFF8B4B57),
    onSecondary            = Color.White,
    secondaryContainer     = Color(0xFFFFD9E0),
    onSecondaryContainer   = Color(0xFF3B0718),
    tertiary               = Color(0xFF8C5B00),
    onTertiary             = Color.White,
    tertiaryContainer      = Color(0xFFFFDEB9),
    onTertiaryContainer    = Color(0xFF2C1900),
    background             = Color(0xFFFFF8F7),
    onBackground           = Color(0xFF201110),
    surface                = Color(0xFFF8EDED),
    onSurface              = Color(0xFF201110),
    surfaceVariant         = Color(0xFFFFE0DC),
    onSurfaceVariant       = Color(0xFF5C3B39),
    outline                = Color(0xFF8F6D6B),
    inverseSurface         = Color(0xFF382624),
    inverseOnSurface       = Color(0xFFFFEEEC),
    inversePrimary         = Color(0xFFFFB3A4)
)

// ── Light Sunrise ─────────────────────────────────────────────────────────────
private val LightSunriseColorScheme = lightColorScheme(
    primary                = PastelSunrise,
    onPrimary              = Color(0xFF1A0900),
    primaryContainer       = Color(0xFFFFDFC2),
    onPrimaryContainer     = Color(0xFF2C1100),
    secondary              = Color(0xFF8A5000),
    onSecondary            = Color.White,
    secondaryContainer     = Color(0xFFFFDDB7),
    onSecondaryContainer   = Color(0xFF2C1800),
    tertiary               = Color(0xFF8B5E00),
    onTertiary             = Color.White,
    tertiaryContainer      = Color(0xFFFFE0A0),
    onTertiaryContainer    = Color(0xFF2A1A00),
    background             = Color(0xFFFFF9F4),
    onBackground           = Color(0xFF1F1200),
    surface                = Color(0xFFFFF0E0),
    onSurface              = Color(0xFF1F1200),
    surfaceVariant         = Color(0xFFFFE6C8),
    onSurfaceVariant       = Color(0xFF5C3C1C),
    outline                = Color(0xFF8F6A40),
    inverseSurface         = Color(0xFF382200),
    inverseOnSurface       = Color(0xFFFFF0E0),
    inversePrimary         = Color(0xFFFFB96B)
)

// ── Light Ocean ───────────────────────────────────────────────────────────────
private val LightOceanColorScheme = lightColorScheme(
    primary                = PastelOcean,
    onPrimary              = Color.White,
    primaryContainer       = Color(0xFFCDE8FF),
    onPrimaryContainer     = Color(0xFF001D36),
    secondary              = Color(0xFF3A6685),
    onSecondary            = Color.White,
    secondaryContainer     = Color(0xFFBDE4FF),
    onSecondaryContainer   = Color(0xFF001D30),
    tertiary               = Color(0xFF00658A),
    onTertiary             = Color.White,
    tertiaryContainer      = Color(0xFFC1E8FF),
    onTertiaryContainer    = Color(0xFF001F2C),
    background             = Color(0xFFF4FBFF),
    onBackground           = Color(0xFF001629),
    surface                = Color(0xFFE6F4FF),
    onSurface              = Color(0xFF001629),
    surfaceVariant         = Color(0xFFD2E8F8),
    onSurfaceVariant       = Color(0xFF2C4858),
    outline                = Color(0xFF567888),
    inverseSurface         = Color(0xFF0D2D40),
    inverseOnSurface       = Color(0xFFEFF5FF),
    inversePrimary         = Color(0xFF80C8F4)
)

// ── AMOLED Neon Depth (Purple → Pink) ─────────────────────────────────────────
private val AmoledNeonDepthColorScheme = darkColorScheme(
    primary                = NeonDepthPurple,
    onPrimary              = AmoledBlack,
    primaryContainer       = Color(0xFF2E005A),
    onPrimaryContainer     = Color(0xFFE8D0FF),
    secondary              = NeonDepthPink,
    onSecondary            = AmoledBlack,
    secondaryContainer     = Color(0xFF3D0020),
    onSecondaryContainer   = Color(0xFFFFB3C6),
    tertiary               = NeonDepthPeach,
    onTertiary             = AmoledBlack,
    tertiaryContainer      = Color(0xFF3A1528),
    onTertiaryContainer    = Color(0xFFFFD9E6),
    background             = AmoledBlack,
    onBackground           = Color(0xFFF0E6FF),
    surface                = NeonDepthSurface,
    onSurface              = Color(0xFFF0E6FF),
    surfaceVariant         = Color(0xFF1E1A24),
    onSurfaceVariant       = Color(0xFFCCC4D4),
    outline                = Color(0xFF5A4A68),
    outlineVariant         = Color(0xFF1E1428),
    inverseSurface         = Color(0xFFF0E6FF),
    inverseOnSurface       = AmoledBlack,
    inversePrimary         = Color(0xFF6B00A8),
    error                  = Error,
    onError                = Rose10,
    scrim                  = Color(0xFF000000)
)

// ── AMOLED Pure Black & White ─────────────────────────────────────────────────
private val AmoledBWColorScheme = darkColorScheme(
    primary                = Color(0xFFFFFFFF),   // Pure white
    onPrimary              = Color(0xFF000000),
    primaryContainer       = Color(0xFF1A1A1A),
    onPrimaryContainer     = Color(0xFFFFFFFF),
    secondary              = Color(0xFFCCCCCC),   // Light grey
    onSecondary            = Color(0xFF000000),
    secondaryContainer     = Color(0xFF111111),
    onSecondaryContainer   = Color(0xFFEEEEEE),
    tertiary               = Color(0xFF999999),   // Mid grey
    onTertiary             = Color(0xFF000000),
    tertiaryContainer      = Color(0xFF0D0D0D),
    onTertiaryContainer    = Color(0xFFDDDDDD),
    background             = Color(0xFF000000),   // True black
    onBackground           = Color(0xFFFFFFFF),
    surface                = Color(0xFF0A0A0A),
    onSurface              = Color(0xFFFFFFFF),
    surfaceVariant         = Color(0xFF141414),
    onSurfaceVariant       = Color(0xFFBBBBBB),
    outline                = Color(0xFF444444),
    outlineVariant         = Color(0xFF222222),
    inverseSurface         = Color(0xFFFFFFFF),
    inverseOnSurface       = Color(0xFF000000),
    inversePrimary         = Color(0xFF333333),
    error                  = Color(0xFFFF4444),
    onError                = Color(0xFF000000),
    errorContainer         = Color(0xFF1A0000),
    onErrorContainer       = Color(0xFFFFAAAA),
    scrim                  = Color(0xFF000000)
)

@Composable
fun ABMusicTheme(
    themeMode      : ThemeMode = ThemeMode.SYSTEM,
    dynamicColor   : Boolean   = true,
    wallpaperActive: Boolean   = false,
    accentColor    : String    = "",
    content        : @Composable () -> Unit
) {
    val isAmoled = themeMode in setOf(
        ThemeMode.AMOLED, ThemeMode.AMOLED_CYAN,
        ThemeMode.AMOLED_PINK, ThemeMode.AMOLED_GOLD,
        ThemeMode.AMOLED_GREEN, ThemeMode.AMOLED_ORANGE,
        ThemeMode.AMOLED_MIDNIGHT, ThemeMode.AMOLED_VIOLET, ThemeMode.AMOLED_TURQUOISE,
        ThemeMode.AMOLED_BW, ThemeMode.AMOLED_NEON_DEPTH
    )
    val isDark = when (themeMode) {
        ThemeMode.LIGHT,
        ThemeMode.LIGHT_LAVENDER, ThemeMode.LIGHT_MINT,
        ThemeMode.LIGHT_CORAL, ThemeMode.LIGHT_SUNRISE,
        ThemeMode.LIGHT_OCEAN -> false
        else -> if (themeMode == ThemeMode.SYSTEM) isSystemInDarkTheme() else true
    }

    val colorScheme = when (themeMode) {
        ThemeMode.AMOLED          -> AmoledColorScheme
        ThemeMode.AMOLED_CYAN     -> AmoledCyanColorScheme
        ThemeMode.AMOLED_PINK     -> AmoledPinkColorScheme
        ThemeMode.AMOLED_GOLD     -> AmoledGoldColorScheme
        ThemeMode.AMOLED_GREEN    -> AmoledGreenColorScheme
        ThemeMode.AMOLED_ORANGE   -> AmoledOrangeColorScheme
        ThemeMode.AMOLED_MIDNIGHT -> AmoledMidnightColorScheme
        ThemeMode.AMOLED_VIOLET   -> AmoledVioletColorScheme
        ThemeMode.AMOLED_TURQUOISE-> AmoledTurquoiseColorScheme
        ThemeMode.AMOLED_BW       -> AmoledBWColorScheme
        ThemeMode.AMOLED_NEON_DEPTH-> AmoledNeonDepthColorScheme
        ThemeMode.LIGHT           -> LightColorScheme
        ThemeMode.LIGHT_LAVENDER  -> LightLavenderColorScheme
        ThemeMode.LIGHT_MINT      -> LightMintColorScheme
        ThemeMode.LIGHT_CORAL     -> LightCoralColorScheme
        ThemeMode.LIGHT_SUNRISE   -> LightSunriseColorScheme
        ThemeMode.LIGHT_OCEAN     -> LightOceanColorScheme
        ThemeMode.DARK            -> DarkColorScheme
        ThemeMode.SYSTEM          -> when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            isDark -> DarkColorScheme
            else   -> LightColorScheme
        }
    }

    // ── Custom Accent Color ──────────────────────────────────────────────
    // Empty = user hasn't customized it yet, so the theme's own primary
    // stands. When set, it retints primary/tint slots on top of whichever
    // scheme (AMOLED variant, dynamic, etc.) was picked above — the rest of
    // the scheme (background/surface/secondary/tertiary) stays untouched so
    // the base theme's character is preserved, only the "brand" hue changes.
    val finalColorScheme = remember(colorScheme, accentColor) {
        val accent = accentColor.takeIf { it.isNotBlank() }
            ?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }
        if (accent == null) {
            colorScheme
        } else {
            val onAccent = if (accent.luminance() > 0.5f) Color(0xFF000000) else Color(0xFFFFFFFF)
            val container = lerp(accent, colorScheme.surface, 0.55f)
            val onContainer = if (container.luminance() > 0.5f) Color(0xFF000000) else Color(0xFFFFFFFF)
            colorScheme.copy(
                primary            = accent,
                onPrimary          = onAccent,
                primaryContainer   = container,
                onPrimaryContainer = onContainer,
                inversePrimary     = accent,
                surfaceTint        = accent
            )
        }
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor     = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isStatusBarContrastEnforced = false
                window.isNavigationBarContrastEnforced = false
            }
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars     = !isDark
                isAppearanceLightNavigationBars = !isDark
            }
        }
    }

    MaterialTheme(
        colorScheme = finalColorScheme,
        typography  = ABMusicTypography,
        content     = {
            CompositionLocalProvider(
                LocalThemeMode       provides themeMode,
                LocalWallpaperActive provides wallpaperActive
            ) {
                content()
            }
        }
    )
}
