package com.io.ab.music.ui.screens.video
import com.io.ab.music.LocalIsInPictureInPicture
import com.io.ab.music.ImmersiveVideoState
import com.io.ab.music.VideoPipController
import com.io.ab.music.buildVideoPipParams

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import android.util.Rational
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.io.ab.music.domain.model.Video
import com.io.ab.music.ui.viewmodel.VideoViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

// ── Scale mode ────────────────────────────────────────────────────────────────
enum class ScaleMode(val label: String, val resizeMode: Int) {
    FIT    ("Fit",     AspectRatioFrameLayout.RESIZE_MODE_FIT),
    FILL   ("Fill",    AspectRatioFrameLayout.RESIZE_MODE_FILL),
    CROP   ("Crop",    AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
    STRETCH("Stretch", AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT)
}

// ── Rotation mode ─────────────────────────────────────────────────────────────
enum class RotationMode { AUTO, PORTRAIT, LANDSCAPE, LANDSCAPE_REVERSE }

/** Hides both status + navigation bars in immersive-sticky mode. Called on
 *  screen mount and again from MainActivity.onWindowFocusChanged so the bars
 *  don't linger visible after the window regains focus. */
internal fun applyImmersiveMode(activity: Activity?) {
    val window = activity?.window ?: return
    WindowCompat.setDecorFitsSystemWindows(window, false)
    val ic = WindowInsetsControllerCompat(window, window.decorView)
    ic.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    ic.hide(WindowInsetsCompat.Type.systemBars())
}

@OptIn(UnstableApi::class, ExperimentalFoundationApi::class)
@Composable
fun VideoPlayerScreen(
    video     : Video,
    viewModel : VideoViewModel,
    onBack    : () -> Unit
) {
    val context  = LocalContext.current
    val activity = context as? Activity
    val scope    = rememberCoroutineScope()
    val density  = LocalDensity.current
    // FIX: while floating in PiP, this composable must stay alive — don't run
    // exit/teardown logic (clearCurrentVideo, etc.) that would otherwise fire
    // when the PiP configuration change briefly recomposes the nav graph.
    // rememberUpdatedState ensures onDispose{} (a closure created once) always
    // reads the LATEST isInPip value rather than a stale captured snapshot.
    val isInPipState = rememberUpdatedState(LocalIsInPictureInPicture.current)

    // ── Audio / Subtitle track state ──────────────────────────────────────
    var audioTracks              by remember { mutableStateOf<List<Triple<Int, Int, String>>>(emptyList()) }
    var selectedAudioGroupIdx    by remember { mutableIntStateOf(viewModel.savedAudioGroupIdx) }
    var showAudioMenu            by remember { mutableStateOf(false) }
    var subtitleTracks           by remember { mutableStateOf<List<Triple<Int, Int, String>>>(emptyList()) }
    var selectedSubtitleGroupIdx by remember { mutableIntStateOf(viewModel.savedSubtitleGroupIdx) }
    var showSubMenu              by remember { mutableStateOf(false) }

    // ── Load persisted action button states ───────────────────────────────
    var rotationMode   by remember { mutableStateOf(RotationMode.AUTO) }
    var scaleMode      by remember { mutableStateOf(ScaleMode.FILL) }
    var playbackSpeed  by remember { mutableFloatStateOf(1f) }
    var isLoopEnabled  by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val savedScale    = viewModel.loadScaleMode()
        val savedSpeed    = viewModel.loadPlaybackSpeed()
        val savedLoop     = viewModel.loadLoopEnabled()
        val savedRotation = viewModel.loadRotationMode()
        scaleMode     = runCatching { ScaleMode.valueOf(savedScale) }.getOrDefault(ScaleMode.FILL)
        playbackSpeed = savedSpeed
        isLoopEnabled = savedLoop
        rotationMode  = runCatching { RotationMode.valueOf(savedRotation) }.getOrDefault(RotationMode.AUTO)
        selectedAudioGroupIdx    = viewModel.loadAudioTrackGroup()
        selectedSubtitleGroupIdx = viewModel.loadSubtitleTrackGroup()
    }

