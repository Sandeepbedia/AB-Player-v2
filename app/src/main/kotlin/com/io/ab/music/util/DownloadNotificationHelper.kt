package com.io.ab.music.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.io.ab.music.MainActivity
import com.io.ab.music.R

/**
 * Handles system-tray notifications for song download events.
 * Shows a completion notification when a song finishes downloading.
 */
object DownloadNotificationHelper {

    const val CHANNEL_ID   = "ab_music_download_channel"
    const val CHANNEL_NAME = "Song Downloads"
    private const val BASE_NOTIF_ID = 2000

    /**
     * Register the download notification channel on Android 8+.
     * Call once from Application.onCreate().
     */
    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifies when a song download is complete"
                setShowBadge(true)
                enableVibration(true)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    /**
     * Show "Download complete" notification for a finished song.
     * Tapping the notification opens the app.
     *
     * @param songTitle  The song title that was downloaded
     * @param artistName The artist name
     * @param notifId    Unique ID — pass a unique value per download to allow
     *                   multiple concurrent download notifications.
     */
    fun showDownloadComplete(
        context   : Context,
        songTitle : String,
        artistName: String = "",
        notifId   : Int    = BASE_NOTIF_ID
    ) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Tap → open app
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notifId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val bodyText = if (artistName.isNotBlank())
            "$songTitle — $artistName"
        else
            songTitle

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(
                android.graphics.BitmapFactory.decodeResource(
                    context.resources,
                    R.mipmap.ic_launcher
                )
            )
            .setContentTitle("Download complete ✓")
            .setContentText(bodyText)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("\"$songTitle\" has been saved to your library.")
                    .setSummaryText("Tap to open AB Player")
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(notifId, notification)
    }

    /**
     * Show a download-failed notification.
     */
    fun showDownloadFailed(
        context   : Context,
        songTitle : String,
        reason    : String = "",
        notifId   : Int   = BASE_NOTIF_ID + 1
    ) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Download failed")
            .setContentText("\"$songTitle\" could not be downloaded${if (reason.isNotBlank()) ": $reason" else "."}")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()

        manager.notify(notifId, notification)
    }
}
