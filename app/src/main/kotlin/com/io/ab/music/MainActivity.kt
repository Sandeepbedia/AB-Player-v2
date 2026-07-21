package com.io.ab.music

import android.Manifest
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import com.io.ab.music.data.preferences.UserPreferences
import com.io.ab.music.service.MusicService
import com.io.ab.music.ui.navigation.ABMusicNavGraph
import com.io.ab.music.ui.screens.home.PermissionScreen
import com.io.ab.music.ui.theme.ABMusicTheme
import com.io.ab.music.ui.theme.ThemeMode
import com.io.ab.music.ui.viewmodel.LibraryViewModel
import com.io.ab.music.ui.viewmodel.PlayerViewModel
import com.io.ab.music.ui.viewmodel.SettingsViewModel
import com.io.ab.music.util.UpdateNotificationHelper
import com.io.ab.music.util.CrashReportManager
import com.io.ab.music.ui.screens.crash.CrashReportScreen
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.delay

/** True while the Activity is in system Picture-in-Picture (floating) mode.
 *  Screens that must NOT tear down their player/state while floating (e.g.
 *  VideoPlayerScreen) read this to skip disposal logic during the transition. */
val LocalIsInPictureInPicture = staticCompositionLocalOf { false }

/** Tracks whether VideoPlayerScreen is currently mounted/active. Used by
 *  MainActivity.onWindowFocusChanged to re-hide the status/nav bars — Android
 *  re-shows system bars whenever the window regains focus (returning from a
 *  dialog, PiP resize, Recents, another app, etc.), so without this the nav
 *  bar / gesture pill would pop back up and stay visible over the video. */
object ImmersiveVideoState {
    var isActive: Boolean = false
}

/** Action for the broadcast sent when the user taps Play/Pause on the
 *  system Picture-in-Picture window for the VIDEO player. */
const val ACTION_VIDEO_PIP_PLAY_PAUSE = "com.io.ab.music.ACTION_VIDEO_PIP_PLAY_PAUSE"

/** Actions for the Previous / Next buttons on the floating (PiP) video
 *  window — added alongside Play/Pause so users can skip episodes without
 *  leaving the floating player. */
const val ACTION_VIDEO_PIP_PREVIOUS = "com.io.ab.music.ACTION_VIDEO_PIP_PREVIOUS"
const val ACTION_VIDEO_PIP_NEXT     = "com.io.ab.music.ACTION_VIDEO_PIP_NEXT"

/** Bridges the system PiP window's Play/Pause/Previous/Next actions back to
 *  whichever ExoPlayer is currently driving VideoPlayerScreen.
 *
 *  FIX: Entering PiP for video previously used a bare
 *  PictureInPictureParams with NO custom actions. Android's SystemUI then
 *  fell back to auto-overlaying the app's *active MediaSession* controls on
 *  top of the floating window — but the only MediaSession running in this
 *  app belongs to MusicService (the AUDIO player), not the video screen.
 *  So tapping Play/Pause on the floating video actually toggled MUSIC
 *  playback. VideoPlayerScreen now registers its real play/pause/prev/next
 *  callbacks here, and buildVideoPipParams() below supplies them as explicit
 *  RemoteActions — which take priority over the automatic MediaSession
 *  overlay — so the buttons on the floating window control the video. */
object VideoPipController {
    var isPlaying by mutableStateOf(false)
    var onPlayPause: (() -> Unit)? = null
    var onPrevious: (() -> Unit)? = null
    var onNext: (() -> Unit)? = null
    // FIX: previously always true, so Previous/Next were shown even when
    // there was only a single video (nothing to skip to). VideoPlayerScreen
    // sets this based on whether the current queue actually has more than
    // one item, so the floating window only offers skip controls when
    // they'll do something.
    var hasQueue: Boolean by mutableStateOf(false)
    // FIX: tapping the system PiP window's own "close" (X) button used to
    // leave the video's ExoPlayer alive — it kept playing audio in the
    // background with nothing visible, because the only cleanup path was a
    // Compose LifecycleEventObserver registered deep inside
    // VideoPlayerScreen, which can lose the race against the Activity/
    // Composition actually tearing down. MainActivity.onStop() below calls
    // this directly instead — a plain Activity callback that always fires
    // reliably, independent of Compose's own teardown timing.
    var onForceStop: (() -> Unit)? = null
}