    // ── Rotation ──────────────────────────────────────────────────────────
    LaunchedEffect(rotationMode) {
        activity?.requestedOrientation = when (rotationMode) {
            RotationMode.AUTO             -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
            RotationMode.PORTRAIT         -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            RotationMode.LANDSCAPE        -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            RotationMode.LANDSCAPE_REVERSE-> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
        }
    }
    DisposableEffect(Unit) {
        onDispose { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }

    // ── Status bar / nav bar — true immersive video ─────────────────────────
    // FIX: Previously only the status bar was hidden, leaving the nav bar /
    // 3-button pill / gesture handle visibly sitting over the video the whole
    // time. Now BOTH status and navigation bars are hidden with
    // BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE, which is Android's standard
    // "immersive sticky" mode: an edge swipe still temporarily reveals the
    // bars (including for the system Back Gesture), then the system
    // auto-hides them again on its own after a short timeout — so Back
    // Gesture keeps working and the bars don't stay stuck on screen.
    //
    // FIX: The bars used to silently reappear and stay visible after the
    // window regained focus (e.g. after a dialog, PiP resize, or coming back
    // from Recents/another app) because Android re-shows system bars on
    // focus changes and nothing was re-hiding them. ImmersiveVideoState now
    // flags "video player is active" and MainActivity re-applies the hide in
    // onWindowFocusChanged, so the bars snap back to hidden immediately
    // instead of lingering until the user taps to toggle controls again.
    DisposableEffect(Unit) {
        ImmersiveVideoState.isActive = true
        applyImmersiveMode(activity)
        onDispose {
            ImmersiveVideoState.isActive = false
            val w2 = activity?.window
            if (w2 != null) {
                WindowCompat.setDecorFitsSystemWindows(w2, false)
                val ic2 = WindowInsetsControllerCompat(w2, w2.decorView)
                ic2.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // ── Get saved last position ───────────────────────────────────────────
    val savedStartPos = remember(video.id) { viewModel.getLastPosition(video.id) }

    // ── ExoPlayer ────────────────────────────────────────────────────────
    val exoPlayer = remember(video.id) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(video.contentUri))
            prepare()
            if (savedStartPos > 5000L) seekTo(savedStartPos)
            playWhenReady = true
        }
    }

    // ── Auto-select by language REMOVED ──────────────────────────────────
    // FIX: Auto language detection was overriding the user's saved audio/subtitle
    // selection on every video open. ExoPlayer already picks the best default track
    // automatically. User can manually change via the audio/subtitle menu.

    // ── Position auto-save ────────────────────────────────────────────────
    LaunchedEffect(exoPlayer, video.id) {
        while (true) {
            delay(3000L)
            val pos = exoPlayer.currentPosition
            val dur = exoPlayer.duration.coerceAtLeast(1L)
            if (pos > 5000L && pos < dur - 5000L) viewModel.saveLastPosition(video.id, pos)
        }
    }

    // ── Player state ──────────────────────────────────────────────────────
    var isPlaying     by remember { mutableStateOf(true) }
    var currentPos    by remember { mutableLongStateOf(0L) }
    var totalDuration by remember { mutableLongStateOf(video.duration.coerceAtLeast(1L)) }
    var playbackEnded by remember { mutableStateOf(false) }
    var isBuffering   by remember { mutableStateOf(false) }

    // ── UI state ──────────────────────────────────────────────────────────
    var showControls    by remember { mutableStateOf(true) }
    var isLocked        by remember { mutableStateOf(false) }
    var showScaleMenu   by remember { mutableStateOf(false) }
    var showSpeedMenu   by remember { mutableStateOf(false) }
    var showMoreMenu    by remember { mutableStateOf(false) }
    var showRecentPanel by remember { mutableStateOf(false) }
    var showEpisodesPanel by remember { mutableStateOf(false) }
    // Search + filter for the Episodes (web-series) panel.
    var episodeSearchQuery by remember { mutableStateOf("") }
    var episodeFilter      by remember { mutableStateOf(EpisodeFilter.ALL) }
    var showVideoInfo   by remember { mutableStateOf(false) }
    var showRotateMenu  by remember { mutableStateOf(false) }
    var isBackgroundPlay by remember { mutableStateOf(false) }
    var showResumeDialog by remember { mutableStateOf(savedStartPos > 5000L) }

    // ── Sleep Timer ───────────────────────────────────────────────────────
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var sleepTimerRemaining  by remember { mutableIntStateOf(0) }
    val sleepTimerScope      = rememberCoroutineScope()
    var sleepTimerJob        by remember { mutableStateOf<Job?>(null) }

    // ── Rename / Delete ───────────────────────────────────────────────────
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val anyMenuOpen by remember {
        derivedStateOf {
            showAudioMenu || showSubMenu || showScaleMenu || showSpeedMenu ||
            showMoreMenu  || showRotateMenu || showRecentPanel || showVideoInfo || showEpisodesPanel
        }
    }

    // ── Gesture state ─────────────────────────────────────────────────────
    var seekDelta       by remember { mutableLongStateOf(0L) }
    var showSeekOverlay by remember { mutableStateOf(false) }
    var seekDirection   by remember { mutableStateOf(true) }
    val audioManager    = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume       = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat() }
    var volumeVal       by remember {
        mutableFloatStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) / maxVolume)
    }
    var brightnessVal   by remember { mutableFloatStateOf(getBrightness(context)) }
    var showBrightness  by remember { mutableStateOf(false) }
    var showVolume      by remember { mutableStateOf(false) }
    var showPipOverlay  by remember { mutableStateOf(false) }

    // ── Seek bar drag state (FIX: stable dragPosition avoids seek bar glitch) ──
    var isDraggingSeek by remember { mutableStateOf(false) }
    var dragPosition   by remember { mutableLongStateOf(0L) }
    var playerViewRef  by remember { mutableStateOf<PlayerView?>(null) }

    // ── Pinch-to-zoom ──────────────────────────────────────────────────────
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }
    var showZoomIndicator by remember { mutableStateOf(false) }
    val snapPoints = remember { listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 3f, 4f) }
    val snapAnim = remember { androidx.compose.animation.core.Animatable(1f) }

    LaunchedEffect(scaleMode) { zoomScale = 1f; panX = 0f; panY = 0f; snapAnim.snapTo(1f) }

    LaunchedEffect(zoomScale) {
        delay(600)
        val nearest = snapPoints.minBy { abs(it - zoomScale) }
        if (abs(nearest - zoomScale) > 0.01f) {
            snapAnim.snapTo(zoomScale)
            snapAnim.animateTo(nearest, animationSpec = tween(200))
            zoomScale = snapAnim.value
        }
    }

    // ── Extra volume (0–200%) — VIDEO PLAYER ONLY, not used in music player ──
    var videoVolume   by remember { mutableFloatStateOf(volumeVal) }
    var loudnessBoost by remember { mutableStateOf<android.media.audiofx.LoudnessEnhancer?>(null) }

    LaunchedEffect(videoVolume, exoPlayer) {
        loudnessBoost?.release()
        loudnessBoost = null
        if (videoVolume > 1f) {
            exoPlayer.volume = 1f
            try {
                val enhancer = android.media.audiofx.LoudnessEnhancer(exoPlayer.audioSessionId)
                enhancer.setTargetGain(((videoVolume - 1f) * 3000).toInt().coerceIn(0, 4000))
                enhancer.setEnabled(true)
                loudnessBoost = enhancer
            } catch (_: Exception) {}
        } else {
            exoPlayer.volume = 1f // Keep player volume at 1.0, let system volume handle attenuation below 100% to avoid double attenuation
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose { loudnessBoost?.release() }
    }

    // ── Hide controls timer ───────────────────────────────────────────────
    var controlsJob by remember { mutableStateOf<Job?>(null) }
    fun resetHideTimer() {
        controlsJob?.cancel()
        showControls = true
        if (!isLocked && !anyMenuOpen) {
            controlsJob = scope.launch { delay(3500); showControls = false }
        }
    }
    fun toggleControls() {
        if (showControls) { controlsJob?.cancel(); showControls = false }
        else resetHideTimer()
    }

    LaunchedEffect(anyMenuOpen) {
        if (anyMenuOpen) { controlsJob?.cancel(); showControls = true }
    }

    // FIX: When playback reaches the end, the center Play/Pause button already
    // correctly turns into a Replay button — but if the hide-controls timer
    // (started on the LAST user interaction, e.g. seeking near the end) fired
    // right around the same moment, `showControls` was still false. The
    // Replay button was invisible, so tapping the center of the screen only
    // toggled controls back on instead of restarting — looking exactly like
    // "the play/pause button doesn't work after the video ends". Force the
    // controls visible (and keep them visible, no auto-hide) the moment
    // playback ends, so the Replay button is always reachable immediately.
    LaunchedEffect(playbackEnded) {
        if (playbackEnded) { controlsJob?.cancel(); showControls = true }
    }

    // FIX: When the floating (PiP) window is resized back to full screen —
    // i.e. isInPip flips from true back to false — the controls/seek bar
    // could be left in a stale state (still hidden, or a leftover menu open)
    // so taps appeared to fall through to the gesture layer underneath
    // instead of hitting the buttons. Force a clean reset the moment we're
    // no longer in PiP so the controls are guaranteed interactive again.
    LaunchedEffect(isInPipState.value) {
        if (!isInPipState.value) {
            showAudioMenu = false; showSubMenu = false; showScaleMenu = false
            showSpeedMenu = false; showMoreMenu = false; showRotateMenu = false
            showRecentPanel = false; showEpisodesPanel = false
            resetHideTimer()
        }
    }

    // ── Keep screen on + restore brightness on exit ───────────────────────
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        resetHideTimer()
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            // FIX: Restore window brightness to system default (-1f) so video brightness
            // change does NOT persist after leaving the video player.
            activity?.window?.attributes = activity?.window?.attributes?.also { lp ->
                lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
        }
    }

    // ── Helper: track label from language tag ─────────────────────────────
    fun langLabel(tag: String?): String {
        if (tag.isNullOrBlank()) return ""
        return try {
            val locale = Locale.forLanguageTag(tag)
            val name = locale.getDisplayName(Locale.ENGLISH).trim()
            if (name.equals(tag, ignoreCase = true) || name.isBlank()) tag.uppercase()
            else name
        } catch (_: Exception) { tag.uppercase() }
    }

    // ── ExoPlayer listener ────────────────────────────────────────────────
    DisposableEffect(exoPlayer) {
        fun refreshTracks() {
            val tracks = exoPlayer.currentTracks
            val audios = mutableListOf<Triple<Int, Int, String>>()
            tracks.groups.forEachIndexed { gi, group ->
                if (group.type == C.TRACK_TYPE_AUDIO) {
                    for (ti in 0 until group.length) {
                        val fmt  = group.getTrackFormat(ti)
                        val lang = langLabel(fmt.language)
                        val label = when {
                            !fmt.label.isNullOrBlank() -> langLabel(fmt.label).ifBlank { fmt.label!! }
                            lang.isNotBlank() -> lang
                            else -> "Track ${audios.size + 1}"
                        }
                        audios.add(Triple(gi, ti, label))
                        if (group.isTrackSelected(ti)) selectedAudioGroupIdx = gi
                    }
                }
            }
            audioTracks = audios

            val subs = mutableListOf<Triple<Int, Int, String>>()
            tracks.groups.forEachIndexed { gi, group ->
                if (group.type == C.TRACK_TYPE_TEXT) {
                    for (ti in 0 until group.length) {
                        val fmt  = group.getTrackFormat(ti)
                        val lang = langLabel(fmt.language)
                        val label = when {
                            !fmt.label.isNullOrBlank() -> langLabel(fmt.label).ifBlank { fmt.label!! }
                            lang.isNotBlank() -> lang
                            else -> "Sub ${subs.size + 1}"
                        }
                        subs.add(Triple(gi, ti, label))
                        if (group.isTrackSelected(ti)) selectedSubtitleGroupIdx = gi
                    }
                }
            }
            subtitleTracks = subs
        }

        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) { refreshTracks() }
            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                if (rotationMode == RotationMode.AUTO && videoSize.width > 0 && videoSize.height > 0) {
                    val isPortraitVideo = videoSize.height > videoSize.width
                    activity?.requestedOrientation = if (isPortraitVideo)
                        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                }
            }
            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
                when (state) {
                    Player.STATE_ENDED -> {
                        if (isLoopEnabled) { exoPlayer.seekTo(0); exoPlayer.play() }
                        else { playbackEnded = true; isPlaying = false; viewModel.clearLastPosition(video.id) }
                    }
                    Player.STATE_READY -> {
                        totalDuration = exoPlayer.duration.coerceAtLeast(1L)
                        refreshTracks()
                    }
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            // FIX (regression): this effect is keyed on `exoPlayer`, which is
            // itself `remember(video.id) {...}` — so switching to another
            // episode/recent video from the panels creates a NEW ExoPlayer
            // and disposes this OLD one. That used to also call
            // viewModel.clearCurrentVideo(), which set currentVideo to null
            // and made NavGraph's `currentVideo?.let{...} ?: popBackStack()`
            // immediately pop the screen — i.e. tapping an episode/recent
            // item silently exited the player. Releasing the OLD player here
            // is still correct (a new one now owns playback), but clearing
            // the current video must NOT happen here; that only belongs to
            // the screen-level teardown effect below, which runs once when
            // VideoPlayerScreen itself leaves composition.
            if (!isInPipState.value) {
                val pos = exoPlayer.currentPosition
                val dur = exoPlayer.duration.coerceAtLeast(1L)
                if (pos > 5000L && pos < dur - 5000L) viewModel.saveLastPosition(video.id, pos)
                exoPlayer.release()
            }
        }
    }

    // ── Screen-level teardown ──────────────────────────────────────────────
    // Runs only when VideoPlayerScreen itself leaves composition (real back
    // navigation / process death), NOT when the user switches episodes from
    // the Recent/Episodes panel — see FIX note above for why this must be
    // separate from the per-player DisposableEffect(exoPlayer).
    DisposableEffect(Unit) {
        onDispose {
            if (!isInPipState.value) viewModel.clearCurrentVideo()
        }
    }

    // ── Audio focus ───────────────────────────────────────────────────────
    DisposableEffect(Unit) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val focusRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build())
                .setOnAudioFocusChangeListener { focusChange ->
                    when (focusChange) {
                        AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> exoPlayer.pause()
                        AudioManager.AUDIOFOCUS_GAIN -> exoPlayer.play()
                    }
                }.build()
        } else null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && focusRequest != null)
            am.requestAudioFocus(focusRequest)
        else @Suppress("DEPRECATION") am.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)

        onDispose {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && focusRequest != null)
                am.abandonAudioFocusRequest(focusRequest)
            else @Suppress("DEPRECATION") am.abandonAudioFocus(null)
        }
    }

    // ── FIX: video kept playing (audio-only, invisible) in the background
    // when the user pressed the phone's Home button / navigation gesture.
    // Nothing was pausing the player on the Activity going to background —
    // it only paused on explicit audio-focus loss. Now we observe the host
    // Lifecycle directly: on ON_STOP we pause playback unless the user is in
    // real PiP or explicitly enabled "Background play" (the floating icon),
    // and on ON_START we resume playback only if we were the ones who paused
    // it (so we don't override a video the user had manually paused).
    val lifecycleOwner        = LocalLifecycleOwner.current
    val isBackgroundPlayState = rememberUpdatedState(isBackgroundPlay)
    var pausedByLifecycle     by remember { mutableStateOf(false) }
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    if (!isInPipState.value && !isBackgroundPlayState.value && exoPlayer.isPlaying) {
                        exoPlayer.pause()
                        pausedByLifecycle = true
                    }
                }
                Lifecycle.Event.ON_START -> {
                    if (pausedByLifecycle) {
                        exoPlayer.play()
                        pausedByLifecycle = false
                    }
                }
                Lifecycle.Event.ON_DESTROY -> {
                    // FIX: tapping the system "X" close button on the floating
                    // (PiP) video window destroys the Activity while
                    // isInPipState is STILL true (Android never fires
                    // onPictureInPictureModeChanged(false) first) — so the
                    // per-player and screen-level onDispose blocks above,
                    // which only release()/clearCurrentVideo() when NOT in
                    // PiP, used to skip cleanup entirely. That left the
                    // ExoPlayer instance, its wakelock, and the "currently
                    // playing" state alive with nothing showing it, so
                    // "closing" the floating player didn't actually stop
                    // anything. A real ON_DESTROY always means the screen is
                    // gone for good, so we force cleanup here regardless of
                    // the PiP flag.
                    try {
                        val pos = exoPlayer.currentPosition
                        val dur = exoPlayer.duration.coerceAtLeast(1L)
                        if (pos > 5000L && pos < dur - 5000L) viewModel.saveLastPosition(video.id, pos)
                    } catch (_: Exception) {}
                    try { exoPlayer.release() } catch (_: Exception) {}
                    viewModel.clearCurrentVideo()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // FIX: registers this screen's real play/pause with VideoPipController so
    // the system PiP window's action button (see MainActivity/buildVideoPipParams)
    // controls THIS video's ExoPlayer instead of falling back to whatever the
    // music session is doing. Cleared on dispose so a stale callback can't
    // fire into a released player after the user leaves the video screen.
    DisposableEffect(exoPlayer) {
        VideoPipController.onPlayPause = {
            when {
                playbackEnded -> { exoPlayer.seekTo(0); exoPlayer.play() }
                exoPlayer.isPlaying -> exoPlayer.pause()
                else -> exoPlayer.play()
            }
        }
        // FIX: floating (PiP) window only ever showed a Play/Pause button —
        // there was no way to skip episodes without leaving the floating
        // player. Wire the same Previous/Next behavior used by the on-screen
        // buttons into the PiP RemoteActions (see buildVideoPipParams).
        VideoPipController.onPrevious = {
            if (exoPlayer.currentPosition > 5000) exoPlayer.seekTo(0) else viewModel.playPrev()
        }
        VideoPipController.onNext = { viewModel.playNext() }
        // FIX: see MainActivity.onStop() — called directly (not through
        // Compose's Lifecycle observer) whenever the Activity is actually
        // finishing, e.g. the system PiP window's "X" close button. Forces
        // real cleanup so the video/audio can't keep playing invisibly.
        VideoPipController.onForceStop = {
            try {
                val pos = exoPlayer.currentPosition
                val dur = exoPlayer.duration.coerceAtLeast(1L)
                if (pos > 5000L && pos < dur - 5000L) viewModel.saveLastPosition(video.id, pos)
            } catch (_: Exception) {}
            try { exoPlayer.pause() } catch (_: Exception) {}
            try { exoPlayer.release() } catch (_: Exception) {}
            viewModel.clearCurrentVideo()
        }
        onDispose {
            VideoPipController.onPlayPause = null
            VideoPipController.onPrevious  = null
            VideoPipController.onNext      = null
            VideoPipController.onForceStop = null
        }
    }
    LaunchedEffect(isPlaying) { VideoPipController.isPlaying = isPlaying }

    // ── Position ticker ───────────────────────────────────────────────────
    LaunchedEffect(exoPlayer) {
        while (true) {
            if (!isDraggingSeek) currentPos = exoPlayer.currentPosition.coerceAtLeast(0L)
            delay(250)
        }
    }

    // ── Speed / scale apply ───────────────────────────────────────────────
    LaunchedEffect(playbackSpeed) { exoPlayer.setPlaybackSpeed(playbackSpeed) }
    LaunchedEffect(scaleMode) { playerViewRef?.resizeMode = scaleMode.resizeMode }

    // ── Track switch helpers ──────────────────────────────────────────────
    fun switchAudioTrack(groupIndex: Int, trackIndex: Int) {
        try {
            val groups = exoPlayer.currentTracks.groups
            if (groupIndex < groups.size && groups[groupIndex].type == C.TRACK_TYPE_AUDIO) {
                exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
                    .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                    .setOverrideForType(TrackSelectionOverride(groups[groupIndex].mediaTrackGroup, trackIndex))
                    .build()
                selectedAudioGroupIdx = groupIndex
                viewModel.saveAudioTrackGroup(groupIndex)
            }
        } catch (_: Exception) {}
    }

    fun switchSubtitleTrack(groupIndex: Int, trackIndex: Int) {
        try {
            if (groupIndex == -1) {
                exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
                    .setIgnoredTextSelectionFlags(C.SELECTION_FLAG_DEFAULT or C.SELECTION_FLAG_FORCED)
                    .clearOverridesOfType(C.TRACK_TYPE_TEXT).build()
                selectedSubtitleGroupIdx = -1
                viewModel.saveSubtitleTrackGroup(-1)
            } else {
                val groups = exoPlayer.currentTracks.groups
                if (groupIndex < groups.size && groups[groupIndex].type == C.TRACK_TYPE_TEXT) {
                    exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
                        .setIgnoredTextSelectionFlags(0)
                        .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                        .setOverrideForType(TrackSelectionOverride(groups[groupIndex].mediaTrackGroup, trackIndex))
                        .build()
                    selectedSubtitleGroupIdx = groupIndex
                    viewModel.saveSubtitleTrackGroup(groupIndex)
                }
            }
        } catch (_: Exception) {}
    }

    // ── Apply saved track preferences after track list changes ────────────
    LaunchedEffect(audioTracks, subtitleTracks) {
        if (audioTracks.isNotEmpty()) {
            val saved = viewModel.savedAudioGroupIdx
            if (saved >= 0 && audioTracks.any { it.first == saved } && selectedAudioGroupIdx != saved) {
                val match = audioTracks.first { it.first == saved }
                switchAudioTrack(match.first, match.second)
            }
        }
        if (subtitleTracks.isNotEmpty()) {
            val saved = viewModel.savedSubtitleGroupIdx
            when {
                saved >= 0 && subtitleTracks.any { it.first == saved } && selectedSubtitleGroupIdx != saved -> {
                    val match = subtitleTracks.first { it.first == saved }
                    switchSubtitleTrack(match.first, match.second)
                }
                saved == -1 && selectedSubtitleGroupIdx >= 0 ->
                    switchSubtitleTrack(-1, -1)
            }
        }
    }

    fun enterBackgroundPlay() {
        isBackgroundPlay = true
        viewModel.enableBackgroundPlay()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                // FIX: was a bare PictureInPictureParams with no actions, which let
                // Android fall back to overlaying the MUSIC session's play/pause on
                // the floating window (see VideoPipController). Supply the real
                // video-bound action explicitly.
                activity?.enterPictureInPictureMode(buildVideoPipParams(context, exoPlayer.isPlaying))
            } catch (_: Exception) {}
        }
    }

    // FIX: while in PiP, intercepting back here and calling onBack() would pop
    // the nav stack underneath the floating window. The system handles back
    // presses for PiP windows itself (closes/dismisses the floating window).
    BackHandler(enabled = !isInPipState.value) {
        // FIX: previously back always called onBack() immediately, even with
        // the Recent/Episodes/other panels open — closing the panel first
        // (like any Android overlay) is what users expect from a back press.
        when {
            showEpisodesPanel -> showEpisodesPanel = false
            showRecentPanel   -> showRecentPanel = false
            showAudioMenu     -> showAudioMenu = false
            showSubMenu       -> showSubMenu = false
            showScaleMenu     -> showScaleMenu = false
            showSpeedMenu     -> showSpeedMenu = false
            showMoreMenu      -> showMoreMenu = false
            showRotateMenu    -> showRotateMenu = false
            showVideoInfo     -> showVideoInfo = false
            else              -> onBack()
        }
    }

    val uiState by viewModel.uiState.collectAsState()
    val recentlyPlayed = uiState.recentlyPlayed.filter { it.id != video.id }
    // ── Same-folder episodes (web-series) ─────────────────────────────────
    // Replaces the old "Fit" scale option: if this video's folder has more
    // than one video in it (e.g. Episode 1, 2, 3... of a web series), show
    // the whole folder's episode list in a right-side panel so the user can
    // jump straight to any other episode without leaving the player.
    val folderEpisodes = remember(uiState.videos, video.folderName) {
        uiState.videos.filter { it.folderName == video.folderName }
            .sortedWith(compareBy(naturalOrderComparator()) { it.title })
    }
    val hasMultipleEpisodes = folderEpisodes.size > 1
    // Only offer Previous/Next on the floating (PiP) window when there's
    // actually more than one video in the queue to skip to.
    LaunchedEffect(hasMultipleEpisodes) {
        VideoPipController.hasQueue = hasMultipleEpisodes
    }
    // ── Episodes panel search + filter ─────────────────────────────────────
    // "All" shows every video in the folder in series order; "Watched" /
    // "Unwatched" filter by whether there's a saved resume position; the
    // search box narrows down by title — handy for long web-series folders.
    val displayedEpisodes = remember(folderEpisodes, episodeSearchQuery, episodeFilter, video.id) {
        folderEpisodes
            .filter { ep ->
                episodeSearchQuery.isBlank() || ep.title.contains(episodeSearchQuery, ignoreCase = true)
            }
            .filter { ep ->
                when (episodeFilter) {
                    EpisodeFilter.ALL       -> true
                    EpisodeFilter.WATCHED   -> ep.id == video.id || viewModel.getLastPosition(ep.id) > 0L
                    EpisodeFilter.UNWATCHED -> ep.id != video.id && viewModel.getLastPosition(ep.id) <= 0L
                }
            }
    }

    // ── Resume Dialog ─────────────────────────────────────────────────────
    if (showResumeDialog) {
        AlertDialog(
            onDismissRequest = { showResumeDialog = false },
            title   = { Text("Resume Playback") },
            text    = { ReduceDialogScrim(); Text("Resume from ${formatMs(savedStartPos)}?") },
            confirmButton = { TextButton(onClick = { exoPlayer.seekTo(savedStartPos); showResumeDialog = false }) { Text("Resume") } },
            dismissButton = { TextButton(onClick = { exoPlayer.seekTo(0); showResumeDialog = false }) { Text("Start Over") } }
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        val screenWidthPx  = with(density) { maxWidth.toPx() }
        val screenHeightPx = with(density) { maxHeight.toPx() }
        val boxMaxWidth  = maxWidth
        val boxMaxHeight = maxHeight
        val deadZonePx     = with(density) { 50.dp.toPx() }
        val activeWidthPx  = screenWidthPx - 2 * deadZonePx

        // ── ExoPlayer Surface ────────────────────────────────────────────
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player        = exoPlayer
                    useController = false
                    resizeMode    = scaleMode.resizeMode
                    setShowSubtitleButton(false)
                    subtitleView?.setStyle(
                        androidx.media3.ui.CaptionStyleCompat(
                            android.graphics.Color.WHITE, android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT,
                            androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                            0xFF1A1A1A.toInt(), null
                        )
                    )
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    playerViewRef = this
                }
            },
            update = { pv ->
                if (pv.player != exoPlayer) pv.player = exoPlayer
                pv.resizeMode = scaleMode.resizeMode
                playerViewRef = pv
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // FIX: was excluding the FULL view rect from system gestures,
                    // which silently disabled the OS back-swipe everywhere — even
                    // inside the 50dp dead zones meant to preserve it. Now only the
                    // active brightness/pip/volume panel strip is excluded, on
                    // actual measured pixel bounds, so the dead zone behaves the
                    // same on every device regardless of density/cutouts.
                    val dz = with(density) { 50.dp.toPx() }.toInt()
                    pv.systemGestureExclusionRects = listOf(
                        android.graphics.Rect(dz, 0, (pv.width - dz).coerceAtLeast(dz), pv.height)
                    )
                }
            },
            modifier = Modifier.fillMaxSize().zIndex(0f)
                .graphicsLayer(
                    scaleX = zoomScale, scaleY = zoomScale,
                    translationX = panX, translationY = panY
                )
        )

        // ── Buffering ─────────────────────────────────────────────────────
        if (isBuffering && !playbackEnded) {
            CircularProgressIndicator(
                modifier    = Modifier.align(Alignment.Center).size(48.dp).zIndex(1f),
                color       = Color.White, strokeWidth = 3.dp
            )
        }

        // ── Gesture Layer — 3-Panel System (Brightness | PiP | Volume) ───────
        // Dead zones: 50dp on each side → reserved for Android Back Gesture.
        // Panels are computed in pixels from density, passed as pointerInput keys
        // so they stay accurate after rotation without stale-capture bugs.
        //
        //  |←50dp→|←──Left(Green)──→|←─Center(Yellow)─→|←─Right(Pink)──→|←50dp→|
        //           brightness up/dn    swipe-down→PiP      volume up/dn
        if (!anyMenuOpen && !isInPipState.value) {
            val panelWPx    = activeWidthPx / 3f
            // Panel X boundaries (absolute screen px)
            val brightStart = deadZonePx
            val brightEnd   = deadZonePx + panelWPx          // end of LEFT panel
            val pipEnd      = deadZonePx + panelWPx * 2f     // end of CENTER panel
            val volEnd      = screenWidthPx - deadZonePx     // end of RIGHT panel

            Box(modifier = Modifier.fillMaxSize().zIndex(1f)) {

                // Debug colour-tint overlays removed — were left in from
                // development (green/yellow/pink panel tints + black
                // top/bottom dead-zone shading) and visibly darkened/tinted
                // the video for real users. Panels and dead zones are still
                // fully functional below, just invisible now.

                // ── Unified gesture surface ───────────────────────────────────
                Box(
                    modifier = Modifier.fillMaxSize()
                        // Tap / double-tap
                        .pointerInput(isLocked, screenWidthPx) {
                            detectTapGestures(
                                onTap = { toggleControls() },
                                onDoubleTap = {
                                    if (!isLocked) {
                                        val seekMs = 10_000L
                                        if (it.x < screenWidthPx / 2f) {
                                            exoPlayer.seekTo((exoPlayer.currentPosition - seekMs).coerceAtLeast(0L))
                                            seekDirection = false
                                        } else {
                                            exoPlayer.seekTo((exoPlayer.currentPosition + seekMs).coerceAtMost(totalDuration))
                                            seekDirection = true
                                        }
                                        showSeekOverlay = true
                                        scope.launch { delay(800); showSeekOverlay = false }
                                    }
                                }
                            )
                        }
                        // Swipe / pinch — keys include ALL panel boundaries so lambda
                        // is never stale after rotation or density change
                        .pointerInput(isLocked, brightEnd, pipEnd, volEnd, screenWidthPx) {
                            if (!isLocked) {
                                awaitEachGesture {
                                    val down          = awaitFirstDown(requireUnconsumed = false)
                                    val downX         = down.position.x
                                    var isDrag        = false
                                    var totalDragX    = 0f
                                    var totalDragY    = 0f
                                    var gestureType   : String? = null
                                    var pipTriggered  = false
                                    var isZoom        = false
                                    var lastSpan      = 0f
                                    var lastCx        = 0f
                                    var lastCy        = 0f

                                    // ── Classify panel at touch-down ─────────
                                    // Dead zone → pass straight through (back gesture)
                                    // FIX: previously only checked downX, so a touch
                                    // starting near the TOP edge (where the user swipes
                                    // down to reveal the hidden status bar) still landed
                                    // inside the brightness/pip/volume X-range and was
                                    // treated as a normal drag — brightness or volume
                                    // would jump at the same time as the status bar
                                    // swipe-reveal. Now the top strip (same height as the
                                    // dead zone) is also excluded, same as left/right.
                                    val downY         = down.position.y
                                    val panel: String = when {
                                        downX < brightStart || downX > volEnd -> "dead"
                                        downY < deadZonePx                    -> "dead"
                                        downX < brightEnd                     -> "brightness"
                                        downX < pipEnd                        -> "pip"
                                        else                                  -> "volume"
                                    }

                                    // FIX: previously the dead-zone classification only
                                    // skipped the *single-finger swipe* branch further
                                    // below, but a touch that started in the dead zone
                                    // and then picked up a second finger (or whose first
                                    // event batch already contained 2 pointers) still fell
                                    // into the pinch-to-zoom branch, which on some devices
                                    // (different touch-slop / density) could nudge both
                                    // brightness and volume indicators on screen via the
                                    // zoom-overlay code path. Dead-zone touches now bail
                                    // out immediately and never enter the gesture loop at
                                    // all, so nothing but the system back-gesture can ever
                                    // fire from there — consistent across all devices.
                                    if (panel == "dead") {
                                        return@awaitEachGesture
                                    }

                                    var event = awaitPointerEvent()
                                    if (event.changes.size >= 2) {
                                        // Pinch-to-zoom start
                                        isZoom = true
                                        val pts = event.changes.map { it.position }
                                        lastCx = pts.sumOf { it.x.toDouble() }.toFloat() / pts.size
                                        lastCy = pts.sumOf { it.y.toDouble() }.toFloat() / pts.size
                                        val dx = pts[0].x - pts[1].x; val dy = pts[0].y - pts[1].y
                                        lastSpan = kotlin.math.sqrt(dx * dx + dy * dy)
                                        event.changes.forEach { it.consume() }
                                    }

                                    while (event.changes.any { it.pressed }) {
                                        if (isZoom) {
                                            // ── Pinch-to-zoom ────────────────
                                            if (event.changes.size >= 2) {
                                                val pts = event.changes.map { it.position }
                                                val cx = pts.sumOf { it.x.toDouble() }.toFloat() / pts.size
                                                val cy = pts.sumOf { it.y.toDouble() }.toFloat() / pts.size
                                                val dx = pts[0].x - pts[1].x; val dy = pts[0].y - pts[1].y
                                                val span = kotlin.math.sqrt(dx * dx + dy * dy)
                                                if (lastSpan > 0f)
                                                    zoomScale = (zoomScale * (span / lastSpan)).coerceIn(0.5f, 4f)
                                                showZoomIndicator = true
                                                val limitX = abs(zoomScale - 1f) * screenWidthPx / 2f
                                                val limitY = abs(zoomScale - 1f) * screenHeightPx / 2f
                                                panX = (panX + (cx - lastCx)).coerceIn(-limitX, limitX)
                                                panY = (panY + (cy - lastCy)).coerceIn(-limitY, limitY)
                                                lastSpan = span; lastCx = cx; lastCy = cy
                                            }
                                            event.changes.forEach { it.consume() }
                                        } else if (panel != "dead") {
                                            // ── Single-finger swipe ───────────
                                            val change = event.changes.firstOrNull() ?: break
                                            val drag   = change.position - change.previousPosition
                                            totalDragX += drag.x
                                            totalDragY += drag.y
                                            val totalMove = abs(totalDragX) + abs(totalDragY)

                                            // Lock gesture type once 40dp threshold crossed
                                            if (!isDrag && totalMove > 40f) {
                                                isDrag = true
                                                gestureType = when {
                                                    // Clearly horizontal → seek
                                                    abs(totalDragX) > abs(totalDragY) * 1.5f -> "seek"
                                                    // Vertical swipe — use the PANEL decided at touch-down
                                                    panel == "brightness" -> "brightness"
                                                    panel == "pip"        -> "pip"
                                                    panel == "volume"     -> "volume"
                                                    else                  -> null
                                                }
                                            }

                                            if (isDrag) {
                                                change.consume()
                                                when (gestureType) {
                                                    "seek" -> {
                                                        val delta = (drag.x / screenWidthPx * 120_000L).toLong()
                                                        seekDelta    += delta
                                                        seekDirection = seekDelta >= 0
                                                        showSeekOverlay = true
                                                    }
                                                    "brightness" -> {
                                                        // Swipe UP (negative Y) → brighter
                                                        brightnessVal = (brightnessVal - drag.y / screenHeightPx * 1.5f).coerceIn(0f, 1f)
                                                        setBrightness(activity, brightnessVal)
                                                        showBrightness  = true
                                                        showVolume      = false
                                                        showPipOverlay  = false
                                                    }
                                                    "pip" -> {
                                                        // Only trigger on first confirmed DOWNWARD swipe (positive Y)
                                                        if (!pipTriggered && totalDragY > 80f &&
                                                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                                            pipTriggered   = true
                                                            showPipOverlay = true
                                                            scope.launch {
                                                                delay(350)
                                                                try {
                                                                    // FIX: use the real video play/pause action, not a bare
                                                                    // params object — see VideoPipController.
                                                                    activity?.enterPictureInPictureMode(
                                                                        buildVideoPipParams(context, exoPlayer.isPlaying)
                                                                    )
                                                                } catch (_: Exception) {}
                                                                delay(1200)
                                                                showPipOverlay = false
                                                            }
                                                        }
                                                    }
                                                    "volume" -> {
                                                        // Swipe UP (negative Y) → louder
                                                        val newVol = (videoVolume + (-drag.y / screenHeightPx * 1.5f) * 2f).coerceIn(0f, 2f)
                                                        videoVolume = newVol
                                                        val sysVol = (newVol.coerceAtMost(1f) * maxVolume)
                                                            .roundToInt().coerceIn(0, maxVolume.toInt())
                                                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, sysVol, 0)
                                                        volumeVal       = newVol.coerceAtMost(1f)
                                                        showVolume      = true
                                                        showBrightness  = false
                                                        showPipOverlay  = false
                                                    }
                                                }
                                            }
                                        }

                                        event = awaitPointerEvent()
                                        // Detect mid-gesture pinch upgrade
                                        if (!isZoom && event.changes.size >= 2) {
                                            isZoom = true
                                            val pts = event.changes.map { it.position }
                                            lastCx = pts.sumOf { it.x.toDouble() }.toFloat() / pts.size
                                            lastCy = pts.sumOf { it.y.toDouble() }.toFloat() / pts.size
                                            val dx = pts[0].x - pts[1].x; val dy = pts[0].y - pts[1].y
                                            lastSpan = kotlin.math.sqrt(dx * dx + dy * dy)
                                            showZoomIndicator = true
                                            event.changes.forEach { it.consume() }
                                        }
                                    }

                                    // ── Gesture end ───────────────────────────
                                    if (isZoom) {
                                        showZoomIndicator = true
                                        scope.launch { delay(800); showZoomIndicator = false }
                                    } else if (isDrag) {
                                        if (gestureType == "seek") {
                                            exoPlayer.seekTo((currentPos + seekDelta).coerceIn(0L, totalDuration))
                                        }
                                        seekDelta = 0L
                                        scope.launch {
                                            delay(800)
                                            showSeekOverlay = false
                                            showBrightness  = false
                                            showVolume      = false
                                        }
                                    }
                                }
                            }
                        }
                )
            }
        }

        // ── Controls overlay ──────────────────────────────────────────────
        // FIX: hidden in PiP — the floating window is tiny and the system
        // already overlays its own play/pause/close controls on it.
        AnimatedVisibility(visible = showControls && !isInPipState.value, enter = fadeIn(tween(0)), exit = fadeOut(tween(0)),
            modifier = Modifier.fillMaxSize().zIndex(2f)) {
            Box(modifier = Modifier.fillMaxSize()) {

                // ── Top bar ───────────────────────────────────────────────
                Box(
                    modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(0.88f), Color.Transparent)))
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(
                                WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                            )
                        )
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Back
                        IconButton(onClick = onBack) {
                            Icon(Icons.Filled.ArrowBack, null, tint = Color.White)
                        }
                        // Thumbnail + title
                        AsyncImage(
                            model            = ImageRequest.Builder(context).data(video.contentUri).crossfade(true).build(),
                            contentDescription = null, contentScale = ContentScale.Crop,
                            modifier         = Modifier.size(36.dp).clip(RoundedCornerShape(6.dp))
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(video.title, color = Color.White,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.basicMarquee())
                            if (boxMaxWidth > boxMaxHeight) {
                                Text("${video.resolution} • ${video.formattedSize}",
                                    color = Color.White.copy(0.7f), style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        // Action buttons — only in landscape; in portrait they appear at bottom
                        // Order: floating, audio track, caption, fit, speed, recently played, episodes
                        if (boxMaxWidth > boxMaxHeight) {
                            // Background play (floating)
                            IconButton(onClick = { enterBackgroundPlay(); resetHideTimer() }) {
                                Icon(Icons.Rounded.OpenInNew, null,
                                    tint = if (isBackgroundPlay) Color(0xFFBB86FC) else Color.White)
                            }
                            // Audio track
                            Box {
                                IconButton(onClick = {
                                    showAudioMenu = !showAudioMenu
                                    showSubMenu = false; showScaleMenu = false; showSpeedMenu = false; showMoreMenu = false; showRotateMenu = false
                                }) {
                                    Icon(Icons.Rounded.RecordVoiceOver, null,
                                        tint = if (audioTracks.size > 1) Color.White else Color.White.copy(0.45f))
                                }
                                if (audioTracks.size > 1) {
                                    Surface(modifier = Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 6.dp),
                                        shape = CircleShape, color = Color(0xFFBB86FC)) {
                                        Text("${audioTracks.size}", color = Color.Black, fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp))
                                    }
                                }
                            }
                            // Subtitles (caption)
                            IconButton(onClick = {
                                showSubMenu = !showSubMenu
                                showAudioMenu = false; showScaleMenu = false; showSpeedMenu = false; showMoreMenu = false; showRotateMenu = false
                            }) {
                                Icon(Icons.Rounded.ClosedCaption, null,
                                    tint = if (selectedSubtitleGroupIdx >= 0) Color(0xFFBB86FC) else Color.White)
                            }
                            // Scale (fit)
                            IconButton(onClick = {
                                showScaleMenu = !showScaleMenu
                                showAudioMenu = false; showSubMenu = false; showRotateMenu = false; showSpeedMenu = false; showMoreMenu = false
                            }) {
                                Icon(Icons.Filled.AspectRatio, null, tint = Color.White)
                            }
                            // Speed
                            TextButton(onClick = {
                                showSpeedMenu = !showSpeedMenu
                                showAudioMenu = false; showSubMenu = false; showRotateMenu = false; showScaleMenu = false; showMoreMenu = false
                            }) {
                                Text("${playbackSpeed}x", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                            // Recently played
                            IconButton(onClick = { showRecentPanel = !showRecentPanel; if (showRecentPanel) controlsJob?.cancel() else resetHideTimer() }) {
                                Icon(Icons.Rounded.History, null, tint = Color.White)
                            }
                            // Episodes (same-folder web-series list)
                            if (hasMultipleEpisodes) {
                                Box {
                                    IconButton(onClick = {
                                        showEpisodesPanel = !showEpisodesPanel
                                        if (showEpisodesPanel) controlsJob?.cancel() else resetHideTimer()
                                    }) {
                                        Icon(Icons.Rounded.VideoLibrary, null,
                                            tint = if (showEpisodesPanel) Color(0xFFBB86FC) else Color.White)
                                    }
                                    Surface(modifier = Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 6.dp),
                                        shape = CircleShape, color = Color(0xFFBB86FC)) {
                                        Text("${folderEpisodes.size}", color = Color.Black, fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Center controls (portrait/landscape rearranged) ───────
                if (!isLocked) {
                    Row(
                        modifier              = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(28.dp),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick  = { if (exoPlayer.currentPosition > 5000) exoPlayer.seekTo(0) else viewModel.playPrev(); resetHideTimer() },
                            modifier = Modifier.size(52.dp)
                        ) { Icon(Icons.Filled.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(36.dp)) }

                        IconButton(
                            onClick  = { exoPlayer.seekTo((exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L)); resetHideTimer() },
                            modifier = Modifier.size(52.dp)
                        ) { Icon(Icons.Filled.Replay10, null, tint = Color.White, modifier = Modifier.size(36.dp)) }

                        Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.2f), modifier = Modifier.size(72.dp)) {
                            IconButton(
                                onClick  = {
                                    if (playbackEnded) { exoPlayer.seekTo(0); exoPlayer.play(); playbackEnded = false }
                                    else { if (isPlaying) exoPlayer.pause() else exoPlayer.play() }
                                    resetHideTimer()
                                },
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Icon(
                                    imageVector = when { playbackEnded -> Icons.Filled.Replay; isPlaying -> Icons.Filled.Pause; else -> Icons.Filled.PlayArrow },
                                    contentDescription = null, tint = Color.White, modifier = Modifier.size(46.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick  = { exoPlayer.seekTo((exoPlayer.currentPosition + 10_000L).coerceAtMost(totalDuration)); resetHideTimer() },
                            modifier = Modifier.size(52.dp)
                        ) { Icon(Icons.Filled.Forward10, null, tint = Color.White, modifier = Modifier.size(36.dp)) }

                        IconButton(
                            onClick  = { viewModel.playNext(); resetHideTimer() },
                            modifier = Modifier.size(52.dp)
                        ) { Icon(Icons.Filled.SkipNext, null, tint = Color.White, modifier = Modifier.size(36.dp)) }
                    }
                }

                // ── Bottom bar with seek bar ─────────────────────────────
                if (!isLocked) {
                    Column(
                        modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.88f))))
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp)
                    ) {
                        val displayPos = if (isDraggingSeek) dragPosition else currentPos
                        val safeTotal  = totalDuration.coerceAtLeast(1L)
                        val progress   = (displayPos.toFloat() / safeTotal.toFloat()).coerceIn(0f, 1f)

                        // Lock + rotate above seek bar
                        Row(
                            modifier              = Modifier.fillMaxWidth().padding(bottom = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = {
                                isLocked = !isLocked
                                if (isLocked) {
                                    controlsJob?.cancel(); showControls = true
                                    controlsJob = scope.launch { delay(2000); showControls = false }
                                } else resetHideTimer()
                            }, modifier = Modifier.size(44.dp)) {
                                Icon(
                                    if (isLocked) Icons.Filled.Lock else Icons.Filled.LockOpen, null,
                                    tint = if (isLocked) Color(0xFFBB86FC) else Color.White.copy(0.8f),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            IconButton(onClick = {
                                // FIX: was toggling AUTO <-> PORTRAIT, which does nothing useful —
                                // a portrait video is already portrait. The rotate button's real
                                // job is to force LANDSCAPE for full-screen viewing regardless of
                                // the device's rotation lock, then release back to AUTO.
                                rotationMode = if (rotationMode == RotationMode.LANDSCAPE) RotationMode.AUTO else RotationMode.LANDSCAPE
                                viewModel.saveRotationMode(rotationMode.name)
                                resetHideTimer()
                            }, modifier = Modifier.size(44.dp)) {
                                Icon(
                                    Icons.Rounded.ScreenRotation, null,
                                    tint = if (rotationMode == RotationMode.LANDSCAPE) Color(0xFFBB86FC) else Color.White.copy(0.8f),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        // Action buttons at bottom in portrait mode
                        if (boxMaxWidth <= boxMaxHeight) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Order: floating, audio track, caption, fit, speed, recently played, episodes
                                // Background play (floating)
                                IconButton(onClick = { enterBackgroundPlay(); resetHideTimer() }) {
                                    Icon(Icons.Rounded.OpenInNew, null,
                                        tint = if (isBackgroundPlay) Color(0xFFBB86FC) else Color.White)
                                }
                                // Audio track
                                Box {
                                    IconButton(onClick = {
                                        showAudioMenu = !showAudioMenu
                                        showSubMenu = false; showScaleMenu = false; showSpeedMenu = false; showMoreMenu = false; showRotateMenu = false
                                    }) {
                                        Icon(Icons.Rounded.RecordVoiceOver, null,
                                            tint = if (audioTracks.size > 1) Color.White else Color.White.copy(0.45f))
                                    }
                                    if (audioTracks.size > 1) {
                                        Surface(modifier = Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 6.dp),
                                            shape = CircleShape, color = Color(0xFFBB86FC)) {
                                            Text("${audioTracks.size}", color = Color.Black, fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp))
                                        }
                                    }
                                }
                                // Subtitles (caption)
                                IconButton(onClick = {
                                    showSubMenu = !showSubMenu
                                    showAudioMenu = false; showScaleMenu = false; showSpeedMenu = false; showMoreMenu = false; showRotateMenu = false
                                }) {
                                    Icon(Icons.Rounded.ClosedCaption, null,
                                        tint = if (selectedSubtitleGroupIdx >= 0) Color(0xFFBB86FC) else Color.White)
                                }
                                // Scale (fit)
                                IconButton(onClick = {
                                    showScaleMenu = !showScaleMenu
                                    showAudioMenu = false; showSubMenu = false; showRotateMenu = false; showSpeedMenu = false; showMoreMenu = false
                                }) {
                                    Icon(Icons.Filled.AspectRatio, null, tint = Color.White)
                                }
                                // Speed
                                TextButton(onClick = {
                                    showSpeedMenu = !showSpeedMenu
                                    showAudioMenu = false; showSubMenu = false; showRotateMenu = false; showScaleMenu = false; showMoreMenu = false
                                }) {
                                    Text("${playbackSpeed}x", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                                // Recently played
                                IconButton(onClick = { showRecentPanel = !showRecentPanel; if (showRecentPanel) controlsJob?.cancel() else resetHideTimer() }) {
                                    Icon(Icons.Rounded.History, null, tint = Color.White)
                                }
                                // Episodes (same-folder web-series list)
                                if (hasMultipleEpisodes) {
                                    Box {
                                        IconButton(onClick = {
                                            showEpisodesPanel = !showEpisodesPanel
                                            if (showEpisodesPanel) controlsJob?.cancel() else resetHideTimer()
                                        }) {
                                            Icon(Icons.Rounded.VideoLibrary, null,
                                                tint = if (showEpisodesPanel) Color(0xFFBB86FC) else Color.White)
                                        }
                                        Surface(modifier = Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 6.dp),
                                            shape = CircleShape, color = Color(0xFFBB86FC)) {
                                            Text("${folderEpisodes.size}", color = Color.Black, fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp))
                                        }
                                    }
                                }
                            }
                        }

                        // FIX: Seek bar glitch — don't call seekTo onValueChange.
                        // Only update dragPosition (a pure state var). Seek applied on onValueChangeFinished.
                        Slider(
                            value         = progress,
                            onValueChange = { frac ->
                                isDraggingSeek = true
                                dragPosition   = (frac * safeTotal).toLong()
                                controlsJob?.cancel()
                            },
                            onValueChangeFinished = {
                                exoPlayer.seekTo(dragPosition)
                                // FIX: seek bar glitch on release — currentPos held the
                                // stale pre-drag position until the next 250ms ticker
                                // tick, so the thumb visibly snapped back then jumped
                                // forward again right after letting go. Setting it here
                                // immediately removes that flash.
                                currentPos     = dragPosition
                                isDraggingSeek = false
                                resetHideTimer()
                            },
                            colors  = SliderDefaults.colors(
                                thumbColor         = Color.White,
                                activeTrackColor   = Color(0xFFBB86FC),
                                inactiveTrackColor = Color.White.copy(0.3f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier              = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            // Left: time
                            Text(formatMs(displayPos), color = Color.White, fontSize = 12.sp)

                            // Center: volume indicator + loop + info
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.VolumeUp, null,
                                        tint = if (videoVolume > 1f) Color(0xFFBB86FC) else Color.White.copy(0.5f),
                                        modifier = Modifier.size(16.dp))
                                    Text("${(videoVolume * 100).roundToInt()}%",
                                        color = if (videoVolume > 1f) Color(0xFFBB86FC) else Color.White.copy(0.5f),
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(start = 2.dp, end = 8.dp))
                                }
                                IconButton(onClick = {
                                    isLoopEnabled = !isLoopEnabled
                                    viewModel.saveLoopEnabled(isLoopEnabled)
                                    resetHideTimer()
                                }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Rounded.Repeat, null,
                                        tint = if (isLoopEnabled) Color(0xFFBB86FC) else Color.White.copy(0.5f),
                                        modifier = Modifier.size(18.dp))
                                }
                                IconButton(onClick = { showVideoInfo = !showVideoInfo; resetHideTimer() }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Rounded.Info, null, tint = Color.White.copy(0.5f), modifier = Modifier.size(18.dp))
                                }
                            }

                            // Right: total duration
                            Text(formatMs(safeTotal), color = Color.White, fontSize = 12.sp)
                        }
                    }
                }

                // ── Lock indicator ────────────────────────────────────────
                if (isLocked) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AnimatedVisibility(visible = showControls, modifier = Modifier.align(Alignment.Center)) {
                            Surface(shape = RoundedCornerShape(50), color = Color.Black.copy(0.75f)) {
                                Row(modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Filled.Lock, null, tint = Color(0xFFBB86FC), modifier = Modifier.size(20.dp))
                                    Text("Screen Locked", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                        AnimatedVisibility(visible = showControls, modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp)) {
                            IconButton(onClick = { isLocked = false; resetHideTimer() },
                                modifier = Modifier.background(Color.Black.copy(0.6f), CircleShape).size(44.dp)) {
                                Icon(Icons.Filled.LockOpen, null, tint = Color.White)
                            }
                        }
                    }
                }

                // FIX: Audio track / Captions / Fit(Scale) / Speed used to be tiny
                // DropdownMenus manually offset with hardcoded end-padding guesses
                // ("end = 196.dp" etc). Those offsets assumed a fixed button layout,
                // so on other screen widths / after the action-bar reordered, the
                // popup rendered away from the icon that opened it — i.e. it
                // "appeared somewhere else". They're now real side panels (like
                // Recently Played / Episodes below), always anchored to CenterEnd,
                // so they can never drift off from their trigger button.

                if (showVideoInfo) {
                    AlertDialog(
                        onDismissRequest = { showVideoInfo = false },
                        title = { Text("Video Info") },
                        text  = {
                            ReduceDialogScrim()
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                InfoRow("Title", video.title); InfoRow("Resolution", video.resolution)
                                InfoRow("Size", video.formattedSize); InfoRow("Duration", video.formattedDuration)
                                InfoRow("Format", video.mimeType); InfoRow("Folder", video.folderName)
                                if (audioTracks.isNotEmpty()) InfoRow("Audio", audioTracks.joinToString { it.third })
                                if (subtitleTracks.isNotEmpty()) InfoRow("Subtitles", subtitleTracks.joinToString { it.third })
                            }
                        },
                        confirmButton = { TextButton(onClick = { showVideoInfo = false }) { Text("Close") } }
                    )
                }

                // ── Sleep Timer Dialog (with custom input) ────────────────
                if (showSleepTimerDialog) {
                    VideoSleepTimerDialog(
                        remaining  = sleepTimerRemaining,
                        onSelect   = { mins ->
                            sleepTimerJob?.cancel()
                            sleepTimerRemaining = mins * 60
                            sleepTimerJob = sleepTimerScope.launch {
                                while (sleepTimerRemaining > 0) {
                                    delay(1000L)
                                    sleepTimerRemaining -= 1
                                }
                                exoPlayer.pause()
                            }
                            showSleepTimerDialog = false
                        },
                        onCancel   = { sleepTimerJob?.cancel(); sleepTimerRemaining = 0; showSleepTimerDialog = false },
                        onDismiss  = { showSleepTimerDialog = false }
                    )
                }

                // ── Rename Dialog ─────────────────────────────────────────
                if (showRenameDialog) {
                    com.io.ab.music.ui.components.RenameDialog(
                        currentName = video.title,
                        onDismiss   = { showRenameDialog = false },
                        onConfirm   = { newName -> viewModel.renameVideo(context, video, newName); showRenameDialog = false }
                    )
                }

                // ── Delete Dialog ─────────────────────────────────────────
                if (showDeleteDialog) {
                    AlertDialog(
                        onDismissRequest = { showDeleteDialog = false },
                        icon  = { Icon(Icons.Rounded.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
                        title = { Text("Delete Video") },
                        text  = { ReduceDialogScrim(); Text("\"${video.title}\" will be permanently deleted from your device.") },
                        confirmButton = {
                            Button(onClick = { viewModel.deleteVideo(video); showDeleteDialog = false; onBack() },
                                shape  = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Delete") }
                        },
                        dismissButton = {
                            OutlinedButton(onClick = { showDeleteDialog = false }, shape = RoundedCornerShape(12.dp)) { Text("Cancel") }
                        }
                    )
                }
            }
        }

        // ── Audio track panel ───────────────────────────────────────────────
        AnimatedVisibility(
            visible  = showAudioMenu,
            enter    = slideInHorizontally { it } + fadeIn(),
            exit     = slideOutHorizontally { it } + fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd).zIndex(3f)
        ) {
            VideoOptionPanel(title = "Audio Track", icon = Icons.Rounded.RecordVoiceOver,
                onClose = { showAudioMenu = false; resetHideTimer() }) {
                if (audioTracks.isEmpty()) {
                    Text("No audio tracks detected", color = Color.White.copy(0.6f),
                        style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(10.dp))
                } else {
                    audioTracks.forEach { (groupIdx, trackIdx, label) ->
                        VideoOptionRow(
                            label    = label,
                            selected = selectedAudioGroupIdx == groupIdx,
                            icon     = Icons.Rounded.RecordVoiceOver,
                            onClick  = { switchAudioTrack(groupIdx, trackIdx); showAudioMenu = false; resetHideTimer() }
                        )
                    }
                }
            }
        }

        // ── Captions / Subtitles panel ──────────────────────────────────────
        AnimatedVisibility(
            visible  = showSubMenu,
            enter    = slideInHorizontally { it } + fadeIn(),
            exit     = slideOutHorizontally { it } + fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd).zIndex(3f)
        ) {
            VideoOptionPanel(title = "Captions / Subtitles", icon = Icons.Rounded.ClosedCaption,
                onClose = { showSubMenu = false; resetHideTimer() }) {
                VideoOptionRow(
                    label    = "Off",
                    selected = selectedSubtitleGroupIdx == -1,
                    icon     = Icons.Rounded.ClosedCaptionDisabled,
                    onClick  = { switchSubtitleTrack(-1, -1); showSubMenu = false; resetHideTimer() }
                )
                if (subtitleTracks.isEmpty()) {
                    Text("No subtitles in this file", color = Color.White.copy(0.6f),
                        style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(10.dp))
                } else {
                    subtitleTracks.forEach { (groupIdx, trackIdx, label) ->
                        VideoOptionRow(
                            label    = label,
                            selected = selectedSubtitleGroupIdx == groupIdx,
                            icon     = Icons.Rounded.ClosedCaption,
                            onClick  = { switchSubtitleTrack(groupIdx, trackIdx); showSubMenu = false; resetHideTimer() }
                        )
                    }
                }
            }
        }

        // ── Fit / Scale panel ───────────────────────────────────────────────
        AnimatedVisibility(
            visible  = showScaleMenu,
            enter    = slideInHorizontally { it } + fadeIn(),
            exit     = slideOutHorizontally { it } + fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd).zIndex(3f)
        ) {
            VideoOptionPanel(title = "Fit", icon = Icons.Filled.AspectRatio,
                onClose = { showScaleMenu = false; resetHideTimer() }) {
                ScaleMode.entries.forEach { mode ->
                    VideoOptionRow(
                        label    = mode.label,
                        selected = scaleMode == mode,
                        icon     = Icons.Filled.AspectRatio,
                        onClick  = {
                            scaleMode = mode
                            viewModel.saveScaleMode(mode.name)
                            showScaleMenu = false; resetHideTimer()
                        }
                    )
                }
            }
        }

        // ── Speed panel ──────────────────────────────────────────────────────
        AnimatedVisibility(
            visible  = showSpeedMenu,
            enter    = slideInHorizontally { it } + fadeIn(),
            exit     = slideOutHorizontally { it } + fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd).zIndex(3f)
        ) {
            VideoOptionPanel(title = "Speed", icon = Icons.Filled.Speed,
                onClose = { showSpeedMenu = false; resetHideTimer() }) {
                listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f, 3.5f, 4f).forEach { spd ->
                    VideoOptionRow(
                        label    = "${spd}x",
                        selected = playbackSpeed == spd,
                        icon     = Icons.Filled.Speed,
                        onClick  = {
                            playbackSpeed = spd
                            viewModel.savePlaybackSpeed(spd)
                            showSpeedMenu = false; resetHideTimer()
                        }
                    )
                }
            }
        }

        // ── Recently played panel ─────────────────────────────────────────
        AnimatedVisibility(
            visible  = showRecentPanel && recentlyPlayed.isNotEmpty(),
            enter    = slideInHorizontally { it } + fadeIn(),
            exit     = slideOutHorizontally { it } + fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd).zIndex(3f)
        ) {
            Surface(modifier = Modifier.fillMaxHeight().width(220.dp),
                color = Color.Black.copy(0.88f),
                shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.History, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Recently Played", color = Color.White, style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        IconButton(onClick = { showRecentPanel = false; resetHideTimer() }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Filled.Close, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        itemsIndexed(recentlyPlayed) { _, recentVideo ->
                            RecentVideoItem(
                                video   = recentVideo, context = context,
                                lastPos = viewModel.getLastPosition(recentVideo.id),
                                onClick = { viewModel.playVideo(recentVideo); showRecentPanel = false }
                            )
                        }
                    }
                }
            }
        }

        // ── Episodes panel (same-folder web-series) ────────────────────────
        AnimatedVisibility(
            visible  = showEpisodesPanel && hasMultipleEpisodes,
            enter    = slideInHorizontally { it } + fadeIn(),
            exit     = slideOutHorizontally { it } + fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd).zIndex(3f)
        ) {
            Surface(modifier = Modifier.fillMaxHeight().width(260.dp),
                color = Color.Black.copy(0.88f),
                shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.VideoLibrary, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Episodes", color = Color.White, style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold)
                            Text(video.folderName, color = Color.White.copy(0.6f),
                                style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = {
                            showEpisodesPanel = false; episodeSearchQuery = ""; episodeFilter = EpisodeFilter.ALL
                            resetHideTimer()
                        }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Filled.Close, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    // Search box — filters the list below by video title.
                    OutlinedTextField(
                        value = episodeSearchQuery,
                        onValueChange = { episodeSearchQuery = it },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        placeholder = { Text("Search videos…", color = Color.White.copy(0.4f), fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Filled.Search, null, tint = Color.White.copy(0.6f), modifier = Modifier.size(16.dp)) },
                        trailingIcon = {
                            if (episodeSearchQuery.isNotEmpty()) {
                                IconButton(onClick = { episodeSearchQuery = "" }, modifier = Modifier.size(20.dp)) {
                                    Icon(Icons.Filled.Close, null, tint = Color.White.copy(0.6f), modifier = Modifier.size(14.dp))
                                }
                            }
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall.copy(color = Color.White),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = Color(0xFFBB86FC),
                            unfocusedBorderColor = Color.White.copy(0.25f),
                            cursorColor          = Color(0xFFBB86FC)
                        )
                    )
                    Spacer(Modifier.height(8.dp))
                    // Filter chips: All / Watched / Unwatched.
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        EpisodeFilter.entries.forEach { f ->
                            val selected = episodeFilter == f
                            Surface(
                                onClick = { episodeFilter = f },
                                shape  = RoundedCornerShape(50),
                                color  = if (selected) Color(0xFFBB86FC).copy(0.28f) else Color.White.copy(0.08f)
                            ) {
                                Text(f.label,
                                    color = if (selected) Color(0xFFBB86FC) else Color.White.copy(0.75f),
                                    fontSize = 10.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (displayedEpisodes.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().padding(top = 24.dp), contentAlignment = Alignment.Center) {
                            Text("No videos found", color = Color.White.copy(0.5f), style = MaterialTheme.typography.labelSmall)
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(displayedEpisodes, key = { it.id }) { episodeVideo ->
                                // Number reflects the video's position in the full,
                                // unfiltered series order (not the filtered list),
                                // so "Video 4" always means the same video even
                                // while searching/filtering.
                                val seriesIndex = folderEpisodes.indexOf(episodeVideo) + 1
                                EpisodeListItem(
                                    index      = seriesIndex,
                                    video      = episodeVideo,
                                    isPlaying  = episodeVideo.id == video.id,
                                    context    = context,
                                    lastPos    = viewModel.getLastPosition(episodeVideo.id),
                                    onClick    = {
                                        if (episodeVideo.id != video.id) {
                                            viewModel.playVideo(episodeVideo, folderEpisodes)
                                        }
                                        showEpisodesPanel = false
                                        episodeSearchQuery = ""
                                        episodeFilter = EpisodeFilter.ALL
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Seek overlay ──────────────────────────────────────────────────
        AnimatedVisibility(visible = showSeekOverlay, enter = fadeIn(tween(0)), exit = fadeOut(tween(0)),
            modifier = Modifier.align(Alignment.Center).zIndex(4f)) {
            Surface(shape = RoundedCornerShape(12.dp), color = Color.Black.copy(0.65f)) {
                Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(if (seekDirection) Icons.Filled.FastForward else Icons.Filled.FastRewind,
                        null, tint = Color.White, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.height(4.dp))
                    Text(formatMs((currentPos + seekDelta).coerceIn(0L, totalDuration)),
                        color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
        }

        // ── Brightness overlay (left panel) ──────────────────────────────
        AnimatedVisibility(visible = showBrightness, enter = fadeIn(tween(150)), exit = fadeOut(tween(300)),
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 32.dp).zIndex(4f)) {
            GestureIndicator(icon = Icons.Filled.BrightnessHigh, value = brightnessVal, maxValue = 1f,
                label = "${(brightnessVal * 100).roundToInt()}%")
        }

        // ── PiP overlay (center panel) ────────────────────────────────────
        AnimatedVisibility(visible = showPipOverlay, enter = fadeIn(tween(150)), exit = fadeOut(tween(300)),
            modifier = Modifier.align(Alignment.Center).zIndex(4f)) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xCC000000)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Filled.PictureInPicture,
                        contentDescription = null,
                        tint = Color(0xFFFFCC00),
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        "Entering Floating Player…",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // ── Volume overlay (right panel) ──────────────────────────────────
        AnimatedVisibility(visible = showVolume, enter = fadeIn(tween(150)), exit = fadeOut(tween(300)),
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 32.dp).zIndex(4f)) {
            GestureIndicator(icon = Icons.Filled.VolumeUp, value = volumeVal / 1f, maxValue = 1f,
                label = "${(videoVolume * 100).roundToInt()}%", accent = videoVolume > 1f)
        }

        // ── Zoom indicator ────────────────────────────────────────────────
        AnimatedVisibility(visible = showZoomIndicator, enter = fadeIn(tween(0)), exit = fadeOut(tween(0)),
            modifier = Modifier.align(Alignment.Center).zIndex(4f)) {
            Surface(shape = RoundedCornerShape(12.dp), color = Color.Black.copy(0.65f)) {
                Text("${(zoomScale * 100).roundToInt()}%",
                    color = Color.White, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp))
            }
        }
    }
}

// ── Video Sleep Timer Dialog (with custom input) ──────────────────────────────
@Composable
private fun VideoSleepTimerDialog(
    remaining: Int,
    onSelect : (Int) -> Unit,
    onCancel : () -> Unit,
    onDismiss: () -> Unit
) {
    val presets = listOf(5 to "5 min", 10 to "10 min", 15 to "15 min",
                         30 to "30 min", 60 to "1 hour", 90 to "90 min")
    var customText  by remember { mutableStateOf("") }
    var customError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon  = { Icon(Icons.Rounded.Timer, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("Sleep Timer") },
        text  = {
            ReduceDialogScrim()
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (remaining > 0) {
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.Center) {
                            Text("Active: %d:%02d remaining".format(remaining / 60, remaining % 60),
                                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                        Text("Cancel Timer")
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                }
                Text("Quick select:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(presets) { (min, label) ->
                        FilterChip(selected = false, onClick = { onSelect(min) }, label = { Text(label) })
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text("Custom duration:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value         = customText,
                        onValueChange = { customText = it.filter { c -> c.isDigit() }; customError = false },
                        label         = { Text("Minutes") },
                        singleLine    = true, isError = customError,
                        supportingText = if (customError) {{ Text("Enter 1–999") }} else null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier      = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)
                    )
                    Button(
                        onClick = {
                            val mins = customText.toIntOrNull()
                            if (mins != null && mins in 1..999) onSelect(mins) else customError = true
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Set") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

// ── Composables ───────────────────────────────────────────────────────────────

/** Keeps just a slight dim behind video-player dialogs instead of the heavy default scrim. */
@Composable
private fun ReduceDialogScrim(dimAmount: Float = 0.32f) {
    val view = LocalView.current
    SideEffect {
        (view.parent as? DialogWindowProvider)?.window?.setDimAmount(dimAmount)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(80.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f),
            maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** Reusable right-side sliding panel — same look as Recently Played / Episodes,
 *  used for Audio Track, Captions, Fit(Scale) and Speed so every option list
 *  in the video player shares one consistent, reliably-anchored surface
 *  instead of small DropdownMenus with hand-guessed offsets. */
@Composable
private fun VideoOptionPanel(
    title  : String,
    icon   : ImageVector,
    onClose: () -> Unit,
    width  : androidx.compose.ui.unit.Dp = 200.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(modifier = Modifier.fillMaxHeight().width(width),
        color = Color.Black.copy(0.88f),
        shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)) {
        Column(modifier = Modifier.padding(12.dp).fillMaxHeight()) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(title, color = Color.White, style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.Close, null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            // FIX: option lists (Speed, Audio Track, Captions…) used to sit in a
            // plain Column with no scroll — once the list of options was taller
            // than the panel, the rest was just clipped off with no way to reach
            // it. This now scrolls whenever content overflows the available height.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                content()
            }
        }
    }
}

@Composable
private fun VideoOptionRow(label: String, selected: Boolean, icon: ImageVector, onClick: () -> Unit) {
    Surface(
        onClick  = onClick,
        color    = if (selected) Color(0xFFBB86FC).copy(0.22f) else Color.Transparent,
        shape    = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(if (selected) Icons.Filled.Check else icon, null,
                tint = if (selected) Color(0xFFBB86FC) else Color.White.copy(0.7f), modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(label, color = if (selected) Color(0xFFBB86FC) else Color.White,
                style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun RecentVideoItem(video: Video, context: Context, lastPos: Long, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.White.copy(0.08f), shape = RoundedCornerShape(8.dp)) {
        Row(modifier = Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(width = 72.dp, height = 42.dp).clip(RoundedCornerShape(6.dp))) {
                AsyncImage(model = ImageRequest.Builder(context).data(video.contentUri).crossfade(true).build(),
                    contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                if (lastPos > 0L && video.duration > 0L) {
                    val progress = (lastPos.toFloat() / video.duration.toFloat()).coerceIn(0f, 1f)
                    Box(modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color.White.copy(0.3f))) {
                        Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(progress).background(Color(0xFFBB86FC)))
                    }
                }
                Surface(modifier = Modifier.align(Alignment.BottomEnd).padding(2.dp),
                    shape = RoundedCornerShape(3.dp), color = Color.Black.copy(0.7f)) {
                    Text(video.formattedDuration, color = Color.White, fontSize = 8.sp,
                        modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp))
                }
            }
            Spacer(Modifier.width(6.dp))
            Text(text = video.title, color = Color.White, style = MaterialTheme.typography.labelSmall,
                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun EpisodeListItem(
    index: Int, video: Video, isPlaying: Boolean, context: Context, lastPos: Long, onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = if (isPlaying) Color(0xFFBB86FC).copy(0.22f) else Color.White.copy(0.08f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(modifier = Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(width = 72.dp, height = 42.dp).clip(RoundedCornerShape(6.dp))) {
                AsyncImage(model = ImageRequest.Builder(context).data(video.contentUri).crossfade(true).build(),
                    contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                if (lastPos > 0L && video.duration > 0L && !isPlaying) {
                    val progress = (lastPos.toFloat() / video.duration.toFloat()).coerceIn(0f, 1f)
                    Box(modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color.White.copy(0.3f))) {
                        Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(progress).background(Color(0xFFBB86FC)))
                    }
                }
                if (isPlaying) {
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.35f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                } else {
                    Surface(modifier = Modifier.align(Alignment.BottomEnd).padding(2.dp),
                        shape = RoundedCornerShape(3.dp), color = Color.Black.copy(0.7f)) {
                        Text(video.formattedDuration, color = Color.White, fontSize = 8.sp,
                            modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp))
                    }
                }
            }
            Spacer(Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Video $index", color = Color(0xFFBB86FC), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Text(text = video.title,
                    color = if (isPlaying) Color(0xFFBB86FC) else Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun GestureIndicator(
    icon    : ImageVector,
    value   : Float,
    maxValue: Float = 1f,
    label   : String = "${(value / maxValue * 100).roundToInt()}%",
    accent  : Boolean = false
) {
    Surface(shape = RoundedCornerShape(12.dp), color = Color.Black.copy(0.65f)) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, tint = if (accent) Color(0xFFBB86FC) else Color.White, modifier = Modifier.size(24.dp))
            Box(modifier = Modifier.width(6.dp).height(80.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(0.25f))) {
                Box(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .fillMaxHeight((value / maxValue).coerceIn(0f, 1f))
                    .clip(RoundedCornerShape(3.dp)).background(Color(0xFFBB86FC)))
            }
            Text(label, color = if (accent) Color(0xFFBB86FC) else Color.White, fontSize = 11.sp,
                fontWeight = if (accent) FontWeight.Bold else FontWeight.Normal)
        }
    }
}

/** Filter options for the Episodes (web-series) panel search/filter bar. */
private enum class EpisodeFilter(val label: String) {
    ALL("All"), WATCHED("Watched"), UNWATCHED("Unwatched")
}

// ── Helpers ───────────────────────────────────────────────────────────────────
private fun formatMs(ms: Long): String {
    val totalSec = ms / 1000
    val h = totalSec / 3600; val m = (totalSec % 3600) / 60; val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** Natural-order string comparator so "Episode 2" sorts before "Episode 10"
 *  (plain lexical sort would put "Episode 10" first). Splits each name into
 *  runs of digits vs. non-digits and compares number chunks numerically. */
private fun naturalOrderComparator(): Comparator<String> = Comparator { a, b ->
    val ra = Regex("\\d+|\\D+").findAll(a).map { it.value }.toList()
    val rb = Regex("\\d+|\\D+").findAll(b).map { it.value }.toList()
    var i = 0
    while (i < ra.size && i < rb.size) {
        val pa = ra[i]; val pb = rb[i]
        val cmp = if (pa.firstOrNull()?.isDigit() == true && pb.firstOrNull()?.isDigit() == true) {
            (pa.toLongOrNull() ?: 0L).compareTo(pb.toLongOrNull() ?: 0L)
        } else {
            pa.compareTo(pb, ignoreCase = true)
        }
        if (cmp != 0) return@Comparator cmp
        i++
    }
    ra.size - rb.size
}

private fun getBrightness(context: Context): Float = try {
    Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
} catch (_: Exception) { 0.5f }

private fun setBrightness(activity: Activity?, value: Float) {
    activity?.window?.attributes = activity?.window?.attributes?.also { lp ->
        lp.screenBrightness = value.coerceIn(0.01f, 1f)
    }
}
