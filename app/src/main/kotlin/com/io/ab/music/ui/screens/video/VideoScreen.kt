package com.io.ab.music.ui.screens.video

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import com.io.ab.music.ui.components.guardPagerScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import android.content.Intent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.IntentSenderRequest
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.io.ab.music.domain.model.Video
import com.io.ab.music.ui.navigation.LocalMiniPlayerScrollState
import com.io.ab.music.ui.viewmodel.VideoSortOrder
import com.io.ab.music.ui.viewmodel.VideoViewType
import com.io.ab.music.ui.viewmodel.VideoViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoScreen(
    onVideoClick  : (Video) -> Unit,
    viewModel     : VideoViewModel = hiltViewModel()
) {
    val uiState      by viewModel.uiState.collectAsState()
    val scrollState  = LocalMiniPlayerScrollState.current
    val gridState    = rememberLazyGridState()
    val listState    = rememberLazyListState()
    val context      = LocalContext.current

    // FIX: "Delete not working" — same scoped-storage confirmation flow as songs;
    // Android 10+ requires the system dialog before a video not created by this app
    // can actually be deleted.
    val deleteVideoRequest by viewModel.deleteVideoRequest.collectAsState()
    val deleteVideoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result -> viewModel.onDeleteVideoResult(result.resultCode == android.app.Activity.RESULT_OK) }
    LaunchedEffect(deleteVideoRequest) {
        deleteVideoRequest?.let { sender ->
            deleteVideoLauncher.launch(IntentSenderRequest.Builder(sender).build())
        }
    }

    // FIX: was a flat 90dp bottom padding that didn't account for the real
    // navigation bar height, so on 3-button-nav devices the last video row
    // still sat under the MiniPlayer. Match the same inset used elsewhere.
    val miniPlayerBottomInset = androidx.compose.foundation.layout.WindowInsets.navigationBars
        .asPaddingValues().calculateBottomPadding() + 112.dp
    val currentVideo by viewModel.currentVideo.collectAsState()
    val lastPositions by viewModel.lastPositions.collectAsState()

    var showSearch   by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showFolders  by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.scanVideos()
    }

    // Scroll signal for mini player hide/show
    LaunchedEffect(gridState) {
        var lastOffset = 0
        var lastIndex  = 0
        snapshotFlow { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset }
            .collect { (idx, offset) ->
                val delta = when {
                    idx > lastIndex -> -100f
                    idx < lastIndex ->  100f
                    else -> (lastOffset - offset).toFloat()
                }
                scrollState.onScrollDelta(delta)
                lastIndex  = idx
                lastOffset = offset
            }
    }
    LaunchedEffect(listState) {
        var lastOffset = 0
        var lastIndex  = 0
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (idx, offset) ->
                val delta = when {
                    idx > lastIndex -> -100f
                    idx < lastIndex ->  100f
                    else -> (lastOffset - offset).toFloat()
                }
                scrollState.onScrollDelta(delta)
                lastIndex  = idx
                lastOffset = offset
            }
    }


    // ── Content only — header is embedded as first item in the list/grid ──
    androidx.compose.foundation.layout.Box(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
    when {
        uiState.isLoading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        uiState.filteredVideos.isEmpty() -> {
            // Still need header even in empty state
            Column(modifier = Modifier.fillMaxSize()) {
                VideoHeader(
                    uiState      = uiState,
                    showSearch   = showSearch,
                    showSortMenu = showSortMenu,
                    showFolders  = showFolders,
                    viewModel    = viewModel,
                    onShowSearch = { showSearch = it },
                    onShowSort   = { showSortMenu = it },
                    onShowFolders= { showFolders = it }
                )
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.VideoLibrary, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f))
                        Spacer(Modifier.height(12.dp))
                        Text("No videos found", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (uiState.searchQuery.isNotBlank()) {
                            TextButton(onClick = { viewModel.onSearchQuery("") }) { Text("Clear search") }
                        }
                    }
                }
            }
        }
        else -> {
            val count = uiState.filteredVideos.size
            if (uiState.viewType == VideoViewType.GRID) {
                LazyVerticalGrid(
                    columns        = GridCells.Adaptive(minSize = 160.dp),
                    state          = gridState,
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = miniPlayerBottomInset),
                    verticalArrangement   = Arrangement.spacedBy(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier       = Modifier.fillMaxSize()
                ) {
                    // ── Sticky header as first item ───────────────────────────
                    item(span = { GridItemSpan(maxLineSpan) }, key = "video_header") {
                        VideoHeader(
                            uiState      = uiState,
                            showSearch   = showSearch,
                            showSortMenu = showSortMenu,
                            showFolders  = showFolders,
                            viewModel    = viewModel,
                            onShowSearch = { showSearch = it },
                            onShowSort   = { showSortMenu = it },
                            onShowFolders= { showFolders = it }
                        )
                    }
                    // Recently played
                    if (uiState.recentlyPlayed.isNotEmpty() && uiState.searchQuery.isBlank()) {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "recent_header") {
                            RecentlyPlayedSection(
                                videos       = uiState.recentlyPlayed,
                                onVideoClick = { video ->
                                    viewModel.playVideo(video)
                                    onVideoClick(video)
                                }
                            )
                        }
                    }
                    // Stats bar
                    item(span = { GridItemSpan(maxLineSpan) }, key = "stats") {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "$count video${if (count != 1) "s" else ""}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    items(uiState.filteredVideos, key = { it.id }) { video ->
                        VideoGridCard(
                            video      = video,
                            isPlaying  = currentVideo?.id == video.id,
                            lastPos    = lastPositions[video.id] ?: 0L,
                            onClick    = {
                                viewModel.playVideo(video, uiState.filteredVideos)
                                onVideoClick(video)
                            },
                            onPlayNext  = { v ->
                                val cur = uiState.filteredVideos.toMutableList()
                                val curVidIdx = cur.indexOfFirst { it.id == v.id }
                                if (curVidIdx < 0) cur.add(1.coerceAtMost(cur.size), v)
                                viewModel.playVideo(v, cur)
                                onVideoClick(v)
                            },
                            onDelete   = { viewModel.deleteVideo(it) },
                            onShare    = { shareVideoFile(context, it) },
                            onRename   = { v, name -> viewModel.renameVideo(context, v, name) }
                        )
                    }
                }
            } else {
                LazyColumn(
                    state          = listState,
                    contentPadding = PaddingValues(bottom = miniPlayerBottomInset),
                    modifier       = Modifier.fillMaxSize()
                ) {
                    // ── Sticky header as first item ───────────────────────────
                    item(key = "video_header") {
                        VideoHeader(
                            uiState      = uiState,
                            showSearch   = showSearch,
                            showSortMenu = showSortMenu,
                            showFolders  = showFolders,
                            viewModel    = viewModel,
                            onShowSearch = { showSearch = it },
                            onShowSort   = { showSortMenu = it },
                            onShowFolders= { showFolders = it }
                        )
                    }
                    // Recently played
                    if (uiState.recentlyPlayed.isNotEmpty() && uiState.searchQuery.isBlank()) {
                        item(key = "recent_header") {
                            RecentlyPlayedSection(
                                videos       = uiState.recentlyPlayed,
                                onVideoClick = { video ->
                                    viewModel.playVideo(video)
                                    onVideoClick(video)
                                }
                            )
                        }
                    }
                    item(key = "stats") {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "$count video${if (count != 1) "s" else ""}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    items(uiState.filteredVideos, key = { it.id }) { video ->
                        VideoListItem(
                            video      = video,
                            isPlaying  = currentVideo?.id == video.id,
                            lastPos    = lastPositions[video.id] ?: 0L,
                            onClick    = {
                                viewModel.playVideo(video, uiState.filteredVideos)
                                onVideoClick(video)
                            },
                            onPlayNext  = { v ->
                                val cur = uiState.filteredVideos.toMutableList()
                                if (!cur.contains(v)) cur.add(1.coerceAtMost(cur.size), v)
                                viewModel.playVideo(v, cur)
                                onVideoClick(v)
                            },
                            onDelete   = { viewModel.deleteVideo(it) },
                            onShare    = { shareVideoFile(context, it) },
                            onRename   = { v, name -> viewModel.renameVideo(context, v, name) }
                        )
                    }
                }
            }
        }
    } // end when
    } // end gradient Box
}

