package com.io.ab.music

import android.app.Application
import coil.Coil
import coil.ImageLoader
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.io.ab.music.util.DownloadNotificationHelper
import com.io.ab.music.util.UpdateNotificationHelper
import com.io.ab.music.util.CrashHandler
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ABMusicApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // FIX/feature: catch crashes app-wide, write a full .txt log, and restart
        // straight into a crash-report screen the user can send to the developer's
        // Telegram in one tap. Installed first so it covers Application init too.
        CrashHandler.install(this)
        // Register VideoFrameDecoder so AsyncImage can generate thumbnails
        // from video content URIs (content://media/external/video/media/...)
        // Disk cache avoids re-decoding artwork from source files on every re-open.
        Coil.setImageLoader(
            ImageLoader.Builder(this)
                .components {
                    add(VideoFrameDecoder.Factory())
                }
                .crossfade(true)
                .memoryCache {
                    MemoryCache.Builder(this)
                        .maxSizePercent(0.25)
                        .build()
                }
                .diskCache {
                    DiskCache.Builder()
                        .directory(cacheDir.resolve("coil_cache"))
                        .maxSizeBytes(50 * 1024 * 1024)
                        .build()
                }
                .build()
        )
        // Create notification channel for app update alerts (Android 8+)
        UpdateNotificationHelper.createChannel(this)
        // Create notification channel for song download completion (Android 8+)
        DownloadNotificationHelper.createChannel(this)
    }
}
