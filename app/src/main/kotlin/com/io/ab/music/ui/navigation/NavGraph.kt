package com.io.ab.music.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.navigation.*
import androidx.navigation.compose.*
import com.io.ab.music.domain.model.Song
import com.io.ab.music.ui.components.MiniPlayer
import com.io.ab.music.ui.components.PagerScrollGuard
import com.io.ab.music.ui.components.DogPetPositionState
import com.io.ab.music.ui.components.rememberDogPetState
import com.io.ab.music.ui.screens.home.HomeScreen
import com.io.ab.music.ui.screens.explore.ExploreScreen
import com.io.ab.music.ui.screens.favorites.FavoritesScreen
import com.io.ab.music.ui.screens.playlists.PlaylistsScreen
import com.io.ab.music.ui.screens.playlists.PlaylistDetailScreen
import com.io.ab.music.ui.screens.player.PlayerScreen
import com.io.ab.music.ui.screens.player.QueueScreen
import com.io.ab.music.ui.screens.search.SearchScreen
import com.io.ab.music.ui.screens.settings.ChangelogHistoryScreen
import com.io.ab.music.ui.screens.settings.SettingsScreen
import com.io.ab.music.ui.screens.video.VideoScreen
import com.io.ab.music.ui.screens.video.VideoPlayerScreen
import com.io.ab.music.ui.viewmodel.PlayerViewModel
import com.io.ab.music.ui.viewmodel.SettingsViewModel
import com.io.ab.music.ui.viewmodel.VideoViewModel
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch

// Shared scroll state so any tab can signal the mini player to hide
val LocalMiniPlayerScrollState = staticCompositionLocalOf<MiniPlayerScrollState> {
    error("MiniPlayerScrollState not provided")
}

class MiniPlayerScrollState {
    var isScrollingDown by mutableStateOf(false)
        private set

    private var cumulativeDelta = 0f

    fun reset() {
        cumulativeDelta = 0f
        isScrollingDown = false
    }

    fun onScrollDelta(delta: Float) {
        if (delta == 0f) return
        cumulativeDelta += delta
        val next = when {
            delta < 0f -> {
                cumulativeDelta = minOf(cumulativeDelta, 0f)
                cumulativeDelta < -80f
            }
            else -> {
                cumulativeDelta = maxOf(cumulativeDelta, 0f)
                false
            }
        }
        if (next != isScrollingDown) isScrollingDown = next
    }
}