// ── Video page header (embedded in scroll content) ────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VideoHeader(
    uiState      : com.io.ab.music.ui.viewmodel.VideoUiState,
    showSearch   : Boolean,
    showSortMenu : Boolean,
    showFolders  : Boolean,
    viewModel    : VideoViewModel,
    onShowSearch : (Boolean) -> Unit,
    onShowSort   : (Boolean) -> Unit,
    onShowFolders: (Boolean) -> Unit
) {
    // No extra background surface here — VideoScreen already paints the
    // gradient behind the whole top area, so wrapping the header in another
    // background just doubled it up and added an extra draw pass.
    Box(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showSearch) {
                    OutlinedTextField(
                        value         = uiState.searchQuery,
                        onValueChange = viewModel::onSearchQuery,
                        modifier      = Modifier.weight(1f).padding(horizontal = 4.dp),
                        placeholder   = { Text("Search videos…") },
                        singleLine    = true,
                        leadingIcon   = { Icon(Icons.Filled.Search, null) },
                        trailingIcon  = {
                            IconButton(onClick = { onShowSearch(false); viewModel.onSearchQuery("") }) {
                                Icon(Icons.Filled.Close, null)
                            }
                        },
                        shape = RoundedCornerShape(50)
                    )
                } else {
                    Text(
                        text     = "Videos",
                        style    = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp)
                    )
                    IconButton(onClick = { onShowFolders(!showFolders) }) {
                        Icon(
                            if (uiState.selectedFolder != null) Icons.Filled.Folder else Icons.Outlined.Folder,
                            null,
                            tint = if (uiState.selectedFolder != null) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { onShowSearch(true) }) {
                        Icon(Icons.Filled.Search, null)
                    }
                    Box {
                        IconButton(onClick = { onShowSort(true) }) {
                            Icon(Icons.Filled.Sort, null)
                        }
                        DropdownMenu(
                            expanded         = showSortMenu,
                            onDismissRequest = { onShowSort(false) }
                        ) {
                            SortItem("Recently Added", VideoSortOrder.DATE_RECENT,  uiState.sortOrder, viewModel) { onShowSort(false) }
                            SortItem("Name A–Z",       VideoSortOrder.NAME_AZ,      uiState.sortOrder, viewModel) { onShowSort(false) }
                            SortItem("Name Z–A",       VideoSortOrder.NAME_ZA,      uiState.sortOrder, viewModel) { onShowSort(false) }
                            SortItem("Largest First",  VideoSortOrder.SIZE_LARGE,   uiState.sortOrder, viewModel) { onShowSort(false) }
                            SortItem("Longest First",  VideoSortOrder.DURATION_LONG,uiState.sortOrder, viewModel) { onShowSort(false) }
                        }
                    }
                    IconButton(onClick = viewModel::toggleViewType) {
                        Icon(
                            if (uiState.viewType == VideoViewType.GRID) Icons.Filled.ViewList else Icons.Filled.GridView,
                            null
                        )
                    }
                }
            }

            AnimatedVisibility(visible = showFolders && uiState.folders.isNotEmpty()) {
                val folderScrollConnection = remember {
                    object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
                        override fun onPostScroll(
                            consumed: androidx.compose.ui.geometry.Offset,
                            available: androidx.compose.ui.geometry.Offset,
                            source: androidx.compose.ui.input.nestedscroll.NestedScrollSource
                        ): androidx.compose.ui.geometry.Offset {
                            return if (kotlin.math.abs(available.x) > kotlin.math.abs(available.y))
                                available else androidx.compose.ui.geometry.Offset.Zero
                        }
                    }
                }
                LazyRow(
                    modifier             = Modifier
                        .fillMaxWidth()
                        .guardPagerScroll()
                        .nestedScroll(folderScrollConnection),
                    contentPadding       = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement= Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = uiState.selectedFolder == null,
                            onClick  = { viewModel.onSelectFolder(null) },
                            label    = { Text("All") },
                            leadingIcon = {
                                if (uiState.selectedFolder == null)
                                    Icon(Icons.Filled.Check, null, modifier = Modifier.size(16.dp))
                            }
                        )
                    }
                    items(uiState.folders) { folder ->
                        FilterChip(
                            selected = uiState.selectedFolder == folder,
                            onClick  = { viewModel.onSelectFolder(folder) },
                            label    = { Text(folder, maxLines = 1) },
                            leadingIcon = {
                                if (uiState.selectedFolder == folder)
                                    Icon(Icons.Filled.Check, null, modifier = Modifier.size(16.dp))
                            }
                        )
                    }
                }
            }

            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(0.4f))
        }
    }
}

