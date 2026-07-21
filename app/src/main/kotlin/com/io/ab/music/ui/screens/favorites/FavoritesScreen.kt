package com.io.ab.music.ui.screens.favorites

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.io.ab.music.domain.model.Song
import com.io.ab.music.ui.viewmodel.LibraryViewModel
import com.io.ab.music.ui.viewmodel.PlayerViewModel
import androidx.hilt.navigation.compose.hiltViewModel
import com.io.ab.music.ui.components.rememberArtworkModel
import com.io.ab.music.ui.components.MiniPlayerBottomSpacer
import com.io.ab.music.ui.navigation.LocalMiniPlayerScrollState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun FavoritesScreen(
    onSongClick     : (Song, List<Song>) -> Unit,
    libraryViewModel: LibraryViewModel = hiltViewModel(),
    playerViewModel : PlayerViewModel  = hiltViewModel()
) {
    val uiState       by libraryViewModel.uiState.collectAsState()
    val currentSongId by playerViewModel.currentSongId.collectAsState()
    val isPlaying     by playerViewModel.isPlaying.collectAsState()
    val favorites     = uiState.favorites

    // Heart pulse animation disabled for performance
    val heartScale = 1f

    // FIX: MiniPlayer never hid while scrolling this tab — wire it up the same
    // way HomeScreen/VideoScreen do.
    val listState         = androidx.compose.foundation.lazy.rememberLazyListState()
    val miniPlayerScroll  = LocalMiniPlayerScrollState.current
    var lastScrollIdx     by remember { mutableIntStateOf(0) }
    var lastScrollOffset  by remember { mutableIntStateOf(0) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (idx, offset) ->
                val delta = when {
                    idx > lastScrollIdx -> -100f
                    idx < lastScrollIdx ->  100f
                    else -> (lastScrollOffset - offset).toFloat()
                }
                miniPlayerScroll.onScrollDelta(delta)
                lastScrollIdx    = idx
                lastScrollOffset = offset
            }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    LazyColumn(modifier = Modifier.fillMaxSize(), state = listState) {
        item {
            // ── Hero Banner ──────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
            ) {
                // Blurred mosaic artwork background from first 4 favorites
                if (favorites.isNotEmpty()) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        favorites.take(4).forEachIndexed { i, s ->
                            val model = rememberArtworkModel(s.artworkUri, 256)
                            AsyncImage(
                                model              = model,
                                contentDescription = null,
                                contentScale       = ContentScale.Crop,
                                modifier           = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .blur(24.dp)
                            )
                        }
                    }
                }
                // Dark overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.45f),
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.95f)
                                )
                            )
                        )
                )
                // Hero content
                Column(
                    modifier             = Modifier
                        .align(Alignment.Center)
                        .padding(top = 24.dp),
                    horizontalAlignment  = Alignment.CenterHorizontally
                ) {
                    // Animated heart icon
                    // PERF FIX: .scale(Float) reads heartScale during composition, so this
                    // infinite (never-stops) pulse animation was forcing a full recomposition
                    // of this Box every ~16ms, forever — even while sitting in an adjacent,
                    // non-visible pager tab. graphicsLayer{} defers the read to the draw
                    // phase instead, so the pulse no longer costs a recomposition at all.
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .graphicsLayer {
                                scaleX = heartScale
                                scaleY = heartScale
                            }
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.errorContainer,
                                        MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Favorite, null,
                            tint     = Color.White,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "Your Favorites",
                        style  = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.ExtraBold
                        ),
                        color  = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        if (favorites.isEmpty()) "No songs yet"
                        else "${favorites.size} song${if (favorites.size != 1) "s" else ""}",
                        style  = MaterialTheme.typography.bodyMedium,
                        color  = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(18.dp))

                    // Action buttons
                    if (favorites.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = { onSongClick(favorites.first(), favorites) },
                                shape   = RoundedCornerShape(14.dp),
                                colors  = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Play All", fontWeight = FontWeight.SemiBold)
                            }
                            OutlinedButton(
                                onClick = {
                                    val shuffled = favorites.shuffled()
                                    onSongClick(shuffled.first(), shuffled)
                                },
                                shape  = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Rounded.Shuffle, null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Shuffle")
                            }
                        }
                    }
                }
            }
        }

        if (favorites.isEmpty()) {
            item {
                Column(
                    modifier             = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 64.dp, horizontal = 32.dp),
                    horizontalAlignment  = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Rounded.FavoriteBorder, null,
                        modifier = Modifier.size(80.dp),
                        tint     = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                    Spacer(Modifier.height(20.dp))
                    Text(
                        "No favorites yet",
                        style     = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color     = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Tap ♥ on any song while it's playing to add it here",
                        style     = MaterialTheme.typography.bodyMedium,
                        color     = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            // Stats row
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val totalMin = favorites.sumOf { it.duration } / 60000
                    StatChip(
                        icon  = Icons.Rounded.MusicNote,
                        label = "${favorites.size} Songs"
                    )
                    StatChip(
                        icon  = Icons.Rounded.Schedule,
                        label = "${totalMin}m total"
                    )
                    val uniqueArtists = favorites.map { it.artist }.distinct().size
                    StatChip(
                        icon  = Icons.Rounded.Person,
                        label = "$uniqueArtists artists"
                    )
                }
            }
        }

        itemsIndexed(favorites, key = { _, s -> s.id }) { index, song ->
            val isCurrent = song.id == currentSongId
            AnimatedVisibility(
                visible      = true,
                enter        = fadeIn() + slideInVertically(initialOffsetY = { it / 4 }),
            ) {
                FavoriteSongRow(
                    index             = index + 1,
                    song              = song,
                    isPlaying         = isCurrent,
                    isActuallyPlaying = isCurrent && isPlaying,
                    onSongClick       = { onSongClick(song, favorites) },
                    onRemove          = { libraryViewModel.toggleFavorite(song.id) }
                )
            }
        }

        item(key = "bottom_pad") { MiniPlayerBottomSpacer() }
    } // end LazyColumn
    } // end gradient Box
}

