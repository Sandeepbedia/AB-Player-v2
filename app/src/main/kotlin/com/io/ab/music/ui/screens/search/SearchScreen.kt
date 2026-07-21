package com.io.ab.music.ui.screens.search

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.io.ab.music.domain.model.Song
import com.io.ab.music.ui.viewmodel.LibraryViewModel
import com.io.ab.music.ui.viewmodel.PlayerViewModel
import androidx.hilt.navigation.compose.hiltViewModel
import com.io.ab.music.ui.components.SwipeDismissWrapper
import com.io.ab.music.ui.components.rememberArtworkModel

private val searchCategories = listOf("All", "Songs", "Albums", "Artists")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SearchScreen(
    onBack          : () -> Unit,
    onSongClick     : (Song, List<Song>) -> Unit,
    libraryViewModel: LibraryViewModel = hiltViewModel(),
    playerViewModel : PlayerViewModel  = hiltViewModel()
) {
    val uiState       by libraryViewModel.uiState.collectAsState()
    val currentSongId by playerViewModel.currentSongId.collectAsState()
    val isPlaying     by playerViewModel.isPlaying.collectAsState()

    var query            by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    val focusRequester   = remember { FocusRequester() }
    val focusManager     = LocalFocusManager.current

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val allResults = if (query.isBlank()) emptyList() else uiState.searchResults
    val results = when (selectedCategory) {
        "Songs"   -> allResults.filter { it.title.contains(query, ignoreCase = true) }
        "Albums"  -> allResults.filter { it.album.contains(query, ignoreCase = true) }
        "Artists" -> allResults.filter { it.artist.contains(query, ignoreCase = true) }
        else      -> allResults
    }

    SwipeDismissWrapper(onDismiss = onBack) {

    val primary    = MaterialTheme.colorScheme.primary
    val background = MaterialTheme.colorScheme.background
    val halfScreenBrush = androidx.compose.runtime.remember(primary, background) {
        Brush.verticalGradient(
            0.0f  to primary.copy(alpha = 0.38f),
            0.30f to primary.copy(alpha = 0.18f),
            0.55f to primary.copy(alpha = 0.07f),
            1.0f  to background.copy(alpha = 0f)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.50f)
                .background(halfScreenBrush)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {

            // ── AMOLED Search Header ──────────────────────────────────────────
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                0f   to MaterialTheme.colorScheme.primary.copy(alpha = 0.20f),
                                1f   to Color.Transparent
                            )
                        )
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {

                        // Top row: back + title
                        Row(
                            verticalAlignment      = Alignment.CenterVertically,
                            horizontalArrangement  = Arrangement.spacedBy(12.dp)
                        ) {
                            // Back button — glowing circle style
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                        CircleShape
                                    )
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                        CircleShape
                                    )
                                    .clickable(onClick = onBack),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Rounded.ArrowBack,
                                    contentDescription = "Back",
                                    tint     = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Column {
                                Text(
                                    "Search",
                                    style      = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    "${uiState.songs.size} songs in library",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                )
                            }
                        }

                        // AMOLED search field — dark with neon border on focus
                        SearchField(
                            query          = query,
                            focusRequester = focusRequester,
                            onQueryChange  = {
                                query = it
                                libraryViewModel.setSearchQuery(it)
                            },
                            onClear        = {
                                query = ""
                                libraryViewModel.setSearchQuery("")
                            },
                            onSearch       = { focusManager.clearFocus() }
                        )

                        // Category chips — pill style with neon selected state
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding        = PaddingValues(horizontal = 2.dp)
                        ) {
                            items(searchCategories) { cat ->
                                val selected = selectedCategory == cat
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(
                                            if (selected)
                                                MaterialTheme.colorScheme.primary
                                            else
                                                MaterialTheme.colorScheme.surfaceVariant
                                        )
                                        .border(
                                            width = if (selected) 0.dp else 1.dp,
                                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                            shape = RoundedCornerShape(50)
                                        )
                                        .clickable { selectedCategory = cat }
                                        .padding(horizontal = 18.dp, vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text  = cat,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                        ),
                                        color = if (selected)
                                                    MaterialTheme.colorScheme.onPrimary
                                                else
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── Body ──────────────────────────────────────────────────────────
            if (query.isBlank()) {
                // Empty state — AMOLED search landing
                item {
                    SearchEmptyLanding(totalSongs = uiState.songs.size)
                }
            } else if (results.isEmpty()) {
                item {
                    SearchNoResults(query = query)
                }
            } else {
                // Result count badge
                item {
                    Row(
                        modifier          = Modifier
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                    RoundedCornerShape(50)
                                )
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(
                                "${results.size} result${if (results.size != 1) "s" else ""}",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            "for \"$query\"",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                items(results, key = { it.id }, contentType = { "song_row" }) { song ->
                    val isCurrent = song.id == currentSongId
                    AmoledSearchResultRow(
                        song              = song,
                        isCurrentSong     = isCurrent,
                        isActuallyPlaying = isCurrent && isPlaying,
                        onClick           = { onSongClick(song, results) },
                        modifier          = Modifier.animateItemPlacement()
                    )
                }
            }
        }
    }
    }
}

