package com.io.ab.music.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import coil.compose.AsyncImage
import com.io.ab.music.ui.viewmodel.PlayerViewModel
import androidx.hilt.navigation.compose.hiltViewModel
import com.io.ab.music.domain.model.Song
import com.io.ab.music.ui.components.SwipeDismissWrapper
import com.io.ab.music.ui.components.rememberArtworkModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(
    onBack   : () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val state by viewModel.playerState.collectAsState()
    val queue = state.queue

    SwipeDismissWrapper(onDismiss = onBack) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text("Play Queue", style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold)
                    Text("${queue.size} songs", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Rounded.KeyboardArrowDown, "Close",
                        modifier = Modifier.size(30.dp))
                }
            },
            actions = {
                IconButton(onClick = { viewModel.toggleShuffle() }) {
                    Icon(
                        Icons.Rounded.Shuffle, "Shuffle",
                        tint = if (state.shuffleEnabled) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(0.3f))

        if (queue.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.QueueMusic, null,
                        modifier = Modifier.size(72.dp),
                        tint     = MaterialTheme.colorScheme.outline.copy(0.4f))
                    Spacer(Modifier.height(12.dp))
                    Text("Queue is empty", style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                itemsIndexed(queue, key = { _, s -> s.id }) { index, song ->
                    val isCurrent = song.id == state.currentSong?.id
                    QueueSongRow(
                        index = index,
                        song = song,
                        isCurrent = isCurrent,
                        isPlaying = state.isPlaying,
                        onClick = { viewModel.playQueue(queue, index) }
                    )

                    if (index < queue.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 96.dp),
                            color    = MaterialTheme.colorScheme.outlineVariant.copy(0.25f)
                        )
                    }
                }
                item { Spacer(Modifier.height(60.dp)) }
            }
        }
    }
    }
}

@Composable
private fun QueueSongRow(
    index: Int,
    song: Song,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    val artworkModel = rememberArtworkModel(song.artworkUri, 128)

    Surface(
        onClick = onClick,
        color   = if (isCurrent)
                      MaterialTheme.colorScheme.primaryContainer.copy(0.3f)
                  else Color.Transparent,
        modifier= Modifier.fillMaxWidth()
    ) {
        Row(
            modifier         = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment= Alignment.CenterVertically
        ) {
            Box(
                modifier         = Modifier.width(32.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isCurrent && isPlaying) {
                    Icon(Icons.Rounded.GraphicEq, null,
                        tint     = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp))
                } else if (isCurrent) {
                    Icon(Icons.Rounded.PlayArrow, null,
                        tint     = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp))
                } else {
                    Text("${index + 1}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.width(10.dp))

            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(10.dp))
            ) {
                AsyncImage(
                    model = artworkModel, contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                if (artworkModel == null) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(
                            Brush.linearGradient(listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.secondary
                            ))
                        ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.MusicNote, null,
                            tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    song.title,
                    style    = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color    = if (isCurrent) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    song.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }

            Icon(Icons.Rounded.DragHandle, "Reorder",
                tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f),
                modifier = Modifier.size(20.dp).padding(start = 4.dp))
        }
    }
}
