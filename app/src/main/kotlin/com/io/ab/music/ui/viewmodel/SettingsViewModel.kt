package com.io.ab.music.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.Coil
import com.io.ab.music.data.preferences.UserPreferences
import com.io.ab.music.data.repository.MusicRepository
import com.io.ab.music.ui.theme.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import androidx.compose.runtime.Stable
import javax.inject.Inject

@Stable
data class UpdateInfo(
    val versionName  : String       = "",
    val versionCode  : Int          = 0,
    val releaseNotes : String       = "",
    val changelog    : List<String> = emptyList(),
    val apkUrl       : String       = "",
    val forceUpdate  : Boolean      = false
)

@Stable
data class SettingsUiState(
    val themeMode            : ThemeMode  = ThemeMode.SYSTEM,
    val dynamicColor         : Boolean    = true,
    val isListView           : Boolean    = true,
    val audioFocus           : Boolean    = true,
    val sleepTimerSeconds    : Int        = 0,
    val bassBoostEnabled     : Boolean    = false,
    val skipSilence          : Boolean    = false,
    val includeSubfolders    : Boolean    = true,
    val playbackNotification : Boolean    = true,
    val showThemeDialog      : Boolean    = false,
    val showSleepTimerDialog : Boolean    = false,
    val showAccentDialog     : Boolean    = false,
    val showEqDialog         : Boolean    = false,
    val isRescanning         : Boolean    = false,
    val isClearingCache      : Boolean    = false,
    val snackbarMessage      : String?    = null,
    val updateInfo           : UpdateInfo? = null,
    val isCheckingUpdate     : Boolean    = false,
    val updateChecked        : Boolean    = false,
    // Manual wallpaper theme
    val wallpaperEnabled     : Boolean    = false,
    val wallpaperPath        : String     = "",
    val wallpaperBlur        : Float      = 18f,
    val wallpaperDim         : Float      = 0.30f,
    val showWallpaperDialog  : Boolean    = false,
    // Dog pet companion
    val petEnabled           : Boolean    = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val prefs     : UserPreferences,
    private val repository: MusicRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private var sleepTimerJob: Job? = null

    init {
        loadPersistedPreferences()
        restoreSleepTimerFromPrefs()
        checkForUpdate()  // Auto-check on first load (silent, no force)
    }

    // ── Restore sleep timer from DataStore ────────────────────────────────────
    private fun restoreSleepTimerFromPrefs() {
        viewModelScope.launch {
            val endMs = prefs.sleepTimerEndMs.first()
            if (endMs <= 0L) return@launch
            val remaining = ((endMs - System.currentTimeMillis()) / 1000L).toInt()
            if (remaining <= 0) {
                // Timer already expired while app was closed
                prefs.setSleepTimerEndMs(0L)
                return@launch
            }
            // Restore UI state and restart countdown
            _uiState.update { it.copy(sleepTimerSeconds = remaining) }
            startCountdown(remaining)
        }
    }

    // ── Load all persisted prefs into UI state ────────────────────────────────
    private fun loadPersistedPreferences() {
        viewModelScope.launch {
            combine(
                prefs.themeMode,
                prefs.dynamicColor,
                prefs.isListView,
                prefs.audioFocus,
                prefs.playbackNotification
            ) { theme, dynamic, list, focus, playbackNotif ->
                val mode = runCatching { ThemeMode.valueOf(theme.uppercase()) }
                    .getOrDefault(ThemeMode.SYSTEM)
                _uiState.update {
                    it.copy(
                        themeMode            = mode,
                        dynamicColor         = dynamic,
                        isListView           = list,
                        audioFocus           = focus,
                        playbackNotification = playbackNotif
                    )
                }
            }.collect()
        }
        viewModelScope.launch {
            prefs.includeSubfolders.collect { include ->
                _uiState.update { it.copy(includeSubfolders = include) }
            }
        }
        viewModelScope.launch {
            combine(
                prefs.wallpaperEnabled,
                prefs.wallpaperPath
            ) { enabled, path ->
                _uiState.update {
                    it.copy(
                        wallpaperEnabled = enabled,
                        wallpaperPath    = path
                    )
                }
            }.collect()
        }
        // FIX: blur/dim seekbar glitch — this used to also live-collect
        // prefs.wallpaperBlur/wallpaperDim continuously and copy them into
        // uiState, same as the fields above. But setWallpaperBlur/setWallpaperDim
        // ALSO update uiState optimistically and only persist to disk after a
        // 150ms debounce — so while the user was still dragging, the delayed
        // disk write would land, DataStore would re-emit, and this collector
        // would shove that now-stale value back into uiState, making the
        // slider visibly jump backwards mid-drag. Blur/dim are only ever
        // changed from this dialog, so we only need to read the persisted
        // value ONCE on startup to seed uiState — never again afterwards.
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    wallpaperBlur = prefs.wallpaperBlur.first(),
                    wallpaperDim  = prefs.wallpaperDim.first()
                )
            }
        }
        viewModelScope.launch {
            prefs.petEnabled.collect { enabled ->
                _uiState.update { it.copy(petEnabled = enabled) }
            }
        }
    }

    // ── Pet Companion ─────────────────────────────────────────────────────────
    fun setPetEnabled(enabled: Boolean) {
        viewModelScope.launch { prefs.setPetEnabled(enabled) }
        _uiState.update { it.copy(petEnabled = enabled) }
    }

    // ── Theme / Appearance ────────────────────────────────────────────────────
    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            prefs.setThemeMode(mode.name)
            _uiState.update { it.copy(themeMode = mode, showThemeDialog = false) }
        }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { prefs.setDynamicColor(enabled) }
        _uiState.update { it.copy(dynamicColor = enabled) }
    }

    fun setListView(isList: Boolean) {
        viewModelScope.launch { prefs.setListView(isList) }
        _uiState.update { it.copy(isListView = isList) }
    }

    // ── Playback ──────────────────────────────────────────────────────────────
    fun setAudioFocus(enabled: Boolean) {
        viewModelScope.launch { prefs.setAudioFocus(enabled) }
        _uiState.update { it.copy(audioFocus = enabled) }
    }

    /**
     * Start (or replace) the sleep timer.
     * @param minutes 0 = turn off, >0 = countdown starts immediately.
     */
    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        sleepTimerJob = null

        if (minutes <= 0) {
            viewModelScope.launch { prefs.setSleepTimerEndMs(0L) }
            _uiState.update { it.copy(sleepTimerSeconds = 0, showSleepTimerDialog = false) }
            return
        }

        val totalSeconds = minutes * 60
        val endMs = System.currentTimeMillis() + totalSeconds * 1000L
        // Persist end time so timer survives app restarts
        viewModelScope.launch { prefs.setSleepTimerEndMs(endMs) }
        _uiState.update { it.copy(sleepTimerSeconds = totalSeconds, showSleepTimerDialog = false) }
        startCountdown(totalSeconds)
    }

    private fun startCountdown(initialSeconds: Int) {
        sleepTimerJob?.cancel()
        sleepTimerJob = viewModelScope.launch {
            var remaining = initialSeconds
            while (remaining > 0) {
                delay(1_000L)
                remaining--
                _uiState.update { it.copy(sleepTimerSeconds = remaining) }
            }
            // Timer expired
            prefs.setSleepTimerEndMs(0L)
            _uiState.update {
                it.copy(
                    sleepTimerSeconds = 0,
                    snackbarMessage   = "Sleep timer ended — playback stopped"
                )
            }
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        viewModelScope.launch { prefs.setSleepTimerEndMs(0L) }
        _uiState.update { it.copy(sleepTimerSeconds = 0) }
    }



    fun setBassBoost(enabled: Boolean) {
        _uiState.update { it.copy(bassBoostEnabled = enabled) }
    }

    fun setSkipSilence(enabled: Boolean) {
        _uiState.update { it.copy(skipSilence = enabled) }
    }

    // ── Library ───────────────────────────────────────────────────────────────
    fun setIncludeSubfolders(enabled: Boolean) {
        viewModelScope.launch { prefs.setIncludeSubfolders(enabled) }
        _uiState.update { it.copy(includeSubfolders = enabled) }
    }

    fun rescanLibrary() {
        if (_uiState.value.isRescanning) return
        _uiState.update { it.copy(isRescanning = true) }
        viewModelScope.launch {
            try {
                repository.scanAndSaveMusic(_uiState.value.includeSubfolders)
                _uiState.update {
                    it.copy(isRescanning = false, snackbarMessage = "Library scan complete ✓")
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isRescanning = false, snackbarMessage = "Scan failed: ${e.message}")
                }
            }
        }
    }

    // ── Notifications ─────────────────────────────────────────────────────────
    // FIX: this toggle was only ever written to in-memory UI state — never
    // persisted, and MusicService never read it — so turning it off did
    // nothing and it reset to "on" every app restart. Now it's saved to
    // DataStore and MusicService observes it to actually show/hide the
    // playback notification.
    fun setPlaybackNotification(enabled: Boolean) {
        viewModelScope.launch { prefs.setPlaybackNotification(enabled) }
        _uiState.update { it.copy(playbackNotification = enabled) }
    }

    // ── Cache ─────────────────────────────────────────────────────────────────
    fun clearCache() {
        if (_uiState.value.isClearingCache) return
        _uiState.update { it.copy(isClearingCache = true) }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    // Coil 2.x: clear memory + disk cache
                    val imageLoader = Coil.imageLoader(appContext)
                    imageLoader.memoryCache?.clear()
                    imageLoader.diskCache?.clear()
                } catch (_: Exception) {}
                // Wipe app cache directory (covers thumbnail and other caches)
                try {
                    appContext.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
                } catch (_: Exception) {}
                // Wipe external cache if available
                try {
                    appContext.externalCacheDir?.listFiles()?.forEach { it.deleteRecursively() }
                } catch (_: Exception) {}
            }
            _uiState.update {
                it.copy(isClearingCache = false, snackbarMessage = "Cache cleared ✓")
            }
        }
    }

    // ── Manual Wallpaper Theme ────────────────────────────────────────────────
    /**
     * Copies the picked image (from the system Photo Picker) into app-private
     * storage so it survives app restarts without needing persistable URI
     * permissions, then enables the wallpaper and stores its local path.
     */
    fun setWallpaperImage(uri: android.net.Uri) {
        viewModelScope.launch {
            val savedPath = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = java.io.File(appContext.filesDir, "wallpaper").apply { mkdirs() }
                    // FIX: previously always wrote to the same fixed filename
                    // ("manual_wallpaper.jpg"), so Coil's memory/disk cache key
                    // ("wallpaper:$imagePath") never changed even when the user
                    // picked a NEW image — the old cached bitmap kept showing.
                    // Using a unique, timestamped filename per pick forces Coil
                    // to treat it as a brand-new image and reload it.
                    val outFile = java.io.File(dir, "manual_wallpaper_${System.currentTimeMillis()}.jpg")
                    appContext.contentResolver.openInputStream(uri)?.use { input ->
                        outFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    // Clean up any older wallpaper files so app-private storage
                    // doesn't accumulate stale copies over time.
                    dir.listFiles()?.forEach { f ->
                        if (f.absolutePath != outFile.absolutePath && f.name.startsWith("manual_wallpaper")) {
                            f.delete()
                        }
                    }
                    outFile.absolutePath
                }.getOrNull()
            }
            if (savedPath != null) {
                prefs.setWallpaperPath(savedPath)
                prefs.setWallpaperEnabled(true)
                _uiState.update {
                    it.copy(
                        wallpaperPath    = savedPath,
                        wallpaperEnabled = true,
                        snackbarMessage  = "Wallpaper applied ✓"
                    )
                }
            } else {
                _uiState.update { it.copy(snackbarMessage = "Couldn't load that image") }
            }
        }
    }

    fun setWallpaperEnabled(enabled: Boolean) {
        if (enabled && _uiState.value.wallpaperPath.isBlank()) {
            // Nothing picked yet — surface the picker instead of silently no-op'ing
            _uiState.update { it.copy(snackbarMessage = "Choose an image first") }
            return
        }
        viewModelScope.launch { prefs.setWallpaperEnabled(enabled) }
        _uiState.update { it.copy(wallpaperEnabled = enabled) }
    }

    // PERF FIX: Slider.onValueChange fires 30-60x/sec while dragging. Writing to
    // DataStore (disk I/O + serialization) on every single tick caused visible
    // stutter while adjusting the wallpaper blur/dim sliders. The UI state below
    // still updates instantly for a responsive live preview; only the disk write
    // is debounced so it lands once, shortly after the finger stops moving.
    private var wallpaperBlurSaveJob: Job? = null
    private var wallpaperDimSaveJob: Job? = null

    fun setWallpaperBlur(blur: Float) {
        _uiState.update { it.copy(wallpaperBlur = blur) }
        wallpaperBlurSaveJob?.cancel()
        wallpaperBlurSaveJob = viewModelScope.launch {
            delay(150)
            prefs.setWallpaperBlur(blur)
        }
    }

    fun setWallpaperDim(dim: Float) {
        _uiState.update { it.copy(wallpaperDim = dim) }
        wallpaperDimSaveJob?.cancel()
        wallpaperDimSaveJob = viewModelScope.launch {
            delay(150)
            prefs.setWallpaperDim(dim)
        }
    }

    fun clearWallpaper() {
        viewModelScope.launch {
            prefs.setWallpaperEnabled(false)
            prefs.setWallpaperPath("")
            withContext(Dispatchers.IO) {
                runCatching {
                    java.io.File(appContext.filesDir, "wallpaper").deleteRecursively()
                }
            }
        }
        _uiState.update { it.copy(wallpaperEnabled = false, wallpaperPath = "", showWallpaperDialog = false) }
    }

    fun showWallpaperDialog()  = _uiState.update { it.copy(showWallpaperDialog = true) }

    fun onSnackbarShown() = _uiState.update { it.copy(snackbarMessage = null) }

    // ── Dialog helpers ────────────────────────────────────────────────────────
    fun showThemeDialog()      = _uiState.update { it.copy(showThemeDialog = true) }
    fun showSleepTimerDialog() = _uiState.update { it.copy(showSleepTimerDialog = true) }
    fun showAccentDialog()     = _uiState.update { it.copy(showAccentDialog = true) }
    fun showEqDialog()         = _uiState.update { it.copy(showEqDialog = true) }
    fun showDefaultTabDialog() {}

    fun dismissDialog() = _uiState.update {
        it.copy(
            showThemeDialog      = false,
            showSleepTimerDialog = false,
            showAccentDialog     = false,
            showEqDialog         = false,
            showWallpaperDialog  = false
        )
    }

    override fun onCleared() {
        super.onCleared()
        sleepTimerJob?.cancel()
    }

    // ── Update Checker ────────────────────────────────────────────────────────
    private val UPDATE_JSON_URL      = "https://raw.githubusercontent.com/Sandeepbedia/AB-Player/refs/heads/main/update.json"
    private val CURRENT_VERSION_CODE = 422  // Must match versionCode in app/build.gradle
    private var initialCheckDone = false

    fun checkForUpdate(force: Boolean = false, onUpdateFound: ((UpdateInfo) -> Unit)? = null) {
        if (!force && initialCheckDone) return
        if (_uiState.value.isCheckingUpdate) return
        _uiState.update { it.copy(isCheckingUpdate = true, updateChecked = false, updateInfo = null) }
        viewModelScope.launch {
            try {
                val json = withContext(Dispatchers.IO) { URL(UPDATE_JSON_URL).readText() }
                val obj          = JSONObject(json)
                val remoteCode   = obj.optInt("version_code", 0)
                val remoteName   = obj.optString("version_name", "")
                val releaseNotes = obj.optString("release_notes", "")
                val apkUrl       = obj.optString("apk_url", "")
                val forceUpdate  = obj.optBoolean("force_update", false)
                val changelog: List<String> = runCatching {
                    val arr = obj.optJSONArray("changelog")
                    if (arr != null && arr.length() > 0)
                        List(arr.length()) { arr.getString(it) }
                    else
                        releaseNotes.split("•", "\n", ",").map { it.trim() }.filter { it.isNotBlank() }
                }.getOrDefault(emptyList())

                initialCheckDone = true
                if (remoteCode > CURRENT_VERSION_CODE && apkUrl.isNotBlank()) {
                    val info = UpdateInfo(remoteName, remoteCode, releaseNotes, changelog, apkUrl, forceUpdate)
                    _uiState.update {
                        it.copy(
                            isCheckingUpdate = false,
                            updateChecked    = true,
                            updateInfo       = info
                        )
                    }
                    onUpdateFound?.invoke(info)
                } else {
                    _uiState.update { it.copy(isCheckingUpdate = false, updateChecked = true, updateInfo = null) }
                }
            } catch (e: Exception) {
                initialCheckDone = true
                _uiState.update { it.copy(isCheckingUpdate = false, updateChecked = true, updateInfo = null) }
            }
        }
    }
}