// ── Recently Played Section ───────────────────────────────────────────────────
@Composable
private fun RecentlyPlayedSection(
    videos      : List<Video>,
    onVideoClick: (Video) -> Unit
) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp)) {
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.History,
                null,
                tint     = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "Continue Watching",
                style      = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }
        LazyRow(
            modifier              = Modifier.guardPagerScroll(),
            contentPadding        = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(videos.take(10), key = { "recent_${it.id}" }) { video ->
                RecentVideoCard(video = video, context = context, onClick = { onVideoClick(video) })
            }
        }
        HorizontalDivider(
            modifier  = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            thickness = 0.5.dp,
            color     = MaterialTheme.colorScheme.outlineVariant.copy(0.4f)
        )
    }
}

@Composable
private fun RecentVideoCard(
    video  : Video,
    context: android.content.Context,
    onClick: () -> Unit
) {
    Card(
        onClick   = onClick,
        modifier  = Modifier
            .width(150.dp)
            .aspectRatio(16f / 9f),
        shape     = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Box {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(video.contentUri)
                    .crossfade(true)
                    .build(),
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier     = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(0.75f)),
                            startY = 60f
                        )
                    )
            )
            // Play overlay
            Box(
                modifier          = Modifier
                    .align(Alignment.Center)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(0.45f)),
                contentAlignment  = Alignment.Center
            ) {
                Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            // Duration badge
            Surface(
                modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp),
                shape    = RoundedCornerShape(4.dp),
                color    = Color.Black.copy(0.7f)
            ) {
                Text(
                    text     = video.formattedDuration,
                    color    = Color.White,
                    fontSize = 9.sp,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
            // Title
            Text(
                text     = video.title,
                color    = Color.White,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 5.dp, bottom = 5.dp, end = 36.dp)
            )
        }
    }
}

