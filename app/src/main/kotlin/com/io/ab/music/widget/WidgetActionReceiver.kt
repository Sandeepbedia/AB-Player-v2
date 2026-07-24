package com.io.ab.music.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import android.content.ComponentName
import com.google.common.util.concurrent.MoreExecutors
import com.io.ab.music.MainActivity
import com.io.ab.music.service.MusicService

/**
 * Receives widget button taps and routes them to the MediaController.
 */
class WidgetActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            WidgetAction.ACTION_PLAY_PAUSE -> sendMediaCommand(context) { it.playWhenReady = !it.playWhenReady }
            WidgetAction.ACTION_NEXT       -> sendMediaCommand(context) { it.seekToNextMediaItem() }
            WidgetAction.ACTION_PREV       -> sendMediaCommand(context) { it.seekToPreviousMediaItem() }
            WidgetAction.ACTION_OPEN_APP   -> {
                val launch = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                context.startActivity(launch)
            }
            WidgetAction.ACTION_OPEN_QUEUE -> {
                val launch = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("navigate_to", "queue")
                }
                context.startActivity(launch)
            }
            WidgetAction.ACTION_WIDGET_UPDATE -> {
                WidgetStateManager.updateAllWidgets(context)
            }
            WidgetAction.ACTION_FAVORITE -> {
                // Forward to app to toggle favorite - open app with extra
                val launch = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("widget_action", "toggle_favorite")
                }
                context.startActivity(launch)
            }
        }
    }

    private fun sendMediaCommand(context: Context, action: (MediaController) -> Unit) {
        val appContext = context.applicationContext
        val sessionToken = SessionToken(
            appContext,
            ComponentName(appContext, MusicService::class.java)
        )
        val controllerFuture = MediaController.Builder(appContext, sessionToken).buildAsync()
        controllerFuture.addListener({
            try {
                val controller = controllerFuture.get()
                action(controller)
                controller.release()
            } catch (_: Exception) {}
        }, MoreExecutors.directExecutor())
    }
}
