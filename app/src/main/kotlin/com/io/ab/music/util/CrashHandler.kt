package com.io.ab.music.util

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import com.io.ab.music.MainActivity
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

/**
 * Global crash catcher.
 *
 * Feature request: "Agar Mera app khabi bhi crash ho to kisi bhi user ke phone
 * me to full crashlog user see or developer ko direct .txt file me convert
 * hoke direct developer ka telegram me share kar sake taki developer fix kar sake."
 *
 * Whenever an uncaught exception brings the app down, this writes a full crash
 * report (.txt) to internal storage, restarts MainActivity with a flag pointing
 * at that file, and MainActivity shows CrashReportScreen — a full crash-log
 * viewer with a one-tap "Send to Developer" button that uploads the .txt
 * straight to the developer's Telegram via a bot (see CrashReportManager),
 * plus a manual share fallback.
 */
object CrashHandler {

    const val CRASH_DIR      = "crash_logs"
    const val EXTRA_CRASH_LOG = "extra_crash_log_path"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val file = writeCrashLog(appContext, thread, throwable)
                restartInto(appContext, file)
            } catch (_: Throwable) {
                // If crash-handling itself fails, fall back to the system's default
                // handler so the process still terminates in the normal way.
                defaultHandler?.uncaughtException(thread, throwable)
            } finally {
                Process.killProcess(Process.myPid())
                exitProcess(10)
            }
        }
    }

    private fun writeCrashLog(context: Context, thread: Thread, throwable: Throwable): File {
        val dir = File(context.filesDir, CRASH_DIR).apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        val file  = File(dir, "crash_$stamp.txt")

        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))

        val pkgInfo = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull()
        val versionName = pkgInfo?.versionName ?: "unknown"
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            pkgInfo?.longVersionCode?.toString() ?: "unknown"
        else @Suppress("DEPRECATION") pkgInfo?.versionCode?.toString() ?: "unknown"

        val report = buildString {
            appendLine("========== AB Music Crash Report ==========")
            appendLine("Time         : ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
            appendLine("App Version  : $versionName ($versionCode)")
            appendLine("Device       : ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android      : ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("Thread       : ${thread.name}")
            appendLine("=============================================")
            appendLine()
            append(sw.toString())
        }

        file.writeText(report)
        return file
    }

    private fun restartInto(context: Context, crashLogFile: File) {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            putExtra(EXTRA_CRASH_LOG, crashLogFile.absolutePath)
        }
        context.startActivity(intent)
    }
}
