package com.io.ab.music.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import coil.compose.AsyncImage
import com.io.ab.music.domain.model.PlayerState
import com.io.ab.music.ui.navigation.BottomNavItem

/**
 * Landscape-only permanent left navigation sidebar.
 *
 * Structure (per Landscape PRD §2, updated per user feedback):
 *   Logo -> Home/Video/Explore/Favorites/Settings -> (spacer) -> Mini Player
 *
 * This is purely a structural/layout addition for landscape orientation —
 * it reuses the same [BottomNavItem] list / icons / labels / selection and
 * click-handling that the portrait top tab bar already drives, so no nav
 * behaviour changes, only where it's rendered. The old static "AB Player /
 * Music for everyone" profile row at the bottom has been replaced with a
 * compact mini music player: a circular, rotating (while playing) artwork
 * disc + marquee title/artist. No Prev/Play-Pause/Next buttons here —
 * tap it to open the full Now Playing screen, or swipe left/right on it
 * to skip to the next/previous song (same gesture as the portrait
 * MiniPlayer's swipe-to-skip).
 */
@Composable
fun LandscapeSidebar(
    items         : List<BottomNavItem>,
    selectedIndex : Int,
    onItemClick   : (Int) -> Unit,
    playerState   : PlayerState,
    onPlayerClick : () -> Unit,
    onNext        : () -> Unit,
    onPrev        : () -> Unit,
    modifier      : Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(196.dp)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f))
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 18.dp)
    ) {
        // ── App logo ─────────────────────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint     = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text  = "AB Player",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1
            )
        }

        Spacer(Modifier.height(28.dp))

        // ── Nav items ────────────────────────────────────────────────────
        items.forEachIndexed { index, item ->
            SidebarNavRow(
                label    = item.label,
                icon     = if (index == selectedIndex) item.selectedIcon else item.unselectedIcon,
                selected = index == selectedIndex,
                onClick  = { onItemClick(index) }
            )
            Spacer(Modifier.height(4.dp))
        }

        Spacer(Modifier.weight(1f))

        // ── Mini music player (bottom) ──────────────────────────────────
        HorizontalDivider(
            thickness = 0.5.dp,
            color     = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        )
        Spacer(Modifier.height(10.dp))
        SidebarMiniPlayer(
            playerState   = playerState,
            onPlayerClick = onPlayerClick,
            onNext        = onNext,
            onPrev        = onPrev
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SidebarMiniPlayer(
    playerState  : PlayerState,
    onPlayerClick: () -> Unit,
    onNext       : () -> Unit,
    onPrev       : () -> Unit
) {
    val song = playerState.currentSong

    if (song == null) {
        Text(
            "Nothing playing",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    // Spins continuously while playing, freezes (keeps its angle) when paused —
    // same Animatable-driven approach as the portrait MiniPlayer's vinyl spin.
    val spinAnim = remember { Animatable(0f) }
    LaunchedEffect(playerState.isPlaying) {
        if (playerState.isPlaying) {
            spinAnim.animateTo(
                targetValue   = spinAnim.value + 360f,
                animationSpec = infiniteRepeatable(animation = tween(12000, easing = LinearEasing))
            )
        } else {
            spinAnim.stop()
        }
    }
    val displayAngle = spinAnim.value % 360f

    var swipeOffset by remember { mutableFloatStateOf(0f) }

    Card(
        onClick   = onPlayerClick,
        modifier  = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        when {
                            swipeOffset >  70f -> onPrev()
                            swipeOffset < -70f -> onNext()
                        }
                        swipeOffset = 0f
                    },
                    onDragCancel = { swipeOffset = 0f },
                    onHorizontalDrag = { _, delta -> swipeOffset += delta }
                )
            },
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier          = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ── Circular, rotating album art ─────────────────────────────
            val artworkModel = rememberArtworkModel(song.artworkUri, 160, stableKey = song.id)
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .graphicsLayer { rotationZ = displayAngle }
                    .clip(CircleShape)
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
                    ) {
                        Icon(Icons.Rounded.MusicNote, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }

            Spacer(Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    song.title,
                    style    = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
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
        }
    }
}

@Composable
private fun SidebarNavRow(
    label   : String,
    icon    : androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick : () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val bg    = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else Color.Transparent
    val fg    = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        color   = bg,
        shape   = RoundedCornerShape(12.dp),
        modifier= Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interactionSource,
                indication        = null,
                onClick           = onClick
            )
    ) {
        Row(
            modifier          = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = label, tint = fg, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(
                text     = label,
                style    = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                ),
                color    = fg,
                maxLines = 1
            )
        }
    }
}