// ── Video Grid ────────────────────────────────────────────────────────────────
@Composable
private fun VideoGridCard(
    video      : Video,
    isPlaying  : Boolean = false,
    lastPos    : Long    = 0L,
    onClick    : () -> Unit,
    onPlayNext : ((Video) -> Unit)? = null,
    onDelete   : ((Video) -> Unit)? = null,
    onShare    : ((Video) -> Unit)? = null,
    onRename   : ((Video, String) -> Unit)? = null
) {
    val context       = LocalContext.current
    var showMenu      by remember { mutableStateOf(false) }
    var showInfo      by remember { mutableStateOf(false) }
    var showRename    by remember { mutableStateOf(false) }
    var showDeleteDlg by remember { mutableStateOf(false) }

    if (showInfo)   VideoInfoDialog(video = video, onDismiss = { showInfo = false })
    if (showRename) {
        com.io.ab.music.ui.components.RenameDialog(
            currentName = video.title,
            onDismiss   = { showRename = false },
            onConfirm   = { name -> onRename?.invoke(video, name); showRename = false }
        )
    }
    if (showDeleteDlg) {
        VideoDeleteConfirmDialog(
            title     = video.title,
            onDismiss = { showDeleteDlg = false },
            onConfirm = { onDelete?.invoke(video); showDeleteDlg = false }
        )
    }

    Card(
        onClick  = onClick,
        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
        shape    = RoundedCornerShape(10.dp),
        elevation= CardDefaults.cardElevation(2.dp),
        // FIX: Highlight currently playing video
        border   = if (isPlaying) androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFBB86FC)) else null
    ) {
        Box {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(video.contentUri)
                    .crossfade(true)
                    .build(),
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier     = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(0.7f)),
                            startY = 80f
                        )
                    )
            )
            // FIX: Show pause overlay when this video is "last played" (paused/stopped)
            if (isPlaying) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.35f)),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Pause, null, tint = Color.White, modifier = Modifier.size(36.dp))
                }
            } else {
                Icon(
                    Icons.Filled.PlayCircle,
                    null,
                    tint     = Color.White.copy(0.85f),
                    modifier = Modifier.size(36.dp).align(Alignment.Center)
                )
            }
            Surface(
                modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
                shape = RoundedCornerShape(4.dp),
                color = Color.Black.copy(0.7f)
            ) {
                Text(
                    text     = video.formattedDuration,
                    color    = Color.White,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                )
            }
            Text(
                text     = video.title,
                color    = Color.White,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 6.dp, bottom = 6.dp, end = 40.dp)
            )
            // FIX: Last-played progress bar at bottom
            if (lastPos > 0L && video.duration > 0L) {
                val progress = (lastPos.toFloat() / video.duration.toFloat()).coerceIn(0f, 1f)
                Box(modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color.White.copy(0.25f))) {
                    Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(progress).background(Color(0xFFBB86FC)))
                }
            }
            // ── 3-dot menu button ──────────────────────────────────────────
            Box(modifier = Modifier.align(Alignment.TopEnd)) {
                IconButton(
                    onClick  = { showMenu = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Filled.MoreVert, null,
                        tint     = Color.White,
                        modifier = Modifier.size(18.dp))
                }
                VideoItemDropdownMenu(
                    expanded      = showMenu,
                    onDismiss     = { showMenu = false },
                    onPlayNext    = { onPlayNext?.invoke(video); showMenu = false },
                    onInfo        = { showInfo = true; showMenu = false },
                    onShare       = { onShare?.invoke(video); showMenu = false },
                    onRename      = { showRename = true; showMenu = false },
                    onDelete      = { showDeleteDlg = true; showMenu = false }
                )
            }
        }
    }
}

