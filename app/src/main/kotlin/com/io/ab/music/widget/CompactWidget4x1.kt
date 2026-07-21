package com.io.ab.music.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.widget.RemoteViews
import com.io.ab.music.R

/** 4×1 Compact Player Widget – Beautiful Edition (no wavebar) */
class CompactWidget4x1 : BaseWidgetProvider() {
    override val layoutResId = R.layout.widget_compact_4x1

    override fun bindViews(
        ctx: Context,
        views: RemoteViews,
        state: WidgetStateManager.WidgetState,
        artBitmap: Bitmap?
    ) {
        // ── Text ─────────────────────────────────────────────────────────────
        views.setTextViewText(R.id.widget_song_title,  state.songTitle)
        views.setTextViewText(R.id.widget_artist_name, state.artistName)
        views.setTextColor(R.id.widget_song_title,  state.textColor)
        views.setTextColor(R.id.widget_artist_name, applyAlpha(state.textColor, 0.60f))

        // ── Album art ────────────────────────────────────────────────────────
        if (artBitmap != null)
            views.setImageViewBitmap(R.id.widget_album_art, roundedBitmap(artBitmap, 10f, ctx))
        else
            views.setImageViewResource(R.id.widget_album_art, R.drawable.ic_launcher_foreground)

        // ── Play / Pause icon + accent background ────────────────────────────
        val playIcon = if (state.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
        views.setImageViewResource(R.id.widget_btn_play_pause, playIcon)
        views.setInt(R.id.widget_btn_play_pause, "setBackgroundColor", state.primaryColor)

        // ── Favourite tint (highlight when active) ───────────────────────────
        val heartTint = if (state.isFavorite) state.primaryColor else applyAlpha(state.textColor, 0.70f)
        views.setInt(R.id.widget_btn_favorite, "setColorFilter", heartTint)

        // ── Prev / Next tint ─────────────────────────────────────────────────
        views.setInt(R.id.widget_btn_prev, "setColorFilter", applyAlpha(state.textColor, 0.85f))
        views.setInt(R.id.widget_btn_next, "setColorFilter", applyAlpha(state.textColor, 0.85f))
    }

    private fun applyAlpha(color: Int, alpha: Float): Int =
        ((255 * alpha).toInt() shl 24) or (color and 0x00FFFFFF)
}