// ── AMOLED search text field ───────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchField(
    query          : String,
    focusRequester : FocusRequester,
    onQueryChange  : (String) -> Unit,
    onClear        : () -> Unit,
    onSearch       : () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary

    BasicTextField(
        value         = query,
        onValueChange = onQueryChange,
        modifier      = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .focusRequester(focusRequester),
        singleLine    = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        textStyle     = MaterialTheme.typography.bodyLarge.copy(
            color = MaterialTheme.colorScheme.onSurface
        ),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                        RoundedCornerShape(16.dp)
                    )
                    .border(
                        width = if (query.isNotEmpty()) 1.5.dp else 1.dp,
                        brush = if (query.isNotEmpty())
                                    Brush.horizontalGradient(
                                        listOf(
                                            primary,
                                            MaterialTheme.colorScheme.tertiary
                                        )
                                    )
                                else
                                    Brush.horizontalGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                            MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                        )
                                    ),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier          = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Rounded.Search,
                        contentDescription = null,
                        tint     = if (query.isNotEmpty()) primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text(
                                "Search songs, artists, albums…",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                        innerTextField()
                    }
                    AnimatedVisibility(
                        visible = query.isNotEmpty(),
                        enter   = fadeIn() + scaleIn(),
                        exit    = fadeOut() + scaleOut()
                    ) {
                        IconButton(onClick = onClear, modifier = Modifier.size(36.dp)) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = "Clear",
                                tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    )
}

// ── AMOLED landing state ───────────────────────────────────────────────────────
@Composable
private fun SearchEmptyLanding(totalSongs: Int) {
    Column(
        modifier            = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Glowing orb
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.08f),
                                Color.Transparent
                            )
                        ),
                        CircleShape
                    )
            )
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        CircleShape
                    )
                    .border(
                        1.5.dp,
                        Brush.sweepGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.tertiary,
                                MaterialTheme.colorScheme.primary
                            )
                        ),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Search,
                    contentDescription = null,
                    tint     = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Find Your Music",
                style      = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Search across $totalSongs songs",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "by title, artist, or album",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Quick tips row
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            listOf("🎵 Songs", "🎤 Artists", "💿 Albums").forEach { tip ->
                Box(
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(12.dp)
                        )
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        tip,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ── No results state ───────────────────────────────────────────────────────────
@Composable
private fun SearchNoResults(query: String) {
    Column(
        modifier            = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                                Color.Transparent
                            )
                        ),
                        CircleShape
                    )
            )
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.SearchOff,
                    contentDescription = null,
                    tint     = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        Text(
            "No results found",
            style      = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color      = MaterialTheme.colorScheme.onBackground
        )
        Text(
            "\"$query\" doesn't match any song",
            style    = MaterialTheme.typography.bodySmall,
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ── AMOLED Search Result Row ───────────────────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AmoledSearchResultRow(
    song             : Song,
    isCurrentSong    : Boolean,
    isActuallyPlaying: Boolean,
    onClick          : () -> Unit,
    modifier         : Modifier = Modifier
) {
    val artworkModel = rememberArtworkModel(song.artworkUri, 128)

    val bgColor = if (isCurrentSong)
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)
    else
        Color.Transparent

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(bgColor)
            .clickable(onClick = onClick)
    ) {
        // Left accent bar for currently playing
        if (isCurrentSong) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(3.dp)
                    .height(52.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.tertiary
                            )
                        ),
                        RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp)
                    )
            )
        }

        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(
                    start  = if (isCurrentSong) 20.dp else 16.dp,
                    end    = 16.dp,
                    top    = 10.dp,
                    bottom = 10.dp
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Artwork — circle for playing, rounded square otherwise
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(if (isCurrentSong) CircleShape else RoundedCornerShape(12.dp))
                    .then(
                        if (isCurrentSong) Modifier.border(
                            1.5.dp,
                            Brush.sweepGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.tertiary,
                                    MaterialTheme.colorScheme.primary
                                )
                            ),
                            CircleShape
                        ) else Modifier
                    )
            ) {
                if (song.artworkUri != null) {
                    AsyncImage(
                        model              = artworkModel,
                        contentDescription = null,
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.secondary
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.MusicNote, null,
                            tint     = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // Playing overlay
                if (isCurrentSong) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (isActuallyPlaying) Icons.Rounded.GraphicEq
                            else Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint     = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    song.title,
                    style    = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (isCurrentSong) FontWeight.Bold else FontWeight.Medium
                    ),
                    color    = if (isCurrentSong) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    modifier = if (isCurrentSong)
                                   Modifier.basicMarquee(Int.MAX_VALUE, initialDelayMillis = 800)
                               else Modifier,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    song.artist,
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    song.album,
                    style    = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color    = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Right side: playing indicator OR duration
            if (isCurrentSong) {
                Box(
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        if (isActuallyPlaying) Icons.Rounded.VolumeUp else Icons.Rounded.PauseCircle,
                        contentDescription = null,
                        tint     = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            } else {
                Text(
                    formatDurationMs(song.duration),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }

        // Bottom divider
        HorizontalDivider(
            modifier  = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = if (isCurrentSong) 82.dp else 82.dp, end = 16.dp),
            color     = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f),
            thickness = 0.5.dp
        )
    }
}

// Alias — must match the one in SongItem.kt
private fun formatDurationMs(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

// Needed for BasicTextField — must import separately
@Composable
private fun BasicTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier,
    singleLine: Boolean,
    keyboardOptions: KeyboardOptions,
    keyboardActions: KeyboardActions,
    textStyle: androidx.compose.ui.text.TextStyle,
    decorationBox: @Composable (innerTextField: @Composable () -> Unit) -> Unit
) {
    androidx.compose.foundation.text.BasicTextField(
        value         = value,
        onValueChange = onValueChange,
        modifier      = modifier,
        singleLine    = singleLine,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        textStyle     = textStyle,
        decorationBox = decorationBox
    )
}
