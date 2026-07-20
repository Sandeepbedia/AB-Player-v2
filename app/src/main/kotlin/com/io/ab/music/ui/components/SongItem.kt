package com.io.ab.music.ui.components

import android.content.ContentValues
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.io.ab.music.domain.model.Song
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongItem(
    song            : Song,
    isPlaying       : Boolean,
    isFavorite      : Boolean,
    onClick         : () -> Unit,
    onFavoriteToggle: () -> Unit,
    modifier        : Modifier = Modifier,
    onAddToPlaylist : ((Song) -> Unit)? = null,
    onDelete        : ((Song) -> Unit)? = null,
    onShare         : ((Song) -> Unit)? = null,
    onPlayNext      : ((Song) -> Unit)? = null,
    onAddToQueue    : ((Song) -> Unit)? = null,
    onGoToArtist    : ((String) -> Unit)? = null,
    onGoToAlbum     : ((String) -> Unit)? = null,
    onRename        : ((Song, String) -> Unit)? = null
) {
    val context      = LocalContext.current
    val artworkModel = rememberArtworkModel(song.artworkUri, 128)
    val durationText = remember(song.duration) { formatDurationMs(song.duration) }

    val primary   = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val artworkBg = remember(primary, secondary) {
        Brush.linearGradient(listOf(primary, secondary))
    }
    val playingOverlay = remember(primary) { primary.copy(alpha = 0.45f) }

    var showMenu        by remember { mutableStateOf(false) }
    var showInfoDialog  by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }

    if (showInfoDialog) {
        SongInfoDialog(song = song, onDismiss = { showInfoDialog = false })
    }

    if (showRenameDialog) {
        RenameDialog(
            currentName = song.title,
            onDismiss   = { showRenameDialog = false },
            onConfirm   = { newName ->
                onRename?.invoke(song, newName)
                showRenameDialog = false
            }
        )
    }

    Surface(
        onClick  = onClick,
        modifier = modifier.fillMaxWidth(),
        color    = if (isPlaying) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)
                   else Color.Transparent
    ) {
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ── Artwork box ─────────────────────────────────────────────────
            Box(modifier = Modifier.size(52.dp).clip(RoundedCornerShape(11.dp))) {
                Box(
                    modifier         = Modifier.fillMaxSize().background(artworkBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.MusicNote, null,
                        tint = Color.White, modifier = Modifier.size(22.dp))
                }
                AsyncImage(
                    model        = artworkModel,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier     = Modifier.fillMaxSize()
                )
                if (isPlaying) {
                    Box(
                        modifier         = Modifier.fillMaxSize().background(playingOverlay),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.GraphicEq, null,
                            tint     = Color.White,
                            modifier = Modifier.size(22.dp))
                    }
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    song.title,
                    style    = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color    = if (isPlaying) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    modifier = if (isPlaying) Modifier.basicMarquee(
                        iterations         = Int.MAX_VALUE,
                        initialDelayMillis = 800
                    ) else Modifier,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    song.artist,
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        durationText,
                        style    = MaterialTheme.typography.bodySmall,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (song.hasLyrics) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                "LYRICS",
                                style    = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color    = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }

            // ── Favorite toggle ─────────────────────────────────────────────
            IconButton(onClick = onFavoriteToggle, modifier = Modifier.size(36.dp)) {
                Icon(
                    if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    "Favorite",
                    tint     = if (isFavorite) MaterialTheme.colorScheme.error
                               else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }

            // ── Three-dot menu ──────────────────────────────────────────────
            Box {
                IconButton(onClick = { showMenu = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Rounded.MoreVert, "More options",
                        tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp))
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    // ── Play actions ──────────────────────────────────────
                    DropdownMenuItem(
                        text        = { Text("Play Now") },
                        onClick     = { onClick(); showMenu = false },
                        leadingIcon = { Icon(Icons.Rounded.PlayArrow, null) }
                    )
                    DropdownMenuItem(
                        text        = { Text("Play Next") },
                        onClick     = { onPlayNext?.invoke(song); showMenu = false },
                        leadingIcon = { Icon(Icons.Rounded.QueuePlayNext, null) }
                    )
                    DropdownMenuItem(
                        text        = { Text("Add to Queue") },
                        onClick     = { onAddToQueue?.invoke(song); showMenu = false },
                        leadingIcon = { Icon(Icons.Rounded.AddToQueue, null) }
                    )
                    HorizontalDivider()
                    // ── Library actions ───────────────────────────────────
                    DropdownMenuItem(
                        text        = { Text("Add to Playlist") },
                        onClick     = { onAddToPlaylist?.invoke(song); showMenu = false },
                        leadingIcon = { Icon(Icons.Rounded.PlaylistAdd, null) }
                    )
                    DropdownMenuItem(
                        text        = { Text(if (isFavorite) "Remove from Favorites" else "Add to Favorites") },
                        onClick     = { onFavoriteToggle(); showMenu = false },
                        leadingIcon = {
                            Icon(
                                if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                null,
                                tint = if (isFavorite) MaterialTheme.colorScheme.error
                                       else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    )
                    HorizontalDivider()
                    // ── Go to ──────────────────────────────────────────────
                    if (onGoToArtist != null) {
                        DropdownMenuItem(
                            text        = { Text("Go to Artist") },
                            onClick     = { onGoToArtist(song.artist); showMenu = false },
                            leadingIcon = { Icon(Icons.Rounded.Person, null) }
                        )
                    }
                    if (onGoToAlbum != null) {
                        DropdownMenuItem(
                            text        = { Text("Go to Album") },
                            onClick     = { onGoToAlbum(song.album); showMenu = false },
                            leadingIcon = { Icon(Icons.Rounded.Album, null) }
                        )
                    }
                    HorizontalDivider()
                    // ── Info + Share ───────────────────────────────────────
                    DropdownMenuItem(
                        text        = { Text("Song Info") },
                        onClick     = { showInfoDialog = true; showMenu = false },
                        leadingIcon = { Icon(Icons.Rounded.Info, null) }
                    )
                    DropdownMenuItem(
                        text        = { Text("Share") },
                        onClick     = {
                            onShare?.invoke(song) ?: shareSongFile(context, song)
                            showMenu = false
                        },
                        leadingIcon = { Icon(Icons.Rounded.Share, null) }
                    )
                    // ── Set as Ringtone ────────────────────────────────────
                    DropdownMenuItem(
                        text        = { Text("Set as Ringtone") },
                        onClick     = { setAsRingtone(context, song); showMenu = false },
                        leadingIcon = { Icon(Icons.Rounded.NotificationsActive, null) }
                    )
                    // ── Rename ─────────────────────────────────────────────
                    if (onRename != null) {
                        DropdownMenuItem(
                            text        = { Text("Rename File") },
                            onClick     = { showRenameDialog = true; showMenu = false },
                            leadingIcon = { Icon(Icons.Rounded.Edit, null) }
                        )
                    }
                    // ── Delete ─────────────────────────────────────────────
                    if (onDelete != null) {
                        HorizontalDivider()
                        DropdownMenuItem(
                            text        = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            onClick     = { onDelete(song); showMenu = false },
                            leadingIcon = { Icon(Icons.Rounded.Delete, null,
                                tint = MaterialTheme.colorScheme.error) }
                        )
                    }
                }
            }
        }
    }
}