// ── Video List ────────────────────────────────────────────────────────────────
@Composable
private fun VideoListItem(
    video      : Video,
    isPlaying  : Boolean = false,
    lastPos    : Long    = 0L,
    onClick    : () -> Unit,
    onPlayNext : ((Video) -> Unit)? = null,
    onDelete   : ((Video) -> Unit)? = null,
    onShare    : ((Video) -> Unit)? = null,
    onRename   : ((Video, String) -> Unit)? = null
) {
    val context       = LocalContext.current
    var showMenu      by remember { mutableStateOf(false) }
    var showInfo      by remember { mutableStateOf(false) }
    var showRename    by remember { mutableStateOf(false) }
    var showDeleteDlg by remember { mutableStateOf(false) }

    if (showInfo)   VideoInfoDialog(video = video, onDismiss = { showInfo = false })
    if (showRename) {
        com.io.ab.music.ui.components.RenameDialog(
            currentName = video.title,
            onDismiss   = { showRename = false },
            onConfirm   = { name -> onRename?.invoke(video, name); showRename = false }
        )
    }
    if (showDeleteDlg) {
        VideoDeleteConfirmDialog(
            title     = video.title,
            onDismiss = { showDeleteDlg = false },
            onConfirm = { onDelete?.invoke(video); showDeleteDlg = false }
        )
    }

    Surface(
        onClick  = onClick,
        modifier = Modifier.fillMaxWidth(),
        // FIX: Highlight currently playing video
        color    = if (isPlaying) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                   else Color.Transparent
    ) {
        Row(
            modifier          = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(width = 110.dp, height = 62.dp)
                    .clip(RoundedCornerShape(8.dp))
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(video.contentUri)
                        .crossfade(true)
                        .build(),
                    contentDescription = video.title,
                    contentScale = ContentScale.Crop,
                    modifier     = Modifier.fillMaxSize()
                )
                // FIX: Show Pause or Play icon depending on isPlaying
                Icon(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayCircle,
                    null,
                    tint     = Color.White.copy(if (isPlaying) 1f else 0.8f),
                    modifier = Modifier.size(24.dp).align(Alignment.Center)
                )
                Surface(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp),
                    shape = RoundedCornerShape(3.dp),
                    color = Color.Black.copy(0.72f)
                ) {
                    Text(
                        text     = video.formattedDuration,
                        color    = Color.White,
                        fontSize = 9.sp,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
                // FIX: Last-played progress bar
                if (lastPos > 0L && video.duration > 0L) {
                    val progress = (lastPos.toFloat() / video.duration.toFloat()).coerceIn(0f, 1f)
                    Box(modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color.White.copy(0.25f))) {
                        Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(progress).background(Color(0xFFBB86FC)))
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text     = video.title,
                    style    = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text  = "${video.resolution} • ${video.formattedSize}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text  = video.folderName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            // ── 3-dot menu ─────────────────────────────────────────────────
            Box {
                IconButton(
                    onClick  = { showMenu = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Filled.MoreVert, "More",
                        tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp))
                }
                VideoItemDropdownMenu(
                    expanded      = showMenu,
                    onDismiss     = { showMenu = false },
                    onPlayNext    = { onPlayNext?.invoke(video); showMenu = false },
                    onInfo        = { showInfo = true; showMenu = false },
                    onShare       = { onShare?.invoke(video); showMenu = false },
                    onRename      = { showRename = true; showMenu = false },
                    onDelete      = { showDeleteDlg = true; showMenu = false }
                )
            }
        }
    }
}

