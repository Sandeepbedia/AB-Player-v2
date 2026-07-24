package com.io.ab.music.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String) {
    object Home         : Screen("home")
    object Explore      : Screen("explore")
    object Favorites    : Screen("favorites")
    object Video        : Screen("video")
    object Settings     : Screen("settings")
    object Player       : Screen("player")
    object VideoPlayer  : Screen("video_player")
    object Search       : Screen("search")
    object Playlists    : Screen("playlists")
    object PlaylistDetail : Screen("playlist/{playlistId}") {
        fun createRoute(id: Long) = "playlist/$id"
    }
    object AlbumDetail  : Screen("album/{albumId}") {
        fun createRoute(id: Long) = "album/$id"
    }
    object ArtistDetail : Screen("artist/{artistId}") {
        fun createRoute(id: Long) = "artist/$id"
    }
    object SleepTimer   : Screen("sleep_timer")
    object Equalizer    : Screen("equalizer")
    object Queue        : Screen("queue")
    object NowPlaying   : Screen("now_playing")
    object ChangelogHistory : Screen("changelog_history")
}

data class BottomNavItem(
    val screen       : Screen,
    val label        : String,
    val selectedIcon : ImageVector,
    val unselectedIcon: ImageVector,
    val badgeCount   : Int = 0
)

val bottomNavItems = listOf(
    BottomNavItem(
        screen        = Screen.Home,
        label         = "Home",
        selectedIcon  = Icons.Filled.Home,
        unselectedIcon= Icons.Outlined.Home
    ),
    BottomNavItem(
        screen        = Screen.Video,
        label         = "Video",
        selectedIcon  = Icons.Filled.VideoLibrary,
        unselectedIcon= Icons.Outlined.VideoLibrary
    ),
    BottomNavItem(
        screen        = Screen.Explore,
        label         = "Explore",
        selectedIcon  = Icons.Filled.Explore,
        unselectedIcon= Icons.Outlined.Explore
    ),
    BottomNavItem(
        screen        = Screen.Favorites,
        label         = "Favorites",
        selectedIcon  = Icons.Filled.Favorite,
        unselectedIcon= Icons.Outlined.FavoriteBorder
    ),
    BottomNavItem(
        screen        = Screen.Settings,
        label         = "Settings",
        selectedIcon  = Icons.Filled.Settings,
        unselectedIcon= Icons.Outlined.Settings
    )
)
