package com.io.ab.music.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.io.ab.music.data.preferences.UserPreferences
import com.io.ab.music.data.scanner.VideoScanner
import com.io.ab.music.domain.model.Video
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import androidx.compose.runtime.Stable
import javax.inject.Inject

enum class VideoSortOrder { DATE_RECENT, NAME_AZ, NAME_ZA, SIZE_LARGE, DURATION_LONG }
enum class VideoViewType  { GRID, LIST }

@Stable
data class VideoUiState(
    val videos          : List<Video>         = emptyList(),
    val filteredVideos  : List<Video>         = emptyList(),
    val recentlyPlayed  : List<Video>         = emptyList(),
    val isLoading       : Boolean             = true,
    val searchQuery     : String              = "",
    val sortOrder       : VideoSortOrder      = VideoSortOrder.DATE_RECENT,
    val viewType        : VideoViewType       = VideoViewType.GRID,
    val selectedFolder  : String?             = null,
    val folders         : List<String>        = emptyList()
)

@HiltViewModel
class VideoViewModel @Inject constructor(
    application: Application,
    private val videoScanner  : VideoScanner,
    private val userPreferences: UserPreferences
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(VideoUiState())
    val uiState: StateFlow<VideoUiState> = _uiState.asStateFlow()

    private val _currentVideo = MutableStateFlow<Video?>(null)
    val currentVideo: StateFlow<Video?> = _currentVideo.asStateFlow()

    private val _currentQueue = MutableStateFlow<List<Video>>(emptyList())
    val currentQueue: StateFlow<List<Video>> = _currentQueue.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    // ── Last position map: videoId -> positionMs ──────────────────────────
    private val _lastPositions = MutableStateFlow<Map<Long, Long>>(emptyMap())
    val lastPositions: StateFlow<Map<Long, Long>> = _lastPositions.asStateFlow()

    // ── Background play flag ──────────────────────────────────────────────
    private val _isBackgroundPlay = MutableStateFlow(false)
    val isBackgroundPlay: StateFlow<Boolean> = _isBackgroundPlay.asStateFlow()

    init {
        viewModelScope.launch {
            val savedSort = userPreferences.videoSortOrder.first()
            val savedView = userPreferences.videoViewType.first()
            val parsedSort = runCatching { VideoSortOrder.valueOf(savedSort) }.getOrDefault(VideoSortOrder.DATE_RECENT)
            val parsedView = runCatching { VideoViewType.valueOf(savedView) }.getOrDefault(VideoViewType.GRID)
            _uiState.update { it.copy(sortOrder = parsedSort, viewType = parsedView) }
            // Load last positions
            val posData = userPreferences.videoLastPositions.first()
            _lastPositions.value = parsePositions(posData)
            // Load persisted audio/subtitle track selections
            savedAudioGroupIdx    = userPreferences.videoAudioTrackGroup.first()
            savedSubtitleGroupIdx = userPreferences.videoSubtitleTrackGroup.first()
        }
    }

    // FIX: Tracks whether we've already loaded videos once this process lifetime.
    // Without this, VideoScreen's LaunchedEffect(Unit) re-ran scanVideos() on EVERY
    // tab switch (Unit-keyed effects re-fire whenever the composable re-enters
    // composition) — full MediaStore re-query + isLoading=true spinner each time,
    // which is the "lag on screen switch, smooth after a few seconds" symptom.
    private var hasLoadedOnce = false
    private var scanJob: kotlinx.coroutines.Job? = null

    /**
     * Loads videos from MediaStore.
     * - First call (or [forceShowLoading]): shows the loading spinner.
     * - Subsequent calls (e.g. revisiting the tab): refreshes in the background with
     *   NO loading flag toggle, so the UI doesn't flash/re-layout if nothing changed.
     */
    fun scanVideos(forceShowLoading: Boolean = false) {
        // Avoid piling up overlapping scans if the tab is switched to rapidly.
        if (scanJob?.isActive == true && !forceShowLoading) return
        scanJob = viewModelScope.launch(Dispatchers.IO) {
            val showSpinner = forceShowLoading || !hasLoadedOnce
            if (showSpinner) _uiState.update { it.copy(isLoading = true) }

            val all     = videoScanner.scanAllVideos()
            val folders = all.map { it.folderName }.distinct().sorted()
            val videoMap = all.associateBy { it.id.toString() }

            // FIX: previously we always rebuilt recentlyPlayed from the persisted
            // DataStore string on every scan. addToRecent() writes that string
            // asynchronously (fire-and-forget), so if the user played a video and
            // immediately backed out to this screen (which re-triggers a scan),
            // the DataStore write could still be in flight — the scan would read
            // the OLD ids and stomp the freshly-added in-memory entry, making it
            // look like "recently played" forgot the video that was just played.
            // Fix: only hydrate recentlyPlayed from persisted ids on the very
            // first load. On every later scan, just refresh the existing in-memory
            // entries' metadata (thumbnail/duration/path may have changed) while
            // keeping the play order that's already in state.
            val recent = if (!hasLoadedOnce) {
                val savedIds = userPreferences.videoRecentlyPlayedIds.first()
                    .split(",").filter { it.isNotBlank() }
                savedIds.mapNotNull { videoMap[it] }
            } else {
                _uiState.value.recentlyPlayed.mapNotNull { videoMap[it.id.toString()] }
            }

            _uiState.update { s ->
                s.copy(
                    videos         = all,
                    filteredVideos = applyFilters(all, s.searchQuery, s.selectedFolder, s.sortOrder),
                    folders        = folders,
                    recentlyPlayed = recent,
                    isLoading      = false
                )
            }
            hasLoadedOnce = true
        }
    }

    fun onSearchQuery(query: String) {
        _uiState.update { s ->
            val filtered = applyFilters(s.videos, query, s.selectedFolder, s.sortOrder)
            s.copy(searchQuery = query, filteredVideos = filtered)
        }
    }

    fun onSortOrder(order: VideoSortOrder) {
        _uiState.update { s ->
            val filtered = applyFilters(s.videos, s.searchQuery, s.selectedFolder, order)
            s.copy(sortOrder = order, filteredVideos = filtered)
        }
        viewModelScope.launch { userPreferences.setVideoSortOrder(order.name) }
    }

    fun onSelectFolder(folder: String?) {
        _uiState.update { s ->
            val filtered = applyFilters(s.videos, s.searchQuery, folder, s.sortOrder)
            s.copy(selectedFolder = folder, filteredVideos = filtered)
        }
    }

    fun toggleViewType() {
        val newType = if (_uiState.value.viewType == VideoViewType.GRID) VideoViewType.LIST else VideoViewType.GRID
        _uiState.update { s -> s.copy(viewType = newType) }
        viewModelScope.launch { userPreferences.setVideoViewType(newType.name) }
    }

    fun playVideo(video: Video, queue: List<Video> = emptyList()) {
        val q   = queue.ifEmpty { _uiState.value.filteredVideos }
        val idx = q.indexOf(video).coerceAtLeast(0)
        _currentVideo.value = video
        _currentQueue.value = q
        _currentIndex.value = idx
        addToRecent(video)
    }

    fun playNext() {
        val q   = _currentQueue.value
        val idx = (_currentIndex.value + 1).coerceAtMost(q.size - 1)
        _currentIndex.value = idx
        _currentVideo.value = q.getOrNull(idx)
        q.getOrNull(idx)?.let { addToRecent(it) }
    }

    fun playPrev() {
        val q   = _currentQueue.value
        val idx = (_currentIndex.value - 1).coerceAtLeast(0)
        _currentIndex.value = idx
        _currentVideo.value = q.getOrNull(idx)
        q.getOrNull(idx)?.let { addToRecent(it) }
    }

    fun clearCurrentVideo() {
        _currentVideo.value = null
        _isBackgroundPlay.value = false
    }

    /** Save position every few seconds while playing */
    fun saveLastPosition(videoId: Long, positionMs: Long) {
        // Only save if position is meaningful (>5 sec, not near end)
        if (positionMs < 5000L) return
        val current = _lastPositions.value.toMutableMap()
        current[videoId] = positionMs
        _lastPositions.value = current
        viewModelScope.launch {
            userPreferences.setVideoLastPositions(serializePositions(current))
        }
    }

    fun clearLastPosition(videoId: Long) {
        val current = _lastPositions.value.toMutableMap()
        current.remove(videoId)
        _lastPositions.value = current
        viewModelScope.launch {
            userPreferences.setVideoLastPositions(serializePositions(current))
        }
    }

    fun getLastPosition(videoId: Long): Long {
        return _lastPositions.value[videoId] ?: 0L
    }

    // FIX: "Delete not working" — same scoped-storage issue as songs (see
    // LibraryViewModel.deleteSong). contentResolver.delete() on a video the app didn't
    // create throws/refuses on Android 10+, and the old code silently swallowed it while
    // still calling scanVideos(), so the item just reappeared with nothing actually
    // deleted. We now surface an IntentSender for the UI to launch the system's delete
    // confirmation, and only remove the file / rescan once the user actually confirms.
    private val _deleteVideoRequest = MutableStateFlow<android.content.IntentSender?>(null)
    val deleteVideoRequest: StateFlow<android.content.IntentSender?> = _deleteVideoRequest.asStateFlow()
    private var pendingDeleteVideo: Video? = null

    /** Delete a video from device storage and refresh list. */
    fun deleteVideo(video: Video) {
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            try {
                app.contentResolver.delete(video.contentUri, null, null)
                deleteFileQuietly(video.path)
                scanVideos()
            } catch (e: SecurityException) {
                pendingDeleteVideo = video
                _deleteVideoRequest.value = when {
                    android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R ->
                        android.provider.MediaStore.createDeleteRequest(
                            app.contentResolver, listOf(video.contentUri)
                        ).intentSender
                    e is android.app.RecoverableSecurityException -> e.userAction.actionIntent.intentSender
                    else -> null
                }
            } catch (_: Exception) { /* ignore other failures */ }
        }
    }

    /** Called by the UI after the system delete-confirmation dialog result comes back. */
    fun onDeleteVideoResult(confirmed: Boolean) {
        val video = pendingDeleteVideo
        pendingDeleteVideo = null
        _deleteVideoRequest.value = null
        if (confirmed && video != null) {
            viewModelScope.launch(Dispatchers.IO) {
                deleteFileQuietly(video.path)
                scanVideos()
            }
        }
    }

    private fun deleteFileQuietly(path: String) {
        runCatching {
            val file = java.io.File(path)
            if (file.exists()) file.delete()
        }
    }

    /** Rename a video file via MediaStore. */
    fun renameVideo(context: android.content.Context, video: Video, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cv = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Video.Media.DISPLAY_NAME, newName)
                    put(android.provider.MediaStore.Video.Media.TITLE, newName)
                }
                context.contentResolver.update(video.contentUri, cv, null, null)
            } catch (_: Exception) {}
            scanVideos()
        }
    }

    /** Enable background play mode (video continues audio when app backgrounds) */
    fun enableBackgroundPlay() {
        _isBackgroundPlay.value = true
    }

    fun disableBackgroundPlay() {
        _isBackgroundPlay.value = false
    }

    // ── Video player action button persistence ────────────────────────────
    fun saveScaleMode(mode: String)       { viewModelScope.launch { userPreferences.setVideoScaleMode(mode) } }
    fun savePlaybackSpeed(speed: Float)   { viewModelScope.launch { userPreferences.setVideoPlaybackSpeed(speed) } }
    fun saveLoopEnabled(enabled: Boolean) { viewModelScope.launch { userPreferences.setVideoLoopEnabled(enabled) } }
    fun saveRotationMode(mode: String)    { viewModelScope.launch { userPreferences.setVideoRotationMode(mode) } }

    // Persist selected audio/subtitle track index across app restarts
    var savedAudioGroupIdx    : Int = -1
    var savedSubtitleGroupIdx : Int = -1

    suspend fun loadScaleMode()          = userPreferences.videoScaleMode.first()
    suspend fun loadPlaybackSpeed()      = userPreferences.videoPlaybackSpeed.first()
    suspend fun loadLoopEnabled()        = userPreferences.videoLoopEnabled.first()
    suspend fun loadRotationMode()       = userPreferences.videoRotationMode.first()
    suspend fun loadAudioTrackGroup()    = userPreferences.videoAudioTrackGroup.first()
    suspend fun loadSubtitleTrackGroup() = userPreferences.videoSubtitleTrackGroup.first()

    fun saveAudioTrackGroup(idx: Int)    { savedAudioGroupIdx = idx; viewModelScope.launch { userPreferences.setVideoAudioTrackGroup(idx) } }
    fun saveSubtitleTrackGroup(idx: Int) { savedSubtitleGroupIdx = idx; viewModelScope.launch { userPreferences.setVideoSubtitleTrackGroup(idx) } }

    private fun addToRecent(video: Video) {
        _uiState.update { s ->
            val list = (listOf(video) + s.recentlyPlayed.filter { it.id != video.id }).take(20)
            s.copy(recentlyPlayed = list)
        }
        viewModelScope.launch {
            val ids = _uiState.value.recentlyPlayed.joinToString(",") { it.id.toString() }
            userPreferences.setVideoRecentlyPlayedIds(ids)
        }
    }

    private fun applyFilters(
        all    : List<Video>,
        query  : String,
        folder : String?,
        sort   : VideoSortOrder
    ): List<Video> {
        var filtered = all
        if (query.isNotBlank()) filtered = filtered.filter { it.title.contains(query, ignoreCase = true) }
        if (folder != null)     filtered = filtered.filter { it.folderName == folder }
        filtered = when (sort) {
            VideoSortOrder.DATE_RECENT   -> filtered.sortedByDescending { it.dateModified }
            VideoSortOrder.NAME_AZ       -> filtered.sortedBy          { it.title.lowercase() }
            VideoSortOrder.NAME_ZA       -> filtered.sortedByDescending { it.title.lowercase() }
            VideoSortOrder.SIZE_LARGE    -> filtered.sortedByDescending { it.size }
            VideoSortOrder.DURATION_LONG -> filtered.sortedByDescending { it.duration }
        }
        return filtered
    }

    private fun serializePositions(map: Map<Long, Long>): String =
        map.entries.joinToString(",") { "${it.key}:${it.value}" }

    private fun parsePositions(data: String): Map<Long, Long> {
        if (data.isBlank()) return emptyMap()
        return data.split(",").mapNotNull { entry ->
            val parts = entry.split(":")
            if (parts.size == 2) {
                val id  = parts[0].toLongOrNull() ?: return@mapNotNull null
                val pos = parts[1].toLongOrNull() ?: return@mapNotNull null
                id to pos
            } else null
        }.toMap()
    }
}
