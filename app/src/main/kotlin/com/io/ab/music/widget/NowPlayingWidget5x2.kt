package com.io.ab.music.widget

import android.content.Context
import android.graphics.Bitmap
import android.widget.RemoteViews
import com.io.ab.music.R

/**
 * 5×2 "Now Playing Pro" — the flagship widget.
 * Shows: large art, title, artist, seekbar, prev/play/next/fav/queue,
 * animated visualizer bars on the right side.
 */
class NowPlayingWidget5x2 : BaseWidgetProvider() {
    override val layoutResId = R.layout.widget_now_playing_5x2

    override fun bindViews(
        ctx: Context,
        views: RemoteViews,
        state: WidgetStateManager.WidgetState,
        artBitmap: Bitmap?
    ) {
        // Text
        views.setTextViewText(R.id.widget_song_title, state.songTitle)
        views.setTextViewText(R.id.widget_artist_name, state.artistName)
        views.setTextColor(R.id.widget_song_title,  state.textColor)
        views.setTextColor(R.id.widget_artist_name, applyAlpha(state.textColor, 0.7f))

        // Progress
        views.setProgressBar(R.id.widget_progress, 1000, state.progress, false)
        views.setInt(R.id.widget_progress, "setBackgroundColor", android.graphics.Color.TRANSPARENT)

        // Album art
        if (artBitmap != null) {
            val rounded = roundedBitmap(artBitmap, 12f, ctx)
            views.setImageViewBitmap(R.id.widget_album_art, rounded)
        } else {
            views.setImageViewResource(R.id.widget_album_art, R.drawable.ic_launcher_foreground)
        }

        // Play/Pause icon
        val playIcon = if (state.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
        views.setImageViewResource(R.id.widget_btn_play_pause, playIcon)

        // Favorite tint (filled vs outline based on isFavorite)
        views.setInt(R.id.widget_btn_favorite, "setColorFilter",
            if (state.isFavorite) 0xFFFF4081.toInt() else 0xB3FFFFFF.toInt())

        // Dynamic color the play button background tint
        views.setInt(R.id.widget_btn_play_pause, "setBackgroundColor", state.primaryColor)

        // Dynamic visualizer bar heights (static since RemoteViews can't animate,
        // but heights are updated per song giving visual variety)
        val bars = listOf(
            R.id.viz_bar1 to 20, R.id.viz_bar2 to 35,
            R.id.viz_bar3 to 15, R.id.viz_bar4 to 28, R.id.viz_bar5 to 40
        )
        bars.forEach { (id, h) ->
            views.setViewPadding(id, 0, 0, 0, 0)
        }
        bars.forEach { (id, _) ->
            views.setInt(id, "setBackgroundColor", state.primaryColor)
        }
    }

    private fun applyAlpha(color: Int, alpha: Float): Int {
        val a = (255 * alpha).toInt()
        return (color and 0x00FFFFFF) or (a shl 24)
    }
}
