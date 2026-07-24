package com.io.ab.music.ui.screens.playlists

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.io.ab.music.domain.model.Playlist
import com.io.ab.music.ui.components.AddSongsSheet
import com.io.ab.music.ui.viewmodel.LibraryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistsScreen(
    onPlaylistClick : (Playlist) -> Unit,
    libraryViewModel: LibraryViewModel = hiltViewModel()
) {
    val uiState by libraryViewModel.uiState.collectAsState()
    val playlists = uiState.playlists

    var showCreateDialog by remember { mutableStateOf(false) }

    // Quick "add songs" action right from the playlist card — lets the user add
    // songs without first opening the playlist's detail screen.
    var addSongsPlaylist by remember { mutableStateOf<Playlist?>(null) }
    val playlistSongsForSheet by libraryViewModel.playlistSongs.collectAsState()



    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Top Bar ──────────────────────────────────────────────────────
            Box(modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "Playlists",
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                if (playlists.isEmpty()) "Create your first playlist"
                                else "${playlists.size} playlist${if (playlists.size != 1) "s" else ""}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        FilledIconButton(
                            onClick  = { showCreateDialog = true },
                            modifier = Modifier.size(40.dp),
                            shape    = CircleShape,
                            colors   = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(Icons.Rounded.Add, "Create playlist",
                                tint     = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            if (playlists.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement  = Arrangement.Center
                ) {
                    Icon(
                        Icons.Rounded.QueueMusic, null,
                        modifier = Modifier.size(80.dp),
                        tint     = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                    Spacer(Modifier.height(20.dp))
                    Text(
                        "No playlists yet",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Tap the + button to create your first playlist",
                        style     = MaterialTheme.typography.bodyMedium,
                        color     = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = { showCreateDialog = true },
                        shape   = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Rounded.Add, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("New Playlist")
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement   = Arrangement.spacedBy(14.dp)
                ) {
                    items(playlists, key = { it.id }) { playlist ->
                        PlaylistCard(
                            playlist = playlist,
                            onClick  = { onPlaylistClick(playlist) },
                            onAddSongsClick = {
                                libraryViewModel.openPlaylist(playlist)
                                addSongsPlaylist = playlist
                            }
                        )
                    }
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                        Spacer(Modifier.height(90.dp))
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreateDialog = false },
            onCreate  = { name ->
                libraryViewModel.createPlaylist(name)
                showCreateDialog = false
            }
        )
    }

    addSongsPlaylist?.let { playlist ->
        AddSongsSheet(
            allSongs     = uiState.songs,
            alreadyInIds = playlistSongsForSheet.map { it.id }.toSet(),
            onAdd        = { song -> libraryViewModel.addSongToPlaylist(playlist.id, song.id) },
            onDismiss    = {
                libraryViewModel.closePlaylist()
                addSongsPlaylist = null
            }
        )
    }
}

@Composable
private fun PlaylistCard(
    playlist       : Playlist,
    onClick        : () -> Unit,
    onAddSongsClick: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape  = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.75f)
                            )
                        )
                    )
            ) {
                Icon(
                    Icons.Rounded.QueueMusic, null,
                    tint     = Color.White,
                    modifier = Modifier.size(40.dp).align(Alignment.Center)
                )
                // Quick "add songs" shortcut — no need to open the playlist first.
                FilledIconButton(
                    onClick  = onAddSongsClick,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .size(30.dp),
                    shape    = CircleShape,
                    colors   = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color.Black.copy(alpha = 0.45f),
                        contentColor   = Color.White
                    )
                ) {
                    Icon(Icons.Rounded.PlaylistAdd, "Add songs", modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                playlist.name,
                style    = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color    = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${playlist.songCount} song${if (playlist.songCount != 1) "s" else ""}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onCreate : (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title   = { Text("New Playlist", fontWeight = FontWeight.Bold) },
        text    = {
            OutlinedTextField(
                value         = name,
                onValueChange = { name = it },
                placeholder   = { Text("Playlist name") },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onCreate(name.trim()) },
                enabled = name.isNotBlank()
            ) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
