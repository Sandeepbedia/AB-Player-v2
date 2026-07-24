package com.io.ab.music.ui.screens.artist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.io.ab.music.domain.model.Artist
import com.io.ab.music.domain.model.Song
import com.io.ab.music.ui.components.rememberArtworkModel
import com.io.ab.music.ui.viewmodel.LibraryViewModel
import com.io.ab.music.ui.viewmodel.PlayerViewModel

/**
 * "Artist card me ek jada song ho to user wo songs ka list dekh sake" — tapping
 * an artist on the Explore screen used to just play their first song. Now it
 * opens this screen so the user can see and pick from the artist's full song list.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ArtistDetailScreen(
    artist          : Artist,
    onBack          : () -> Unit,
    onSongClick     : (Song, List<Song>) -> Unit,
    libraryViewModel: LibraryViewModel = hiltViewModel(),
    playerViewModel : PlayerViewModel  = hiltViewModel()
) {
    val uiState       by libraryViewModel.uiState.collectAsState()
    val currentSongId by playerViewModel.currentSongId.collectAsState()
    val isPlaying     by playerViewModel.isPlaying.collectAsState()

    val artistSongs = remember(uiState.songs, artist.name) {
        uiState.songs.filter { it.artist == artist.name }
    }

    Scaffold(
        floatingActionButton = {
            if (artistSongs.isNotEmpty()) {
                FloatingActionButton(
                    onClick = { onSongClick(artistSongs.first(), artistSongs) },
                    shape   = CircleShape
                ) {
                    Icon(Icons.Rounded.PlayArrow, "Play all")
                }
            }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Rounded.ArrowBack, "Back")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val artworkModel = rememberArtworkModel(artist.artworkUri, 200)
                        Box(
                            modifier = Modifier.size(76.dp).clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (artist.artworkUri != null) {
                                AsyncImage(
                                    model = artworkModel, contentDescription = null,
                                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(Icons.Rounded.Person, null, tint = Color.White, modifier = Modifier.size(34.dp))
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(
                                artist.name,
                                style    = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "${artistSongs.size} songs • ${artist.albumCount} albums",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (artistSongs.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No songs found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                itemsIndexed(artistSongs, key = { _, s -> s.id }) { index, song ->
                    val isCurrent = song.id == currentSongId
                    ArtistSongRow(
                        index             = index + 1,
                        song              = song,
                        isPlaying         = isCurrent,
                        isActuallyPlaying = isCurrent && isPlaying,
                        onClick           = { onSongClick(song, artistSongs) }
                    )
                }
            }

            item { Spacer(Modifier.height(110.dp)) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ArtistSongRow(
    index            : Int,
    song             : Song,
    isPlaying        : Boolean,
    isActuallyPlaying: Boolean,
    onClick          : () -> Unit
) {
    val artworkModel = rememberArtworkModel(song.artworkUri, 128)
    Surface(
        onClick  = onClick,
        modifier = Modifier.fillMaxWidth(),
        color    = if (isPlaying) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.Transparent
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                "$index",
                style    = MaterialTheme.typography.bodyMedium,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(24.dp)
            )
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                AsyncImage(
                    model = artworkModel, contentDescription = null,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                )
                if (song.artworkUri == null) {
                    Icon(
                        Icons.Rounded.MusicNote,
                        null,
                        tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(22.dp).align(Alignment.Center)
                    )
                }
                if (isPlaying) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(if (isActuallyPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    song.title,
                    style    = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color    = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (isPlaying) Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 800) else Modifier
                )
                Text(
                    song.album,
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                song.formattedDuration,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
