package com.io.ab.music.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.io.ab.music.domain.model.Song

/**
 * Bottom sheet for picking songs to add to a playlist. Shared between
 * PlaylistDetailScreen and the quick "add songs" shortcut on the Explore
 * screen's playlist row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSongsSheet(
    allSongs    : List<Song>,
    alreadyInIds: Set<Long>,
    onAdd       : (Song) -> Unit,
    onDismiss   : () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(allSongs, query) {
        if (query.isBlank()) allSongs
        else allSongs.filter {
            it.title.contains(query, ignoreCase = true) || it.artist.contains(query, ignoreCase = true)
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp)) {
            Text(
                "Add Songs",
                style    = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            OutlinedTextField(
                value         = query,
                onValueChange = { query = it },
                placeholder   = { Text("Search songs") },
                singleLine    = true,
                leadingIcon   = { Icon(Icons.Rounded.Search, null) },
                modifier      = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                itemsIndexed(filtered, key = { _, s -> s.id }) { _, song ->
                    val already = song.id in alreadyInIds
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                song.title,
                                style    = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                song.artist,
                                style    = MaterialTheme.typography.bodySmall,
                                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (already) {
                            Icon(
                                Icons.Rounded.CheckCircle, "Already added",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            IconButton(onClick = { onAdd(song) }) {
                                Icon(Icons.Rounded.AddCircleOutline, "Add")
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}
