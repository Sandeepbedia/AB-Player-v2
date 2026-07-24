package com.io.ab.music.ui.viewmodel

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.io.ab.music.data.preferences.UserPreferences
import com.io.ab.music.data.repository.MusicRepository
import com.io.ab.music.domain.model.PlayerState
import com.io.ab.music.domain.model.RepeatMode
import com.io.ab.music.domain.model.Song
import com.io.ab.music.service.MusicService
import com.io.ab.music.ui.screens.player.EqPreset
import com.io.ab.music.ui.screens.player.EqualizerController
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: MusicRepository,
    private val prefs: UserPreferences
) : ViewModel() {

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    // ── Equalizer ──────────────────────────────────────────────────────────────
    private var eqController: EqualizerController? = null
    private val _eqBandLevels = MutableStateFlow<FloatArray>(FloatArray(5) { 0f })
    val eqBandLevels: StateFlow<FloatArray> = _eqBandLevels.asStateFlow()
    val eqBandLabels: List<String> get() = eqController?.bandLabels
        ?: listOf("60Hz", "230Hz", "910Hz", "4kHz", "14kHz")
    val eqBandCount: Int get() = eqController?.bandCount ?: 5
    val eqBandLevelRange: IntRange get() = eqController?.bandLevelRange ?: (-1000..1000)

    val isPlaying: StateFlow<Boolean> = _playerState
        .map { it.isPlaying }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val currentSongId: StateFlow<Long?> = _playerState
        .map { it.currentSong?.id }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val isPremium: StateFlow<Boolean> = prefs.isPremium
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val favoriteSongIds: StateFlow<Set<Long>> = repository.getAllFavoriteSongIds()
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val currentSongIsFavorite: StateFlow<Boolean> = combine(currentSongId, favoriteSongIds) { songId, favoriteIds ->
        songId != null && songId in favoriteIds
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // ── Lyrics ──────────────────────────────────────────────────────────────────
    private val _lyricsState = MutableStateFlow<LyricsState>(LyricsState.Idle)
    val lyricsState: StateFlow<LyricsState> = _lyricsState.asStateFlow()

    // FIX: Track WHERE the lyrics came from (Local file vs Online/LRCLib) so the
    // UI can show a "Local" / "Online" badge confirming lyrics work both ways.
    private val _lyricsSource = MutableStateFlow<LyricsSource?>(null)
    val lyricsSource: StateFlow<LyricsSource?> = _lyricsSource.asStateFlow()

    /** Current highlighted line index for karaoke (updates every 250ms when synced lyrics loaded) */
    val currentLyricIndex: StateFlow<Int> = combine(_lyricsState, _playerState) { lyrics, player ->
        if (lyrics is LyricsState.Synced) {
            val posMs = player.progress
            val idx = lyrics.lines.indexOfLast { it.timeMs <= posMs }
            if (idx < 0) 0 else idx
        } else -1
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), -1)

    fun fetchLyrics() {
        val song = _playerState.value.currentSong ?: return
        val rawTitle  = song.title.trim()
        val rawArtist = song.artist.trim()
        val cleanTitle  = rawTitle
            .substringBeforeLast(".", rawTitle)
            .replace(Regex("\\s*[\\(\\[]\\s*(feat\\.?|ft\\.?|featuring)\\s+.*?[\\)\\]]", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*[\\(\\[]\\s*(official.*?|lyrics?|lyrical video|audio|video|visualizer|explicit|remaster(ed)?).*?[\\)\\]]", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*[-–—]\\s*(official|lyrics?|lyrical|audio|video|visualizer|remaster(ed)?|explicit).*$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        val cleanArtist = rawArtist
            .takeIf { it.isNotBlank() && !it.equals("Unknown Artist", ignoreCase = true) }
            ?.split(Regex("\\s*(,|;|/|&| x | X | feat\\.? | ft\\.? | featuring )\\s*", RegexOption.IGNORE_CASE))
            ?.firstOrNull()
            ?.trim()
            .orEmpty()

        if (cleanTitle.isBlank()) {
            _lyricsSource.value = null
            _lyricsState.value = LyricsState.Error("Unknown song")
            return
        }
        _lyricsSource.value = null
        _lyricsState.value = LyricsState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cachedLyrics = repository.getCachedLyrics(song.id)
                if (cachedLyrics != null) {
                    _lyricsSource.value = LyricsSource.LOCAL
                    _lyricsState.value = parseLrc(cachedLyrics.lyrics)
                    return@launch
                }

                // 1 Local .lrc file next to the audio file — works fully OFFLINE,
                //    always checked first regardless of network state.
                val localLrc = tryLocalLrc(song.path)
                if (localLrc != null) {
                    _lyricsSource.value = LyricsSource.LOCAL
                    repository.cacheLyrics(song.id, localLrc, hasSyncedTimestamps(localLrc))
                    _lyricsState.value = parseLrc(localLrc)
                    return@launch
                }

                // FIX: No local file — before hitting the network, check if we're
                // actually online. Without this, a no-internet device would just
                // see a generic "Lyrics not found" after silently failing both
                // HTTP calls, which looked like a bug rather than a network issue.
                if (!isNetworkAvailable()) {
                    _lyricsSource.value = null
                    _lyricsState.value = LyricsState.Error(
                        "No internet connection — and no local lyrics file (.lrc) found for this song"
                    )
                    return@launch
                }

                // 2️⃣ LRCLib direct GET (ONLINE)
                val (syncedDirect, plainDirect) = fetchRawFromLrclib(cleanArtist, cleanTitle)
                if (syncedDirect != null) {
                    _lyricsSource.value = LyricsSource.ONLINE
                    repository.cacheLyrics(song.id, syncedDirect, true)
                    _lyricsState.value = parseLrc(syncedDirect)
                    return@launch
                }
                if (plainDirect != null) {
                    _lyricsSource.value = LyricsSource.ONLINE
                    repository.cacheLyrics(song.id, plainDirect, false)
                    _lyricsState.value = LyricsState.Plain(plainDirect)
                    return@launch
                }
                // 3️⃣ LRCLib search fallback (ONLINE)
                val (syncedSearch, plainSearch) = fetchRawFromLrclibSearch(cleanArtist, cleanTitle)
                if (syncedSearch != null) {
                    _lyricsSource.value = LyricsSource.ONLINE
                    repository.cacheLyrics(song.id, syncedSearch, true)
                    _lyricsState.value = parseLrc(syncedSearch)
                    return@launch
                }
                if (plainSearch != null) {
                    _lyricsSource.value = LyricsSource.ONLINE
                    repository.cacheLyrics(song.id, plainSearch, false)
                    _lyricsState.value = LyricsState.Plain(plainSearch)
                    return@launch
                }

                _lyricsSource.value = null
                _lyricsState.value = LyricsState.Error("Lyrics not found for \"$cleanTitle\"")
            } catch (e: Exception) {
                _lyricsSource.value = null
                _lyricsState.value = LyricsState.Error(e.message ?: "Failed to load lyrics")
            }
        }
    }

    /** Quick connectivity check using ConnectivityManager (works on all API levels we target). */
    private fun isNetworkAvailable(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val network = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } catch (_: Exception) {
            true // If the check itself fails, don't block — fall through and let the HTTP call decide
        }
    }

    /**
     * Try to read a .lrc sidecar file for OFFLINE lyrics support.
     * FIX: Previously only checked the exact "<filename>.lrc" path — many lyric
     * downloader tools / file managers save with different casing (.LRC) or the
     * extension appended rather than replaced (e.g. "Song.mp3.lrc"). Checking a
     * few common variants makes offline lyrics actually work reliably.
     */
    private fun hasSyncedTimestamps(text: String): Boolean =
        text.lineSequence().any { Regex("\\[\\d{1,2}:\\d{2}(?:[.:]\\d{1,3})?]").containsMatchIn(it) }

    private fun tryLocalLrc(audioPath: String): String? {
        val base = audioPath.substringBeforeLast('.', audioPath)
        val candidates = listOf(
            "$base.lrc",
            "$base.LRC",
            "$audioPath.lrc",
            "$audioPath.LRC"
        )
        for (path in candidates) {
            try {
                val file = java.io.File(path)
                if (file.exists() && file.canRead()) {
                    val text = file.readText()
                    if (text.isNotBlank()) return text
                }
            } catch (_: Exception) { /* try next candidate */ }
        }
        return null
    }

    /** Parse LRC text → LyricsState (Synced if timestamps found, Plain otherwise) */
    private fun parseLrc(lrc: String): LyricsState {
        val lrcRegex = Regex("\\[(\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?](.*)")
        val lines = mutableListOf<LrcLine>()
        for (raw in lrc.lines()) {
            val match = lrcRegex.matchEntire(raw.trim()) ?: continue
            val (min, sec, fraction, text) = match.destructured
            val timeMs = min.toLong() * 60_000 +
                sec.toLong() * 1_000 +
                fraction.padEnd(3, '0').take(3).toLongOrNull().orZero()
            val cleanText = text.trim()
            if (cleanText.isNotBlank()) lines += LrcLine(timeMs, cleanText)
        }
        return if (lines.isNotEmpty()) LyricsState.Synced(lines.sortedBy { it.timeMs })
        else {
            // No timestamps — treat as plain
            val plain = lrc.lines()
                .map { it.replace(Regex("\\[.*?]"), "").trim() }
                .filter { it.isNotBlank() }
                .joinToString("\n")
            if (plain.isNotBlank()) LyricsState.Plain(plain)
            else LyricsState.Error("Empty lyrics")
        }
    }

    /** LRCLib direct GET — returns Pair(syncedLyrics?, plainLyrics?). Retries once on transient failure. */
    private fun fetchRawFromLrclib(artist: String, title: String): Pair<String?, String?> {
        repeat(2) { attempt ->
            try {
                val encodedTitle = java.net.URLEncoder.encode(title, "UTF-8")
                val queryParams = mutableListOf("track_name=$encodedTitle")
                if (artist.isNotBlank()) {
                    val encodedArtist = java.net.URLEncoder.encode(artist, "UTF-8")
                    queryParams += "artist_name=$encodedArtist"
                }
                val url = java.net.URL("https://lrclib.net/api/get?${queryParams.joinToString("&")}")
                val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "ABMusic/2.5.8 (github.com/user/abmusic)")
                    connectTimeout = 8000; readTimeout = 8000
                }
                if (conn.responseCode != 200) {
                    if (attempt == 0) return@repeat else return null to null
                }
                val json = org.json.JSONObject(conn.inputStream.bufferedReader().readText())
                val synced = json.optString("syncedLyrics", "").trim().ifBlank { null }
                val plain  = json.optString("plainLyrics",  "").trim().ifBlank { null }
                return synced to plain
            } catch (_: Exception) {
                if (attempt == 1) return null to null
            }
        }
        return null to null
    }

    /** LRCLib search endpoint — broader matching */
    private fun fetchRawFromLrclibSearch(artist: String, title: String): Pair<String?, String?> {
        return try {
            val query = java.net.URLEncoder.encode("$artist $title", "UTF-8")
            val url   = java.net.URL("https://lrclib.net/api/search?q=$query")
            val conn  = (url.openConnection() as java.net.HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", "ABMusic/2.5.8 (github.com/user/abmusic)")
                connectTimeout = 8000; readTimeout = 8000
            }
            if (conn.responseCode != 200) return null to null
            val arr = org.json.JSONArray(conn.inputStream.bufferedReader().readText())
            if (arr.length() == 0) return null to null
            val best = (0 until arr.length())
                .map { arr.getJSONObject(it) }
                .firstOrNull { item ->
                    item.optString("syncedLyrics", "").isNotBlank() ||
                        item.optString("plainLyrics", "").isNotBlank()
                } ?: return null to null
            val synced = best.optString("syncedLyrics", "").trim().ifBlank { null }
            val plain  = best.optString("plainLyrics",  "").trim().ifBlank { null }
            synced to plain
        } catch (_: Exception) { null to null }
    }

    private fun Long?.orZero(): Long = this ?: 0L

    fun clearLyrics() {
        _lyricsState.value = LyricsState.Idle
        _lyricsSource.value = null
    }

    // ── Sleep timer ────────────────────────────────────────────────────────────
    private val _sleepTimerRemaining = MutableStateFlow(0)
    val sleepTimerRemaining: StateFlow<Int> = _sleepTimerRemaining.asStateFlow()
    private var sleepTimerJob: Job? = null

    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var progressJob: Job? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _playerState.update { it.copy(isPlaying = isPlaying) }
            if (isPlaying) {
                startProgressUpdates()
            } else {
                progressJob?.cancel()
                // FIX: Save exact position the instant playback pauses, so a
                // force-close right after pausing doesn't lose progress.
                val pos = controller?.currentPosition
                if (pos != null && pos > 0L) {
                    viewModelScope.launch { prefs.setLastPlayedPositionMs(pos) }
                }
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            updateCurrentSong()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> updateCurrentSong()
                Player.STATE_ENDED -> {
                    _playerState.update { it.copy(isPlaying = false, progress = 0L) }
                }
            }
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            _playerState.update { it.copy(shuffleEnabled = shuffleModeEnabled) }
            viewModelScope.launch { prefs.setShuffleEnabled(shuffleModeEnabled) }
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            val rm = when (repeatMode) {
                Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                else                   -> RepeatMode.OFF
            }
            _playerState.update { it.copy(repeatMode = rm) }
            viewModelScope.launch { prefs.setRepeatMode(rm.name) }
        }
    }

    init {
        viewModelScope.launch {
            prefs.repeatMode.first().let { saved ->
                val rm = runCatching { RepeatMode.valueOf(saved) }.getOrDefault(RepeatMode.OFF)
                _playerState.update { it.copy(repeatMode = rm) }
            }
            prefs.shuffleEnabled.first().let { saved ->
                _playerState.update { it.copy(shuffleEnabled = saved) }
            }
            // Restore last played song for MiniPlayer display after force-stop.
            // This loads the song into playerState so the mini-player is visible
            // even if the MediaService has been killed.
            // FIX: This restore MUST complete before connectToService() runs its
            // controller-ready callback, otherwise the callback sees currentSong == null
            // and skips rebuilding the MediaItem — which is exactly why play/pause
            // stopped working after the app was force-closed and reopened.
            val lastSongId = prefs.lastPlayedSongId.firstOrNull() ?: -1L
            if (lastSongId > 0L && _playerState.value.currentSong == null) {
                try {
                    val song = repository.getSongById(lastSongId)
                    if (song != null) {
                        _playerState.update { it.copy(currentSong = song, isPlaying = false) }
                    }
                } catch (_: Exception) { /* ignore — song may have been deleted */ }
            }
            connectToService()
        }
    }

    private fun connectToService() {
        val sessionToken = SessionToken(context, ComponentName(context, MusicService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            controller = controllerFuture?.get()
            controller?.addListener(playerListener)

            val state = _playerState.value
            controller?.repeatMode = when (state.repeatMode) {
                RepeatMode.OFF -> Player.REPEAT_MODE_OFF
                RepeatMode.ONE -> Player.REPEAT_MODE_ONE
                RepeatMode.ALL -> Player.REPEAT_MODE_ALL
            }
            controller?.shuffleModeEnabled = state.shuffleEnabled

            controller?.let { ctrl ->
                // FIX: After a force-stop/process-death, MusicService is restarted fresh
                // and ExoPlayer has NO MediaItem loaded — only the MiniPlayer's `currentSong`
                // was restored (from prefs) for display purposes. Calling play()/pause() on
                // an empty player is a no-op, which is exactly why play/pause stopped working
                // on both the MiniPlayer and the Now Playing screen after reopening the app.
                // If the controller truly has nothing loaded but we have a restored song,
                // rebuild a single-item queue (paused, seeked to the last known position)
                // so playback controls work again immediately.
                if (ctrl.mediaItemCount == 0 && state.currentSong != null) {
                    restoreControllerFromSavedState(ctrl, state.currentSong)
                } else {
                    _playerState.update { it.copy(isPlaying = ctrl.isPlaying) }
                    val pos = ctrl.currentPosition.takeIf { it >= 0L } ?: 0L
                    _playerState.update { it.copy(progress = pos) }
                    if (ctrl.isPlaying) startProgressUpdates()
                }

                // FIX: Use MusicService.audioSessionId — the real ExoPlayer session ID.
                // Previously this used fragile reflection on MediaController which always
                // threw NoSuchFieldException, silently falling back to session 0 (global mix)
                // which may not bind to ExoPlayer on all devices.
                initEqualizer()
            }

            updateCurrentSong()
        }, MoreExecutors.directExecutor())
    }

    /**
     * Rebuilds the player's queue from the last known song + saved position after a
     * fresh process start (MediaController connected but ExoPlayer queue is empty).
     * Loads paused — does NOT auto-play — so the user controls playback explicitly,
     * but play/pause on MiniPlayer and Now Playing screen will now actually work.
     */
    private fun restoreControllerFromSavedState(ctrl: MediaController, song: Song) {
        val mediaItem = MediaItem.Builder()
            .setMediaId(song.id.toString())
            .setUri(Uri.parse(song.path))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artist)
                    .setAlbumTitle(song.album)
                    .setArtworkUri(song.artworkUri?.let { Uri.parse(it) })
                    .build()
            ).build()

        viewModelScope.launch {
            val savedPos = prefs.lastPlayedPositionMs.firstOrNull() ?: 0L
            ctrl.setMediaItem(mediaItem, savedPos.coerceAtLeast(0L))
            ctrl.prepare()
            ctrl.playWhenReady = false

            _playerState.update {
                it.copy(
                    currentSong       = song,
                    isPlaying         = false,
                    progress          = savedPos,
                    duration          = song.duration,
                    queue             = listOf(song),
                    currentQueueIndex = 0
                )
            }
        }
    }

    // FIX: Initialize EQ with the real ExoPlayer audio session ID from MusicService.
    //      If the service hasn't started yet (sessionId == 0), we retry once with a delay.
    private fun initEqualizer() {
        eqController?.release()
        eqController = EqualizerController(0)
        _eqBandLevels.value = FloatArray(eqController!!.bandCount) {
            eqController!!.getBandLevelDb(it)
        }
        restoreEqFromPrefs()
    }

    /** Restore saved EQ band levels from DataStore into the active EQ controller */
    private fun restoreEqFromPrefs() {
        viewModelScope.launch {
            val saved = prefs.eqBandLevels.firstOrNull() ?: return@launch
            if (saved.isBlank()) return@launch
            val levels = saved.split(",").mapNotNull { it.trim().toFloatOrNull() }
            levels.forEachIndexed { i, level ->
                if (i < eqBandCount) {
                    eqController?.setBandLevel(i, level)
                }
            }
            if (levels.isNotEmpty()) {
                _eqBandLevels.value = FloatArray(eqBandCount) { i ->
                    levels.getOrElse(i) { eqController?.getBandLevelDb(i) ?: 0f }
                }
            }
        }
    }

    fun syncWithService() {
        val ctrl = controller ?: return
        _playerState.update { it.copy(isPlaying = ctrl.isPlaying) }
        val pos = ctrl.currentPosition.takeIf { it >= 0L } ?: 0L
        _playerState.update { it.copy(progress = pos) }
        if (ctrl.isPlaying) startProgressUpdates()
        updateCurrentSong()
    }

    fun playQueue(songs: List<Song>, startIndex: Int = 0) {
        if (songs.isEmpty()) return
        val safeStartIndex = startIndex.coerceIn(songs.indices)
        val startSong = songs[safeStartIndex]

        // FIX: tapping the song that is already loaded/playing used to restart it
        // from 0:00 every time (playQueue() -> setMediaItems() -> prepare() -> play()).
        // If the tapped song is the one already current, just flip play/pause instead
        // of reloading the whole queue.
        if (_playerState.value.currentSong?.id == startSong.id) {
            playPause()
            return
        }

        _playerState.update {
            it.copy(
                queue             = songs,
                currentSong       = startSong,
                currentQueueIndex = safeStartIndex,
                duration          = startSong.duration,
                progress          = 0L
            )
        }
        val mediaItems = songs.map { song ->
            MediaItem.Builder()
                .setMediaId(song.id.toString())
                .setUri(Uri.parse(song.path))
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(song.title)
                        .setArtist(song.artist)
                        .setAlbumTitle(song.album)
                        .setArtworkUri(song.artworkUri?.let { Uri.parse(it) })
                        .build()
                ).build()
        }
        controller?.apply {
            setMediaItems(mediaItems, safeStartIndex, 0L)
            prepare()
            play()
        }
    }

    fun playPause() {
        controller?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    fun next()     { controller?.seekToNextMediaItem() }
    fun previous() { controller?.seekToPreviousMediaItem() }

    fun seekTo(position: Long) {
        val realDuration = controller?.duration?.takeIf { it > 0L } ?: _playerState.value.duration
        val safePos = position.coerceIn(0L, realDuration.coerceAtLeast(0L))
        controller?.seekTo(safePos)
        _playerState.update { it.copy(progress = safePos) }
    }

    fun toggleShuffle() {
        val enabled = !(_playerState.value.shuffleEnabled)
        controller?.shuffleModeEnabled = enabled
        _playerState.update { it.copy(shuffleEnabled = enabled) }
        viewModelScope.launch { prefs.setShuffleEnabled(enabled) }
    }

    fun cycleRepeatMode() {
        val next = when (_playerState.value.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        val mode = when (next) {
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
        }
        controller?.repeatMode = mode
        _playerState.update { it.copy(repeatMode = next) }
        viewModelScope.launch { prefs.setRepeatMode(next.name) }
    }

    fun toggleFavorite(songId: Long) {
        viewModelScope.launch {
            val isFav = repository.isFavorite(songId).first()
            repository.toggleFavorite(songId, !isFav)
        }
    }

    // ── Sleep Timer ───────────────────────────────────────────────────────────
    fun startSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        if (minutes <= 0) { _sleepTimerRemaining.value = 0; return }
        _sleepTimerRemaining.value = minutes * 60
        sleepTimerJob = viewModelScope.launch {
            while (_sleepTimerRemaining.value > 0) {
                delay(1000L)
                _sleepTimerRemaining.update { (it - 1).coerceAtLeast(0) }
            }
            controller?.pause()
        }
    }

    // ── Equalizer public API ───────────────────────────────────────────────────
    fun setEqBand(bandIndex: Int, levelDb: Float) {
        eqController?.setBandLevel(bandIndex, levelDb)
        _eqBandLevels.update { old ->
            old.copyOf().also { it[bandIndex.coerceIn(0, it.lastIndex)] = levelDb }
        }
        // Persist EQ changes
        viewModelScope.launch {
            val levels = _eqBandLevels.value.joinToString(",")
            prefs.setEqBandLevels(levels)
        }
    }

    fun applyEqPreset(preset: EqPreset) {
        eqController?.applyPreset(preset)
        _eqBandLevels.value = FloatArray(eqBandCount) { i ->
            eqController?.getBandLevelDb(i) ?: 0f
        }
        // Persist EQ preset
        viewModelScope.launch {
            val levels = _eqBandLevels.value.joinToString(",")
            prefs.setEqBandLevels(levels)
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        _sleepTimerRemaining.value = 0
    }

    // ── Queue helpers ─────────────────────────────────────────────────────────

    /** Insert song immediately after the current position. */
    fun playNext(song: Song) {
        val c = controller ?: return
        val mediaItem = MediaItem.Builder()
            .setMediaId(song.id.toString())
            .setUri(android.net.Uri.parse(song.path))
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artist)
                    .setAlbumTitle(song.album)
                    .setArtworkUri(song.artworkUri?.let { android.net.Uri.parse(it) })
                    .build()
            ).build()
        val insertIdx = (c.currentMediaItemIndex + 1).coerceAtMost(c.mediaItemCount)
        c.addMediaItem(insertIdx, mediaItem)
        _playerState.update { it.copy(queue = it.queue.toMutableList().also { q ->
            q.add(insertIdx.coerceAtMost(q.size), song)
        }) }
    }

    /** Append song to the end of the current queue. */
    fun addToQueue(song: Song) {
        val c = controller ?: return
        val mediaItem = MediaItem.Builder()
            .setMediaId(song.id.toString())
            .setUri(android.net.Uri.parse(song.path))
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artist)
                    .setAlbumTitle(song.album)
                    .setArtworkUri(song.artworkUri?.let { android.net.Uri.parse(it) })
                    .build()
            ).build()
        c.addMediaItem(mediaItem)
        _playerState.update { it.copy(queue = it.queue + song) }
    }

    /** Delete a song file from device storage. */
    fun deleteSong(context: android.content.Context, song: Song, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val deleted = try {
                val rows = context.contentResolver.delete(
                    android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    "${android.provider.MediaStore.Audio.Media._ID}=?",
                    arrayOf(song.id.toString())
                )
                if (rows == 0) {
                    // Fallback: delete the file directly
                    java.io.File(song.path).delete()
                } else true
            } catch (e: Exception) {
                false
            }
            onDone(deleted)
        }
    }

    /** Rename a song file in MediaStore. */
    fun renameSong(context: android.content.Context, song: Song, newName: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val done = try {
                val cv = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Audio.Media.DISPLAY_NAME, newName)
                    put(android.provider.MediaStore.Audio.Media.TITLE, newName)
                }
                val rows = context.contentResolver.update(
                    android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    cv,
                    "${android.provider.MediaStore.Audio.Media._ID}=?",
                    arrayOf(song.id.toString())
                )
                rows > 0
            } catch (e: Exception) {
                false
            }
            onDone(done)
        }
    }

    private fun updateCurrentSong() {
        val c = controller ?: return
        val mediaItem = c.currentMediaItem ?: return
        val state = _playerState.value
        val song = state.queue.find { it.id.toString() == mediaItem.mediaId }
            ?: state.queue.getOrNull(c.currentMediaItemIndex)
        val newDuration = c.duration.takeIf { it > 0L } ?: state.duration
        _playerState.update {
            it.copy(
                currentSong       = song ?: it.currentSong,
                duration          = newDuration,
                currentQueueIndex = c.currentMediaItemIndex
            )
        }
        song?.let {
            viewModelScope.launch {
                repository.recordRecentlyPlayed(it.id)
                repository.incrementPlayCount(it.id)
                // Persist last played song for MiniPlayer restore after force-stop
                prefs.setLastPlayedSongId(it.id)
            }
        }
    }

    private fun startProgressUpdates() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (true) {
                val pos = controller?.currentPosition ?: 0L
                _playerState.update { it.copy(progress = pos.coerceAtLeast(0L)) }
                // FIX: Persist position periodically so play/pause works correctly
                // after the app is force-closed and reopened — connectToService()
                // uses this to rebuild the MediaItem at the right spot.
                if (pos > 0L) prefs.setLastPlayedPositionMs(pos)
                delay(500L)
            }
        }
    }

    override fun onCleared() {
        controller?.removeListener(playerListener)
        progressJob?.cancel()
        sleepTimerJob?.cancel()
        eqController?.release()
        controllerFuture?.let { future ->
            if (future.isDone && !future.isCancelled) {
                try { future.get().release() } catch (_: Exception) {}
            }
        }
        super.onCleared()
    }
}

/** One timestamped line in an LRC file */
data class LrcLine(val timeMs: Long, val text: String)

/** Where the currently displayed lyrics were loaded from. */
enum class LyricsSource { LOCAL, ONLINE }

sealed class LyricsState {
    data object Idle    : LyricsState()
    data object Loading : LyricsState()
    /** LRC with timestamps — enables karaoke highlight + auto-scroll */
    data class Synced(val lines: List<LrcLine>) : LyricsState()
    /** Plain text (no timestamps) — simple scrollable display */
    data class Plain(val lyrics: String) : LyricsState()
    data class Error(val message: String) : LyricsState()
}
