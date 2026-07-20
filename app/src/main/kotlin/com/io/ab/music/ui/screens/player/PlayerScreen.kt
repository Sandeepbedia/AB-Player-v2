package com.io.ab.music.ui.screens.player

import android.content.Intent
import androidx.compose.animation.core.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.io.ab.music.domain.model.RepeatMode
import androidx.hilt.navigation.compose.hiltViewModel
import com.io.ab.music.ui.components.rememberArtworkModel
import com.io.ab.music.ui.theme.LocalThemeMode
import com.io.ab.music.ui.theme.ThemeMode
import com.io.ab.music.ui.theme.NeonDepthPurple
import com.io.ab.music.ui.theme.NeonDepthPink
import com.io.ab.music.ui.theme.NeonDepthPeach
import com.io.ab.music.ui.theme.NeonDepthSurface
import com.io.ab.music.ui.viewmodel.LrcLine
import com.io.ab.music.ui.viewmodel.LyricsState
import com.io.ab.music.ui.viewmodel.LyricsSource
import com.io.ab.music.ui.viewmodel.PlayerViewModel

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    onBack      : () -> Unit,
    onQueueClick: () -> Unit = {},
    viewModel   : PlayerViewModel = hiltViewModel()
) {
    val state        by viewModel.playerState.collectAsState()
    val isFavorite   by viewModel.currentSongIsFavorite.collectAsState()
    val sleepRemaining by viewModel.sleepTimerRemaining.collectAsState()
    val context      = LocalContext.current
    val song         = state.currentSong
    val songTitle    = song?.title?.takeIf { it.isNotBlank() } ?: "No song selected"
    val songArtist   = song?.artist?.takeIf { it.isNotBlank() } ?: "Unknown Artist"
    // FIX: Use song?.id as stableKey so artwork is not recreated on play/pause toggle —
    // only when the actual song changes. This prevents the album art from flashing blank when paused.
    val artworkModel = rememberArtworkModel(song?.artworkUri, 640, stableKey = song?.id)
    val progressFraction = remember(state.progress, state.duration) {
        if (state.duration > 0) state.progress.toFloat() / state.duration else 0f
    }
    val progressText = remember(state.progress) { formatMs(state.progress) }
    val durationText = remember(state.duration)  { formatMs(state.duration) }

    // ── Dialog state ──────────────────────────────────────────────────────────
    var showEqDialog    by remember { mutableStateOf(false) }
    var showSleepDialog by remember { mutableStateOf(false) }
    var showMoreMenu    by remember { mutableStateOf(false) }
    var showLyrics      by remember { mutableStateOf(false) }

    // Clear lyrics on song change; if sheet is already open → auto-refetch
    LaunchedEffect(song?.id) {
        viewModel.clearLyrics()
        if (showLyrics) viewModel.fetchLyrics()
    }

    // Artwork pop animation disabled for performance
    val artworkScale = 1f

    // ── Dialogs ───────────────────────────────────────────────────────────────
    if (showEqDialog) {
        EqDialog(
            viewModel = viewModel,
            artworkUri = song?.artworkUri,
            songId = song?.id,
            onDismiss = { showEqDialog = false }
        )
    }
    if (showSleepDialog) {
        SleepTimerPlayerDialog(
            remaining = sleepRemaining,
            artworkUri = song?.artworkUri,
            songId = song?.id,
            onSelect  = { minutes ->
                viewModel.startSleepTimer(minutes)
                showSleepDialog = false
            },
            onCancel  = {
                viewModel.cancelSleepTimer()
                showSleepDialog = false
            },
            onDismiss = { showSleepDialog = false }
        )
    }
    if (showMoreMenu) {
        MoreOptionsSheet(
            artworkUri  = song?.artworkUri,
            songId      = song?.id,
            songTitle   = songTitle,
            isFavorite  = isFavorite,
            onFavorite  = { song?.id?.let { viewModel.toggleFavorite(it) } },
            onQueue     = { onQueueClick(); showMoreMenu = false },
            onShare     = {
                val songPath = song?.path
                if (songPath != null) {
                    try {
                        val file = java.io.File(songPath)
                        val uri = androidx.core.content.FileProvider.getUriForFile(
                            context, "${context.packageName}.provider", file
                        )
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "audio/*"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share ${songTitle}"))
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(context, "Cannot share: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                    }
                } else {
                    android.widget.Toast.makeText(context, "Song path not available", android.widget.Toast.LENGTH_SHORT).show()
                }
                showMoreMenu = false
            },
            onDismiss   = { showMoreMenu = false }
        )
    }
    if (showLyrics) {
        LyricsSheet(
            artworkUri       = song?.artworkUri,
            songId           = song?.id,
            lyricsState      = viewModel.lyricsState.collectAsState().value,
            lyricsSource     = viewModel.lyricsSource.collectAsState().value,
            currentLineIndex = viewModel.currentLyricIndex.collectAsState().value,
            onRetry          = { viewModel.fetchLyrics() },
            onDismiss        = {
                showLyrics = false
                viewModel.clearLyrics()
            }
        )
    }

    // ── Dynamic background color (Spotify-style) ────────────────────────────────
    val themeMode = LocalThemeMode.current
    val isNeonDepth = themeMode == ThemeMode.AMOLED_NEON_DEPTH
    val artworkColors = rememberArtworkColors(song?.artworkUri, song?.id)
    val dominantColor = artworkColors?.primary
    val secondaryColor = artworkColors?.secondary ?: dominantColor
    val accentColor = artworkColors?.accent ?: secondaryColor
    val defaultBg     = MaterialTheme.colorScheme.background
    // For light themes: use a darkened/saturated version of the dominant color so content stays readable.
    // For dark/AMOLED: use dominant color as-is for immersive Spotify-style look.
    val isLightTheme = defaultBg.luminance() > 0.5f
    val targetBg = when {
        isNeonDepth -> NeonDepthPurple.deepen(0.82f)
        dominantColor != null && isLightTheme -> dominantColor.deepen(0.58f)
        dominantColor != null -> dominantColor.deepen(0.82f)
        else -> defaultBg
    }
    val targetTopColor = when {
        isNeonDepth -> NeonDepthPurple.deepen(0.90f)
        accentColor != null && isLightTheme -> accentColor.deepen(0.64f)
        accentColor != null -> accentColor.deepen(0.90f)
        else -> defaultBg
    }
    val targetMidColor = when {
        isNeonDepth -> NeonDepthPink.deepen(0.62f)
        secondaryColor != null && isLightTheme -> secondaryColor.deepen(0.48f)
        secondaryColor != null -> secondaryColor.deepen(0.62f)
        else -> defaultBg
    }
    // Background color transitions disabled for performance — instant colors
    val animatedBg       = targetBg
    val animatedTopColor = targetTopColor
    val animatedMidColor = targetMidColor

    // isBgDark rules:
    // 1. Light theme + dominant color extracted → we darkened it above, so always dark → White text
    // 2. Dark theme (isLightTheme=false) → background is always dark → always White text
    // 3. Light theme + no dominant color → check luminance normally
    val isBgDark = when {
        !isLightTheme                          -> true   // dark/AMOLED theme → always white text
        isLightTheme && dominantColor != null  -> true   // light theme, darkened palette → white text
        else                                   -> animatedBg.luminance() < 0.5f
    }
    val onDynamicBg      = if (isBgDark) Color.White else Color.Black
    val onDynamicBgMuted = onDynamicBg.copy(alpha = 0.70f)

    // ── Swipe drag tracking ───────────────────────────────────────────────────
    // FIX: The old implementation could leave the screen "stuck" mid-swipe
    // because every onDragEnd/onDragCancel launched a NEW spring-back coroutine
    // without cancelling any previous one — overlapping animations on the same
    // Animatable raced each other and could settle on a non-zero offset, leaving
    // the screen frozen in a half-dismissed state after the finger lifted.
    //
    // Fix: keep a single tracked job (`settleJob`) and always cancel it before
    // starting a new animation. Use Animatable.animateTo with `snapTo`-safe
    // sequencing so the offset always resolves to either 0 (snap back) or fully
    // off-screen (dismiss), never stuck in between.
    // PERF FIX: dragOffsetY used to be an Animatable whose value was updated via
    // coroutineScope.launch { snapTo() } inside onDrag — that spun up a brand-new
    // coroutine for every single pointer-move event while swiping (can be 60-120/sec),
    // which is a real source of stutter on this screen. Live dragging now writes a
    // plain snapshot state directly (instant, no coroutine); a coroutine is only
    // started once per gesture-end to animate the settle.
    var dragOffsetY    by remember { mutableFloatStateOf(0f) }
    val coroutineScope = rememberCoroutineScope()
    var settleJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val screenHeightPx = with(LocalDensity.current) { 1000.dp.toPx() } // generous off-screen distance

    fun settleClosed() {
        settleJob?.cancel()
        settleJob = coroutineScope.launch {
            dragOffsetY = screenHeightPx
            onBack()
        }
    }

    fun settleOpen() {
        settleJob?.cancel()
        settleJob = coroutineScope.launch {
            dragOffsetY = 0f
        }
    }

    // Bottom gradient color:
    // • Light theme + dominant color → darken to 40% so text stays white/readable
    // • Dark theme + dominant color  → darken to 15% so it merges into near-black nicely
    // • No dominant color            → surface (near-black in dark, white in light)
    val bottomGradientColor = when {
        isNeonDepth -> NeonDepthPeach.deepen(0.16f)
        dominantColor != null && isLightTheme -> dominantColor.deepen(0.28f)
        dominantColor != null -> dominantColor.deepen(0.16f)
        !isLightTheme -> Color(0xFF08080A.toInt())
        else -> MaterialTheme.colorScheme.surface
    }
    val animatedBottomColor = bottomGradientColor

    val view = LocalView.current

    DisposableEffect(Unit) {
        val window = (view.context as? android.app.Activity)?.window ?: return@DisposableEffect onDispose {}
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        val insetsCtrl = WindowCompat.getInsetsController(window, view)
        insetsCtrl.show(androidx.core.view.WindowInsetsCompat.Type.statusBars())
        insetsCtrl.isAppearanceLightStatusBars = !isBgDark
        insetsCtrl.isAppearanceLightNavigationBars = !isBgDark
        onDispose {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            insetsCtrl.show(androidx.core.view.WindowInsetsCompat.Type.statusBars())
            insetsCtrl.isAppearanceLightStatusBars = !isBgDark
            insetsCtrl.isAppearanceLightNavigationBars = !isBgDark
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationY = dragOffsetY.coerceAtLeast(0f)
                alpha = 1f - (dragOffsetY.coerceAtLeast(0f) / 700f).coerceIn(0f, 0.45f)
            }
            // Solid base colour (dominant album art colour)
            .background(animatedBg)
            // Full-screen gradient: album colour top → deep dark bottom
            // fillMaxSize already covers behind status bar because edge-to-edge is active
            .background(
                Brush.verticalGradient(
                    0.00f to animatedTopColor.copy(alpha = 0.96f),
                    0.28f to animatedBg.copy(alpha = 0.92f),
                    0.62f to animatedMidColor.copy(alpha = 0.78f),
                    1.00f to animatedBottomColor
                )
            )
            .background(
                Brush.linearGradient(
                    listOf(
                        if (isNeonDepth) NeonDepthPink.copy(alpha = 0.22f)
                        else (accentColor ?: animatedTopColor).copy(alpha = 0.22f),
                        Color.Transparent,
                        if (isNeonDepth) NeonDepthPeach.copy(alpha = 0.16f)
                        else (secondaryColor ?: animatedMidColor).copy(alpha = 0.16f)
                    )
                )
            )
            .pointerInput(Unit) {
                // FIX: Use detectDragGestures — the official Compose high-level API for drags.
                // Axis is locked on first movement >10px to distinguish swipe-down (dismiss)
                // from swipe-left/right (skip track). Any in-flight settle animation is
                // cancelled the moment a new drag starts so it can never fight a fresh gesture.
                var gestureAxis: String? = null
                var totalDx = 0f
                detectDragGestures(
                    onDragStart = {
                        settleJob?.cancel()
                        gestureAxis = null
                        totalDx     = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val dx = dragAmount.x
                        val dy = dragAmount.y
                        if (gestureAxis == null &&
                            (kotlin.math.abs(dx) > 10f || kotlin.math.abs(dy) > 10f)) {
                            gestureAxis = if (kotlin.math.abs(dy) >= kotlin.math.abs(dx))
                                "vertical" else "horizontal"
                        }
                        when (gestureAxis) {
                            "vertical" -> {
                                // PERF FIX: direct state write, no coroutine launch per pointer move.
                                dragOffsetY = (dragOffsetY + dy).coerceAtLeast(0f)
                            }
                            "horizontal" -> totalDx += dx
                        }
                    },
                    onDragEnd = {
                        when (gestureAxis) {
                            "vertical" -> {
                                if (dragOffsetY > 150f) settleClosed() else settleOpen()
                            }
                            "horizontal" -> {
                                if (totalDx > 80f)       viewModel.previous()
                                else if (totalDx < -80f) viewModel.next()
                            }
                        }
                    },
                    onDragCancel = { settleOpen() }
                )
            }
    ) {
        Column(
            modifier            = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Top Bar ──────────────────────────────────────────────────────
            Row(
                modifier          = Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Rounded.KeyboardArrowDown, "Back",
                        tint     = onDynamicBg,
                        modifier = Modifier.size(34.dp))
                }
                Column(
                    modifier            = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Now Playing",
                        style = MaterialTheme.typography.labelLarge,
                        color = onDynamicBgMuted)
                    Text(
                        song?.album ?: "",
                        style    = MaterialTheme.typography.bodySmall,
                        color    = onDynamicBgMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onQueueClick) {
                    Icon(Icons.Rounded.QueueMusic, "Queue",
                        tint = onDynamicBg)
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── Album Art (enhanced with glow + playing pulse) ─────────────────
            val glowInfinite = rememberInfiniteTransition(label = "artGlow")
            val glowPulse by glowInfinite.animateFloat(
                initialValue  = 0.35f,
                targetValue   = 0.70f,
                animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), androidx.compose.animation.core.RepeatMode.Reverse),
                label         = "glowPulse"
            )
            val glowAlpha = if (state.isPlaying) glowPulse else 0.15f
            val artScale  by animateFloatAsState(
                targetValue   = if (state.isPlaying) 1.0f else 0.95f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium),
                label         = "artScale"
            )
            // Outer glow ring
            Box(
                modifier          = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                contentAlignment  = Alignment.Center
            ) {
                // Glow background (blurred color ring effect)
                if (dominantColor != null && state.isPlaying) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .graphicsLayer {
                                scaleX = 1.06f
                                scaleY = 1.06f
                            }
                            .clip(RoundedCornerShape(22.dp))
                            .background(
                                Brush.radialGradient(
                                    0.0f to dominantColor.copy(alpha = glowAlpha * 0.8f),
                                    0.5f to dominantColor.copy(alpha = glowAlpha * 0.4f),
                                    1.0f to Color.Transparent
                                )
                            )
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .padding(horizontal = 8.dp)
                        .graphicsLayer { scaleX = artScale; scaleY = artScale }
                        .clip(RoundedCornerShape(16.dp))
                        .shadow(if (state.isPlaying) 32.dp else 16.dp, RoundedCornerShape(16.dp))
                ) {
                    if (song?.artworkUri != null) {
                        AsyncImage(model = artworkModel, contentDescription = song.title,
                            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        Box(modifier = Modifier.fillMaxSize().background(
                            Brush.linearGradient(listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.secondaryContainer,
                                MaterialTheme.colorScheme.tertiaryContainer
                            ))), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.MusicNote, null,
                                tint     = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                                modifier = Modifier.size(72.dp))
                        }
                    }
                    // Subtle vignette overlay
                    Box(
                        modifier = Modifier.fillMaxSize().background(
                            Brush.radialGradient(
                                0.7f to Color.Transparent,
                                1.0f to Color.Black.copy(alpha = 0.18f)
                            )
                        )
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // ── Song Info ─────────────────────────────────────────────────────
            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text     = songTitle,
                        style    = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color    = onDynamicBg,
                        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 900)
                            .fillMaxWidth(),
                        maxLines = 1, overflow = TextOverflow.Clip
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text      = songArtist,
                        style     = MaterialTheme.typography.bodyMedium,
                        color     = onDynamicBgMuted,
                        modifier  = Modifier.fillMaxWidth(),
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(12.dp))
                IconButton(
                    onClick  = { song?.id?.let { viewModel.toggleFavorite(it) } }
                ) {
                    Icon(
                        if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        "Favorite",
                        tint     = if (isFavorite) MaterialTheme.colorScheme.primary
                                   else onDynamicBgMuted,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // ── Seekbar ───────────────────────────────────────────────────────
            // FIX: Local drag state prevents live progress updates from fighting with drag
            var isDragging   by remember { mutableStateOf(false) }
            var dragFraction by remember { mutableFloatStateOf(0f) }
            val displayFraction = if (isDragging) dragFraction else progressFraction
            // FIX: Use safe duration — controller may return 0 before STATE_READY
            val safeDuration = state.duration.takeIf { it > 0L } ?: 1L
            val displayText = remember(isDragging, dragFraction, state.progress, state.duration) {
                if (isDragging)
                    formatMs((dragFraction * safeDuration).toLong())
                else progressText
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                Slider(
                    value         = displayFraction,
                    onValueChange = { frac ->
                        isDragging   = true
                        dragFraction = frac
                    },
                    onValueChangeFinished = {
                        // FIX: Seek using safe duration so slider position maps correctly
                        viewModel.seekTo((dragFraction * safeDuration).toLong())
                        isDragging = false
                    },
                    modifier      = Modifier.fillMaxWidth(),
                    colors        = SliderDefaults.colors(
                        thumbColor         = onDynamicBg,
                        activeTrackColor   = onDynamicBg,
                        inactiveTrackColor = onDynamicBg.copy(alpha = 0.25f)
                    )
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(displayText, style = MaterialTheme.typography.labelSmall,
                        color = onDynamicBgMuted)
                    Text(durationText, style = MaterialTheme.typography.labelSmall,
                        color = onDynamicBgMuted)
                }
            }

            Spacer(Modifier.height(10.dp))

            // ── Main Controls ─────────────────────────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.toggleShuffle() }) {
                    Icon(Icons.Rounded.Shuffle, "Shuffle",
                        tint     = if (state.shuffleEnabled) MaterialTheme.colorScheme.primary
                                   else onDynamicBgMuted,
                        modifier = Modifier.size(24.dp))
                }
                IconButton(onClick = { viewModel.previous() }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.SkipPrevious, "Previous",
                        tint     = onDynamicBg,
                        modifier = Modifier.size(38.dp))
                }
                FilledIconButton(
                    onClick  = { viewModel.playPause() },
                    modifier = Modifier.size(76.dp),
                    shape    = CircleShape,
                    colors   = IconButtonDefaults.filledIconButtonColors(
                        containerColor = onDynamicBg,
                        contentColor   = if (isBgDark) Color.Black else Color.White
                    )
                ) {
                    Icon(
                        if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp)
                    )
                }
                IconButton(onClick = { viewModel.next() }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.SkipNext, "Next",
                        tint     = onDynamicBg,
                        modifier = Modifier.size(38.dp))
                }
                IconButton(onClick = { viewModel.cycleRepeatMode() }) {
                    Icon(
                        when (state.repeatMode) {
                            RepeatMode.ONE -> Icons.Rounded.RepeatOne
                            else           -> Icons.Rounded.Repeat
                        },
                        "Repeat",
                        tint     = if (state.repeatMode != RepeatMode.OFF) MaterialTheme.colorScheme.primary
                                   else onDynamicBgMuted,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(Modifier.weight(1f))  // push Quick Actions to bottom

            // ── Quick Actions ─────────────────────────────────────────────────
            Card(
                modifier  = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                shape     = RoundedCornerShape(18.dp),
                colors    = CardDefaults.cardColors(
                    containerColor = onDynamicBg.copy(alpha = if (isBgDark) 0.10f else 0.08f)
                ),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Row(
                    modifier              = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    PlayerActionButton(Icons.Rounded.Lyrics, "Lyrics", onDynamicBg, onDynamicBgMuted, onClick = {
                        showLyrics = true
                        viewModel.fetchLyrics()
                    })
                    PlayerActionButton(Icons.Rounded.Equalizer, "EQ", onDynamicBg, onDynamicBgMuted, onClick = { showEqDialog = true })
                    // Sleep button — show remaining time if active
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showSleepDialog = true }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Icon(Icons.Rounded.Timer, null,
                            tint     = if (sleepRemaining > 0) MaterialTheme.colorScheme.primary
                                       else onDynamicBg,
                            modifier = Modifier.size(22.dp))
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (sleepRemaining > 0) "%d:%02d".format(sleepRemaining / 60, sleepRemaining % 60)
                            else "Sleep",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (sleepRemaining > 0) MaterialTheme.colorScheme.primary
                                    else onDynamicBgMuted
                        )
                    }
                    // Share
                    PlayerActionButton(Icons.Rounded.Share, "Share", onDynamicBg, onDynamicBgMuted) {
                        val songPath = song?.path
                        if (songPath != null) {
                            try {
                                val file = java.io.File(songPath)
                                val uri = androidx.core.content.FileProvider.getUriForFile(
                                    context, "${context.packageName}.provider", file
                                )
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "audio/*"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "Share $songTitle"))
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(context, "Cannot share: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    PlayerActionButton(Icons.Rounded.MoreVert, "More", onDynamicBg, onDynamicBgMuted, onClick = { showMoreMenu = true })
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ── EQ Dialog ─────────────────────────────────────────────────────────────────
@Composable
private fun EqDialog(
    viewModel: PlayerViewModel,
    artworkUri: String?,
    songId: Long?,
    onDismiss: () -> Unit
) {
    val surfaceColors = rememberPlayerSurfaceColors(null, null)
    val eqLevels   by viewModel.eqBandLevels.collectAsState()
    val bandLabels  = viewModel.eqBandLabels
    val bandCount   = viewModel.eqBandCount

    // FIX: remember WITHOUT eqLevels as a key.
    // Old code: remember(eqLevels) {...} — because PlayerViewModel emits a new FloatArray
    // on every setEqBand() call (via copyOf()), eqLevels changes reference every drag event,
    // causing remember to recreate all mutableFloatStateOf objects mid-gesture → sliders snap.
    // New approach: initialise once, sync from VM only via LaunchedEffect (preset / open).
    val localLevels = remember(bandCount) {
        Array(bandCount) { mutableFloatStateOf(0f) }
    }
    LaunchedEffect(eqLevels.contentHashCode()) {
        eqLevels.forEachIndexed { i, v ->
            if (i < localLevels.size) localLevels[i].floatValue = v
        }
    }

    val presets = listOf(
        "Flat"   to EqPreset.FLAT,
        "Bass"   to EqPreset.BASS,
        "Vocal"  to EqPreset.VOCAL,
        "Treble" to EqPreset.TREBLE,
        "Club"   to EqPreset.CLUB
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = surfaceColors.container,
        titleContentColor = surfaceColors.content,
        textContentColor = surfaceColors.content,
        icon  = { Icon(Icons.Rounded.Equalizer, null, tint = surfaceColors.accent) },
        title = { Text("Equalizer") },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {

                // Preset chips
                Text(
                    "Presets",
                    style = MaterialTheme.typography.labelSmall,
                    color = surfaceColors.mutedContent
                )
                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(presets.size) { idx ->
                        val (label, preset) = presets[idx]
                        FilterChip(
                            selected = false,
                            onClick  = { viewModel.applyEqPreset(preset) },
                            label    = { Text(label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                HorizontalDivider(
                    modifier  = Modifier.padding(vertical = 4.dp),
                    thickness = 0.5.dp,
                    color     = surfaceColors.mutedContent.copy(alpha = 0.24f)
                )

                // Band sliders — continuous (no steps) for smooth feel
                (0 until bandCount).forEach { i ->
                    val label = bandLabels.getOrElse(i) { "Band $i" }
                    val level = localLevels.getOrNull(i)?.floatValue ?: 0f
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text     = label,
                            style    = MaterialTheme.typography.labelSmall,
                            color    = surfaceColors.mutedContent,
                            modifier = Modifier.width(48.dp)
                        )
                        Slider(
                            value         = level,
                            onValueChange = { v ->
                                localLevels.getOrNull(i)?.floatValue = v
                                viewModel.setEqBand(i, v)
                            },
                            valueRange = -10f..10f,
                            // FIX: Removed steps=19 — discrete steps cause jumpy slider feel.
                            //      Continuous slider is smoother and still snaps to integer dB
                            //      values via the text display.
                            modifier   = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = surfaceColors.accent,
                                activeTrackColor = surfaceColors.accent,
                                inactiveTrackColor = surfaceColors.mutedContent.copy(alpha = 0.25f)
                            )
                        )
                        Text(
                            text     = "%+.0fdB".format(level),
                            style    = MaterialTheme.typography.labelSmall,
                            color    = if (level > 0f) MaterialTheme.colorScheme.primary
                                       else if (level < 0f) MaterialTheme.colorScheme.error
                                       else surfaceColors.mutedContent,
                            modifier = Modifier.width(40.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.End
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = surfaceColors.accent)) { Text("Done") }
        },
        dismissButton = {
            TextButton(onClick = {
                viewModel.applyEqPreset(EqPreset.FLAT)
            }, colors = ButtonDefaults.textButtonColors(contentColor = surfaceColors.accent)) { Text("Reset") }
        }
    )
}
// ── Sleep Timer Dialog ────────────────────────────────────────────────────────
@Composable
private fun SleepTimerPlayerDialog(
    remaining: Int,
    artworkUri: String?,
    songId: Long?,
    onSelect : (Int) -> Unit,
    onCancel : () -> Unit,
    onDismiss: () -> Unit
) {
    val surfaceColors = rememberPlayerSurfaceColors(null, null)
    val presets = listOf(5 to "5 min", 10 to "10 min", 15 to "15 min",
                         30 to "30 min", 60 to "1 hour", 90 to "90 min")
    var customText   by remember { mutableStateOf("") }
    var customError  by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = surfaceColors.container,
        titleContentColor = surfaceColors.content,
        textContentColor = surfaceColors.content,
        icon  = { Icon(Icons.Rounded.Timer, null, tint = surfaceColors.accent) },
        title = { Text("Sleep Timer") },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Active timer card
                if (remaining > 0) {
                    Card(colors = CardDefaults.cardColors(
                        containerColor = surfaceColors.chip)) {
                        Row(modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Timer, null, tint = surfaceColors.accent)
                            Spacer(Modifier.width(8.dp))
                            Text("Remaining: %d:%02d".format(remaining / 60, remaining % 60),
                                color = surfaceColors.content,
                                fontWeight = FontWeight.SemiBold)
                        }
                    }
                    OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)) {
                        Icon(Icons.Rounded.TimerOff, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Cancel Timer")
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                }

                // Preset chips
                Text("Quick select:", style = MaterialTheme.typography.labelSmall,
                    color = surfaceColors.mutedContent)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(presets) { (min, label) ->
                        FilterChip(
                            selected = false,
                            onClick  = { onSelect(min) },
                            label    = { Text(label) }
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // Custom input
                Text("Custom duration:", style = MaterialTheme.typography.labelSmall,
                    color = surfaceColors.mutedContent)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value         = customText,
                        onValueChange = { customText = it.filter { c -> c.isDigit() }; customError = false },
                        label         = { Text("Minutes") },
                        singleLine    = true,
                        isError       = customError,
                        supportingText = if (customError) {{ Text("Enter 1–999") }} else null,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        ),
                        modifier = Modifier.weight(1f),
                        shape    = RoundedCornerShape(12.dp)
                    )
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = surfaceColors.accent),
                        onClick = {
                            val mins = customText.toIntOrNull()
                            if (mins != null && mins in 1..999) {
                                onSelect(mins)
                            } else {
                                customError = true
                            }
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Set") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = surfaceColors.accent)) { Text("Close") } }
    )
}

// ── More Options Bottom Sheet ─────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoreOptionsSheet(
    artworkUri: String?,
    songId: Long?,
    songTitle : String,
    isFavorite: Boolean,
    onFavorite: () -> Unit,
    onQueue   : () -> Unit,
    onShare   : () -> Unit,
    onDismiss : () -> Unit
) {
    val surfaceColors = rememberPlayerSurfaceColors(null, null)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = surfaceColors.container,
        contentColor = surfaceColors.content
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(songTitle,
                style    = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = surfaceColors.content,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            HorizontalDivider(color = surfaceColors.mutedContent.copy(alpha = 0.20f))
            listOf(
                Triple(if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                       if (isFavorite) "Remove from Favorites" else "Add to Favorites", onFavorite),
                Triple(Icons.Rounded.QueueMusic,    "View Queue",             onQueue),
                Triple(Icons.Rounded.Share,         "Share Song",             onShare),
                Triple(Icons.Rounded.PlaylistAdd,   "Add to Playlist",        {}),
                Triple(Icons.Rounded.Info,          "Song Info",              {}),
            ).forEach { (icon, label, action) ->
                ListItem(
                    headlineContent = { Text(label) },
                    leadingContent  = {
                        Icon(icon, null,
                            tint = if (label.contains("Favorites") && isFavorite)
                                MaterialTheme.colorScheme.error
                            else surfaceColors.accent)
                    },
                    modifier = Modifier.clickable { action() }
                )
            }
        }
    }
}

@Composable
private fun PlayerActionButton(
    icon       : androidx.compose.ui.graphics.vector.ImageVector,
    label      : String,
    iconColor  : Color,
    labelColor : Color,
    onClick    : () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier            = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Icon(icon, null,
            tint     = iconColor,
            modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        Text(label,
            style = MaterialTheme.typography.labelSmall,
            color = labelColor)
    }
}

private fun formatMs(ms: Long): String {
    val totalSec = ms / 1000
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}

// ── Dynamic background color from album art (Spotify-style) ────────────────────
/**
 * Loads the artwork bitmap via Coil and extracts a dominant/vibrant swatch
 * using androidx.palette. Returns null while loading or if there's no art —
 * caller should fall back to the theme's default colors in that case.
 */
private data class PlayerArtworkColors(
    val primary: Color,
    val secondary: Color,
    val accent: Color
)

private data class PlayerSurfaceColors(
    val container: Color,
    val content: Color,
    val mutedContent: Color,
    val accent: Color,
    val chip: Color
)

private fun Color.deepen(factor: Float): Color = Color(
    red = (red * factor).coerceIn(0f, 1f),
    green = (green * factor).coerceIn(0f, 1f),
    blue = (blue * factor).coerceIn(0f, 1f),
    alpha = alpha
)

private fun Palette.Swatch?.asColor(): Color? = this?.let { Color(it.rgb) }


@Composable
private fun rememberPlayerSurfaceColors(artworkUri: String?, songId: Long?): PlayerSurfaceColors {
    val scheme = MaterialTheme.colorScheme
    val themeMode = LocalThemeMode.current
    if (themeMode == ThemeMode.AMOLED_NEON_DEPTH) {
        return PlayerSurfaceColors(
            container = NeonDepthSurface,
            content = Color.White,
            mutedContent = Color.White.copy(alpha = 0.74f),
            accent = NeonDepthPurple,
            chip = NeonDepthPink.copy(alpha = 0.25f)
        )
    }
    val artworkColors = rememberArtworkColors(artworkUri, songId)
    val container = artworkColors?.primary?.let { color ->
        if (color.luminance() > 0.42f) color.deepen(0.42f) else color.deepen(0.72f)
    } ?: scheme.surface
    val content = if (container.luminance() < 0.5f) Color.White else Color.Black
    val accent = artworkColors?.accent?.let { color ->
        if (color.luminance() < 0.35f) color.deepen(1.35f) else color.deepen(0.86f)
    } ?: scheme.primary
    val chip = artworkColors?.secondary?.let { color ->
        if (color.luminance() > 0.45f) color.deepen(0.55f) else color.deepen(0.82f)
    } ?: scheme.primaryContainer
    return PlayerSurfaceColors(
        container = container,
        content = content,
        mutedContent = content.copy(alpha = 0.74f),
        accent = accent,
        chip = chip
    )
}






@Composable
private fun rememberArtworkColors(artworkUri: String?, songId: Long?): PlayerArtworkColors? {
    val context = LocalContext.current
    var colors by remember(songId) { mutableStateOf<PlayerArtworkColors?>(null) }

    LaunchedEffect(songId, artworkUri) {
        if (artworkUri == null) {
            colors = null
            return@LaunchedEffect
        }
        colors = withContext(Dispatchers.IO) {
            try {
                val request = ImageRequest.Builder(context)
                    .data(artworkUri)
                    .size(180)
                    .allowHardware(false)
                    .build()
                val result = context.imageLoader.execute(request)
                if (result is SuccessResult) {
                    val bitmap = (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                    bitmap?.let { bmp ->
                        val palette = Palette.from(bmp).maximumColorCount(24).generate()
                        val primary = palette.darkVibrantSwatch.asColor()
                            ?: palette.vibrantSwatch.asColor()
                            ?: palette.dominantSwatch.asColor()
                            ?: palette.darkMutedSwatch.asColor()
                        val secondary = palette.mutedSwatch.asColor()
                            ?: palette.lightMutedSwatch.asColor()
                            ?: palette.dominantSwatch.asColor()
                            ?: primary
                        val accent = palette.vibrantSwatch.asColor()
                            ?: palette.lightVibrantSwatch.asColor()
                            ?: palette.dominantSwatch.asColor()
                            ?: secondary
                        if (primary != null && secondary != null && accent != null) {
                            PlayerArtworkColors(primary, secondary, accent)
                        } else null
                    }
                } else null
            } catch (e: Exception) {
                null
            }
        }
    }
    return colors
}

// ── Lyrics Bottom Sheet ────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LyricsSheet(
    artworkUri       : String?,
    songId           : Long?,
    lyricsState     : LyricsState,
    lyricsSource    : LyricsSource?,
    currentLineIndex: Int,
    onRetry         : () -> Unit,
    onDismiss       : () -> Unit
) {
    val surfaceColors = rememberPlayerSurfaceColors(null, null)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = surfaceColors.container,
        contentColor = surfaceColors.content,
        dragHandle = {
            // Custom drag handle with title row
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(surfaceColors.mutedContent.copy(alpha = 0.50f))
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Lyrics,
                        contentDescription = null,
                        tint = surfaceColors.accent,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Lyrics",
                        style    = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color    = surfaceColors.content
                    )
                    // FIX: Source badge — shows whether lyrics were loaded from a
                    // local .lrc file (works fully offline) or fetched online via
                    // LRCLib, confirming both paths are working.
                    if (lyricsSource != null) {
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            shape  = RoundedCornerShape(50),
                            color  = if (lyricsSource == LyricsSource.LOCAL)
                                         surfaceColors.accent.copy(alpha = 0.18f)
                                     else surfaceColors.chip
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Icon(
                                    if (lyricsSource == LyricsSource.LOCAL)
                                        Icons.Rounded.DownloadDone
                                    else Icons.Rounded.CloudDone,
                                    contentDescription = null,
                                    tint = surfaceColors.content,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    if (lyricsSource == LyricsSource.LOCAL) "Offline" else "Online",
                                    style    = MaterialTheme.typography.labelSmall,
                                    color    = surfaceColors.content
                                )
                            }
                        }
                    }
                    if (lyricsState is LyricsState.Synced) {
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            shape  = RoundedCornerShape(50),
                            color  = surfaceColors.chip
                        ) {
                            Text(
                                "Synced",
                                style    = MaterialTheme.typography.labelSmall,
                                color    = surfaceColors.content,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 300.dp, max = 520.dp)
                .padding(bottom = 32.dp)
        ) {
            when (lyricsState) {
                is LyricsState.Loading -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(36.dp),
                            color    = surfaceColors.accent
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("Loading lyrics…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = surfaceColors.mutedContent)
                    }
                }
                is LyricsState.Synced -> {
                    KaraokeLyricsView(
                        lines            = lyricsState.lines,
                        currentLineIndex = currentLineIndex
                    )
                }
                is LyricsState.Plain -> {
                    val scrollState = rememberScrollState()
                    Text(
                        text     = lyricsState.lyrics,
                        style    = MaterialTheme.typography.bodyLarge.copy(lineHeight = 28.sp),
                        color    = surfaceColors.content,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .verticalScroll(scrollState)
                    )
                }
                is LyricsState.Error -> {
                    val isOffline = lyricsState.message.contains("No internet", ignoreCase = true)
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            if (isOffline) Icons.Rounded.CloudOff else Icons.Rounded.Lyrics,
                            null,
                            tint     = surfaceColors.mutedContent,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            lyricsState.message,
                            style     = MaterialTheme.typography.bodyMedium,
                            color     = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(16.dp))
                        OutlinedButton(onClick = onRetry) {
                            Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Retry")
                        }
                    }
                }
                is LyricsState.Idle -> {
                    Text(
                        "Fetching lyrics…",
                        style    = MaterialTheme.typography.bodyMedium,
                        color    = surfaceColors.mutedContent,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    }
}

