package com.io.ab.music.ui.screens.playlists

import androidx.compose.animation.*
import androidx.compose.foundation.background
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
import com.io.ab.music.domain.model.Playlist
import com.io.ab.music.domain.model.Song
import com.io.ab.music.ui.components.rememberArtworkModel
import com.io.ab.music.ui.components.AddSongsSheet
import com.io.ab.music.ui.viewmodel.LibraryViewModel
import com.io.ab.music.ui.viewmodel.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlist        : Playlist,
    onBack          : () -> Unit,
    onSongClick     : (Song, List<Song>) -> Unit,
    libraryViewModel: LibraryViewModel = hiltViewModel(),
    playerViewModel : PlayerViewModel  = hiltViewModel()
) {
    val songs         by libraryViewModel.playlistSongs.collectAsState()
    val current        by libraryViewModel.selectedPlaylist.collectAsState()
    val uiState        by libraryViewModel.uiState.collectAsState()
    val currentSongId  by playerViewModel.currentSongId.collectAsState()
    val isPlaying      by playerViewModel.isPlaying.collectAsState()

    var showAddSongs by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    DisposableEffect(playlist.id) {
        libraryViewModel.openPlaylist(playlist)
        onDispose { libraryViewModel.closePlaylist() }
    }

    val displayPlaylist = current ?: playlist

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddSongs = true },
                shape   = CircleShape
            ) {
                Icon(Icons.Rounded.Add, "Add songs")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            item {
                Box(modifier = Modifier.fillMaxWidth().height(240.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.30f),
                                        MaterialTheme.colorScheme.background
                                    )
                                )
                            )
                    )
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.Rounded.ArrowBack, "Back")
                            }
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = { showDeleteConfirm = true }) {
                                Icon(Icons.Rounded.Delete, "Delete playlist")
                            }
                        }

                        Column(
                            modifier            = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(16.dp))
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
                                Icon(
                                    Icons.Rounded.QueueMusic, null,
                                    tint     = Color.White,
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(
                                displayPlaylist.name,
                                style    = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                color    = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "${songs.size} song${if (songs.size != 1) "s" else ""}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(14.dp))
                            if (songs.isNotEmpty()) {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Button(
                                        onClick = { onSongClick(songs.first(), songs) },
                                        shape   = RoundedCornerShape(14.dp)
                                    ) {
                                        Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(20.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Play All", fontWeight = FontWeight.SemiBold)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            val shuffled = songs.shuffled()
                                            onSongClick(shuffled.first(), shuffled)
                                        },
                                        shape = RoundedCornerShape(14.dp)
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
            }

            if (songs.isEmpty()) {
                item {
                    Column(
                        modifier            = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 56.dp, horizontal = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Rounded.MusicOff, null,
                            modifier = Modifier.size(72.dp),
                            tint     = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "No songs in this playlist",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Tap + to add songs",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            itemsIndexed(songs, key = { _, s -> s.id }) { index, song ->
                val isCurrent = song.id == currentSongId
                PlaylistSongRow(
                    index             = index + 1,
                    song              = song,
                    isPlaying         = isCurrent,
                    isActuallyPlaying = isCurrent && isPlaying,
                    onSongClick       = { onSongClick(song, songs) },
                    onRemove          = { libraryViewModel.removeSongFromPlaylist(displayPlaylist.id, song.id) }
                )
            }

            item { Spacer(Modifier.height(110.dp)) }
        }
    }

    if (showAddSongs) {
        AddSongsSheet(
            allSongs     = uiState.songs,
            alreadyInIds = songs.map { it.id }.toSet(),
            onAdd        = { song -> libraryViewModel.addSongToPlaylist(displayPlaylist.id, song.id) },
            onDismiss    = { showAddSongs = false }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title   = { Text("Delete Playlist?", fontWeight = FontWeight.Bold) },
            text    = { Text("\"${displayPlaylist.name}\" will be permanently deleted. Songs themselves won't be removed from your library.") },
            confirmButton = {
                TextButton(onClick = {
                    libraryViewModel.deletePlaylist(displayPlaylist)
                    showDeleteConfirm = false
                    onBack()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun PlaylistSongRow(
    index            : Int,
    song             : Song,
    isPlaying        : Boolean,
    isActuallyPlaying: Boolean,
    onSongClick      : () -> Unit,
    onRemove         : () -> Unit
) {
    val artworkModel = rememberArtworkModel(song.artworkUri, 128)

    Surface(
        onClick  = onSongClick,
        modifier = Modifier.fillMaxWidth(),
        color    = if (isPlaying) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.30f)
                   else Color.Transparent
    ) {
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.width(28.dp), contentAlignment = Alignment.Center) {
                Text(
                    "$index",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isPlaying) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier.size(50.dp).clip(RoundedCornerShape(10.dp))
            ) {
                if (song.artworkUri != null) {
                    AsyncImage(
                        model              = artworkModel,
                        contentDescription = null,
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier.fillMaxSize()
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
                            color = Color.White,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    song.title,
                    style    = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color    = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    song.artist,
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Rounded.Close, "Remove from playlist",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp))
            }
        }
    }
}