// Routes that go full screen (no tab bar / mini player)
private val fullScreenRoutes = setOf(
    Screen.Player.route,
    Screen.VideoPlayer.route,
    Screen.Search.route,
    Screen.Queue.route,
    Screen.ChangelogHistory.route,
    Screen.PlaylistDetail.route,
    Screen.ArtistDetail.route
)

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ABMusicNavGraph(
    initialRoute: String? = null,
    playerViewModel: PlayerViewModel = hiltViewModel(),
    videoViewModel : VideoViewModel  = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val navController    = rememberNavController()
    val currentBackStack by navController.currentBackStackEntryAsState()
    val currentRoute     = currentBackStack?.destination?.route

    val isFullScreen = remember(currentRoute) {
        currentRoute != null && currentRoute in fullScreenRoutes
    }

    val currentSongId by playerViewModel.currentSongId.collectAsState()

    // Scroll-aware mini player: hide on scroll-down, show on scroll-up
    val miniPlayerScrollState = remember { MiniPlayerScrollState() }
    val initialPage = remember(initialRoute) {
        bottomNavItems.indexOfFirst { it.screen.route == initialRoute }.coerceAtLeast(0)
    }
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { bottomNavItems.size })
    val scope      = rememberCoroutineScope()

    LaunchedEffect(initialRoute) {
        val target = bottomNavItems.indexOfFirst { it.screen.route == initialRoute }
        if (target >= 0 && target != pagerState.currentPage) pagerState.scrollToPage(target)
    }

    // Reset mini player scroll state when switching tabs — ensures it re-appears
    LaunchedEffect(pagerState.currentPage) {
        miniPlayerScrollState.reset()
    }

    val showMiniPlayer = currentSongId != null && !isFullScreen && !miniPlayerScrollState.isScrollingDown
    val wallpaperActive = com.io.ab.music.ui.theme.LocalWallpaperActive.current
    val settingsUiState by settingsViewModel.uiState.collectAsState()

    // FIX: Pet position state lives here (outside AnimatedVisibility) so the pet's
    // dragged position survives MiniPlayer hide/show cycles caused by scrolling.
    val dogPetPositionState = rememberDogPetState()

    CompositionLocalProvider(LocalMiniPlayerScrollState provides miniPlayerScrollState) {
    Scaffold(
        containerColor = Color.Transparent,
        contentColor   = MaterialTheme.colorScheme.onBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            AnimatedVisibility(
                visible = !isFullScreen,
                enter   = fadeIn(tween(180)) + slideInVertically(tween(180)) { -it },
                exit    = fadeOut(tween(130)) + slideOutVertically(tween(130)) { -it }
            ) {
                RoundedTabBar(
                    // FIX: pill-highlight glitch on screen switch — `pagerState.currentPage`
                    // tracks live scroll position, so animating/swiping across more than one
                    // tab (e.g. Home → Settings) made the highlight flash across every
                    // in-between tab before landing. `targetPage` stays pinned to the actual
                    // destination for the whole transition, so the pill jumps straight there.
                    selectedIndex = pagerState.targetPage,
                    wallpaperActive = wallpaperActive,
                    onTabClick    = { idx ->
                        scope.launch {
                            // FIX: Use simple tween — spring caused visible bounce/jitter on tab taps
                            pagerState.animateScrollToPage(
                                page          = idx,
                                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
                            )
                        }
                    }
                )
            }
        }
    ) { innerPadding ->

        Box(modifier = Modifier.fillMaxSize()) {
            // ── Global theme gradient — starts from very top (behind status bar + tab bar) ──
            // Single source of truth for the app background gradient.
            // Tab bar and all screens are transparent so this gradient bleeds through
            // from window top to mid-screen, producing one continuous premium backdrop.
            if (!isFullScreen) {
                val primary    = MaterialTheme.colorScheme.primary
                val background = MaterialTheme.colorScheme.background
                val themeMode  = com.io.ab.music.ui.theme.LocalThemeMode.current
                if (themeMode != com.io.ab.music.ui.theme.ThemeMode.AMOLED_BW) {
                    val globalBrush = remember(primary, background) {
                        Brush.verticalGradient(
                            0.0f  to primary.copy(alpha = 0.42f),
                            0.15f to primary.copy(alpha = 0.32f),
                            0.30f to primary.copy(alpha = 0.20f),
                            0.50f to primary.copy(alpha = 0.08f),
                            0.75f to primary.copy(alpha = 0.02f),
                            1.0f  to background.copy(alpha = 0f)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.58f)
                            .background(globalBrush)
                    )
                }
            }

        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {

            if (!isFullScreen) {
                // FIX: Removed per-page scaleFactor animateFloatAsState — it ran a coroutine
                //      and triggered recomposition on EVERY animation frame during swipe,
                //      causing 60fps UI rebuilds even when nothing changed.
                // FIX: swiping past the first/last tab showed the default stretch/glow
                //      overscroll effect, which has its own little settle-back animation —
                //      felt like a random delay at either end of the tab strip. Disabling
                //      overscroll here removes that lingering bounce entirely.
                CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
                HorizontalPager(
                    state                 = pagerState,
                    modifier              = Modifier.fillMaxSize(),
                    key                   = { bottomNavItems[it].screen.route },
                    beyondBoundsPageCount = 1,  // pre-compose neighbours for zero-jank swipe
                    // FIX: Home/Video/Explore each contain their own horizontal card
                    // rows on the same axis as this page-swipe gesture, which was
                    // partially triggering a page-switch while scrolling those rows.
                    // Those rows now call Modifier.guardPagerScroll(), which flips
                    // this off for the duration of a touch on them.
                    userScrollEnabled      = PagerScrollGuard.pagerScrollEnabled,
                    flingBehavior         = PagerDefaults.flingBehavior(
                        state                   = pagerState,
                        snapAnimationSpec       = tween(durationMillis = 260, easing = FastOutSlowInEasing),
                        snapPositionalThreshold = 0.3f
                    )
                ) { page ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        when (bottomNavItems[page].screen) {
                            Screen.Home      -> HomeScreen(
                                onSearchClick      = { navController.navigate(Screen.Search.route) },
                                onSongClick        = { song, queue ->
                                    playerViewModel.playQueue(queue, queue.indexOf(song).coerceAtLeast(0))
                                },
                                onShuffleAll       = {},
                                onChangelogHistory = { navController.navigate(Screen.ChangelogHistory.route) }
                            )
                            Screen.Video     -> VideoScreen(
                                onVideoClick  = { navController.navigate(Screen.VideoPlayer.route) },
                                viewModel     = videoViewModel
                            )
                            Screen.Explore   -> ExploreScreen(
                                onSongClick   = { song, queue ->
                                    playerViewModel.playQueue(queue, queue.indexOf(song).coerceAtLeast(0))
                                },
                                onSearchClick = { navController.navigate(Screen.Search.route) },
                                onPlaylistClick = { playlist ->
                                    navController.navigate(Screen.PlaylistDetail.createRoute(playlist.id))
                                },
                                onArtistClick = { artist ->
                                    navController.navigate(Screen.ArtistDetail.createRoute(artist.id))
                                }
                            )
                            Screen.Playlists -> PlaylistsScreen(
                                onPlaylistClick = { playlist ->
                                    navController.navigate(Screen.PlaylistDetail.createRoute(playlist.id))
                                }
                            )
                            Screen.Favorites -> FavoritesScreen(
                                onSongClick = { song, queue ->
                                    playerViewModel.playQueue(queue, queue.indexOf(song).coerceAtLeast(0))
                                }
                            )
                            Screen.Settings  -> SettingsScreen(
                                onChangelogHistory = { navController.navigate(Screen.ChangelogHistory.route) }
                            )
                            else             -> {}
                        }
                    }
                }
                } // end CompositionLocalProvider(LocalOverscrollConfiguration)

                // MiniPlayer: spring-based slide-up from bottom with scale + fade.
                // initialOffsetY = 100% of own height (not full screen) for a snappy natural feel.
                AnimatedVisibility(
                    visible = showMiniPlayer,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding(),
                    enter = scaleIn(
                        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
                        initialScale  = 0.85f,
                        transformOrigin = TransformOrigin(0.5f, 1f)
                    ) + slideInVertically(
                        animationSpec  = tween(durationMillis = 320, easing = FastOutSlowInEasing),
                        initialOffsetY = { it }
                    ) + fadeIn(tween(260, easing = FastOutSlowInEasing)),
                    exit  = scaleOut(
                        animationSpec   = tween(durationMillis = 260, easing = FastOutSlowInEasing),
                        targetScale     = 0.85f,
                        transformOrigin = TransformOrigin(0.5f, 1f)
                    ) + slideOutVertically(
                        animationSpec  = tween(durationMillis = 260, easing = FastOutSlowInEasing),
                        targetOffsetY  = { it }
                    ) + fadeOut(tween(200, easing = FastOutSlowInEasing))
                ) {
                    MiniPlayerHost(
                        playerViewModel     = playerViewModel,
                        petEnabled          = settingsUiState.petEnabled,
                        dogPositionState    = dogPetPositionState,
                        onPlayerClick       = { navController.navigate(Screen.Player.route) }
                    )
                }
            }

            // Full-screen overlay routes — NavHost only manages these
            NavHost(
                navController       = navController,
                startDestination    = Screen.Home.route,
                modifier            = Modifier.fillMaxSize(),
                enterTransition     = { fadeIn(tween(0)) },
                exitTransition      = { fadeOut(tween(0)) },
                popEnterTransition  = { fadeIn(tween(0)) },
                popExitTransition   = { fadeOut(tween(0)) }
            ) {
                // Tab destinations are empty — rendering is handled by the Pager above
                composable(Screen.Home.route)      {}
                composable(Screen.Video.route)     {}
                composable(Screen.Explore.route)   {}
                composable(Screen.Playlists.route) {}
                composable(Screen.Favorites.route) {}
                composable(Screen.Settings.route)  {}

                composable(
                    Screen.PlaylistDetail.route,
                    arguments = listOf(navArgument("playlistId") { type = NavType.LongType }),
                    enterTransition    = {
                        slideInVertically(tween(300, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(260))
                    },
                    exitTransition     = { slideOutVertically(tween(260, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(220)) },
                    popEnterTransition = { fadeIn(tween(200)) },
                    popExitTransition  = { slideOutVertically(tween(260, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(220)) }
                ) { backStackEntry ->
                    val pid = backStackEntry.arguments?.getLong("playlistId") ?: 0L
                    val libraryViewModel: com.io.ab.music.ui.viewmodel.LibraryViewModel = hiltViewModel()
                    val uiState by libraryViewModel.uiState.collectAsState()
                    val playlist = uiState.playlists.find { it.id == pid }
                    if (playlist != null) {
                        PlaylistDetailScreen(
                            playlist         = playlist,
                            onBack           = { navController.popBackStack() },
                            onSongClick      = { song, queue ->
                                playerViewModel.playQueue(queue, queue.indexOf(song).coerceAtLeast(0))
                            },
                            libraryViewModel = libraryViewModel
                        )
                    } else {
                        // Playlist not found (e.g. deleted) — bail out gracefully
                        LaunchedEffect(Unit) { navController.popBackStack() }
                    }
                }

                composable(
                    Screen.ArtistDetail.route,
                    arguments = listOf(navArgument("artistId") { type = NavType.LongType }),
                    enterTransition    = {
                        slideInVertically(tween(300, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(260))
                    },
                    exitTransition     = { slideOutVertically(tween(260, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(220)) },
                    popEnterTransition = { fadeIn(tween(200)) },
                    popExitTransition  = { slideOutVertically(tween(260, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(220)) }
                ) { backStackEntry ->
                    val aid = backStackEntry.arguments?.getLong("artistId") ?: 0L
                    val libraryViewModel: com.io.ab.music.ui.viewmodel.LibraryViewModel = hiltViewModel()
                    val uiState by libraryViewModel.uiState.collectAsState()
                    val artist = uiState.artists.find { it.id == aid }
                    if (artist != null) {
                        com.io.ab.music.ui.screens.artist.ArtistDetailScreen(
                            artist           = artist,
                            onBack           = { navController.popBackStack() },
                            onSongClick      = { song, queue ->
                                playerViewModel.playQueue(queue, queue.indexOf(song).coerceAtLeast(0))
                            },
                            libraryViewModel = libraryViewModel
                        )
                    } else {
                        LaunchedEffect(Unit) { navController.popBackStack() }
                    }
                }

                composable(
                    Screen.Player.route,
                    enterTransition    = {
                        slideInVertically(tween(320, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(280))
                    },
                    exitTransition     = { slideOutVertically(tween(260, easing = FastOutSlowInEasing)) { it } },
                    popEnterTransition = { slideInVertically(tween(300, easing = FastOutSlowInEasing)) { -it } + fadeIn(tween(280)) },
                    popExitTransition  = { slideOutVertically(tween(300, easing = FastOutSlowInEasing)) { it } }
                ) {
                    PlayerScreen(
                        onBack       = { navController.popBackStack() },
                        onQueueClick = { navController.navigate(Screen.Queue.route) },
                        viewModel    = playerViewModel
                    )
                }

                composable(
                    Screen.Queue.route,
                    enterTransition = {
                        slideInVertically(tween(280, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(250))
                    },
                    exitTransition  = { slideOutVertically(tween(240, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(220)) }
                ) {
                    QueueScreen(
                        onBack    = { navController.popBackStack() },
                        viewModel = playerViewModel
                    )
                }

                composable(
                    Screen.Search.route,
                    enterTransition = {
                        slideInVertically(tween(280, easing = FastOutSlowInEasing)) { -it / 2 } + fadeIn(tween(260))
                    },
                    exitTransition  = { slideOutVertically(tween(240, easing = FastOutSlowInEasing)) { -it / 2 } + fadeOut(tween(220)) }
                ) {
                    SearchScreen(
                        onBack      = { navController.popBackStack() },
                        onSongClick = { song, queue ->
                            playerViewModel.playQueue(queue, queue.indexOf(song).coerceAtLeast(0))
                        }
                    )
                }

                // VideoPlayer full screen
                composable(
                    Screen.VideoPlayer.route,
                    enterTransition = {
                        slideInVertically(tween(320, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(280))
                    },
                    exitTransition  = { slideOutVertically(tween(260, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(240)) },
                    popEnterTransition = { slideInVertically(tween(300, easing = FastOutSlowInEasing)) { -it } + fadeIn(tween(280)) },
                    popExitTransition  = { slideOutVertically(tween(280, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(260)) }
                ) {
                    val currentVideo by videoViewModel.currentVideo.collectAsState()
                    currentVideo?.let { video ->
                        VideoPlayerScreen(
                            video     = video,
                            viewModel = videoViewModel,
                            onBack    = { navController.popBackStack() }
                        )
                    } ?: run { navController.popBackStack() }
                }

                composable(
                    Screen.ChangelogHistory.route,
                    enterTransition    = {
                        slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(260))
                    },
                    exitTransition     = {
                        slideOutHorizontally(tween(260, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(220))
                    },
                    popEnterTransition = {
                        slideInHorizontally(tween(280, easing = FastOutSlowInEasing)) { -it } + fadeIn(tween(250))
                    },
                    popExitTransition  = {
                        slideOutHorizontally(tween(260, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(220))
                    }
                ) {
                    ChangelogHistoryScreen(onBack = { navController.popBackStack() })
                }
            }
        }
        } // end innerPadding Box
    } // end global gradient outer Box (Scaffold lambda)
    } // end CompositionLocalProvider
}

@Composable
private fun MiniPlayerHost(
    playerViewModel : PlayerViewModel,
    petEnabled      : Boolean = true,
    dogPositionState: DogPetPositionState? = null,
    onPlayerClick   : () -> Unit
) {
    val playerState by playerViewModel.playerState.collectAsState()
    MiniPlayer(
        playerState      = playerState,
        onPlayerClick    = onPlayerClick,
        onPlayPause      = { playerViewModel.playPause() },
        onNext           = { playerViewModel.next() },
        onPrev           = { playerViewModel.previous() },
        showPet          = petEnabled,
        dogPositionState = dogPositionState
    )
}

// ── Neumorphic pill tab bar ────────────────────────────────────────────────────
@Composable
fun RoundedTabBar(
    selectedIndex  : Int,
    wallpaperActive: Boolean = false,
    onTabClick     : (Int) -> Unit
) {
    // Tab bar is always transparent — screen gradient shows through behind status bar
    Surface(
        modifier       = Modifier.fillMaxWidth(),
        color          = Color.Transparent,
        shadowElevation= 0.dp,
        tonalElevation = 0.dp
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                bottomNavItems.forEachIndexed { index, item ->
                    val selected = index == selectedIndex
                    RoundedTab(
                        selected = selected,
                        label    = item.label,
                        onClick  = { onTabClick(index) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            // FIX: hide the divider line over a custom wallpaper — a hard edge
            // looks out of place floating on top of a photo background.
            if (!wallpaperActive) {
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color     = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )
            }
        }
    }
}

@Composable
private fun RoundedTab(
    selected: Boolean,
    label   : String,
    onClick : () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor by animateColorAsState(
        targetValue   = if (selected) MaterialTheme.colorScheme.primaryContainer
                        else androidx.compose.ui.graphics.Color.Transparent,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label         = "tab_bg"
    )
    val contentColor by animateColorAsState(
        targetValue   = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label         = "tab_fg"
    )

    // FIX: pill "shadow flash" on page switch — Surface(onClick = ...) drives the
    // tap through a clickable Modifier that shows the default ripple/indication
    // (a dark expanding overlay) on top of the color animation. With both running
    // together the ripple reads as a brief dark "shadow" flashing across the pill
    // right as the highlight animates in. The color swap already IS the feedback,
    // so the ripple is redundant — drop it via an explicit null-indication
    // clickable and a plain (non-interactive) Surface for the background.
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        modifier       = modifier
            .padding(horizontal = 3.dp, vertical = 2.dp)
            .clickable(
                interactionSource = interactionSource,
                indication        = null,
                onClick           = onClick
            ),
        shape          = RoundedCornerShape(50),
        color          = containerColor,
        tonalElevation = 0.dp,
        shadowElevation= 0.dp
    ) {
        Box(
            modifier         = Modifier.fillMaxWidth().padding(vertical = 5.dp, horizontal = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text     = label,
                style    = MaterialTheme.typography.labelMedium,
                color    = contentColor,
                maxLines = 1
            )
        }
    }
}
