package com.io.ab.music.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ab_music_prefs")

@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        val THEME_MODE           = stringPreferencesKey("theme_mode")
        val ACCENT_COLOR         = stringPreferencesKey("accent_color")
        val DYNAMIC_COLOR        = booleanPreferencesKey("dynamic_color")
        val SHUFFLE_ENABLED      = booleanPreferencesKey("shuffle_enabled")
        val REPEAT_MODE          = stringPreferencesKey("repeat_mode")
        val SLEEP_TIMER_MINUTES  = intPreferencesKey("sleep_timer_minutes")
        val SLEEP_TIMER_END_MS   = longPreferencesKey("sleep_timer_end_ms")
        val LIST_VIEW            = booleanPreferencesKey("list_view")
        val DEFAULT_TAB          = intPreferencesKey("default_tab")
        val AUDIO_FOCUS          = booleanPreferencesKey("audio_focus")
        val SKIP_SILENCE         = booleanPreferencesKey("skip_silence")
        val HOME_SORT_ORDER      = stringPreferencesKey("home_sort_order")
        val IS_PREMIUM           = booleanPreferencesKey("is_premium")
        val EQ_BAND_LEVELS       = stringPreferencesKey("eq_band_levels")
        val PLAYBACK_NOTIFICATION = booleanPreferencesKey("playback_notification")
        val INCLUDE_SUBFOLDERS   = booleanPreferencesKey("include_subfolders")
        val LIBRARY_SORT_ORDER   = stringPreferencesKey("library_sort_order")
        // Video prefs
        val VIDEO_SORT_ORDER          = stringPreferencesKey("video_sort_order")
        val VIDEO_VIEW_TYPE           = stringPreferencesKey("video_view_type")
        val VIDEO_RECENTLY_PLAYED     = stringPreferencesKey("video_recently_played_ids")
        // Video last position resume (videoId -> positionMs as CSV "id:pos,id:pos")
        val VIDEO_LAST_POSITIONS      = stringPreferencesKey("video_last_positions")
        // Video player action button state persistence
        val VIDEO_SCALE_MODE          = stringPreferencesKey("video_scale_mode")
        val VIDEO_PLAYBACK_SPEED      = floatPreferencesKey("video_playback_speed")
        val VIDEO_LOOP_ENABLED        = booleanPreferencesKey("video_loop_enabled")
        val VIDEO_ROTATION_MODE       = stringPreferencesKey("video_rotation_mode")
        val VIDEO_AUDIO_TRACK_GROUP   = intPreferencesKey("video_audio_track_group")
        val VIDEO_SUBTITLE_TRACK_GROUP= intPreferencesKey("video_subtitle_track_group")
        // Last played song — persisted so MiniPlayer can show it after force-stop
        val LAST_PLAYED_SONG_ID       = longPreferencesKey("last_played_song_id")
        // FIX: Persist last playback position so play/pause works correctly after
        // the app process is killed and reopened (MediaController has no MediaItem
        // loaded until we rebuild it from this saved position).
        val LAST_PLAYED_POSITION_MS   = longPreferencesKey("last_played_position_ms")
        // Manual wallpaper theme — custom background image behind transparent bars
        val WALLPAPER_ENABLED         = booleanPreferencesKey("wallpaper_enabled")
        val WALLPAPER_PATH            = stringPreferencesKey("wallpaper_path")
        val WALLPAPER_BLUR            = floatPreferencesKey("wallpaper_blur")
        val WALLPAPER_DIM             = floatPreferencesKey("wallpaper_dim")
        // Dog pet companion
        val PET_ENABLED               = booleanPreferencesKey("pet_enabled")
        // Landscape mode bottom mini player toggle
        val LANDSCAPE_MINI_PLAYER_ENABLED = booleanPreferencesKey("landscape_mini_player_enabled")
    }

    val themeMode:        Flow<String>  = context.dataStore.data.map { it[THEME_MODE]          ?: "SYSTEM" }
    val accentColor:      Flow<String>  = context.dataStore.data.map { it[ACCENT_COLOR]         ?: "" }
    val dynamicColor:     Flow<Boolean> = context.dataStore.data.map { it[DYNAMIC_COLOR]        ?: true }
    val isListView:       Flow<Boolean> = context.dataStore.data.map { it[LIST_VIEW]            ?: true }
    val defaultTab:       Flow<Int>     = context.dataStore.data.map { it[DEFAULT_TAB]          ?: 0 }
    val audioFocus:       Flow<Boolean> = context.dataStore.data.map { it[AUDIO_FOCUS]          ?: true }
    val repeatMode:       Flow<String>  = context.dataStore.data.map { it[REPEAT_MODE]          ?: "OFF" }
    val shuffleEnabled:   Flow<Boolean> = context.dataStore.data.map { it[SHUFFLE_ENABLED]      ?: false }
    val homeSortOrder:    Flow<String>  = context.dataStore.data.map { it[HOME_SORT_ORDER]      ?: "DATE_ADDED" }
    val isPremium:        Flow<Boolean> = context.dataStore.data.map { it[IS_PREMIUM]           ?: false }
    val eqBandLevels:     Flow<String>  = context.dataStore.data.map { it[EQ_BAND_LEVELS]       ?: "" }
    val playbackNotification: Flow<Boolean> = context.dataStore.data.map { it[PLAYBACK_NOTIFICATION] ?: true }
    val includeSubfolders:Flow<Boolean> = context.dataStore.data.map { it[INCLUDE_SUBFOLDERS]   ?: true }
    val librarySortOrder: Flow<String>  = context.dataStore.data.map { it[LIBRARY_SORT_ORDER]   ?: "NAME" }
    val sleepTimerEndMs:  Flow<Long>    = context.dataStore.data.map { it[SLEEP_TIMER_END_MS]   ?: 0L }
    // Video
    val videoSortOrder:   Flow<String>  = context.dataStore.data.map { it[VIDEO_SORT_ORDER]     ?: "DATE_RECENT" }
    val videoViewType:    Flow<String>  = context.dataStore.data.map { it[VIDEO_VIEW_TYPE]      ?: "LIST" }
    val videoRecentlyPlayedIds: Flow<String> = context.dataStore.data.map { it[VIDEO_RECENTLY_PLAYED] ?: "" }
    val videoLastPositions: Flow<String> = context.dataStore.data.map { it[VIDEO_LAST_POSITIONS] ?: "" }
    // Video player action button states
    val videoScaleMode:    Flow<String>  = context.dataStore.data.map { it[VIDEO_SCALE_MODE]      ?: "FIT" }
    val videoPlaybackSpeed:Flow<Float>   = context.dataStore.data.map { it[VIDEO_PLAYBACK_SPEED]  ?: 1f }
    val videoLoopEnabled:  Flow<Boolean> = context.dataStore.data.map { it[VIDEO_LOOP_ENABLED]    ?: false }
    val videoRotationMode: Flow<String>  = context.dataStore.data.map { it[VIDEO_ROTATION_MODE]   ?: "AUTO" }
    val videoAudioTrackGroup:  Flow<Int>  = context.dataStore.data.map { it[VIDEO_AUDIO_TRACK_GROUP]   ?: -1 }
    val videoSubtitleTrackGroup: Flow<Int> = context.dataStore.data.map { it[VIDEO_SUBTITLE_TRACK_GROUP] ?: -1 }
    // Last played song
    val lastPlayedSongId: Flow<Long> = context.dataStore.data.map { it[LAST_PLAYED_SONG_ID] ?: -1L }
    val lastPlayedPositionMs: Flow<Long> = context.dataStore.data.map { it[LAST_PLAYED_POSITION_MS] ?: 0L }
    // Manual wallpaper theme
    val wallpaperEnabled: Flow<Boolean> = context.dataStore.data.map { it[WALLPAPER_ENABLED] ?: false }
    val wallpaperPath:    Flow<String>  = context.dataStore.data.map { it[WALLPAPER_PATH]    ?: "" }
    val wallpaperBlur:    Flow<Float>   = context.dataStore.data.map { it[WALLPAPER_BLUR]    ?: 18f }
    val wallpaperDim:     Flow<Float>   = context.dataStore.data.map { it[WALLPAPER_DIM]     ?: 0.30f }
    // Dog pet companion
    val petEnabled:       Flow<Boolean> = context.dataStore.data.map { it[PET_ENABLED]       ?: false }
    // Landscape mode bottom mini player toggle (default on)
    val landscapeMiniPlayerEnabled: Flow<Boolean> = context.dataStore.data.map { it[LANDSCAPE_MINI_PLAYER_ENABLED] ?: false }

    suspend fun setThemeMode(mode: String)             = context.dataStore.edit { it[THEME_MODE]          = mode }
    suspend fun setDynamicColor(enabled: Boolean)      = context.dataStore.edit { it[DYNAMIC_COLOR]       = enabled }
    suspend fun setListView(isList: Boolean)           = context.dataStore.edit { it[LIST_VIEW]           = isList }
    suspend fun setDefaultTab(tab: Int)                = context.dataStore.edit { it[DEFAULT_TAB]         = tab }
    suspend fun setAudioFocus(enabled: Boolean)        = context.dataStore.edit { it[AUDIO_FOCUS]         = enabled }
    suspend fun setAccentColor(color: String)          = context.dataStore.edit { it[ACCENT_COLOR]        = color }
    suspend fun setRepeatMode(mode: String)            = context.dataStore.edit { it[REPEAT_MODE]         = mode }
    suspend fun setShuffleEnabled(on: Boolean)         = context.dataStore.edit { it[SHUFFLE_ENABLED]     = on }
    suspend fun setHomeSortOrder(order: String)        = context.dataStore.edit { it[HOME_SORT_ORDER]     = order }
    suspend fun setIsPremium(enabled: Boolean)         = context.dataStore.edit { it[IS_PREMIUM]          = enabled }
    suspend fun setEqBandLevels(levels: String)        = context.dataStore.edit { it[EQ_BAND_LEVELS]      = levels }
    suspend fun setPlaybackNotification(enabled: Boolean) = context.dataStore.edit { it[PLAYBACK_NOTIFICATION] = enabled }
    suspend fun setIncludeSubfolders(enabled: Boolean) = context.dataStore.edit { it[INCLUDE_SUBFOLDERS]  = enabled }
    suspend fun setLibrarySortOrder(order: String)     = context.dataStore.edit { it[LIBRARY_SORT_ORDER]  = order }
    suspend fun setSleepTimerEndMs(endMs: Long)        = context.dataStore.edit { it[SLEEP_TIMER_END_MS]  = endMs }
    // Video
    suspend fun setVideoSortOrder(order: String)       = context.dataStore.edit { it[VIDEO_SORT_ORDER]    = order }
    suspend fun setVideoViewType(type: String)         = context.dataStore.edit { it[VIDEO_VIEW_TYPE]     = type }
    suspend fun setVideoRecentlyPlayedIds(ids: String) = context.dataStore.edit { it[VIDEO_RECENTLY_PLAYED] = ids }
    suspend fun setVideoLastPositions(data: String)    = context.dataStore.edit { it[VIDEO_LAST_POSITIONS]  = data }
    suspend fun setVideoScaleMode(mode: String)        = context.dataStore.edit { it[VIDEO_SCALE_MODE]      = mode }
    suspend fun setVideoPlaybackSpeed(speed: Float)    = context.dataStore.edit { it[VIDEO_PLAYBACK_SPEED]  = speed }
    suspend fun setVideoLoopEnabled(enabled: Boolean)  = context.dataStore.edit { it[VIDEO_LOOP_ENABLED]    = enabled }
    suspend fun setVideoRotationMode(mode: String)     = context.dataStore.edit { it[VIDEO_ROTATION_MODE]   = mode }
    suspend fun setVideoAudioTrackGroup(idx: Int)      = context.dataStore.edit { it[VIDEO_AUDIO_TRACK_GROUP]  = idx }
    suspend fun setVideoSubtitleTrackGroup(idx: Int)   = context.dataStore.edit { it[VIDEO_SUBTITLE_TRACK_GROUP] = idx }
    // Last played song
    suspend fun setLastPlayedSongId(id: Long)          = context.dataStore.edit { it[LAST_PLAYED_SONG_ID] = id }
    suspend fun setLastPlayedPositionMs(pos: Long)     = context.dataStore.edit { it[LAST_PLAYED_POSITION_MS] = pos }
    // Manual wallpaper theme
    suspend fun setWallpaperEnabled(enabled: Boolean)  = context.dataStore.edit { it[WALLPAPER_ENABLED] = enabled }
    suspend fun setWallpaperPath(path: String)         = context.dataStore.edit { it[WALLPAPER_PATH]    = path }
    suspend fun setWallpaperBlur(blur: Float)          = context.dataStore.edit { it[WALLPAPER_BLUR]    = blur }
    suspend fun setWallpaperDim(dim: Float)            = context.dataStore.edit { it[WALLPAPER_DIM]     = dim }
    suspend fun setPetEnabled(enabled: Boolean)        = context.dataStore.edit { it[PET_ENABLED]       = enabled }
    suspend fun setLandscapeMiniPlayerEnabled(enabled: Boolean) = context.dataStore.edit { it[LANDSCAPE_MINI_PLAYER_ENABLED] = enabled }
}
