package com.io.ab.music.widget

import android.content.Context
import android.content.Intent
import com.io.ab.music.domain.model.Song

/**
 * Helper called from MusicService to push state updates to all widgets.
 * Call this whenever song changes, playback state changes, or favorite changes.
 */
object WidgetUpdateHelper {

    fun onSongChanged(
        context: Context,
        song: Song?,
        isPlaying: Boolean,
        isFavorite: Boolean,
        progress: Long,
        duration: Long
    ) {
        val progressInt = if (duration > 0)
            ((progress.toFloat() / duration) * 1000).toInt().coerceIn(0, 1000) else 0

        val state = WidgetStateManager.WidgetState(
            songTitle   = song?.title ?: "No song playing",
            artistName  = song?.artist ?: "Unknown Artist",
            isPlaying   = isPlaying,
            isFavorite  = isFavorite,
            progress    = progressInt,
            artworkUri  = song?.artworkUri
        )
        WidgetStateManager.saveState(context, state)
        WidgetStateManager.updateAllWidgets(context)
    }

    fun onPlaybackChanged(context: Context, isPlaying: Boolean) {
        val state = WidgetStateManager.loadState(context)
        WidgetStateManager.saveState(context, state.copy(isPlaying = isPlaying))
        WidgetStateManager.updateAllWidgets(context)
    }

    fun onProgressChanged(context: Context, progress: Long, duration: Long) {
        // Throttle: only update widgets for progress if not too frequent
        val progressInt = if (duration > 0)
            ((progress.toFloat() / duration) * 1000).toInt().coerceIn(0, 1000) else 0
        val state = WidgetStateManager.loadState(context)
        WidgetStateManager.saveState(context, state.copy(progress = progressInt))
        // Don't call updateAllWidgets for every progress tick - too expensive.
        // Let periodic updatePeriodMillis handle seekbar or use partial RemoteViews update:
        // We only update AppWidget if needed via ACTION_WIDGET_UPDATE
    }

    fun onFavoriteChanged(context: Context, isFavorite: Boolean) {
        val state = WidgetStateManager.loadState(context)
        WidgetStateManager.saveState(context, state.copy(isFavorite = isFavorite))
        WidgetStateManager.updateAllWidgets(context)
    }
}
