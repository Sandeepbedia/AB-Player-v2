package com.io.ab.music.domain.model

import android.net.Uri
import androidx.compose.runtime.Stable

@Stable
data class Video(
    val id: Long,
    val title: String,
    val displayName: String,
    val duration: Long,        // ms
    val size: Long,            // bytes
    val width: Int,
    val height: Int,
    val path: String,
    val contentUri: Uri,
    val thumbnailUri: Uri?,
    val dateAdded: Long,
    val dateModified: Long,
    val mimeType: String,
    val resolution: String = "${width}x${height}",
    val folderName: String = path.substringBeforeLast("/").substringAfterLast("/")
) {
    val formattedDuration: String get() {
        val totalSec = duration / 1000
        val h  = totalSec / 3600
        val m  = (totalSec % 3600) / 60
        val s  = totalSec % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    val formattedSize: String get() {
        val mb = size / (1024.0 * 1024.0)
        return if (mb >= 1024) "%.1f GB".format(mb / 1024) else "%.1f MB".format(mb)
    }
}
