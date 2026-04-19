package com.aurapdf.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.aurapdf.app.MainActivity
import com.aurapdf.app.R
import com.aurapdf.app.domain.tts.ReadingController
import com.aurapdf.app.domain.tts.TtsState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service that keeps TTS alive while the app is backgrounded.
 *
 * Responsibilities:
 *  - Show a persistent media-style notification with Pause/Resume and Stop actions.
 *  - Observe [ReadingController.ttsState] and update the notification accordingly.
 *  - Handle notification action intents ([ACTION_PAUSE], [ACTION_RESUME], [ACTION_STOP]).
 *  - Stop itself when TTS reaches [TtsState.Finished] or [TtsState.Idle].
 *
 * The actual TTS playback is owned by [ReadingController] (singleton), so the
 * ViewModel and this service share the same state without coupling.
 */
@AndroidEntryPoint
class ReadingForegroundService : Service() {

    @Inject
    lateinit var readingController: ReadingController

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    companion object {
        const val CHANNEL_ID      = "aurapdf_reading_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_PAUSE  = "com.aurapdf.app.ACTION_PAUSE"
        const val ACTION_RESUME = "com.aurapdf.app.ACTION_RESUME"
        const val ACTION_STOP   = "com.aurapdf.app.ACTION_STOP"

        fun start(context: Context) =
            context.startForegroundService(
                Intent(context, ReadingForegroundService::class.java)
            )

        fun stop(context: Context) =
            context.stopService(Intent(context, ReadingForegroundService::class.java))
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ──────────────────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification = buildNotification(TtsState.Loading)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        observeTtsState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE  -> readingController.pause()
            ACTION_RESUME -> readingController.resume()
            ACTION_STOP   -> {
                readingController.stop()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    // ──────────────────────────────────────────────────────────────────────────
    // State observation
    // ──────────────────────────────────────────────────────────────────────────

    private fun observeTtsState() {
        serviceScope.launch {
            readingController.ttsState.collectLatest { state ->
                updateNotification(state)
                if (state is TtsState.Finished || state is TtsState.Idle) {
                    stopSelf()
                }
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Notification
    // ──────────────────────────────────────────────────────────────────────────

    private fun updateNotification(state: TtsState) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(state))
    }

    private fun buildNotification(state: TtsState): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val playPauseAction = if (state is TtsState.Paused) {
            NotificationCompat.Action(
                android.R.drawable.ic_media_play,
                "Resume",
                pendingBroadcastIntent(ACTION_RESUME),
            )
        } else {
            NotificationCompat.Action(
                android.R.drawable.ic_media_pause,
                "Pause",
                pendingBroadcastIntent(ACTION_PAUSE),
            )
        }

        val stopAction = NotificationCompat.Action(
            android.R.drawable.ic_delete,
            "Stop",
            pendingBroadcastIntent(ACTION_STOP),
        )

        val contentText = when (state) {
            is TtsState.Speaking -> "Reading…"
            is TtsState.Paused   -> "Paused"
            is TtsState.Loading  -> "Initialising voice…"
            is TtsState.Error    -> "Error: ${state.message}"
            else                 -> "AuraPDF"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("AuraPDF")
            .setContentText(contentText)
            .setContentIntent(openAppIntent)
            .addAction(playPauseAction)
            .addAction(stopAction)
            .setOngoing(state is TtsState.Speaking || state is TtsState.Loading)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSilent(true)
            .build()
    }

    /**
     * Creates a [PendingIntent] that re-delivers [action] to this service.
     * Using a service intent (rather than broadcast) keeps it simple and avoids
     * a separate BroadcastReceiver.
     */
    private fun pendingBroadcastIntent(action: String): PendingIntent =
        PendingIntent.getService(
            this,
            action.hashCode(),
            Intent(this, ReadingForegroundService::class.java).apply { this.action = action },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "AuraPDF Reading",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shows TTS reading progress and controls"
            setShowBadge(false)
        }
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }
}
