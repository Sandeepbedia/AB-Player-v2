package com.io.ab.music.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
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
import com.io.ab.music.domain.model.PlayerState
import com.io.ab.music.domain.model.Song

/**
 * Landscape-only right-hand panel: "Playing Now" + "Queue (Up Next)".
 * Structural addition only (PRD §4) — reads the same [PlayerState] the
 * portrait Now-Playing/Queue screens already use; no playback logic changes.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LandscapeQueuePanel(
    playerState     : PlayerState,
    onQueueItemClick: (Int) -> Unit,
    onPlayerClick   : () -> Unit,
    modifier        : Modifier = Modifier
) {
    val song = playerState.currentSong

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(268.dp)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f))
            .statusBarsPadding()
            .padding(16.dp)
    ) {
        // ── Playing Now ──────────────────────────────────────────────────
        SectionLabel(icon = Icons.Rounded.GraphicEq, text = "Playing Now")
        Spacer(Modifier.height(10.dp))

        if (song != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication        = null,
                        onClick           = onPlayerClick
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val artworkModel = rememberArtworkModel(song.artworkUri, 160, stableKey = song.id)
                Box(
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp))
                ) {
                    AsyncImage(
                        model = artworkModel, contentDescription = null,
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                    )
                    if (artworkModel == null) {
                        Box(
                            modifier = Modifier.fillMaxSize().background(
                                Brush.linearGradient(
                                    listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)
                                )
                            ),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Rounded.MusicNote, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        song.title,
                        style    = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color    = MaterialTheme.colorScheme.primary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = if (playerState.isPlaying)
                            Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 1200)
                        else Modifier
                    )
                    Text(
                        song.artist,
                        style    = MaterialTheme.typography.bodySmall,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(6.dp))
                MiniVisualizerBars(isPlaying = playerState.isPlaying)
            }
        } else {
            Text(
                "Nothing playing",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(22.dp))

        // ── Queue (Up Next) ──────────────────────────────────────────────
        SectionLabel(icon = Icons.Rounded.QueueMusic, text = "Queue (Up Next)")
        Spacer(Modifier.height(6.dp))

        val upNext = remember(playerState.queue, playerState.currentQueueIndex) {
            if (playerState.queue.isEmpty()) emptyList()
            else playerState.queue.drop(playerState.currentQueueIndex + 1)
        }

        if (upNext.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().padding(top = 24.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Queue is empty",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier              = Modifier.weight(1f),
                verticalArrangement   = Arrangement.spacedBy(2.dp)
            ) {
                itemsIndexed(upNext, key = { _, s -> s.id }) { idx, song ->
                    QueueUpNextRow(
                        song    = song,
                        onClick = { onQueueItemClick(playerState.currentQueueIndex + 1 + idx) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            text,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun QueueUpNextRow(song: Song, onClick: () -> Unit) {
    val artworkModel = rememberArtworkModel(song.artworkUri, 96, stableKey = song.id)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Rounded.DragHandle, null,
            tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp))) {
            AsyncImage(
                model = artworkModel, contentDescription = null,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
            )
            if (artworkModel == null) {
                Box(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary))
                    ),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Rounded.MusicNote, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                song.title,
                style    = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color    = MaterialTheme.colorScheme.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(
                song.artist,
                style    = MaterialTheme.typography.labelSmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(4.dp))
        Text(
            song.formattedDuration,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        IconButton(onClick = { /* handled via row click; kept for PRD "More Menu" affordance */ }, modifier = Modifier.size(28.dp)) {
            Icon(
                Icons.Rounded.MoreVert, "More",
                tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun MiniVisualizerBars(isPlaying: Boolean) {
    val infinite = rememberInfiniteTransition(label = "queue_panel_vis")
    val bar1 by infinite.animateFloat(4f, 14f, infiniteRepeatable(tween(380, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "qb1")
    val bar2 by infinite.animateFloat(8f, 16f, infiniteRepeatable(tween(300, 60, FastOutSlowInEasing), RepeatMode.Reverse), label = "qb2")
    val bar3 by infinite.animateFloat(5f, 12f, infiniteRepeatable(tween(340, 120, FastOutSlowInEasing), RepeatMode.Reverse), label = "qb3")

    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        listOf(bar1, bar2, bar3).forEach { h ->
            Box(
                modifier = Modifier
                    .width(2.5.dp)
                    .height(if (isPlaying) h.dp else 5.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}
