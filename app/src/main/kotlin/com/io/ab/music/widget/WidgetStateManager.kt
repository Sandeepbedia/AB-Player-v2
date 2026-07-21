package com.io.ab.music.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.content.edit
import androidx.palette.graphics.Palette

/**
 * Singleton that holds the current playback state for all widgets.
 * MusicService writes here; widget providers read from here.
 */
object WidgetStateManager {

    data class WidgetState(
        val songTitle: String = "No song playing",
        val artistName: String = "Unknown Artist",
        val isPlaying: Boolean = false,
        val isFavorite: Boolean = false,
        val progress: Int = 0,               // 0..1000
        val artworkUri: String? = null,
        val primaryColor: Int = Color.parseColor("#BB86FC"),
        val accentColor: Int  = Color.parseColor("#9D4EDD"),
        val bgColor: Int      = Color.parseColor("#0D0D17"),
        val textColor: Int    = Color.WHITE
    )

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(WidgetAction.PREFS_NAME, Context.MODE_PRIVATE)

    fun saveState(ctx: Context, state: WidgetState) {
        prefs(ctx).edit {
            putString(WidgetAction.PREF_SONG_TITLE,  state.songTitle)
            putString(WidgetAction.PREF_ARTIST,      state.artistName)
            putBoolean(WidgetAction.PREF_IS_PLAYING, state.isPlaying)
            putBoolean(WidgetAction.PREF_IS_FAVORITE,state.isFavorite)
            putInt(WidgetAction.PREF_PROGRESS,       state.progress)
            putString(WidgetAction.PREF_ARTWORK_URI, state.artworkUri)
            putInt(WidgetAction.PREF_PRIMARY_CLR,    state.primaryColor)
            putInt(WidgetAction.PREF_ACCENT_CLR,     state.accentColor)
            putInt(WidgetAction.PREF_BG_CLR,         state.bgColor)
        }
    }

    fun loadState(ctx: Context): WidgetState {
        val p = prefs(ctx)
        return WidgetState(
            songTitle   = p.getString(WidgetAction.PREF_SONG_TITLE, "No song playing") ?: "No song playing",
            artistName  = p.getString(WidgetAction.PREF_ARTIST, "Unknown Artist") ?: "Unknown Artist",
            isPlaying   = p.getBoolean(WidgetAction.PREF_IS_PLAYING, false),
            isFavorite  = p.getBoolean(WidgetAction.PREF_IS_FAVORITE, false),
            progress    = p.getInt(WidgetAction.PREF_PROGRESS, 0),
            artworkUri  = p.getString(WidgetAction.PREF_ARTWORK_URI, null),
            primaryColor= p.getInt(WidgetAction.PREF_PRIMARY_CLR, Color.parseColor("#BB86FC")),
            accentColor = p.getInt(WidgetAction.PREF_ACCENT_CLR,  Color.parseColor("#9D4EDD")),
            bgColor     = p.getInt(WidgetAction.PREF_BG_CLR,      Color.parseColor("#0D0D17")),
        )
    }

    /** Extract dynamic palette from album art bitmap and blend into the state */
    fun extractPalette(bitmap: Bitmap, state: WidgetState): WidgetState {
        val palette = Palette.from(bitmap).generate()
        val vibrant  = palette.getVibrantColor(state.primaryColor)
        val muted    = palette.getMutedColor(state.accentColor)
        val darkMuted= palette.getDarkMutedColor(Color.parseColor("#0D0D17"))
        val light    = palette.getLightVibrantSwatch()?.rgb ?: Color.WHITE

        return state.copy(
            primaryColor = vibrant,
            accentColor  = muted,
            bgColor      = darken(darkMuted, 0.6f),
            textColor    = if (isDark(darkMuted)) Color.WHITE else Color.parseColor("#121212")
        )
    }

    private fun darken(color: Int, factor: Float): Int {
        val r = (Color.red(color)   * factor).toInt().coerceIn(0, 255)
        val g = (Color.green(color) * factor).toInt().coerceIn(0, 255)
        val b = (Color.blue(color)  * factor).toInt().coerceIn(0, 255)
        return Color.argb(200, r, g, b)   // keep semi-transparency
    }

    private fun isDark(color: Int): Boolean {
        val r = Color.red(color) / 255.0
        val g = Color.green(color) / 255.0
        val b = Color.blue(color) / 255.0
        val luminance = 0.2126 * r + 0.7152 * g + 0.0722 * b
        return luminance < 0.5
    }

    /** Notify all widget providers to refresh */
    fun updateAllWidgets(ctx: Context) {
        val am = AppWidgetManager.getInstance(ctx)
        val providers = listOf(
            CompactWidget4x1::class.java
        )
        providers.forEach { cls ->
            val ids = am.getAppWidgetIds(ComponentName(ctx, cls))
            if (ids.isNotEmpty()) {
                val intent = android.content.Intent(ctx, cls).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
                ctx.sendBroadcast(intent)
            }
        }
    }
}
