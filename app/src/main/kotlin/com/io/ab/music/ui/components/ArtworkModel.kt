package com.io.ab.music.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import coil.request.CachePolicy
import coil.request.ImageRequest

/**
 * Builds a Coil [ImageRequest] for a song's artwork.
 *
 * Strategy (in order):
 *  1. Per-song embedded art  → content://media/external/audio/media/{id}/albumart
 *     This gives each song its OWN cover even when songs share an albumId.
 *  2. If [artworkUri] is null or Coil fails to decode it, [AsyncImage] shows
 *     the placeholder defined at the call-site (first-letter avatar / music note).
 *
 * FIX: [stableKey] defaults to artworkUri but can be set to song.id (as String)
 * so that the ImageRequest is NOT recreated on play/pause state changes.
 * This prevents album art from flashing back to the placeholder when paused.
 */
@Composable
fun rememberArtworkModel(
    artworkUri : String?,
    sizePx     : Int,
    loadArtwork: Boolean = true,
    stableKey  : Any?    = artworkUri    // pass song.id.toString() to freeze on song change only
): Any? {
    val context = LocalContext.current
    return remember(stableKey, sizePx, loadArtwork, context) {
        if (!loadArtwork || artworkUri == null) {
            null
        } else {
            ImageRequest.Builder(context)
                .data(artworkUri)
                .size(sizePx)
                .crossfade(true)           // smooth fade-in when art loads
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                // Allow Coil to decode the embedded JPEG/PNG from the MediaStore URI
                .allowHardware(true)
                .build()
        }
    }
}
