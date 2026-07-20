package com.io.ab.music.widget

object WidgetAction {
    const val ACTION_PLAY_PAUSE = "com.io.ab.music.widget.PLAY_PAUSE"
    const val ACTION_NEXT       = "com.io.ab.music.widget.NEXT"
    const val ACTION_PREV       = "com.io.ab.music.widget.PREV"
    const val ACTION_FAVORITE   = "com.io.ab.music.widget.FAVORITE"
    const val ACTION_OPEN_APP   = "com.io.ab.music.widget.OPEN_APP"
    const val ACTION_OPEN_QUEUE = "com.io.ab.music.widget.OPEN_QUEUE"

    // Broadcast from service → widgets
    const val ACTION_WIDGET_UPDATE = "com.io.ab.music.widget.WIDGET_UPDATE"

    // Prefs keys for widget state
    const val PREF_SONG_TITLE   = "widget_song_title"
    const val PREF_ARTIST       = "widget_artist"
    const val PREF_IS_PLAYING   = "widget_is_playing"
    const val PREF_IS_FAVORITE  = "widget_is_favorite"
    const val PREF_PROGRESS     = "widget_progress"       // 0..1000
    const val PREF_ARTWORK_URI  = "widget_artwork_uri"
    const val PREF_PRIMARY_CLR  = "widget_primary_color"
    const val PREF_ACCENT_CLR   = "widget_accent_color"
    const val PREF_BG_CLR       = "widget_bg_color"
    const val PREFS_NAME        = "ab_widget_prefs"
}