@Composable
private fun StatChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Surface(
        shape  = RoundedCornerShape(12.dp),
        color  = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier          = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                icon, null,
                modifier = Modifier.size(14.dp),
                tint     = MaterialTheme.colorScheme.primary
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FavoriteSongRow(
    index            : Int,
    song             : Song,
    isPlaying        : Boolean,
    isActuallyPlaying: Boolean,
    onSongClick      : () -> Unit,
    onRemove         : () -> Unit
) {
    val artworkModel = rememberArtworkModel(song.artworkUri, 128)
    val subtitle = remember(song.artist, song.duration) {
        "${song.artist} • ${formatDuration(song.duration)}"
    }

    // Waveform animation disabled for performance — static heights
    val waveH1 = 12f
    val waveH2 = 16f
    val waveH3 = 10f

    Surface(
        onClick = onSongClick,
        modifier = Modifier.fillMaxWidth(),
        color    = if (isPlaying)
                       MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.30f)
                   else Color.Transparent
    ) {
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Index / waveform indicator
            Box(
                modifier         = Modifier.width(32.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isActuallyPlaying) {
                    Row(
                        verticalAlignment     = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        listOf(waveH1, waveH2, waveH3).forEach { h ->
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(h.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                } else {
                    Text(
                        "$index",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isPlaying) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.width(10.dp))

            // Artwork
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                if (song.artworkUri != null) {
                    AsyncImage(
                        model            = artworkModel,
                        contentDescription = null,
                        contentScale     = ContentScale.Crop,
                        modifier         = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.tertiary
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            song.title.firstOrNull()?.uppercaseChar()?.toString() ?: "♪",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }
                }
                // Playing overlay badge
                if (isPlaying) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                    )
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    song.title,
                    style    = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize   = 15.sp
                    ),
                    color    = if (isPlaying) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Remove favorite button with confirmation-feel animation
            var pressed by remember { mutableStateOf(false) }
            val heartTint by animateColorAsState(
                targetValue = if (pressed) MaterialTheme.colorScheme.onSurfaceVariant
                              else MaterialTheme.colorScheme.error,
                label       = "heartTint"
            )
            IconButton(
                onClick  = { pressed = true; onRemove() },
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    Icons.Filled.Favorite, "Remove from favorites",
                    tint     = heartTint,
                    modifier = Modifier.size(22.dp)
                )
        }
    }
}
}

private fun formatDuration(ms: Long): String {
    val sec = ms / 1000
    return "%d:%02d".format(sec / 60, sec % 60)
}
