package com.io.ab.music.ui.screens.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import com.io.ab.music.ui.components.guardPagerScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.IntentSenderRequest
import coil.compose.AsyncImage
import com.io.ab.music.domain.model.Song
import com.io.ab.music.ui.navigation.LocalMiniPlayerScrollState
import com.io.ab.music.ui.viewmodel.LibraryViewModel
import com.io.ab.music.ui.viewmodel.PlayerViewModel
import com.io.ab.music.ui.viewmodel.SettingsViewModel
import com.io.ab.music.ui.viewmodel.SortOrder
import androidx.hilt.navigation.compose.hiltViewModel
import com.io.ab.music.ui.components.rememberArtworkModel
import com.io.ab.music.ui.components.MiniPlayerBottomSpacer
import java.util.Calendar
import java.util.concurrent.TimeUnit
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.runtime.snapshotFlow

@Composable
fun HomeScreen(
    onSearchClick      : () -> Unit,
    onSongClick        : (Song, List<Song>) -> Unit,
    onShuffleAll       : () -> Unit,
    onChangelogHistory : () -> Unit = {},
    libraryViewModel   : LibraryViewModel  = hiltViewModel(),
    playerViewModel    : PlayerViewModel   = hiltViewModel(),
    settingsViewModel  : SettingsViewModel = hiltViewModel()
) {
    val uiState       by libraryViewModel.uiState.collectAsState()
    val currentSongId by playerViewModel.currentSongId.collectAsState()
    val isPlaying     by playerViewModel.isPlaying.collectAsState()
    val settingsState by settingsViewModel.uiState.collectAsState()

    // FIX: "Delete not working" — Android 10+ needs explicit user confirmation via a
    // system dialog before a song not created by this app can actually be removed.
    // Launch that confirmation here and report the result back to the ViewModel.
    val deleteSongRequest by libraryViewModel.deleteSongRequest.collectAsState()
    val deleteSongLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result -> libraryViewModel.onDeleteSongResult(result.resultCode == android.app.Activity.RESULT_OK) }
    LaunchedEffect(deleteSongRequest) {
        deleteSongRequest?.let { sender ->
            deleteSongLauncher.launch(IntentSenderRequest.Builder(sender).build())
        }
    }

    // Check for updates after initial list render so startup stays responsive.
    LaunchedEffect(Unit) {
        delay(7_000L)
        settingsViewModel.checkForUpdate(force = false)
    }
    val hasUpdate = settingsState.updateInfo != null

    val greeting = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..11  -> "Good Morning"
            in 12..16 -> "Good Afternoon"
            in 17..20 -> "Good Evening"
            else      -> "Good Night"
        }
    }

    val savedSortFlow = libraryViewModel.homeSortOrder.collectAsState()
    var currentSort  by remember(savedSortFlow.value) { mutableStateOf(savedSortFlow.value) }
    var showSortMenu by remember { mutableStateOf(false) }
    val scope        = rememberCoroutineScope()

    val favoriteIds = remember(uiState.favorites) { uiState.favorites.mapTo(mutableSetOf()) { it.id } }

    val sortedSongs = remember(uiState.songs, currentSort) {
        when (currentSort) {
            SortOrder.NAME       -> uiState.songs.sortedBy { it.title.lowercase() }
            SortOrder.DATE_ADDED -> uiState.songs.sortedByDescending { it.dateAdded }
            SortOrder.DURATION   -> uiState.songs.sortedByDescending { it.duration }
            SortOrder.SIZE       -> uiState.songs.sortedByDescending { it.size }
        }
    }

    // ── Multi-select state ────────────────────────────────────────────────────
    var selectedSongIds by remember { mutableStateOf(emptySet<Long>()) }
    val isMultiSelectMode = selectedSongIds.isNotEmpty()
    var showMultiDeleteConfirm by remember { mutableStateOf(false) }

    // Multi-delete confirm dialog
    if (showMultiDeleteConfirm && selectedSongIds.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showMultiDeleteConfirm = false },
            icon  = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete ${selectedSongIds.size} songs?") },
            text  = { Text("This will permanently delete the selected songs from your device.") },
            confirmButton = {
                Button(
                    onClick = {
                        selectedSongIds.forEach { id ->
                            val song = sortedSongs.find { it.id == id }
                            if (song != null) libraryViewModel.deleteSong(song)
                        }
                        selectedSongIds = emptySet()
                        showMultiDeleteConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showMultiDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    val sortLabel = when (currentSort) {
        SortOrder.NAME       -> "Name"
        SortOrder.DATE_ADDED -> "Date Added"
        SortOrder.DURATION   -> "Duration"
        SortOrder.SIZE       -> "Size"
    }

    // ── Update dialog ─────────────────────────────────────────────────────────
    var showUpdateDialog  by remember { mutableStateOf(false) }

    if (showUpdateDialog && settingsState.updateInfo != null) {
        UpdateAvailableDialog(
            updateInfo         = settingsState.updateInfo!!,
            onDismiss          = { showUpdateDialog = false },
            onViewFullHistory  = { showUpdateDialog = false; onChangelogHistory() }
        )
    }



    // ── Recently played lazy row scroll state ─────────────────────────────────
    val recentScrollState = rememberLazyListState()
    // FIX: Auto-scroll to newest played song (index 0) when list updates
    LaunchedEffect(uiState.recentlyPlayed.firstOrNull()?.id) {
        if (uiState.recentlyPlayed.isNotEmpty()) {
            recentScrollState.scrollToItem(0)
        }
    }

    // FIX: Scroll-aware mini player — snapshotFlow batches scroll events so we never
    //      recompose on every pixel; the previous LaunchedEffect(index, offset) fired
    //      60× per second and caused full-screen redraws during flings.
    val mainListState    = rememberLazyListState()
    val miniPlayerScroll = LocalMiniPlayerScrollState.current
    var lastScrollIdx    by remember { mutableIntStateOf(0) }
    var lastScrollOffset by remember { mutableIntStateOf(0) }
    LaunchedEffect(mainListState) {
        snapshotFlow {
            mainListState.firstVisibleItemIndex to mainListState.firstVisibleItemScrollOffset
        }.collect { (idx, offset) ->
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
        LazyColumn(modifier = Modifier.fillMaxSize(), state = mainListState) {
        // ── Hero Header ──────────────────────────────────────────────────────
        item(key = "header") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, start = 20.dp, end = 20.dp, bottom = 16.dp)
            ) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text  = greeting,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text  = "AB Player",
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {

                        // ── Update icon (moved from Settings) ────────────────
                        BadgedBox(
                            badge = {
                                if (hasUpdate) {
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.error
                                    ) { Text("!") }
                                }
                            }
                        ) {
                            FilledTonalIconButton(
                                onClick  = {
                                    if (hasUpdate) showUpdateDialog = true
                                    else settingsViewModel.checkForUpdate(force = true)
                                },
                                modifier = Modifier.size(40.dp),
                                shape    = CircleShape
                            ) {
                                Icon(
                                    if (hasUpdate) Icons.Rounded.SystemUpdate
                                    else Icons.Rounded.Update,
                                    "Check Update",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // ── Search ───────────────────────────────────────────
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
        }

        // ── Recently Played row ──────────────────────────────────────────────
        if (uiState.recentlyPlayed.isNotEmpty()) {
            item(key = "recently_header") {
                HomeSectionHeader("Recently Played", Icons.Rounded.History) {}
            }
            item(key = "recently_row") {
                // FIX: "Magnet" snap fling — first card now reliably snaps to the
                // start edge on every device. Plain LazyListState fling could leave
                // cards mid-scroll depending on velocity/density, so we drive a real
                // snap fling behavior anchored to the start of each item.
                @OptIn(ExperimentalFoundationApi::class)
                val recentSnapBehavior = rememberSnapFlingBehavior(
                    lazyListState = recentScrollState
                )

                // FIX: long-pressing a Recently Played card used to grow it
                // (extend/de-extend), which felt like a bug since nothing
                // indicated it was a toggle — cards now stay a fixed size on
                // long press; long press no longer does anything here.
                LazyRow(
                    state                 = recentScrollState,
                    flingBehavior         = recentSnapBehavior,
                    contentPadding        = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier              = Modifier.guardPagerScroll()
                ) {
                    val recentSongs = uiState.recentlyPlayed.take(10)
                    items(recentSongs.size, key = { recentSongs[it].id }) { idx ->
                        val song = recentSongs[idx]
                        PremiumSongCard(
                            song              = song,
                            isPlaying         = song.id == currentSongId,
                            isActuallyPlaying = song.id == currentSongId && isPlaying,
                            onClick           = { onSongClick(song, uiState.recentlyPlayed) }
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }

        // ── All Songs header + sort filter ───────────────────────────────────
        item(key = "all_songs_header") {
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Rounded.LibraryMusic, null,
                        tint     = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp))
                    Text("All Songs",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface)
                    Text("(${sortedSongs.size})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box {
                    TextButton(
                        onClick        = { showSortMenu = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Rounded.Sort, null,
                            modifier = Modifier.size(16.dp),
                            tint     = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(4.dp))
                        Text(sortLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary)
                    }
                    DropdownMenu(
                        expanded         = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        listOf(
                            SortOrder.NAME       to "Name",
                            SortOrder.DATE_ADDED to "Date Added",
                            SortOrder.DURATION   to "Duration",
                            SortOrder.SIZE       to "Size"
                        ).forEach { (order, label) ->
                            DropdownMenuItem(
                                text = {
                                    Text(label,
                                        color = if (currentSort == order)
                                            MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface)
                                },
                                onClick = {
                                    currentSort  = order
                                    showSortMenu = false
                                    scope.launch { libraryViewModel.setHomeSortOrder(order) }
                                },
                                leadingIcon = {
                                    if (currentSort == order)
                                        Icon(Icons.Rounded.Check, null,
                                            tint     = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp))
                                }
                            )
                        }
                    }
                }
            }
            HorizontalDivider(
                modifier  = Modifier.padding(horizontal = 20.dp),
                thickness = 0.5.dp,
                color     = MaterialTheme.colorScheme.outlineVariant
            )
        }

        // ── Multi-select action bar ───────────────────────────────────────────
        if (isMultiSelectMode) {
            item(key = "multi_select_bar") {
                val multiCtx = androidx.compose.ui.platform.LocalContext.current
                Surface(
                    color     = MaterialTheme.colorScheme.primaryContainer,
                    tonalElevation = 4.dp
                ) {
                    Row(
                        modifier              = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { selectedSongIds = emptySet() }) {
                                Icon(Icons.Rounded.Close, "Cancel", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                            Text(
                                "${selectedSongIds.size} selected",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Row {
                            // Select all
                            IconButton(onClick = { selectedSongIds = sortedSongs.map { it.id }.toSet() }) {
                                Icon(Icons.Rounded.SelectAll, "Select All", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                            // Share selected
                            IconButton(onClick = {
                                val selectedSongs = sortedSongs.filter { it.id in selectedSongIds }
                                try {
                                    val uriList = selectedSongs.mapNotNull { song ->
                                        runCatching {
                                            androidx.core.content.FileProvider.getUriForFile(
                                                multiCtx, "${multiCtx.packageName}.provider", java.io.File(song.path)
                                            )
                                        }.getOrNull()
                                    }
                                    if (uriList.isNotEmpty()) {
                                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND_MULTIPLE).apply {
                                            type = "audio/*"
                                            putParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM, ArrayList(uriList))
                                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        multiCtx.startActivity(android.content.Intent.createChooser(intent, "Share ${uriList.size} songs"))
                                    }
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(multiCtx, "Cannot share: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                }
                                selectedSongIds = emptySet()
                            }) {
                                Icon(Icons.Rounded.Share, "Share Selected", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                            // Delete selected
                            IconButton(onClick = { showMultiDeleteConfirm = true }) {
                                Icon(Icons.Rounded.Delete, "Delete Selected", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }

        // ── All Songs list ───────────────────────────────────────────────────
        if (sortedSongs.isEmpty()) {
            item(key = "empty") {
                Box(modifier = Modifier.fillMaxWidth().padding(48.dp),
                    contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.LibraryMusic, null,
                            modifier = Modifier.size(80.dp),
                            tint     = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                        Spacer(Modifier.height(16.dp))
                        Text("No music found",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Add songs to your device to get started",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        } else {
            itemsIndexed(sortedSongs, key = { _, song -> song.id }, contentType = { _, _ -> "song_item" }) { index, song ->
                val isCurrent  = song.id == currentSongId
                val isSelected = song.id in selectedSongIds

                val onLongPress = remember(song.id, isSelected) {
                    {
                        selectedSongIds = if (isSelected)
                            selectedSongIds - song.id
                        else
                            selectedSongIds + song.id
                    }
                }
                val onFavoriteToggle = remember(song.id) {
                    { libraryViewModel.toggleFavorite(song.id) }
                }
                val onAddToQueue = remember(song.id) {
                    { playerViewModel.addToQueue(song) }
                }
                val context = androidx.compose.ui.platform.LocalContext.current
                val onShare = remember(song.id, context) {
                    {
                        try {
                            val file = java.io.File(song.path)
                            val uri  = androidx.core.content.FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.provider",
                                file
                            )
                            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "audio/*"
                                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(android.content.Intent.createChooser(intent, "Share ${song.title}"))
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(context, "Cannot share: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                val onClick = remember(song.id, isMultiSelectMode, isSelected, sortedSongs) {
                    {
                        if (isMultiSelectMode) {
                            selectedSongIds = if (isSelected)
                                selectedSongIds - song.id
                            else
                                selectedSongIds + song.id
                        } else {
                            onSongClick(song, sortedSongs)
                        }
                    }
                }
                HomeListSongItem(
                    song              = song,
                    index             = index + 1,
                    isPlaying         = isCurrent && !isMultiSelectMode,
                    isActuallyPlaying = isCurrent && isPlaying && !isMultiSelectMode,
                    isFavorite        = song.id in favoriteIds,
                    isSelected        = isSelected,
                    hasLyrics         = song.hasLyrics,
                    isMultiSelectMode = isMultiSelectMode,
                    onLongPress       = onLongPress,
                    onFavoriteToggle  = onFavoriteToggle,
                    onAddToQueue      = onAddToQueue,
                    onShare           = onShare,
                    onClick           = onClick
                )
            }
            item(key = "bottom_pad") { MiniPlayerBottomSpacer() }
        }
    } // end LazyColumn
    } // end Box (gradient wrapper)
}

// ── Update Available Dialog ────────────────────────────────────────────────────
private enum class UpdateDownloadState { Idle, Downloading, Downloaded, Failed }

@Composable
private fun UpdateAvailableDialog(
    updateInfo        : com.io.ab.music.ui.viewmodel.UpdateInfo,
    onDismiss         : () -> Unit,
    onViewFullHistory : () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var downloadState by remember(updateInfo.apkUrl) { mutableStateOf(UpdateDownloadState.Idle) }
    var downloadProgress by remember(updateInfo.apkUrl) { mutableStateOf(0f) }
    var downloadedApk by remember(updateInfo.apkUrl) { mutableStateOf<java.io.File?>(null) }
    var downloadError by remember(updateInfo.apkUrl) { mutableStateOf<String?>(null) }

    fun installDownloadedApk() {
        val apk = downloadedApk ?: return
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            val intent = android.content.Intent(
                android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                android.net.Uri.parse("package:${context.packageName}")
            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            android.widget.Toast.makeText(context, "Allow install permission, then tap Install again.", android.widget.Toast.LENGTH_LONG).show()
            return
        }
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            apk
        )
        val installIntent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(installIntent)
    }

    fun startInAppDownload() {
        if (downloadState == UpdateDownloadState.Downloading) return
        downloadState = UpdateDownloadState.Downloading
        downloadProgress = 0f
        downloadError = null
        scope.launch {
            val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    val updatesDir = java.io.File(context.cacheDir, "updates").apply { mkdirs() }
                    val apkFile = java.io.File(updatesDir, "ABMusic-${updateInfo.versionName}.apk")
                    val connection = java.net.URL(updateInfo.apkUrl).openConnection() as java.net.HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 15000
                    connection.readTimeout = 30000
                    connection.connect()
                    if (connection.responseCode !in 200..299) {
                        throw java.io.IOException("Download failed (${connection.responseCode})")
                    }
                    val totalBytes = connection.contentLengthLong.takeIf { it > 0L } ?: -1L
                    var copiedBytes = 0L
                    connection.inputStream.use { input ->
                        apkFile.outputStream().use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                val read = input.read(buffer)
                                if (read == -1) break
                                output.write(buffer, 0, read)
                                copiedBytes += read
                                if (totalBytes > 0L) {
                                    val progress = (copiedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        downloadProgress = progress
                                    }
                                }
                            }
                        }
                    }
                    connection.disconnect()
                    if (apkFile.length() <= 0L) throw java.io.IOException("Downloaded APK is empty")
                    apkFile
                }
            }
            result.onSuccess { apk ->
                downloadedApk = apk
                downloadProgress = 1f
                downloadState = UpdateDownloadState.Downloaded
            }.onFailure { error ->
                downloadError = error.localizedMessage ?: "Download failed"
                downloadState = UpdateDownloadState.Failed
            }
        }
    }

    // Parse changelog: prefer structured list, fallback to splitting releaseNotes by "•" or newline
    val changelogItems = remember(updateInfo) {
        when {
            updateInfo.changelog.isNotEmpty() -> updateInfo.changelog
            updateInfo.releaseNotes.isNotBlank() ->
                updateInfo.releaseNotes
                    .split("•", "\n", ",")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
            else -> emptyList()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = MaterialTheme.colorScheme.surface,
        shape            = RoundedCornerShape(24.dp),
        icon = {
            val primaryColor = MaterialTheme.colorScheme.primary
            val iconBgBrush = remember(primaryColor) {
                Brush.radialGradient(
                    listOf(
                        primaryColor.copy(alpha = 0.25f),
                        primaryColor.copy(alpha = 0.08f)
                    )
                )
            }
            Box(
                modifier         = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(iconBgBrush),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.SystemUpdate, null,
                    tint     = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Update Available",
                    style      = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                    color      = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                // Version badge
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text     = "v${updateInfo.versionName}",
                        style    = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color    = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (changelogItems.isNotEmpty()) {
                    // Section label
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier              = Modifier.padding(bottom = 8.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Assignment, null,
                            tint     = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            "What's New",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (changelogItems.size > 5) {
                            Spacer(Modifier.weight(1f))
                            Text(
                                "${changelogItems.size} changes",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // FIX: Show max 5 items; if more than 5, wrap in a scrollable Column
                    val needsScroll = changelogItems.size > 5
                    val listModifier = if (needsScroll)
                        Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())
                    else
                        Modifier

                    Column(modifier = listModifier, verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        changelogItems.forEachIndexed { idx, item ->
                            Row(
                                modifier              = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment     = Alignment.Top
                            ) {
                                // Serial number badge
                                Box(
                                    modifier         = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(
                                            MaterialTheme.colorScheme.primary.copy(
                                                alpha = 0.12f + (idx * 0.04f).coerceAtMost(0.2f)
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text  = "${idx + 1}",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Text(
                                    text     = item,
                                    style    = MaterialTheme.typography.bodySmall,
                                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            // Thin divider between items (except last)
                            if (idx < changelogItems.lastIndex) {
                                HorizontalDivider(
                                    modifier  = Modifier.padding(start = 32.dp),
                                    thickness = 0.5.dp,
                                    color     = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        updateInfo.releaseNotes.ifBlank { "A new version is available." },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        // FIX: Both buttons in confirmButton slot as a Row so they are always horizontal
        confirmButton = {
            Column(
                modifier            = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick  = onDismiss,
                        shape    = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) { Text("Later") }
                    Button(
                        onClick = {
                            when (downloadState) {
                                UpdateDownloadState.Downloaded -> installDownloadedApk()
                                UpdateDownloadState.Downloading -> Unit
                                else -> startInAppDownload()
                            }
                        },
                        enabled = downloadState != UpdateDownloadState.Downloading,
                        shape    = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f),
                        colors   = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        val icon = when (downloadState) {
                            UpdateDownloadState.Downloaded -> Icons.Rounded.InstallMobile
                            UpdateDownloadState.Failed -> Icons.Rounded.Refresh
                            else -> Icons.Rounded.Download
                        }
                        Icon(icon, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            when (downloadState) {
                                UpdateDownloadState.Downloading -> "Downloading"
                                UpdateDownloadState.Downloaded -> "Install"
                                UpdateDownloadState.Failed -> "Retry"
                                else -> "Download"
                            },
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
                if (downloadState == UpdateDownloadState.Downloading) {
                    LinearProgressIndicator(
                        progress = { downloadProgress },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Text(
                        if (downloadProgress > 0f) "Downloading ${(downloadProgress * 100).toInt()}%" else "Starting download...",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
                if (downloadState == UpdateDownloadState.Downloaded) {
                    Text(
                        "Download complete. Tap Install to continue.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
                if (downloadState == UpdateDownloadState.Failed && downloadError != null) {
                    Text(
                        downloadError ?: "Download failed",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }

                // "View full changelog history" link button
                TextButton(
                    onClick  = onViewFullHistory,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.History, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "View Full Changelog History",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        },
        dismissButton = null
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeListSongItem(
    song             : Song,
    index            : Int,
    isPlaying        : Boolean,
    isActuallyPlaying: Boolean,
    isFavorite       : Boolean = false,
    isSelected       : Boolean = false,
    hasLyrics        : Boolean = false,
    isMultiSelectMode: Boolean = false,
    onLongPress      : () -> Unit = {},
    onFavoriteToggle : () -> Unit = {},
    onAddToQueue     : () -> Unit = {},
    onShare          : () -> Unit = {},
    onClick          : () -> Unit
) {
    val durationStr = remember(song.duration) {
        val m = TimeUnit.MILLISECONDS.toMinutes(song.duration)
        val s = TimeUnit.MILLISECONDS.toSeconds(song.duration) % 60
        "%d:%02d".format(m, s)
    }
    val artworkModel = rememberArtworkModel(song.artworkUri, 128)

    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val secondaryContainer = MaterialTheme.colorScheme.secondaryContainer
    val fallbackCardBrush = remember(primaryContainer, secondaryContainer) {
        Brush.linearGradient(listOf(primaryContainer, secondaryContainer))
    }

    var showMenu by remember { mutableStateOf(false) }

    Surface(
        color = when {
            isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
            isPlaying  -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            else       -> Color.Transparent
        },
        modifier = Modifier.combinedClickable(
            onClick     = onClick,
            onLongClick = onLongPress
        )
    ) {
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center) {
                AsyncImage(model = artworkModel, contentDescription = null,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                if (artworkModel == null) {
                    Box(modifier = Modifier.fillMaxSize().background(fallbackCardBrush), contentAlignment = Alignment.Center) {
                        Text(text  = song.title.first().uppercaseChar().toString(),
                            style  = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color  = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                // Show selection checkbox overlay in multi-select mode
                if (isMultiSelectMode) {
                    Box(
                        modifier         = Modifier.fillMaxSize().background(
                            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
                            else MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)
                        ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                            null,
                            tint     = if (isSelected) Color.White else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                } else if (isPlaying) {
                    Box(modifier = Modifier.fillMaxSize()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center) {
                        Icon(if (isActuallyPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            null, tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(text     = song.title,
                    style    = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color    = if (isPlaying) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    modifier = if (isPlaying) Modifier.basicMarquee(
                        iterations         = Int.MAX_VALUE,
                        initialDelayMillis = 800
                    ) else Modifier,
                    overflow = TextOverflow.Ellipsis)
                Text(text     = song.artist,
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                // ── Duration below artist ─────────────────────────────────
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(text  = durationStr,
                        style  = MaterialTheme.typography.labelSmall)
                    if (hasLyrics) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(Icons.Rounded.Lyrics, null, modifier = Modifier.size(11.dp), tint = MaterialTheme.colorScheme.primary)
                                Text("Lyrics", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }

            // ── Heart (Favorite) button ────────────────────────────────────
            IconButton(
                onClick  = onFavoriteToggle,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = if (isFavorite) "Remove from Favorites" else "Add to Favorites",
                    modifier = Modifier.size(18.dp),
                    tint = if (isFavorite) MaterialTheme.colorScheme.error
                           else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ── 3-dot menu ────────────────────────────────────────────────
            Box {
                IconButton(
                    onClick  = { showMenu = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Rounded.MoreVert, "More options",
                        modifier = Modifier.size(18.dp),
                        tint     = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(
                    expanded         = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text        = { Text("Play now") },
                        onClick     = { showMenu = false; onClick() },
                        leadingIcon = { Icon(Icons.Rounded.PlayArrow, null) }
                    )
                    DropdownMenuItem(
                        text        = { Text("Add to Queue") },
                        onClick     = { showMenu = false; onAddToQueue() },
                        leadingIcon = { Icon(Icons.Rounded.QueueMusic, null) }
                    )
                    DropdownMenuItem(
                        text        = {
                            Text(if (isFavorite) "Remove from Favorites" else "Add to Favorites")
                        },
                        onClick     = { showMenu = false; onFavoriteToggle() },
                        leadingIcon = {
                            Icon(
                                if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                null,
                                tint = if (isFavorite) MaterialTheme.colorScheme.error
                                       else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    )
                    DropdownMenuItem(
                        text        = { Text("Share") },
                        onClick     = { showMenu = false; onShare() },
                        leadingIcon = { Icon(Icons.Rounded.Share, null) }
                    )
                }
            }
        }
    }
}

@Composable
fun HomeSectionHeader(
    title   : String,
    icon    : androidx.compose.ui.graphics.vector.ImageVector,
    onSeeAll: () -> Unit
) {
    Row(
        modifier              = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Text(title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface)
        }
        TextButton(onClick = onSeeAll) {
            Text("See all",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PremiumSongCard(
    song             : Song,
    isPlaying        : Boolean,
    isActuallyPlaying: Boolean  = false,
    onClick          : () -> Unit
) {
    val artworkModel = rememberArtworkModel(song.artworkUri, 320)

    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val secondary = MaterialTheme.colorScheme.secondary
    val recentPlayingBorder = remember(primary, tertiary) {
        Brush.linearGradient(listOf(primary, tertiary))
    }
    val recentFallbackBrush = remember(primary, secondary) {
        Brush.linearGradient(listOf(primary, secondary))
    }

    // FIX: card no longer grows on long press — fixed width, plain click only.
    // Flat, lag-free card: no shadow layer, no entrance animation —
    // cheap clip + background only so scrolling and screen reopen stay smooth.
    Column(
        modifier = Modifier
            .width(130.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .then(
                    if (isPlaying) Modifier.border(2.dp, recentPlayingBorder, RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                    else Modifier
                )
        ) {
            AsyncImage(model = artworkModel, contentDescription = null,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            if (artworkModel == null) {
                Box(modifier = Modifier.fillMaxSize().background(recentFallbackBrush), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.MusicNote, null,
                        tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(40.dp))
                }
            }
            // ── Gradient overlay at bottom — matches AlbumCard ─────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))
                        )
                    )
            )
            if (isPlaying) {
                Box(modifier = Modifier.fillMaxSize()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center) {
                    Icon(if (isActuallyPlaying) Icons.Rounded.PauseCircleFilled
                        else Icons.Rounded.PlayCircleFilled,
                        null, tint = Color.White, modifier = Modifier.size(38.dp))
                }
            }
        }
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Text(song.title,
                style    = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color    = if (isPlaying) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist,
                style    = MaterialTheme.typography.labelSmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
