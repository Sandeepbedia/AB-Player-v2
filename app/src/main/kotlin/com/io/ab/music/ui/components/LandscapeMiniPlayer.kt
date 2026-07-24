package com.io.ab.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.io.ab.music.domain.model.PlayerState
import com.io.ab.music.domain.model.RepeatMode

/**
 * Landscape-only bottom bar. Wider screen gives room to surface controls
 * that in portrait live one tap away inside PlayerScreen/QueueScreen
 * (shuffle, repeat, favorite, seek, queue) directly on the persistent bar —
 * every action here calls the exact same PlayerViewModel functions the
 * portrait MiniPlayer/PlayerScreen already use, so no new behavior is
 * introduced, only a wider layout for existing controls.
 */
@Composable
fun LandscapeMiniPlayer(
    playerState  : PlayerState,
    isFavorite   : Boolean,
    onPlayerClick: () -> Unit,
    onPlayPause  : () -> Unit,
    onNext       : () -> Unit,
    onPrev       : () -> Unit,
    onShuffle    : () -> Unit,
    onRepeat     : () -> Unit,
    onFavorite   : () -> Unit,
    onQueueClick : () -> Unit,
    onSeek       : (Long) -> Unit,
    modifier     : Modifier = Modifier
) {
    val song = playerState.currentSong ?: return
    val artworkModel = rememberArtworkModel(song.artworkUri, 192, stableKey = song.id)

    var isDragging   by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    val safeDuration = playerState.duration.takeIf { it > 0L } ?: 1L
    val liveFraction = if (playerState.duration > 0) playerState.progress.toFloat() / playerState.duration else 0f
    val displayFraction = if (isDragging) dragFraction else liveFraction

    Surface(
        modifier        = modifier.fillMaxWidth(),
        tonalElevation  = 3.dp,
        shadowElevation = 6.dp,
        color           = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ── Left: artwork + song/artist ─────────────────────────────
            Row(
                modifier          = Modifier
                    .weight(1f)
                    .clickable(onClick = onPlayerClick),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp))) {
                    if (song.artworkUri != null) {
                        AsyncImage(
                            model = artworkModel, contentDescription = null,
                            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize().background(
                                Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary))
                            ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.MusicNote, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        song.title,
                        style    = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        song.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.width(16.dp))

            // ── Center: playback controls + progress ────────────────────
            Column(
                modifier            = Modifier.weight(1.4f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(onClick = onPrev, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Rounded.SkipPrevious, "Previous", modifier = Modifier.size(22.dp))
                    }
                    FilledIconButton(onClick = onPlayPause, modifier = Modifier.size(40.dp), shape = CircleShape) {
                        Icon(
                            if (playerState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            null, modifier = Modifier.size(22.dp)
                        )
                    }
                    IconButton(onClick = onNext, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Rounded.SkipNext, "Next", modifier = Modifier.size(22.dp))
                    }
                }
                Row(
                    modifier          = Modifier.fillMaxWidth(0.92f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        formatMsShort(if (isDragging) (dragFraction * safeDuration).toLong() else playerState.progress),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value                 = displayFraction,
                        onValueChange         = { frac -> isDragging = true; dragFraction = frac },
                        onValueChangeFinished = {
                            onSeek((dragFraction * safeDuration).toLong())
                            isDragging = false
                        },
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                    )
                    Text(
                        formatMsShort(playerState.duration),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.width(16.dp))

            // ── Right: shuffle / repeat / favorite / queue ──────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onShuffle, modifier = Modifier.size(34.dp)) {
                    Icon(
                        Icons.Rounded.Shuffle, "Shuffle",
                        tint     = if (playerState.shuffleEnabled) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = onRepeat, modifier = Modifier.size(34.dp)) {
                    Icon(
                        if (playerState.repeatMode == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        "Repeat",
                        tint     = if (playerState.repeatMode != RepeatMode.OFF) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = onFavorite, modifier = Modifier.size(34.dp)) {
                    Icon(
                        if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        "Favorite",
                        tint     = if (isFavorite) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = onQueueClick, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Rounded.QueueMusic, "Queue", modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

private fun formatMsShort(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