// ── Song Info Dialog ──────────────────────────────────────────────────────────
@Composable
private fun SongInfoDialog(song: Song, onDismiss: () -> Unit) {
    val sizeKb = if (song.size > 0) "%.1f MB".format(song.size / 1_048_576.0) else "Unknown"
    AlertDialog(
        onDismissRequest = onDismiss,
        icon  = { Icon(Icons.Rounded.Info, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("Song Info") },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                InfoRow("Title",    song.title)
                InfoRow("Artist",   song.artist)
                InfoRow("Album",    song.album)
                InfoRow("Duration", formatDurationMs(song.duration))
                InfoRow("Format",   song.path.substringAfterLast(".").uppercase())
                InfoRow("Size",     sizeKb)
                InfoRow("Path",     song.path)
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, shape = RoundedCornerShape(12.dp)) { Text("Close") }
        }
    )
}

// ── Rename Dialog ─────────────────────────────────────────────────────────────
@Composable
fun RenameDialog(
    currentName: String,
    onDismiss  : () -> Unit,
    onConfirm  : (String) -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon  = { Icon(Icons.Rounded.Edit, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("Rename File") },
        text  = {
            OutlinedTextField(
                value         = name,
                onValueChange = { name = it },
                singleLine    = true,
                label         = { Text("File name") },
                modifier      = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick  = { if (name.isNotBlank()) onConfirm(name.trim()) },
                shape    = RoundedCornerShape(12.dp),
                enabled  = name.isNotBlank() && name.trim() != currentName
            ) { Text("Rename") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(12.dp)) { Text("Cancel") }
        }
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column {
        Text(label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary)
        Text(value,
            style    = MaterialTheme.typography.bodySmall,
            color    = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis)
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun shareSongFile(context: android.content.Context, song: Song) {
    try {
        val file = File(song.path)
        val uri  = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type  = "audio/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share ${song.title}"))
    } catch (e: Exception) {
        Toast.makeText(context, "Cannot share: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

private fun setAsRingtone(context: android.content.Context, song: Song) {
    try {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            if (!Settings.System.canWrite(context)) {
                val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                    Uri.parse("package:${context.packageName}"))
                context.startActivity(intent)
                Toast.makeText(context, "Grant 'Modify system settings' permission, then try again", Toast.LENGTH_LONG).show()
                return
            }
        }
        val cv = ContentValues().apply {
            put(MediaStore.MediaColumns.DATA,          song.path)
            put(MediaStore.MediaColumns.TITLE,         song.title)
            put(MediaStore.MediaColumns.MIME_TYPE,     "audio/*")
            put(MediaStore.Audio.Media.IS_RINGTONE,    true)
            put(MediaStore.Audio.Media.IS_NOTIFICATION,false)
            put(MediaStore.Audio.Media.IS_ALARM,       false)
            put(MediaStore.Audio.Media.IS_MUSIC,       false)
        }
        val uri = MediaStore.Audio.Media.getContentUriForPath(song.path) ?: MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        context.contentResolver.delete(uri, "${MediaStore.MediaColumns.DATA}=\"${song.path}\"", null)
        val newUri = context.contentResolver.insert(uri, cv)
        if (newUri != null) {
            RingtoneManager.setActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE, newUri)
            Toast.makeText(context, "\"${song.title}\" set as ringtone", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Failed to set ringtone", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

internal fun formatDurationMs(ms: Long): String {
    val sec = ms / 1000
    return "%d:%02d".format(sec / 60, sec % 60)
}