/** Builds PictureInPictureParams for the VIDEO player with explicit
 *  Previous / Play-Pause / Next RemoteActions wired to [VideoPipController],
 *  so the floating window always controls the actual video instead of the
 *  music session, and lets the user skip episodes without leaving PiP. */
fun buildVideoPipParams(context: Context, isPlaying: Boolean): PictureInPictureParams {
    val builder = PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9))
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        fun action(actionStr: String, requestCode: Int, iconRes: Int, label: String) = RemoteAction(
            Icon.createWithResource(context, iconRes), label, label,
            PendingIntent.getBroadcast(
                context, requestCode,
                Intent(actionStr).setPackage(context.packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val playPauseLabel = if (isPlaying) "Pause" else "Play"
        val actions = buildList {
            if (VideoPipController.hasQueue) add(action(ACTION_VIDEO_PIP_PREVIOUS, 9002, android.R.drawable.ic_media_previous, "Previous"))
            add(action(ACTION_VIDEO_PIP_PLAY_PAUSE, 9001, playPauseIcon, playPauseLabel))
            if (VideoPipController.hasQueue) add(action(ACTION_VIDEO_PIP_NEXT, 9003, android.R.drawable.ic_media_next, "Next"))
        }
        builder.setActions(actions)
    }
    return builder.build()
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var shortcutRoute by mutableStateOf<String?>(null)

    // ── PiP state, exposed app-wide via LocalIsInPictureInPicture ──────────────
    // FIX: Without this, entering PiP triggered a configuration change that tore
    // down VideoPlayerScreen's DisposableEffect, which called clearCurrentVideo()
    // and removed the player from the nav graph entirely — looking like the app
    // had closed instead of shrinking into the floating PiP window.
    private var isInPip by mutableStateOf(false)

    // FIX (real root cause of "PiP X button doesn't force-stop"): tapping the
    // system PiP window's close (X) button does NOT set isFinishing = true —
    // Android just stops the activity without ever calling finish() on it, then
    // separately fires onPictureInPictureModeChanged(false). So the previous
    // `if (isFinishing)` check in onStop() below was never true for this path
    // and silently did nothing. The correct signal (confirmed pattern for this
    // exact problem) is the COMBINATION of the two callbacks: onStop() fires
    // first while still in PiP, and — only when the X was tapped (not when the
    // user taps the PiP window to expand back to full screen, which resumes
    // straight to onStart()/onResume() and never calls onStop() at all) —
    // onPictureInPictureModeChanged(false) then fires afterward with no
    // onStart() in between. Track that with this flag.
    private var stoppedWithoutForeground = false

    // Receives the tap on the PiP window's Play/Pause RemoteAction and forwards
    // it to whichever ExoPlayer VideoPlayerScreen has registered — see
    // VideoPipController for why this exists instead of relying on defaults.
    private val pipActionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_VIDEO_PIP_PLAY_PAUSE -> VideoPipController.onPlayPause?.invoke()
                ACTION_VIDEO_PIP_PREVIOUS   -> VideoPipController.onPrevious?.invoke()
                ACTION_VIDEO_PIP_NEXT       -> VideoPipController.onNext?.invoke()
            }
        }
    }

    @Inject
    lateinit var userPreferences: UserPreferences

    // FIX: Get PlayerViewModel at Activity level so we can call syncWithService
    // from onResume. This ensures the highlight / isPlaying state is refreshed
    // whenever the app returns from background (the service may still be playing).
    private val playerViewModel: PlayerViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()
    private val libraryViewModel: LibraryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Keep splash visible until library data is loaded from DB.
        splashScreen.setKeepOnScreenCondition { !libraryViewModel.uiState.value.isReady }
        shortcutRoute = routeFromShortcut(intent)
        // Feature: crash report — CrashHandler restarts the app into this Activity
        // with EXTRA_CRASH_LOG set after any uncaught crash. Grab it once here.
        var pendingCrashLogPath by mutableStateOf(
            intent.getStringExtra(com.io.ab.music.util.CrashHandler.EXTRA_CRASH_LOG)
        )
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }

        val pipFilter = IntentFilter(ACTION_VIDEO_PIP_PLAY_PAUSE).apply {
            addAction(ACTION_VIDEO_PIP_PREVIOUS)
            addAction(ACTION_VIDEO_PIP_NEXT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(pipActionReceiver, pipFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(pipActionReceiver, pipFilter)
        }

        setContent {
            val crashLogFile = remember(pendingCrashLogPath) {
                CrashReportManager.findCrashLog(this@MainActivity, pendingCrashLogPath)
            }
            if (crashLogFile != null) {
                CrashReportScreen(
                    logFile   = crashLogFile,
                    onDismiss = { pendingCrashLogPath = null }
                )
                return@setContent
            }

            // Defer update check until the first screen has settled.
            LaunchedEffect(Unit) {
                delay(5_000L)
                settingsViewModel.checkForUpdate(onUpdateFound = { updateInfo ->
                    UpdateNotificationHelper.showUpdateNotification(this@MainActivity, updateInfo)
                })
            }

            val themeStr     by userPreferences.themeMode.collectAsState(initial = "SYSTEM")
            val dynamicColor by userPreferences.dynamicColor.collectAsState(initial = true)
            val wallpaperEnabled by userPreferences.wallpaperEnabled.collectAsState(initial = false)
            val wallpaperPath    by userPreferences.wallpaperPath.collectAsState(initial = "")
            val wallpaperBlur    by userPreferences.wallpaperBlur.collectAsState(initial = 18f)
            val wallpaperDim     by userPreferences.wallpaperDim.collectAsState(initial = 0.30f)

            val themeMode = remember(themeStr) {
                try { ThemeMode.valueOf(themeStr.uppercase()) }
                catch (e: Exception) { ThemeMode.SYSTEM }
            }
            val wallpaperActive = wallpaperEnabled && wallpaperPath.isNotBlank()

            // Keep the floating window's Play/Pause icon in sync with the actual
            // video ExoPlayer state (e.g. it ends, or is paused from elsewhere).
            LaunchedEffect(isInPip, VideoPipController.isPlaying, VideoPipController.hasQueue) {
                if (isInPip && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    try { setPictureInPictureParams(buildVideoPipParams(this@MainActivity, VideoPipController.isPlaying)) }
                    catch (_: Exception) {}
                }
            }

            CompositionLocalProvider(LocalIsInPictureInPicture provides isInPip) {
            ABMusicTheme(
                themeMode       = themeMode,
                dynamicColor    = dynamicColor,
                wallpaperActive = wallpaperActive
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (wallpaperActive) {
                        com.io.ab.music.ui.components.WallpaperBackground(
                            imagePath  = wallpaperPath,
                            blurRadius = wallpaperBlur.dp,
                            dimAlpha   = wallpaperDim
                        )
                    }
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color    = if (wallpaperActive) androidx.compose.ui.graphics.Color.Transparent
                                   else MaterialTheme.colorScheme.background
                    ) {
                        PermissionGate(shortcutRoute = shortcutRoute, libraryViewModel = libraryViewModel)
                    }
                }
            }
            }
        }
    }

    // FIX: Track PiP mode at the Activity level — this is the ONLY reliable signal
    // for "did we just enter/exit floating player mode". Composables alone can't
    // detect this. We expose it via CompositionLocal so VideoPlayerScreen can stay
    // mounted (skip its teardown/clearCurrentVideo logic) while the user is in PiP.
    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: android.content.res.Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPip = isInPictureInPictureMode
        // FIX: see stoppedWithoutForeground above — this combination (PiP just
        // ended AND the activity already went through onStop() with no onStart()
        // in between) only happens when the user tapped the PiP window's close
        // (X) button or swiped it away. Force-stop the app here, the same way
        // the notification's Close App action does.
        if (!isInPictureInPictureMode && stoppedWithoutForeground) {
            forceStopApp()
        }
    }

    override fun onStart() {
        super.onStart()
        stoppedWithoutForeground = false
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(pipActionReceiver) } catch (_: Exception) {}
    }

    // FIX: PiP close button — tapping the system "X" on the floating video
    // window used to be assumed to finish() the Activity, but it doesn't (see
    // stoppedWithoutForeground above) — it just stops it. onStop() is still a
    // direct, always-reliable Activity callback (unlike a Compose Lifecycle
    // observer, which can be torn down before it gets to react), so it's still
    // the right place to record that a stop happened; the actual force-stop for
    // the X-button case now runs from onPictureInPictureModeChanged above once
    // that combination is confirmed. The isFinishing branch here still covers
    // any other genuine finish (e.g. back button), independent of PiP.
    override fun onStop() {
        super.onStop()
        stoppedWithoutForeground = true
        if (isFinishing) {
            forceStopApp()
        }
    }

    // FIX: releasing the ExoPlayer alone left the app process itself alive in
    // the background (and MusicService still running if audio had ever been
    // touched in this session) — same as before, just without a visible video.
    // The notification's "Close App" button (see NotificationCloseReceiver)
    // already does a real force-stop: stop the service, then kill the process.
    // Mirror that exact behavior here so the PiP close button actually closes
    // the app instead of leaving it lingering in the background.
    private fun forceStopApp() {
        try { VideoPipController.onForceStop?.invoke() } catch (_: Exception) {}
        try { stopService(Intent(this, MusicService::class.java)) } catch (_: Exception) {}
        Process.killProcess(Process.myPid())
    }

    // FIX: Pressing Home (or Recents) while the video screen was on top used to
    // just background the Activity like any other app — VideoPlayerScreen's
    // ON_STOP lifecycle observer then paused the video (since neither PiP nor
    // "Background play" was active), so playback silently stopped and, from
    // the user's point of view, the video/app just "closed" instead of
    // continuing as a floating window. onUserLeaveHint() fires BEFORE ON_STOP,
    // so entering PiP here keeps the same ExoPlayer alive and visible in a
    // floating window instead of it ever going to background at all.
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && ImmersiveVideoState.isActive && !isInPip) {
            try {
                enterPictureInPictureMode(buildVideoPipParams(this, VideoPipController.isPlaying))
            } catch (_: Exception) {}
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        shortcutRoute = routeFromShortcut(intent)
    }

    private fun routeFromShortcut(intent: Intent?): String? = when (intent?.action) {
        "com.io.ab.music.shortcut.HOME" -> "home"
        "com.io.ab.music.shortcut.VIDEO" -> "video"
        else -> null
    }

    // FIX: Re-sync player state every time the app is foregrounded.
    // This fixes: play highlight song not showing & mini-player colour wrong after app close/reopen.
    override fun onResume() {
        super.onResume()
        playerViewModel.syncWithService()
    }

    // FIX: System bars (status + nav) get force-shown by Android whenever the
    // window regains focus — e.g. dismissing a dialog, resizing out of PiP,
    // or coming back from Recents/another app. If the video player is active
    // at that moment, re-hide them immediately instead of leaving the nav
    // bar / gesture pill sitting visibly on top of the video.
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && com.io.ab.music.ImmersiveVideoState.isActive) {
            com.io.ab.music.ui.screens.video.applyImmersiveMode(this)
        }
    }
}

@Composable
fun PermissionGate(
    shortcutRoute: String? = null,
    libraryViewModel: LibraryViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val audioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasPermission = permissions[audioPermission] == true
        if (hasPermission) libraryViewModel.scanLibrary()
    }

    if (hasPermission) {
        ABMusicNavGraph(initialRoute = shortcutRoute)
    } else {
        PermissionScreen(
            onRequestPermission = {
                val perms = mutableListOf(audioPermission)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    perms.add(Manifest.permission.POST_NOTIFICATIONS)
                    perms.add(Manifest.permission.READ_MEDIA_VIDEO)
                } else {
                    perms.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
                launcher.launch(perms.toTypedArray())
            }
        )
    }
}
