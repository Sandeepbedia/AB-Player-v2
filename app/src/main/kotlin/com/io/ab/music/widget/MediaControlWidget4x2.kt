package com.io.ab.music.widget

import android.content.Context
import android.graphics.Bitmap
import android.widget.RemoteViews
import com.io.ab.music.R

/** 4×2 Media Control Widget */
class MediaControlWidget4x2 : BaseWidgetProvider() {
    override val layoutResId = R.layout.widget_media_4x2

    override fun bindViews(
        ctx: Context,
        views: RemoteViews,
        state: WidgetStateManager.WidgetState,
        artBitmap: Bitmap?
    ) {
        views.setTextViewText(R.id.widget_song_title, state.songTitle)
        views.setTextViewText(R.id.widget_artist_name, state.artistName)
        views.setTextColor(R.id.widget_song_title,  state.textColor)
        views.setTextColor(R.id.widget_artist_name, applyAlpha(state.textColor, 0.7f))
        views.setProgressBar(R.id.widget_progress, 1000, state.progress, false)

        if (artBitmap != null)
            views.setImageViewBitmap(R.id.widget_album_art, roundedBitmap(artBitmap, 10f, ctx))
        else
            views.setImageViewResource(R.id.widget_album_art, R.drawable.ic_launcher_foreground)

        val playIcon = if (state.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
        views.setImageViewResource(R.id.widget_btn_play_pause, playIcon)
        views.setInt(R.id.widget_btn_play_pause, "setBackgroundColor", state.primaryColor)

        val vizBars = listOf(R.id.viz_bar1, R.id.viz_bar2, R.id.viz_bar3,
            R.id.viz_bar4, R.id.viz_bar5, R.id.viz_bar6, R.id.viz_bar7, R.id.viz_bar8)
        vizBars.forEachIndexed { i, id ->
            views.setInt(id, "setBackgroundColor",
                if (i % 2 == 0) state.primaryColor else state.accentColor)
        }
    }

    private fun applyAlpha(color: Int, alpha: Float): Int {
        val a = (255 * alpha).toInt()
        return (color and 0x00FFFFFF) or (a shl 24)
    }
}
