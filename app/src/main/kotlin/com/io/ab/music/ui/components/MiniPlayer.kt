package com.io.ab.music.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.io.ab.music.domain.model.PlayerState

/**
 * FIX: Home/Video/Explore/Favorites/Settings each hard-coded a different bottom
 * spacer (90/100/100/110dp) for their last list item, none of which accounted
 * for the navigation bar height — so on 3-button-nav devices the MiniPlayer
 * (which sits above the nav bar via .navigationBarsPadding()) still overlapped
 * the last song/video. Use this in every scrollable screen's trailing item so
 * the reserved space is identical everywhere and always includes the real
 * navigation bar inset.
 */
@Composable
fun MiniPlayerBottomSpacer(extra: androidx.compose.ui.unit.Dp = 0.dp) {
    val navBarInset = androidx.compose.foundation.layout.WindowInsets.navigationBars
        .asPaddingValues().calculateBottomPadding()
    androidx.compose.foundation.layout.Spacer(
        modifier = androidx.compose.ui.Modifier.height(112.dp + navBarInset + extra)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MiniPlayer(
    playerState     : PlayerState,
    onPlayerClick   : () -> Unit,
    onPlayPause     : () -> Unit,
    onNext          : () -> Unit,
    onPrev          : () -> Unit = {},
    showPet         : Boolean    = true,
    dogPositionState: DogPetPositionState? = null,
    modifier        : Modifier  = Modifier
) {
    val song = playerState.currentSong ?: return

    // FIX: Use song.id as stable key — artworkModel is NOT recreated on play/pause,
    // only when the song actually changes. Prevents album art from flashing blank when paused.
    val artworkModel = rememberArtworkModel(song.artworkUri, 192, stableKey = song.id)

    val progressFraction = remember(playerState.progress, playerState.duration) {
        if (playerState.duration > 0) playerState.progress.toFloat() / playerState.duration else 0f
    }

    // Swipe gesture
    var swipeOffset by remember { mutableFloatStateOf(0f) }

    // FIX: previous implementation used rememberInfiniteTransition().animateFloat()
    // for spinAngle with NO isRunning gate — that animation runs forever in the
    // background regardless of play state. The displayAngle/frozenAngle logic only
    // froze what was *read* for display, but the underlying spin kept ticking every
    // frame, causing constant recomposition and a visible "still rotating while
    // paused" glitch on the album art. Replaced with an Animatable that is
    // explicitly started only while playing and is genuinely stopped (not just
    // masked) on pause — it keeps its current rotation value when stopped.
    val spinAnim = remember { Animatable(0f) }
    LaunchedEffect(playerState.isPlaying) {
        if (playerState.isPlaying) {
            spinAnim.animateTo(
                targetValue   = spinAnim.value + 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(12000, easing = LinearEasing)
                )
            )
        } else {
            spinAnim.stop()
        }
    }
    val displayAngle = spinAnim.value % 360f

    val infiniteTransition = rememberInfiniteTransition(label = "mini_anim")

    val bar1 by infiniteTransition.animateFloat(
        initialValue = 4f, targetValue = 20f,
        animationSpec = infiniteRepeatable(tween(380, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "b1"
    )
    val bar2 by infiniteTransition.animateFloat(
        initialValue = 12f, targetValue = 24f,
        animationSpec = infiniteRepeatable(tween(290, 80, FastOutSlowInEasing), RepeatMode.Reverse),
        label = "b2"
    )
    val bar3 by infiniteTransition.animateFloat(
        initialValue = 7f, targetValue = 18f,
        animationSpec = infiniteRepeatable(tween(330, 160, FastOutSlowInEasing), RepeatMode.Reverse),
        label = "b3"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow"
    )

    // Static fallback values when paused — avoids frame scheduling when idle
    val displayBar1   = if (playerState.isPlaying) bar1   else 8f
    val displayBar2   = if (playerState.isPlaying) bar2   else 14f
    val displayBar3   = if (playerState.isPlaying) bar3   else 10f
    val displayGlow   = if (playerState.isPlaying) glowAlpha else 0f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        // ── Dog pet sits above the mini player card ───────────────────────────
        if (showPet) {
            val petPosition = dogPositionState ?: rememberDogPetState()
            DogPet(
                isPlaying     = playerState.isPlaying,
                positionState = petPosition,
                modifier      = Modifier
                    .align(androidx.compose.ui.Alignment.TopStart)
                    .offset(y = (-62).dp)
                    .padding(start = 12.dp)
            )
        }

        // ── Card shell ────────────────────────────────────────────────────────
        Card(
            onClick   = onPlayerClick,
            modifier  = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            when {
                                swipeOffset >  110f -> onPrev()
                                swipeOffset < -110f -> onNext()
                            }
                            swipeOffset = 0f
                        },
                        onDragCancel = { swipeOffset = 0f },
                        onHorizontalDrag = { _, delta -> swipeOffset += delta }
                    )
                },
            shape     = RoundedCornerShape(18.dp),
            colors    = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Box {
                // Gradient tint
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.horizontalGradient(
                                0f   to MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                0.5f to MaterialTheme.colorScheme.secondary.copy(alpha = 0.06f),
                                1f   to Color.Transparent
                            )
                        )
                )

                Column {
                    // Thin progress bar at top
                    LinearProgressIndicator(
                        progress   = { progressFraction },
                        modifier   = Modifier.fillMaxWidth().height(2.dp),
                        color      = MaterialTheme.colorScheme.primary,
                        trackColor = Color.Transparent,
                        strokeCap  = androidx.compose.ui.graphics.StrokeCap.Round
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 9.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {

                        // ── Album artwork ─────────────────────────────────────
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(46.dp)
                        ) {
                            // Glow ring — only when playing
                            if (playerState.isPlaying) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .background(
                                            Brush.radialGradient(
                                                listOf(
                                                    MaterialTheme.colorScheme.primary.copy(alpha = displayGlow * 0.5f),
                                                    Color.Transparent
                                                )
                                            ),
                                            CircleShape
                                        )
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .graphicsLayer {
                                        rotationZ = displayAngle
                                    }
                                    .clip(CircleShape)
                                    .border(
                                        width = if (playerState.isPlaying) 1.5.dp else 1.dp,
                                        brush = if (playerState.isPlaying)
                                            Brush.sweepGradient(
                                                listOf(
                                                    MaterialTheme.colorScheme.primary,
                                                    MaterialTheme.colorScheme.tertiary,
                                                    MaterialTheme.colorScheme.primary
                                                )
                                            )
                                        else
                                            Brush.sweepGradient(
                                                listOf(
                                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                                                )
                                            ),
                                        shape = CircleShape
                                    )
                            ) {
                                if (song.artworkUri != null) {
                                    AsyncImage(
                                        model              = artworkModel,
                                        contentDescription = null,
                                        contentScale       = ContentScale.Crop,
                                        modifier           = Modifier.fillMaxSize().clip(CircleShape)
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
                                            text  = song.title.firstOrNull()?.uppercaseChar()?.toString() ?: "♪",
                                            style = MaterialTheme.typography.titleSmall.copy(
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }

                        // ── Song info ─────────────────────────────────────────
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Animated waveform bars (playing indicator)
                                if (playerState.isPlaying) {
                                    Row(
                                        modifier              = Modifier.padding(end = 5.dp),
                                        verticalAlignment     = Alignment.Bottom,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        listOf(displayBar1, displayBar2, displayBar3).forEach { h ->
                                            Box(
                                                modifier = Modifier
                                                    .width(2.dp)
                                                    .height(h.dp)
                                                    .clip(RoundedCornerShape(2.dp))
                                                    .background(MaterialTheme.colorScheme.primary)
                                            )
                                        }
                                    }
                                }

                                AnimatedContent(
                                    targetState    = song.title,
                                    transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) },
                                    label          = "miniTitle"
                                ) { title ->
                                    Text(
                                        text     = title,
                                        style    = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize   = 13.sp
                                        ),
                                        color    = if (playerState.isPlaying)
                                                       MaterialTheme.colorScheme.primary
                                                   else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.95f),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .basicMarquee(
                                                iterations         = Int.MAX_VALUE,
                                                initialDelayMillis = 1200
                                            ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Clip
                                    )
                                }
                            }

                            Spacer(Modifier.height(1.dp))

                            AnimatedContent(
                                targetState    = song.artist,
                                transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) },
                                label          = "miniArtist"
                            ) { artist ->
                                Text(
                                    text     = artist,
                                    style    = MaterialTheme.typography.bodySmall,
                                    color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        // ── Controls row ──────────────────────────────────────
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Previous
                            IconButton(
                                onClick  = onPrev,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.SkipPrevious, "Previous",
                                    modifier = Modifier.size(20.dp),
                                    tint     = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Play / Pause
                            AnimatedContent(
                                targetState    = playerState.isPlaying,
                                transitionSpec = { fadeIn(tween(100)) togetherWith fadeOut(tween(100)) },
                                label          = "playPause"
                            ) { playing ->
                                FilledIconButton(
                                    onClick  = onPlayPause,
                                    modifier = Modifier.size(40.dp),
                                    shape    = CircleShape,
                                    colors   = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor   = MaterialTheme.colorScheme.onPrimary
                                    )
                                ) {
                                    Icon(
                                        imageVector        = if (playing) Icons.Rounded.Pause
                                                             else Icons.Rounded.PlayArrow,
                                        contentDescription = if (playing) "Pause" else "Play",
                                        modifier           = Modifier.size(22.dp)
                                    )
                                }
                            }

                            // Next
                            IconButton(
                                onClick  = onNext,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.SkipNext, "Next",
                                    modifier = Modifier.size(20.dp),
                                    tint     = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
