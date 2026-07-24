package com.io.ab.music.widget

import android.content.Context
import android.graphics.Bitmap
import android.widget.RemoteViews
import com.io.ab.music.R

/** 1×1 Quick Play Widget */
class QuickPlayWidget1x1 : BaseWidgetProvider() {
    override val layoutResId = R.layout.widget_quick_1x1

    override fun bindViews(
        ctx: Context,
        views: RemoteViews,
        state: WidgetStateManager.WidgetState,
        artBitmap: Bitmap?
    ) {
        if (artBitmap != null)
            views.setImageViewBitmap(R.id.widget_album_art, artBitmap)
        else
            views.setImageViewResource(R.id.widget_album_art, R.drawable.ic_launcher_foreground)

        val playIcon = if (state.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
        views.setImageViewResource(R.id.widget_btn_play_pause, playIcon)
    }
}
