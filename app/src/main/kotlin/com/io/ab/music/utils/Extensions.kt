package com.io.ab.music.utils

import android.content.Context
import android.widget.Toast

fun Context.toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

fun Long.formatDuration(): String {
    val totalSec = this / 1000
    val hours = totalSec / 3600
    val minutes = (totalSec % 3600) / 60
    val seconds = totalSec % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}

fun Long.formatFileSize(): String {
    val mb = this / (1024.0 * 1024.0)
    return if (mb >= 1000) "%.1f GB".format(mb / 1024)
    else "%.1f MB".format(mb)
}

fun String.toFolderName(): String {
    return this.trimEnd('/').substringAfterLast('/')
}
