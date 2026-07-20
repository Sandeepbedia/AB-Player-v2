package com.io.ab.music.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.io.ab.music.MainActivity
import com.io.ab.music.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MusicService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer
    private var closeReceiver: NotificationCloseReceiver? = null

    @Inject lateinit var prefs: com.io.ab.music.data.preferences.UserPreferences
    private val serviceScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main + kotlinx.coroutines.SupervisorJob())
    private var equalizer: android.media.audiofx.Equalizer? = null

    companion object {
        @Volatile var audioSessionId: Int = 0
            private set

        // Custom session command for the close-app button
        const val COMMAND_CLOSE_APP = "com.io.ab.music.COMMAND_CLOSE_APP"

        // Fixed notification ID so we can reliably cancel it when the user
        // turns the "Playback Notification" setting off.
        private const val PLAYBACK_NOTIFICATION_ID = 1010
    }

    // FIX: backing field for the "Playback Notification" setting, kept in
    // sync from DataStore in onCreate() and consulted in
    // onUpdateNotification() below.
    private var playbackNotificationEnabled = true

    override fun onCreate() {
        super.onCreate()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        // FIX: the Settings screen's "Audio Focus" toggle was stored in
        // DataStore but never actually read anywhere — ExoPlayer was always
        // built with `handleAudioFocus = true` regardless of the user's
        // choice, so turning the setting off did nothing. Now we observe the
        // preference and apply it to the live player via setAudioAttributes,
        // which re-registers (or releases) ExoPlayer's internal audio focus
        // handling to match.
        prefs.audioFocus
            .onEach { enabled -> player.setAudioAttributes(audioAttributes, enabled) }
            .launchIn(serviceScope)

        // FIX: "Playback Notification" toggle was stored but never actually
        // read anywhere — the media notification always showed regardless
        // of the setting. Observe it here and act on it in
        // onUpdateNotification() below.
        prefs.playbackNotification
            .onEach { playbackNotificationEnabled = it }
            .launchIn(serviceScope)

        audioSessionId = player.audioSessionId
        initEqualizer(audioSessionId)

        val sessionActivityIntent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, sessionActivityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Register broadcast receiver for close-app action
        closeReceiver = NotificationCloseReceiver()
        val filter = IntentFilter(NotificationCloseReceiver.ACTION_CLOSE_APP)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(closeReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(closeReceiver, filter)
        }

        mediaSession = MediaSession.Builder(this, player)
            .setCallback(MediaSessionCallback())
            .setSessionActivity(pendingIntent)
            .build()

        val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelName(R.string.app_name)
            .setNotificationId(PLAYBACK_NOTIFICATION_ID)
            .build()
        setMediaNotificationProvider(notificationProvider)

        // Show close button immediately when service starts
        updateCustomNotificationLayout(player.isPlaying)

        // Listen for playback state changes to update the notification close button
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updateCustomNotificationLayout(isPlaying)
                com.io.ab.music.widget.WidgetUpdateHelper.onPlaybackChanged(this@MusicService, isPlaying)
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                val playing = player.isPlaying
                updateCustomNotificationLayout(playing)
            }
            override fun onMediaItemTransition(
                mediaItem: androidx.media3.common.MediaItem?,
                reason: Int
            ) {
                pushWidgetUpdate()
            }
        })
    }

    /**
     * Close App button is ALWAYS shown in notification so users
     * can fully quit at any time — playing or paused.
     */
    private fun updateCustomNotificationLayout(isPlaying: Boolean) {
        val session = mediaSession ?: return
        // Build a PendingIntent that fires our BroadcastReceiver
        val closeIntent = Intent(NotificationCloseReceiver.ACTION_CLOSE_APP).apply {
            setPackage(packageName)
        }
        val closePendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            closeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // CommandButton always shown — close app from notification any time
        val closeButton = CommandButton.Builder()
            .setDisplayName("Close App")
            .setSessionCommand(SessionCommand(COMMAND_CLOSE_APP, Bundle.EMPTY))
            .setIconResId(android.R.drawable.ic_menu_close_clear_cancel)
            .build()

        session.setCustomLayout(ImmutableList.of(closeButton))
    }

    private fun pushWidgetUpdate() {
        val p = player
        val mediaItem = p.currentMediaItem ?: return
        val meta = mediaItem.mediaMetadata
        val songTitle = meta.title?.toString() ?: "Unknown"
        val artist = meta.artist?.toString() ?: "Unknown"
        val artUri = meta.artworkUri?.toString()
        com.io.ab.music.widget.WidgetUpdateHelper.onSongChanged(
            context    = this,
            song       = null,
            isPlaying  = p.isPlaying,
            isFavorite = false,
            progress   = p.currentPosition,
            duration   = p.duration.coerceAtLeast(1L)
        )
        // Also save title/artist manually since we don't have full Song model here
        val state = com.io.ab.music.widget.WidgetStateManager.loadState(this)
        com.io.ab.music.widget.WidgetStateManager.saveState(
            this,
            state.copy(songTitle = songTitle, artistName = artist, artworkUri = artUri)
        )
        com.io.ab.music.widget.WidgetStateManager.updateAllWidgets(this)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    // FIX: "Playback Notification" (Show media controls in notification
    // shade) had no effect before — MusicService never checked it. A
    // MediaSessionService still needs to satisfy the foreground-service
    // requirement while audio is playing, so we let the system post/update
    // the notification as usual and then immediately cancel it when the
    // user has turned this setting off, instead of leaving it visible.
    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        super.onUpdateNotification(session, startInForegroundRequired)
        if (!playbackNotificationEnabled) {
            (getSystemService(NOTIFICATION_SERVICE) as? NotificationManager)
                ?.cancel(PLAYBACK_NOTIFICATION_ID)
        }
    }

    override fun onDestroy() {
        audioSessionId = 0
        serviceScope.cancel()
        equalizer?.release()
        equalizer = null
        closeReceiver?.let { unregisterReceiver(it) }
        closeReceiver = null
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player ?: return
        if (!player.playWhenReady
            || player.mediaItemCount == 0
            || player.playbackState == Player.STATE_ENDED) {
            stopSelf()
        }
    }

    private fun initEqualizer(sessionId: Int) {
        if (sessionId == 0) return
        try {
            equalizer?.release()
            val eq = android.media.audiofx.Equalizer(0, sessionId).apply {
                enabled = true
            }
            equalizer = eq

            // Observe preferences to apply equalizer changes
            prefs.eqBandLevels
                .onEach { saved ->
                    if (saved.isNotBlank()) {
                        val levels = saved.split(",").mapNotNull { it.trim().toFloatOrNull() }
                        levels.forEachIndexed { i, level ->
                            if (i < eq.numberOfBands) {
                                val mb = (level * 100).toInt()
                                    .coerceIn(eq.bandLevelRange[0].toInt(), eq.bandLevelRange[1].toInt())
                                    .toShort()
                                try {
                                    eq.setBandLevel(i.toShort(), mb)
                                } catch (e: Exception) {
                                    android.util.Log.e("MusicService", "Error setting band $i to $level: ${e.message}")
                                }
                            }
                        }
                    }
                }
                .launchIn(serviceScope)
        } catch (e: Exception) {
            android.util.Log.e("MusicService", "Failed to initialize Equalizer: ${e.message}")
        }
    }

    private inner class MediaSessionCallback : MediaSession.Callback {
        override fun onConnect(
            session   : MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult =
            MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(
                    MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
                        .buildUpon()
                        .add(SessionCommand(COMMAND_CLOSE_APP, Bundle.EMPTY))
                        .build()
                ).build()

        override fun onCustomCommand(
            session      : MediaSession,
            controller   : MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args         : Bundle
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == COMMAND_CLOSE_APP) {
                // Delegate to the broadcast receiver logic
                sendBroadcast(
                    Intent(NotificationCloseReceiver.ACTION_CLOSE_APP).setPackage(packageName)
                )
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
        }
    }
}