// ── Shared video item dropdown ────────────────────────────────────────────────
@Composable
private fun VideoItemDropdownMenu(
    expanded  : Boolean,
    onDismiss : () -> Unit,
    onPlayNext: () -> Unit,
    onInfo    : () -> Unit,
    onShare   : () -> Unit,
    onRename  : () -> Unit,
    onDelete  : () -> Unit
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text        = { Text("Play Next") },
            onClick     = onPlayNext,
            leadingIcon = { Icon(Icons.Rounded.QueuePlayNext, null) }
        )
        HorizontalDivider()
        DropdownMenuItem(
            text        = { Text("Video Info") },
            onClick     = onInfo,
            leadingIcon = { Icon(Icons.Rounded.Info, null) }
        )
        DropdownMenuItem(
            text        = { Text("Share") },
            onClick     = onShare,
            leadingIcon = { Icon(Icons.Rounded.Share, null) }
        )
        DropdownMenuItem(
            text        = { Text("Rename") },
            onClick     = onRename,
            leadingIcon = { Icon(Icons.Rounded.Edit, null) }
        )
        HorizontalDivider()
        DropdownMenuItem(
            text        = { Text("Delete", color = MaterialTheme.colorScheme.error) },
            onClick     = onDelete,
            leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) }
        )
    }
}

// ── Video Info Dialog ─────────────────────────────────────────────────────────
@Composable
private fun VideoInfoDialog(video: Video, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon  = { Icon(Icons.Rounded.Info, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("Video Info") },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                VideoInfoRow("Title",      video.title)
                VideoInfoRow("Resolution", video.resolution)
                VideoInfoRow("Duration",   video.formattedDuration)
                VideoInfoRow("Size",       video.formattedSize)
                VideoInfoRow("Format",     video.mimeType)
                VideoInfoRow("Folder",     video.folderName)
                VideoInfoRow("Path",       video.path)
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, shape = RoundedCornerShape(12.dp)) { Text("Close") }
        }
    )
}

@Composable
private fun VideoInfoRow(label: String, value: String) {
    Column {
        Text(label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary)
        Text(value,
            style    = MaterialTheme.typography.bodySmall,
            color    = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis)
    }
}

// ── Delete Confirm Dialog ─────────────────────────────────────────────────────
@Composable
private fun VideoDeleteConfirmDialog(
    title    : String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon  = { Icon(Icons.Rounded.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text("Delete Video") },
        text  = { Text("\"$title\" will be permanently deleted from your device.") },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shape   = RoundedCornerShape(12.dp),
                colors  = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) { Text("Delete") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(12.dp)) { Text("Cancel") }
        }
    )
}

// ── Share helper ──────────────────────────────────────────────────────────────
private fun shareVideoFile(context: android.content.Context, video: Video) {
    try {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type  = video.mimeType.ifBlank { "video/*" }
            putExtra(Intent.EXTRA_STREAM, video.contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share ${video.title}"))
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "Cannot share: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
    } // end when
}

// ── Sort dropdown item ────────────────────────────────────────────────────────
@Composable
private fun SortItem(
    label    : String,
    order    : VideoSortOrder,
    current  : VideoSortOrder,
    viewModel: VideoViewModel,
    onDismiss: () -> Unit
) {
    DropdownMenuItem(
        text    = { Text(label) },
        onClick = { viewModel.onSortOrder(order); onDismiss() },
        leadingIcon = {
            if (current == order)
                Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
        }
    )
}
