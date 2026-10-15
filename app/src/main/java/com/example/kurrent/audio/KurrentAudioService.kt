package com.example.kurrent.audio

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
import androidx.core.content.ContextCompat
import com.example.kurrent.MainActivity
import com.example.kurrent.R

class KurrentAudioService : Service() {

    private val CHANNEL_ID = "kurrent_audio_playback"
    private val NOTIFICATION_ID = 2026

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val manager = AudioNewsManager.getInstance(applicationContext)

        when (action) {
            ACTION_PREV -> manager.skipPrevious()
            ACTION_TOGGLE -> manager.pauseOrResume()
            ACTION_NEXT -> manager.skipNext()
            ACTION_STOP -> {
                manager.stop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START, ACTION_UPDATE -> {
                val notification = buildMediaNotification()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            }
        }

        return START_STICKY
    }

    private fun buildMediaNotification(): Notification {
        val manager = AudioNewsManager.getInstance(applicationContext)
        val article = manager.getCurrentArticle()
        val isPlaying = manager.isSpeaking()

        val title = article?.title ?: "Kurrent Daily News Briefing"
        val trackInfo = if (manager.isQueueMode) {
            "Track ${manager.currentIndex + 1} of ${manager.getPlaylistSize()} • ${article?.category ?: "Daily Digest"}"
        } else {
            article?.category ?: "Daily Audio Briefing"
        }

        // Open MainActivity when tapping the notification
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Actions
        val prevPending = createActionPendingIntent(ACTION_PREV, 1)
        val togglePending = createActionPendingIntent(ACTION_TOGGLE, 2)
        val nextPending = createActionPendingIntent(ACTION_NEXT, 3)
        val stopPending = createActionPendingIntent(ACTION_STOP, 4)

        val playPauseIcon = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_audio_speaker)
            .setContentTitle(title)
            .setContentText(trackInfo)
            .setSubText("Kurrent Morning Pulse")
            .setContentIntent(openAppPendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying)
            .setOnlyAlertOnce(true)
            .setColor(ContextCompat.getColor(this, R.color.teal_accent))
            .addAction(R.drawable.ic_skip_previous, "Prev", prevPending)
            .addAction(playPauseIcon, if (isPlaying) "Pause" else "Play", togglePending)
            .addAction(R.drawable.ic_skip_next, "Next", nextPending)
            .addAction(R.drawable.ic_stop, "Stop", stopPending)

        return builder.build()
    }

    private fun createActionPendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, KurrentAudioService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Kurrent Daily Audio Briefings",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Hands-free continuous news briefing with lockscreen audio controls."
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.example.kurrent.ACTION_START"
        const val ACTION_UPDATE = "com.example.kurrent.ACTION_UPDATE"
        const val ACTION_PREV = "com.example.kurrent.ACTION_PREV"
        const val ACTION_TOGGLE = "com.example.kurrent.ACTION_TOGGLE"
        const val ACTION_NEXT = "com.example.kurrent.ACTION_NEXT"
        const val ACTION_STOP = "com.example.kurrent.ACTION_STOP"

        fun startService(context: Context) {
            val intent = Intent(context, KurrentAudioService::class.java).apply {
                action = ACTION_START
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                // If background restrictions apply, start regular service
                try {
                    context.startService(intent)
                } catch (ignored: Exception) {}
            }
        }

        fun updateNotification(context: Context) {
            val intent = Intent(context, KurrentAudioService::class.java).apply {
                action = ACTION_UPDATE
            }
            try {
                context.startService(intent)
            } catch (ignored: Exception) {}
        }

        fun stopService(context: Context) {
            val intent = Intent(context, KurrentAudioService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startService(intent)
            } catch (ignored: Exception) {}
        }
    }
}
