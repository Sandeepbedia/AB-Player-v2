package com.io.ab.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext

/**
 * Full-screen manual wallpaper background, drawn behind all app chrome
 * (status bar / nav bar are already transparent via [ABMusicTheme], and
 * the top tab bar + screen scaffolds drop their solid color when
 * [LocalWallpaperActive] is true) — this is what lets the picked image
 * show through everywhere, matching a custom wallpaper theme.
 *
 * @param imagePath local file path of the user-picked image (copied into
 *                  app-private storage so the URI survives reboots/picker grants)
 * @param blurRadius user-controlled blur amount (0.dp = sharp, higher = softer)
 * @param dimAlpha   dark scrim overlay (0f..1f) so text/icons stay legible on bright photos
 */
@Composable
fun WallpaperBackground(
    imagePath : String,
    blurRadius: Dp,
    dimAlpha  : Float,
    modifier  : Modifier = Modifier
) {
    val context = LocalContext.current
    // PERF FIX: this was building a brand-new ImageRequest inline on every single
    // recomposition — including every blur/dim slider tick, which don't change the
    // image at all. Coil treats a new ImageRequest instance as a "model changed"
    // event and re-runs its pipeline, which is exactly the stutter felt while
    // adjusting wallpaper settings. Now it only rebuilds when imagePath changes.
    val imageRequest = remember(imagePath, context) {
        ImageRequest.Builder(context.applicationContext)
            .data(imagePath)
            .size(1080, 1920)
            .crossfade(false)
            .memoryCacheKey("wallpaper:$imagePath")
            .diskCacheKey("wallpaper:$imagePath")
            .build()
    }
    Box(modifier = modifier.fillMaxSize()) {
        AsyncImage(
            model = imageRequest,
            contentDescription = "App wallpaper",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .blur(blurRadius) // no-op below Android 12 (API 31) — image just renders sharp
        )
        if (dimAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = dimAlpha.coerceIn(0f, 1f)))
            )
        }
    }
}
