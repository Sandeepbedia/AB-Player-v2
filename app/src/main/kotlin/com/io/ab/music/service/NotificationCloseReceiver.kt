package com.io.ab.music.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Process

/**
 * BroadcastReceiver that handles the "Close App" action from the music notification.
 * When triggered it stops the music service and kills the process so the app is
 * fully removed from memory — exactly like a force-stop.
 */
class NotificationCloseReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_CLOSE_APP) {
            // 1. Stop the foreground music service cleanly
            context.stopService(Intent(context, MusicService::class.java))
            // 2. Kill the process — removes app from recents & clears all state
            Process.killProcess(Process.myPid())
        }
    }

    companion object {
        const val ACTION_CLOSE_APP = "com.io.ab.music.ACTION_CLOSE_APP"
    }
}