@Composable
private fun KaraokeLyricsView(
    lines           : List<LrcLine>,
    currentLineIndex: Int
) {
    val listState = rememberLazyListState()
    val scope     = rememberCoroutineScope()

    // Auto-scroll: keep current line centered when it changes
    LaunchedEffect(currentLineIndex) {
        if (currentLineIndex >= 0 && currentLineIndex < lines.size) {
            scope.launch {
                // Aim to show current line a bit above center (offset = 3 lines)
                val target = (currentLineIndex - 2).coerceAtLeast(0)
                listState.animateScrollToItem(target)
            }
        }
    }

    // Fade masks top & bottom for elegant overflow feel
    Box(modifier = Modifier.fillMaxWidth()) {
        LazyColumn(
            state             = listState,
            modifier          = Modifier
                .fillMaxWidth()
                .heightIn(min = 300.dp, max = 520.dp),
            contentPadding    = PaddingValues(horizontal = 24.dp, vertical = 48.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            itemsIndexed(lines) { idx, line ->
                KaraokeLine(
                    text      = line.text,
                    isCurrent = idx == currentLineIndex,
                    isPast    = idx < currentLineIndex
                )
            }
        }

        // Top fade mask
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.surface,
                            Color.Transparent
                        )
                    )
                )
        )
        // Bottom fade mask
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
        )
    }
}

@Composable
private fun KaraokeLine(
    text     : String,
    isCurrent: Boolean,
    isPast   : Boolean
) {
    val animScale by animateFloatAsState(
        targetValue   = if (isCurrent) 1.06f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label         = "lyric_scale"
    )
    val alpha by animateFloatAsState(
        targetValue   = when {
            isCurrent -> 1f
            isPast    -> 0.45f
            else      -> 0.65f
        },
        animationSpec = tween(300),
        label         = "lyric_alpha"
    )

    val textColor = when {
        isCurrent -> MaterialTheme.colorScheme.primary
        else      -> MaterialTheme.colorScheme.onSurface
    }

    Text(
        text     = text,
        style    = MaterialTheme.typography.titleMedium.copy(
            fontWeight  = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
            lineHeight  = 30.sp,
            letterSpacing = if (isCurrent) 0.3.sp else 0.sp
        ),
        color    = textColor.copy(alpha = alpha),
        textAlign = TextAlign.Center,
        modifier  = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = animScale
                scaleY = animScale
            }
            .padding(vertical = 4.dp)
    )
}
