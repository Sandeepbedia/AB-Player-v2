package com.io.ab.music.ui.screens.explore

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import com.io.ab.music.ui.components.guardPagerScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import com.io.ab.music.domain.model.Song
import com.io.ab.music.domain.model.Album
import com.io.ab.music.domain.model.Artist
import com.io.ab.music.domain.model.Playlist
import com.io.ab.music.ui.viewmodel.LibraryViewModel
import com.io.ab.music.ui.viewmodel.PlayerViewModel
import androidx.hilt.navigation.compose.hiltViewModel
import com.io.ab.music.ui.components.rememberArtworkModel
import com.io.ab.music.ui.components.AddSongsSheet
import com.io.ab.music.ui.components.MiniPlayerBottomSpacer
import com.io.ab.music.ui.navigation.LocalMiniPlayerScrollState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ExploreScreen(
    onSongClick     : (Song, List<Song>) -> Unit,
    onSearchClick   : () -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onArtistClick   : (Artist) -> Unit = {},
    libraryViewModel: LibraryViewModel = hiltViewModel(),
    playerViewModel : PlayerViewModel  = hiltViewModel()
) {
    val uiState by libraryViewModel.uiState.collectAsState()
    var selectedSection by remember { mutableStateOf("Songs") }
    val currentSongId by playerViewModel.currentSongId.collectAsState()
    val isPlaying     by playerViewModel.isPlaying.collectAsState()

    // "playlist card ke baad songs add karne ka option" — quick add-songs shortcut
    // right from the Explore screen's playlist row, without navigating into the
    // playlist detail screen first.
    var addSongsPlaylist by remember { mutableStateOf<Playlist?>(null) }
    val playlistSongsForSheet by libraryViewModel.playlistSongs.collectAsState()

    // FIX: MiniPlayer never hid while scrolling this tab because nothing here
    // fed the shared scroll state — wire it up the same way HomeScreen does.
    val listState        = rememberLazyListState()
    val miniPlayerScroll = LocalMiniPlayerScrollState.current
    var lastScrollIdx    by remember { mutableIntStateOf(0) }
    var lastScrollOffset by remember { mutableIntStateOf(0) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (idx, offset) ->
                val delta = when {
                    idx > lastScrollIdx -> -100f
                    idx < lastScrollIdx ->  100f
                    else -> (lastScrollOffset - offset).toFloat()
                }
                miniPlayerScroll.onScrollDelta(delta)
                lastScrollIdx    = idx
                lastScrollOffset = offset
            }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    LazyColumn(modifier = Modifier.fillMaxSize(), state = listState) {

        // ── Top Bar ──────────────────────────────────────────────────────────
        item(key = "topbar") {
            Box(modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "Explore",
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "Discover your library",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        FilledIconButton(
                            onClick  = onSearchClick,
                            modifier = Modifier.size(40.dp),
                            shape    = CircleShape,
                            colors   = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(Icons.Rounded.Search, "Search",
                                tint     = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        } // end topbar item

        item(key = "stats") {
            LazyRow(
                modifier = Modifier.fillMaxWidth().guardPagerScroll(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "stat_songs") {
                    StatCard(
                        modifier = Modifier.width(110.dp),
                        label    = "Songs",
                        value    = "${uiState.songs.size}",
                        icon     = Icons.Rounded.MusicNote,
                        color    = MaterialTheme.colorScheme.primary,
                        selected = selectedSection == "Songs",
                        onClick  = { selectedSection = "Songs" }
                    )
                }
                item(key = "stat_playlists") {
                    StatCard(
                        modifier = Modifier.width(110.dp),
                        label    = "Playlists",
                        value    = "",
                        icon     = Icons.Rounded.QueueMusic,
                        color    = MaterialTheme.colorScheme.secondary,
                        selected = selectedSection == "Playlists",
                        onClick  = { selectedSection = "Playlists" }
                    )
                }
                item(key = "stat_most_played") {
                    StatCard(
                        modifier = Modifier.width(110.dp),
                        label    = "Most Played",
                        value    = "${uiState.mostPlayed.size}",
                        icon     = Icons.Rounded.TrendingUp,
                        color    = MaterialTheme.colorScheme.secondary,
                        selected = selectedSection == "Most Played",
                        onClick  = { selectedSection = "Most Played" }
                    )
                }
                item(key = "stat_favorites") {
                    StatCard(
                        modifier = Modifier.width(110.dp),
                        label    = "Favorites",
                        value    = "${uiState.favorites.size}",
                        icon     = Icons.Rounded.Favorite,
                        color    = Color(0xFFFF4081),
                        selected = selectedSection == "Favorites",
                        onClick  = { selectedSection = "Favorites" }
                    )
                }
                item(key = "stat_albums") {
                    StatCard(
                        modifier = Modifier.width(110.dp),
                        label    = "Albums",
                        value    = "${uiState.albums.size}",
                        icon     = Icons.Rounded.Album,
                        color    = MaterialTheme.colorScheme.tertiary,
                        selected = selectedSection == "Albums",
                        onClick  = { selectedSection = "Albums" }
                    )
                }
                item(key = "stat_artists") {
                    StatCard(
                        modifier = Modifier.width(110.dp),
                        label    = "Artists",
                        value    = "",
                        icon     = Icons.Rounded.Person,
                        color    = MaterialTheme.colorScheme.primary,
                        selected = selectedSection == "Artists",
                        onClick  = { selectedSection = "Artists" }
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        item {
            Text(
                text = selectedSection,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
        }

        when (selectedSection) {
            "Songs" -> {
                if (uiState.songs.isEmpty()) {
                    item { EmptyExploreView("No songs found") }
                } else {
                    itemsIndexed(uiState.songs, key = { _, s -> "song_${s.id}" }) { index, song ->
                        ExploreSongRow(
                            index = index + 1,
                            song = song,
                            isPlaying = song.id == currentSongId,
                            isActuallyPlaying = song.id == currentSongId && isPlaying,
                            onClick = { onSongClick(song, uiState.songs) }
                        )
                    }
                }
            }
            "Most Played" -> {
                if (uiState.mostPlayed.isEmpty()) {
                    item { EmptyExploreView("No played songs yet") }
                } else {
                    itemsIndexed(uiState.mostPlayed, key = { _, s -> "most_${s.id}" }) { index, song ->
                        ExploreSongRow(
                            index = index + 1,
                            song = song,
                            isPlaying = song.id == currentSongId,
                            isActuallyPlaying = song.id == currentSongId && isPlaying,
                            onClick = { onSongClick(song, uiState.mostPlayed) }
                        )
                    }
                }
            }
            "Favorites" -> {
                if (uiState.favorites.isEmpty()) {
                    item { EmptyExploreView("No favorite songs yet") }
                } else {
                    itemsIndexed(uiState.favorites, key = { _, s -> "fav_${s.id}" }) { index, song ->
                        ExploreSongRow(
                            index = index + 1,
                            song = song,
                            isPlaying = song.id == currentSongId,
                            isActuallyPlaying = song.id == currentSongId && isPlaying,
                            onClick = { onSongClick(song, uiState.favorites) }
                        )
                    }
                }
            }
            "Albums" -> {
                if (uiState.albums.isEmpty()) {
                    item { EmptyExploreView("No albums found") }
                } else {
                    itemsIndexed(uiState.albums, key = { _, a -> "album_${a.id}" }) { index, album ->
                        ExploreAlbumRow(
                            index = index + 1,
                            album = album,
                            onClick = {
                                val albumSongs = uiState.songs.filter { it.albumId == album.id || it.album == album.name }
                                if (albumSongs.isNotEmpty()) {
                                    onSongClick(albumSongs.first(), albumSongs)
                                }
                            }
                        )
                    }
                }
            }
            "Artists" -> {
                if (uiState.artists.isEmpty()) {
                    item { EmptyExploreView("No artists found") }
                } else {
                    itemsIndexed(uiState.artists, key = { _, a -> "artist_${a.id}" }) { index, artist ->
                        ExploreArtistRow(
                            index = index + 1,
                            artist = artist,
                            onClick = { onArtistClick(artist) }
                        )
                    }
                }
            }
            "Playlists" -> {
                if (uiState.playlists.isEmpty()) {
                    item { EmptyExploreView("No playlists found") }
                } else {
                    itemsIndexed(uiState.playlists, key = { _, p -> "playlist_${p.id}" }) { index, playlist ->
                        ExplorePlaylistRow(
                            index = index + 1,
                            playlist = playlist,
                            onClick = { onPlaylistClick(playlist) },
                            onAddSongsClick = {
                                libraryViewModel.openPlaylist(playlist)
                                addSongsPlaylist = playlist
                            }
                        )
                    }
                }
            }
        }

        item(key = "bottom_pad") { MiniPlayerBottomSpacer() }
    } // end LazyColumn

    addSongsPlaylist?.let { playlist ->
        AddSongsSheet(
            allSongs     = uiState.songs,
            alreadyInIds = playlistSongsForSheet.map { it.id }.toSet(),
            onAdd        = { song -> libraryViewModel.addSongToPlaylist(playlist.id, song.id) },
            onDismiss    = {
                libraryViewModel.closePlaylist()
                addSongsPlaylist = null
            }
        )
    }
    } // end gradient Box
}

@Composable
private fun StatCard(
    modifier: Modifier,
    label   : String,
    value   : String,
    icon    : androidx.compose.ui.graphics.vector.ImageVector,
    color   : Color,
    selected: Boolean,
    onClick : () -> Unit
) {
    Card(
        modifier = modifier.clickable { onClick() },
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(
            containerColor = if (selected) color.copy(alpha = 0.22f) else color.copy(alpha = 0.10f)
        ),
        border   = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) color else color.copy(alpha = 0.18f)
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            Text(value,
                style      = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color      = color)
            Text(label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExploreSongRow(
    index: Int,
    song: Song,
    isPlaying: Boolean = false,
    isActuallyPlaying: Boolean = false,
    onClick: () -> Unit
) {
    val artworkModel = rememberArtworkModel(song.artworkUri, 128)
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = if (isPlaying) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "$index",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(24.dp)
            )

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                AsyncImage(
                    model = artworkModel,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                if (isPlaying) {
                    Box(modifier = Modifier.fillMaxSize()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center) {
                        Icon(if (isActuallyPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (isPlaying) Modifier.basicMarquee(
                        iterations         = Int.MAX_VALUE,
                        initialDelayMillis = 800
                    ) else Modifier
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = song.formattedDuration,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (song.hasLyrics) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(Icons.Rounded.Lyrics, null, modifier = Modifier.size(10.dp), tint = MaterialTheme.colorScheme.primary)
                                Text("Lyrics", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExploreAlbumRow(
    index: Int,
    album: Album,
    onClick: () -> Unit
) {
    val artworkModel = rememberArtworkModel(album.artworkUri, 128)
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "$index",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(24.dp)
            )

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                AsyncImage(
                    model = artworkModel,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = album.name,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = album.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = "${album.songCount} songs",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ExploreArtistRow(
    index: Int,
    artist: Artist,
    onClick: () -> Unit
) {
    val artworkModel = rememberArtworkModel(artist.artworkUri, 128)
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "$index",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(24.dp)
            )

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                AsyncImage(
                    model = artworkModel,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = artist.name,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${artist.albumCount} albums",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = "${artist.songCount} songs",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}


@Composable
private fun ExplorePlaylistRow(
    index: Int,
    playlist: Playlist,
    onClick: () -> Unit,
    onAddSongsClick: () -> Unit = {}
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "$index",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(24.dp)
            )
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.QueueMusic,
                    null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${playlist.songCount} songs",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Quick "add songs" shortcut — no need to open the playlist first.
            IconButton(onClick = onAddSongsClick) {
                Icon(Icons.Rounded.PlaylistAdd, "Add songs", tint = MaterialTheme.colorScheme.primary)
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EmptyExploreView(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } // end LazyColumn
}
