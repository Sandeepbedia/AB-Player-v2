package com.io.ab.music.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.io.ab.music.domain.model.PlayerState
import com.io.ab.music.domain.model.RepeatMode

/**
 * Landscape-only single-row compact mini player (5th revision):
 *   drag handle → art + title/artist → ♡ ≡♪ → time/Slider/time → ⏮ ⏯ ⏭ → ⋮
 * Fixes vs previous build:
 *  - basicMarquee is an experimental foundation API → opted in explicitly
 *    (was causing a hard compile error, not just a warning, on this
 *    Kotlin/Compose-compiler config).
 *  - detectVerticalDragGestures is now called inline with a normal
 *    import instead of through a hand-rolled extension-function wrapper,
 *    which the compiler couldn't resolve (unresolved reference + the
 *    cascaded lambda-inference/operator-ambiguity errors were all a
 *    side effect of that one failed resolution).
 *  - Seek slider + Play/Pause button no longer hardcode red — they use
 *    MaterialTheme.colorScheme.primary/onPrimary like the rest of the
 *    app, so whatever dynamic/RGB accent colour the app picks is used
 *    automatically instead of a fixed colour.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LandscapeBottomBar(
    playerState     : PlayerState,
    isFavorite      : Boolean,
    onPlayerClick   : () -> Unit,
    onPlayPause     : () -> Unit,
    onNext          : () -> Unit,
    onPrev          : () -> Unit,
    onSeek          : (Long) -> Unit,
    onToggleShuffle : () -> Unit,
    onCycleRepeat   : () -> Unit,
    onToggleFavorite: () -> Unit,
    onQueueClick    : () -> Unit,
    modifier        : Modifier = Modifier
) {
    val song = playerState.currentSong ?: return

    var isDragging by remember { mutableStateOf(false) }
    var dragValue  by remember { mutableFloatStateOf(0f) }
    var swipeUpAccum by remember { mutableFloatStateOf(0f) }
    var moreMenuExpanded by remember { mutableStateOf(false) }

    val sliderValue = if (isDragging) dragValue
        else if (playerState.duration > 0) playerState.progress.toFloat() / playerState.duration else 0f

    val playInteractionSource = remember { MutableInteractionSource() }
    val isPlayPressed by playInteractionSource.collectIsPressedAsState()
    val playScale by animateFloatAsState(
        targetValue   = if (isPlayPressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label         = "play_pause_scale"
    )

    Surface(
        modifier       = modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (swipeUpAccum < -36f) onPlayerClick()
                        swipeUpAccum = 0f
                    },
                    onDragCancel = { swipeUpAccum = 0f },
                    onVerticalDrag = { _, dragAmount ->
                        swipeUpAccum += dragAmount
                    }
                )
            },
        color          = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {

            // ── Drag handle ─────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 6.dp, bottom = 2.dp)
                    .size(width = 34.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            )

            // ── Single control row ──────────────────────────────────────
            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ── Artwork + title/artist ───────────────────────────────
                val artworkModel = rememberArtworkModel(song.artworkUri, 160, stableKey = song.id)
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication        = null,
                            onClick           = onPlayerClick
                        )
                ) {
                    AsyncImage(
                        model = artworkModel, contentDescription = null,
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                    )
                    if (artworkModel == null) {
                        Box(
                            modifier = Modifier.fillMaxSize().background(
                                Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary))
                            ),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Rounded.MusicNote, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
                    }
                }

                Spacer(Modifier.width(10.dp))

                Column(modifier = Modifier.width(150.dp)) {
                    Text(
                        song.title,
                        style    = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color    = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = if (playerState.isPlaying)
                            Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 1200)
                        else Modifier
                    )
                    Text(
                        song.artist,
                        style    = MaterialTheme.typography.labelSmall,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.width(10.dp))

                // ── Favorite + Queue ─────────────────────────────────────
                IconButton(onClick = onToggleFavorite, modifier = Modifier.size(30.dp)) {
                    Icon(
                        if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        "Favorite", modifier = Modifier.size(18.dp),
                        tint = if (isFavorite) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onQueueClick, modifier = Modifier.size(30.dp)) {
                    Icon(
                        Icons.Rounded.QueueMusic, "Queue", modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.width(6.dp))

                // ── Time — Seek slider — Time (flexible, takes remaining space) ──
                Text(
                    formatMs(if (isDragging) (dragValue * playerState.duration).toLong() else playerState.progress),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value                 = sliderValue.coerceIn(0f, 1f),
                    onValueChange         = { isDragging = true; dragValue = it },
                    onValueChangeFinished = {
                        onSeek((dragValue * playerState.duration).toLong())
                        isDragging = false
                    },
                    colors = SliderDefaults.colors(
                        thumbColor         = MaterialTheme.colorScheme.primary,
                        activeTrackColor   = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
                    ),
                    modifier = Modifier.weight(1f).padding(horizontal = 10.dp).height(20.dp)
                )
                Text(
                    formatMs(playerState.duration),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.width(10.dp))

                // ── Prev / Play / Next ───────────────────────────────────
                IconButton(onClick = onPrev, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Rounded.SkipPrevious, "Previous", modifier = Modifier.size(22.dp))
                }
                FilledIconButton(
                    onClick           = onPlayPause,
                    interactionSource = playInteractionSource,
                    modifier          = Modifier.size(42.dp).scale(playScale),
                    shape             = CircleShape,
                    colors            = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor   = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        if (playerState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        if (playerState.isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(22.dp)
                    )
                }
                IconButton(onClick = onNext, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Rounded.SkipNext, "Next", modifier = Modifier.size(22.dp))
                }

                // ── More (Shuffle / Repeat / Expand Player) ──────────────
                Box {
                    IconButton(onClick = { moreMenuExpanded = true }, modifier = Modifier.size(30.dp)) {
                        Icon(
                            Icons.Rounded.MoreVert, "More", modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(expanded = moreMenuExpanded, onDismissRequest = { moreMenuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(if (playerState.shuffleEnabled) "Shuffle: On" else "Shuffle: Off") },
                            leadingIcon = {
                                Icon(
                                    Icons.Rounded.Shuffle, null,
                                    tint = if (playerState.shuffleEnabled) MaterialTheme.colorScheme.primary
                                           else LocalContentColor.current
                                )
                            },
                            onClick = { onToggleShuffle(); moreMenuExpanded = false }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    when (playerState.repeatMode) {
                                        RepeatMode.OFF -> "Repeat: Off"
                                        RepeatMode.ALL -> "Repeat: All"
                                        RepeatMode.ONE -> "Repeat: One"
                                    }
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    when (playerState.repeatMode) {
                                        RepeatMode.ONE -> Icons.Rounded.RepeatOne
                                        else           -> Icons.Rounded.Repeat
                                    },
                                    null,
                                    tint = if (playerState.repeatMode != RepeatMode.OFF) MaterialTheme.colorScheme.primary
                                           else LocalContentColor.current
                                )
                            },
                            onClick = { onCycleRepeat(); moreMenuExpanded = false }
                        )
                        DropdownMenuItem(
                            text        = { Text("Expand Player") },
                            leadingIcon = { Icon(Icons.Rounded.KeyboardArrowUp, null) },
                            onClick     = { moreMenuExpanded = false; onPlayerClick() }
                        )
                    }
                }
            }
        }
    }
}

private fun formatMs(ms: Long): String {
    val safe = ms.coerceAtLeast(0L)
    val totalSec = safe / 1000
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}
