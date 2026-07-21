package com.io.ab.music.widget

import android.content.Context
import android.graphics.Bitmap
import android.widget.RemoteViews
import com.io.ab.music.R

/** 2×1 Mini Widget */
class MiniWidget2x1 : BaseWidgetProvider() {
    override val layoutResId = R.layout.widget_mini_2x1

    override fun bindViews(
        ctx: Context,
        views: RemoteViews,
        state: WidgetStateManager.WidgetState,
        artBitmap: Bitmap?
    ) {
        if (artBitmap != null)
            views.setImageViewBitmap(R.id.widget_album_art, roundedBitmap(artBitmap, 8f, ctx))
        else
            views.setImageViewResource(R.id.widget_album_art, R.drawable.ic_launcher_foreground)

        views.setTextViewText(R.id.widget_song_title, state.songTitle)
        views.setTextColor(R.id.widget_song_title, state.textColor)

        val playIcon = if (state.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
        views.setImageViewResource(R.id.widget_btn_play_pause, playIcon)
        views.setInt(R.id.widget_btn_play_pause, "setBackgroundColor", state.primaryColor)

        listOf(R.id.viz_bar1, R.id.viz_bar2, R.id.viz_bar3, R.id.viz_bar4).forEachIndexed { i, id ->
            views.setInt(id, "setBackgroundColor",
                if (i % 2 == 0) state.primaryColor else state.accentColor)
        }
    }
}
