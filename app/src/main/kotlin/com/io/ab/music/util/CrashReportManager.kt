package com.io.ab.music.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Sends captured crash reports (see CrashHandler) straight to the developer's
 * Telegram — https://t.me/Infinity_384 — so any user's crash can reach the
 * developer with the full .txt log attached, no email or manual digging.
 *
 * There's no Bot-API token needed here: we open the developer's chat directly
 * via Telegram's deep link, and hand the crash .txt to the Telegram app itself
 * so the user just has to tap Send once they're in the chat.
 */
object CrashReportManager {

    private const val DEVELOPER_TELEGRAM_USERNAME = "Infinity_384"
    private const val DEVELOPER_TELEGRAM_URL       = "https://t.me/$DEVELOPER_TELEGRAM_USERNAME"
    private const val TELEGRAM_PACKAGE              = "org.telegram.messenger"

    /** Returns the pending crash log written by CrashHandler, if any, or null. */
    fun findCrashLog(context: Context, path: String?): File? {
        if (path != null) {
            val f = File(path)
            if (f.exists()) return f
        }
        val dir = File(context.filesDir, CrashHandler.CRASH_DIR)
        return dir.listFiles()?.maxByOrNull { it.lastModified() }
    }

    fun deleteCrashLog(file: File) {
        runCatching { file.delete() }
    }

    /**
     * Opens the developer's Telegram chat directly (https://t.me/Infinity_384).
     * Tries the Telegram app deep link first, falls back to the browser/whatever
     * app handles t.me links if Telegram isn't installed.
     */
    fun openDeveloperChat(context: Context) {
        val deepLink = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("tg://resolve?domain=$DEVELOPER_TELEGRAM_USERNAME")
        )
        try {
            context.startActivity(deepLink)
        } catch (_: ActivityNotFoundException) {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(DEVELOPER_TELEGRAM_URL)))
        }
    }

    /**
     * Hands the crash .txt to the Telegram app's share sheet so the user can pick
     * the developer's chat and send the file in one tap. Falls back to the
     * system share sheet if Telegram isn't installed.
     */
    fun sendCrashFileViaTelegram(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "AB Music crash report — please forward to @$DEVELOPER_TELEGRAM_USERNAME")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            setPackage(TELEGRAM_PACKAGE)
        }
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            shareManually(context, file)
        }
    }

    /** Fallback: open the system share sheet with the .txt file (any app). */
    fun shareManually(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "AB Music crash report")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Send crash report"))
    }
}
