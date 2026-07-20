package com.io.ab.music.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.io.ab.music.R
import com.io.ab.music.ui.viewmodel.UpdateInfo

object UpdateNotificationHelper {

    const val CHANNEL_ID   = "ab_music_update_channel"
    const val CHANNEL_NAME = "App Updates"
    const val NOTIF_ID     = 1001

    /**
     * Call once at app start (e.g., in Application.onCreate or MainActivity)
     * to register the update notification channel on Android 8+.
     */
    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifies when a new version of ABMusic is available"
                setShowBadge(true)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    /**
     * Show a system tray notification telling the user an update is available.
     * Tapping opens the app so the in-app update dialog handles download/install.
     *
     * Uses the app's launcher icon as the large icon so ABMusic branding
     * is visible in the notification shade.
     */
    fun showUpdateNotification(context: Context, info: UpdateInfo) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Intent: tap notification -> open the app; update download/install stays in-app.
        val updateIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        } ?: Intent(context, com.io.ab.music.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            updateIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build short changelog summary (first 3 items)
        val changelogSummary = info.changelog
            .take(3)
            .joinToString("\n") { "• $it" }
            .ifBlank { info.releaseNotes }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            // Small icon: white outline music note for status bar
            .setSmallIcon(R.drawable.ic_notification)
            // Large icon: full colour app logo visible in notification card
            .setLargeIcon(
                android.graphics.BitmapFactory.decodeResource(
                    context.resources,
                    R.mipmap.ic_launcher
                )
            )
            .setContentTitle("ABMusic v${info.versionName} Available")
            .setContentText("New update ready — tap to download")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("New version v${info.versionName} is ready to install.\n\n$changelogSummary")
                    .setSummaryText("Tap to update")
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            // Action button inside the notification card
            .addAction(
                android.R.drawable.stat_sys_download,
                "Update Now",
                pendingIntent
            )
            .build()

        manager.notify(NOTIF_ID, notification)
    }
}
