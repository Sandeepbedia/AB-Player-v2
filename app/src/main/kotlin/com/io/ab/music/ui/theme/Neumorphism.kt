package com.io.ab.music.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Neumorphism shadow style: FLAT (equal depth from both sides) gives the
 * classic soft-UI "pressed into the background" effect.
 * CONCAVE / CONVEX add gradient fill for embossed/debossed look.
 */
enum class NeumorphStyle { FLAT, CONVEX, PRESSED }

/**
 * Applies a neumorphic double-shadow effect appropriate for the current
 * theme (light, dark, or AMOLED). Call this as a Modifier on any surface.
 *
 * @param themeMode      Current [ThemeMode] — determines which shadow palette to use.
 * @param cornerRadius   Corner radius of the element (must match the shape).
 * @param elevation      Shadow spread; larger = more pronounced effect.
 * @param style          [NeumorphStyle.FLAT] for standard, [PRESSED] for sunken.
 */
fun Modifier.neumorphShadow(
    darkShadow  : Color,
    lightShadow : Color,
    cornerRadius: Dp     = 16.dp,
    elevation   : Dp     = 8.dp,
    style       : NeumorphStyle = NeumorphStyle.FLAT
): Modifier = this.drawBehind {
    val radiusPx    = cornerRadius.toPx()
    val elevPx      = elevation.toPx()
    val offsetPx    = elevPx * 0.8f

    // FIX (3D black blob on AMOLED / Light AMOLED): the framework Paint's own
    // `color` was set to TRANSPARENT (alpha = 0x00). On some devices/renderers
    // a fully-transparent fill combined with setShadowLayer() is drawn as an
    // OPAQUE BLACK rounded-rect by the software shadow fallback path — this is
    // exactly the "3D black box behind icons/cards" bug. Using alpha = 1 (almost
    // invisible, but NOT exactly 0) keeps the fill effectively invisible while
    // preventing the renderer's transparent-paint fallback from drawing black.
    val invisibleFill = android.graphics.Color.argb(1, 0, 0, 0)

    drawIntoCanvas { canvas ->
        val darkPaint = Paint().apply {
            asFrameworkPaint().apply {
                isAntiAlias = true
                color       = invisibleFill
                setShadowLayer(
                    elevPx,
                    if (style == NeumorphStyle.PRESSED) -offsetPx else offsetPx,
                    if (style == NeumorphStyle.PRESSED) -offsetPx else offsetPx,
                    darkShadow.copy(alpha = 0.65f).toArgb()
                )
            }
        }
        val lightPaint = Paint().apply {
            asFrameworkPaint().apply {
                isAntiAlias = true
                color       = invisibleFill
                setShadowLayer(
                    elevPx,
                    if (style == NeumorphStyle.PRESSED) offsetPx else -offsetPx,
                    if (style == NeumorphStyle.PRESSED) offsetPx else -offsetPx,
                    lightShadow.copy(alpha = 0.90f).toArgb()
                )
            }
        }
        val rect = androidx.compose.ui.geometry.RoundRect(
            left         = 0f,
            top          = 0f,
            right        = size.width,
            bottom       = size.height,
            radiusX      = radiusPx,
            radiusY      = radiusPx
        )
        val path = androidx.compose.ui.graphics.Path().apply { addRoundRect(rect) }
        canvas.drawPath(path, darkPaint)
        canvas.drawPath(path, lightPaint)
    }
}

/** Convenience: picks the right shadow colors based on ThemeMode. */
@Composable
fun neumorphColors(themeMode: ThemeMode): Pair<Color, Color> {
    // All dark AMOLED variants (pure black background) → AMOLED shadow palette
    val isAmoled = themeMode in setOf(
        ThemeMode.AMOLED, ThemeMode.AMOLED_CYAN, ThemeMode.AMOLED_PINK, ThemeMode.AMOLED_GOLD,
        ThemeMode.AMOLED_GREEN, ThemeMode.AMOLED_ORANGE, ThemeMode.AMOLED_MIDNIGHT,
        ThemeMode.AMOLED_VIOLET, ThemeMode.AMOLED_TURQUOISE
    )
    // All light variants (including LIGHT_LAVENDER, LIGHT_MINT, etc.) → light shadow palette
    val isLight = themeMode in setOf(
        ThemeMode.LIGHT, ThemeMode.LIGHT_LAVENDER, ThemeMode.LIGHT_MINT,
        ThemeMode.LIGHT_CORAL, ThemeMode.LIGHT_SUNRISE, ThemeMode.LIGHT_OCEAN
    )
    val isDark = when {
        isLight  -> false
        themeMode == ThemeMode.SYSTEM -> isSystemInDarkTheme()
        else -> true
    }
    return when {
        isAmoled -> NeumorphAmoledShadowDark to NeumorphAmoledShadowLight
        isDark   -> NeumorphDarkShadowDark   to NeumorphDarkShadowLight
        else     -> NeumorphLightShadowDark  to NeumorphLightShadowLight  // light + all light variants
    }
}

/** Quick Modifier extension that reads ThemeMode from the composition. */
@Composable
fun Modifier.neumorphCard(
    themeMode   : ThemeMode,
    cornerRadius: Dp           = 20.dp,
    elevation   : Dp           = 8.dp,
    style       : NeumorphStyle = NeumorphStyle.FLAT
): Modifier {
    val (dark, light) = neumorphColors(themeMode)
    return neumorphShadow(dark, light, cornerRadius, elevation, style)
}
