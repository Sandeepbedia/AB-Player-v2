package com.io.ab.music.ui.viewmodel

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.io.ab.music.data.preferences.UserPreferences
import com.io.ab.music.data.repository.MusicRepository
import com.io.ab.music.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.Stable
import javax.inject.Inject

enum class SortOrder { NAME, DATE_ADDED, DURATION, SIZE }
enum class LibraryTab { SONGS, ALBUMS, ARTISTS, FOLDERS, PLAYLISTS, FAVORITES, RECENT }

@Stable
data class LibraryUiState(
    val songs: List<Song> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val favorites: List<Song> = emptyList(),
    val recentlyPlayed: List<Song> = emptyList(),
    val recentlyAdded: List<Song> = emptyList(),
    val mostPlayed: List<Song> = emptyList(),
    val isScanning: Boolean = false,
    val isReady: Boolean = false,
    val sortOrder: SortOrder = SortOrder.NAME,
    val selectedTab: LibraryTab = LibraryTab.SONGS,
    val searchQuery: String = "",
    val searchResults: List<Song> = emptyList(),
    val isListView: Boolean = true
)

private data class LibraryBundle(
    val songs: List<Song>,
    val albums: List<Album>,
    val artists: List<Artist>,
    val playlists: List<Playlist>,
    val favorites: List<Song>
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: MusicRepository,
    private val prefs: UserPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    private var mediaObserver: ContentObserver? = null
    private var lastScanTime = 0L
    private var lyricsWorkerStarted = false
    private val attemptedLyricsDownloads = mutableSetOf<Long>()

    // ── Persist home sort order ────────────────────────────────────────────────
    val homeSortOrder: StateFlow<SortOrder> = prefs.homeSortOrder
        .map { name -> runCatching { SortOrder.valueOf(name) }.getOrDefault(SortOrder.NAME) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SortOrder.NAME)

    suspend fun setHomeSortOrder(order: SortOrder) {
        prefs.setHomeSortOrder(order.name)
    }

    init {
        observeLibrary()
        observeSearch()
        observePrefs()
        viewModelScope.launch { scanIfEmpty() }
        registerMediaObserver()
        viewModelScope.launch { loadSortOrderFromPrefs() }
    }

    private suspend fun loadSortOrderFromPrefs() {
        val orderName = prefs.librarySortOrder.first()
        val order = runCatching { SortOrder.valueOf(orderName) }.getOrDefault(SortOrder.NAME)
        _uiState.update { it.copy(sortOrder = order) }
    }

    private fun applySortOrder(songs: List<Song>, order: SortOrder): List<Song> = when (order) {
        SortOrder.NAME       -> songs.sortedBy { it.title.lowercase() }
        SortOrder.DATE_ADDED -> songs.sortedByDescending { it.dateAdded }
        SortOrder.DURATION   -> songs.sortedByDescending { it.duration }
        SortOrder.SIZE       -> songs.sortedByDescending { it.size }
    }

    private fun observeLibrary() {
        viewModelScope.launch {
            val libraryFlow = combine(
                repository.getAllSongs(),
                repository.getAlbums(),
                repository.getArtists(),
                repository.getAllPlaylists(),
                repository.getFavoriteSongs()
            ) { songs, albums, artists, playlists, favorites ->
                LibraryBundle(songs, albums, artists, playlists, favorites)
            }

            var firstEmission = true
            combine(libraryFlow, repository.getLyricsSongIds()) { library, lyricsIds ->
                val lyricIdSet = lyricsIds.toSet()
                val songsWithLyrics = library.songs.map { it.copy(hasLyrics = it.id in lyricIdSet) }
                val favoritesWithLyrics = library.favorites.map { it.copy(hasLyrics = it.id in lyricIdSet) }
                val currentOrder = _uiState.value.sortOrder
                val sorted = applySortOrder(songsWithLyrics, currentOrder)
                val ready = if (firstEmission) { firstEmission = false; true } else false
                _uiState.update { it.copy(
                    songs = sorted,
                    albums = library.albums,
                    artists = library.artists,
                    playlists = library.playlists,
                    favorites = favoritesWithLyrics,
                    isReady = ready || it.isReady
                )}
                scheduleLyricsDownloads()
            }.collect()
        }
        viewModelScope.launch {
            repository.getRecentlyPlayed(30).collect { recent ->
                _uiState.update { it.copy(recentlyPlayed = recent) }
            }
        }
        viewModelScope.launch {
            repository.getRecentlyAdded(20).collect { added ->
                _uiState.update { it.copy(recentlyAdded = added) }
            }
        }
        viewModelScope.launch {
            repository.getMostPlayed(20).collect { most ->
                _uiState.update { it.copy(mostPlayed = most) }
            }
        }
    }

    // FIX (speed + reliability): The previous version relaunched a small batch
    // every time the songs/lyrics Flow re-emitted, gated by an initial 12s
    // delay EVERY time, and only 3 songs per batch — very slow for a big
    // library. Worse, if every song in a batch failed to find lyrics, the
    // Room lyrics table never changed, so the Flow never re-emitted, so the
    // whole worker silently stalled forever with most songs never tagged.
    //
    // Now a single continuous loop starts once (short 3s startup delay, not
    // 12s), works through the whole library in larger batches with a short
    // gap between requests, and — because it pulls the live song list from
    // _uiState itself every iteration instead of waiting to be re-invoked —
    // it keeps going regardless of whether earlier lookups succeeded, and
    // automatically picks up newly-scanned songs without needing anything
    // external to wake it back up.
    // FIX: "lyrics new added ke hisab se serieswise download hona chahiye" —
    // pending songs used to be processed in whatever order _uiState.songs
    // happened to be sorted in (title A→Z), so a freshly-added song's lyrics
    // could sit at the back of a long queue behind the entire rest of the
    // library. Now the queue is sorted newest-added-first every iteration, so
    // recently added songs get their lyrics fetched first, one after another
    // in sequence, before the worker moves on to older untagged songs.
    private fun scheduleLyricsDownloads() {
        if (lyricsWorkerStarted) return
        lyricsWorkerStarted = true
        viewModelScope.launch(Dispatchers.IO) {
            delay(3_000L) // let the first screen render settle before hitting the network
            while (true) {
                val pending = _uiState.value.songs
                    .filter { !it.hasLyrics && it.id !in attemptedLyricsDownloads }
                    .sortedByDescending { it.dateAdded }
                if (pending.isEmpty()) {
                    delay(20_000L) // nothing to do right now — check again shortly for newly-added songs
                    continue
                }
                pending.take(6).forEach { song ->
                    attemptedLyricsDownloads.add(song.id)
                    // Cached locally in Room via cacheLyrics/downloadAndCacheLyrics
                    // so lyrics don't need to be re-downloaded once tagged.
                    repository.downloadAndCacheLyrics(song)
                    delay(600L) // stay easy on the lrclib.net API
                }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeSearch() {
        viewModelScope.launch {
            _searchQuery
                .debounce(300)
                .distinctUntilChanged()
                .flatMapLatest { q ->
                    if (q.isBlank()) flowOf(emptyList())
                    else repository.searchSongs(q)
                }
                .collect { results ->
                    _uiState.update { it.copy(searchResults = results) }
                }
        }
    }

    private fun observePrefs() {
        viewModelScope.launch {
            prefs.isListView.collect { isList ->
                _uiState.update { it.copy(isListView = isList) }
            }
        }
    }

    // FIX: Previously this only rescanned when the DB was completely empty
    // (first launch), then relied purely on MediaObserver for anything after
    // that. MediaObserver only fires while this ViewModel/process is alive —
    // songs/videos added while the app was fully closed were missed until
    // some unrelated MediaStore change happened to trigger a rescan. Now we
    // always do a background rescan on every app open (mirrors VideoScreen's
    // scanVideos() on open), so new songs show up immediately without
    // waiting on a lucky MediaObserver callback.
    private suspend fun scanIfEmpty() {
        val currentSongs = repository.getAllSongs().first()
        if (currentSongs.isEmpty()) {
            // First launch: scan with the loading flag so HomeScreen shows a spinner.
            forceRescanLibrary()
        } else {
            // Already has songs: refresh quietly in the background for any
            // new/removed files added while the app was closed.
            scanLibrary()
        }
    }

    private fun registerMediaObserver() {
        val handler = Handler(android.os.Looper.getMainLooper())
        var pendingRescan: Runnable? = null

        val observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) = scheduleRescan()
            override fun onChange(selfChange: Boolean, uri: Uri?) = scheduleRescan()

            private fun scheduleRescan() {
                // FIX: Debounce rapid MediaStore notifications (download in progress fires many times)
                // Wait 1.5s after last notification before scanning so file is fully written
                pendingRescan?.let { handler.removeCallbacks(it) }
                val rescanRunnable = Runnable {
                    viewModelScope.launch { forceRescanLibrary() }
                }
                pendingRescan = rescanRunnable
                handler.postDelayed(rescanRunnable, 1500L)
            }
        }
        mediaObserver = observer
        context.contentResolver.registerContentObserver(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            true,
            observer
        )
    }

    // FIX: Force rescan bypassing the 2s debounce guard (used by MediaObserver after download)
    private suspend fun forceRescanLibrary() {
        lastScanTime = 0L
        scanLibrary()
    }

    override fun onCleared() {
        super.onCleared()
        mediaObserver?.let { context.contentResolver.unregisterContentObserver(it) }
    }

    fun scanLibrary() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            if (now - lastScanTime < 2000) return@launch
            lastScanTime = now
            _uiState.update { it.copy(isScanning = true) }
            // Read includeSubfolders preference before scanning
            val includeSubfolders = prefs.includeSubfolders.first()
            repository.scanAndSaveMusic(includeSubfolders)
            _uiState.update { it.copy(isScanning = false) }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun selectTab(tab: LibraryTab) = _uiState.update { it.copy(selectedTab = tab) }

    fun setSortOrder(order: SortOrder) {
        viewModelScope.launch {
            val sorted = withContext(Dispatchers.Default) { applySortOrder(_uiState.value.songs, order) }
            _uiState.update { it.copy(sortOrder = order, songs = sorted) }
            // FIX: Persist sort order so it survives app restarts
            prefs.setLibrarySortOrder(order.name)
        }
    }

    fun toggleListView() {
        viewModelScope.launch {
            val current = _uiState.value.isListView
            prefs.setListView(!current)
        }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch { repository.createPlaylist(name) }
    }

    /** Creates a playlist and immediately adds the given song to it (fixes "Create & Add" flow). */
    fun createPlaylistAndAddSong(name: String, songId: Long) {
        viewModelScope.launch {
            val playlistId = repository.createPlaylist(name)
            repository.addSongToPlaylist(playlistId, songId)
        }
    }

    fun deletePlaylist(playlist: Playlist) {
        viewModelScope.launch { repository.deletePlaylist(playlist) }
    }

    // FIX: Add song to playlist - wiring up repository call
    fun addSongToPlaylist(playlistId: Long, songId: Long) {
        viewModelScope.launch { repository.addSongToPlaylist(playlistId, songId) }
    }

    fun removeSongFromPlaylist(playlistId: Long, songId: Long) {
        viewModelScope.launch { repository.removeSongFromPlaylist(playlistId, songId) }
    }

    // FIX: Expose songs by artist for Artist detail expansion
    fun getSongsByArtist(artistName: String): Flow<List<Song>> =
        repository.getSongsByArtist(artistName)

    // ── Playlist Detail ───────────────────────────────────────────────────────
    private val _selectedPlaylist = MutableStateFlow<com.io.ab.music.domain.model.Playlist?>(null)
    val selectedPlaylist: StateFlow<com.io.ab.music.domain.model.Playlist?> = _selectedPlaylist.asStateFlow()

    private val _playlistSongs = MutableStateFlow<List<Song>>(emptyList())
    val playlistSongs: StateFlow<List<Song>> = _playlistSongs.asStateFlow()

    private var playlistSongsJob: kotlinx.coroutines.Job? = null

    fun openPlaylist(playlist: com.io.ab.music.domain.model.Playlist) {
        _selectedPlaylist.value = playlist
        playlistSongsJob?.cancel()
        playlistSongsJob = viewModelScope.launch {
            repository.getPlaylistSongs(playlist.id).collect { songs ->
                _playlistSongs.value = songs
                // Keep playlist songCount in sync
                _selectedPlaylist.value = playlist.copy(songCount = songs.size)
            }
        }
    }

    fun closePlaylist() {
        playlistSongsJob?.cancel()
        _selectedPlaylist.value = null
        _playlistSongs.value = emptyList()
    }

    fun toggleFavorite(songId: Long) {
        viewModelScope.launch {
            val isFav = repository.isFavorite(songId).first()
            repository.toggleFavorite(songId, !isFav)
        }
    }

    // FIX: "Delete not working" — on Android 10+ (scoped storage) MediaStore.delete()
    // throws a RecoverableSecurityException (API 29) or simply refuses (API 30+) for any
    // file the app didn't create itself, which is true for almost every song a user has.
    // The old code caught that exception and silently swallowed it, so from the user's
    // point of view tapping "Delete" did nothing at all. We now request the system's
    // delete-confirmation dialog via IntentSender and expose it here for the UI to launch;
    // once the user confirms, finishDeleteSong() removes the physical file and refreshes.
    private val _deleteSongRequest = MutableStateFlow<android.content.IntentSender?>(null)
    val deleteSongRequest: StateFlow<android.content.IntentSender?> = _deleteSongRequest.asStateFlow()
    private var pendingDeleteSong: Song? = null

    fun deleteSong(song: Song) {
        viewModelScope.launch {
            val mediaUri = android.content.ContentUris.withAppendedId(
                android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, song.id
            )
            try {
                // Delete from MediaStore — triggers ContentObserver → library rescans automatically
                context.contentResolver.delete(mediaUri, null, null)
                deleteFileQuietly(song.path)
            } catch (e: SecurityException) {
                // Need user consent — build the appropriate system confirmation IntentSender.
                pendingDeleteSong = song
                _deleteSongRequest.value = when {
                    android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R ->
                        android.provider.MediaStore.createDeleteRequest(
                            context.contentResolver, listOf(mediaUri)
                        ).intentSender
                    e is android.app.RecoverableSecurityException -> e.userAction.actionIntent.intentSender
                    else -> null
                }
            } catch (_: Exception) { /* ignore other failures */ }
        }
    }

    /** Called by the UI after the system delete-confirmation dialog result comes back. */
    fun onDeleteSongResult(confirmed: Boolean) {
        val song = pendingDeleteSong
        pendingDeleteSong = null
        _deleteSongRequest.value = null
        if (confirmed && song != null) {
            deleteFileQuietly(song.path)
            scanLibrary()
        }
    }

    private fun deleteFileQuietly(path: String) {
        runCatching {
            val file = java.io.File(path)
            if (file.exists()) file.delete()
        }
    }
}
