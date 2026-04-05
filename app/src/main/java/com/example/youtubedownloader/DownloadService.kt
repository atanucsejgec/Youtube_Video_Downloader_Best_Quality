package com.example.youtubedownloader

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class DownloadService : Service() {

    companion object {
        const val CHANNEL_ID = "yt_download_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_CANCEL = "com.example.youtubedownloader.CANCEL"
        const val ACTION_START = "com.example.youtubedownloader.START"

        fun startService(context: Context) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            context.stopService(Intent(context, DownloadService::class.java))
        }

        fun updateNotification(
            context: Context,
            title: String,
            progress: Int,
            speed: String,
            eta: String,
            queueInfo: String = ""
        ) {
            val manager = NotificationManagerCompat.from(context)
            val notification = buildNotification(context, title, progress, speed, eta, queueInfo)
            try {
                manager.notify(NOTIFICATION_ID, notification)
            } catch (_: SecurityException) {}
        }

        private fun buildNotification(
            context: Context,
            title: String,
            progress: Int,
            speed: String,
            eta: String,
            queueInfo: String
        ): Notification {
            createChannel(context)

            val cancelIntent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_CANCEL
            }
            val cancelPi = PendingIntent.getService(
                context, 0, cancelIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val openIntent = Intent(context, MainActivity::class.java)
            val openPi = PendingIntent.getActivity(
                context, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val contentText = buildString {
                if (speed.isNotBlank()) append(speed)
                if (eta.isNotBlank()) {
                    if (isNotEmpty()) append(" · ")
                    append("ETA: $eta")
                }
                if (queueInfo.isNotBlank()) {
                    if (isNotEmpty()) append(" · ")
                    append(queueInfo)
                }
            }

            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(title.take(50))
                .setContentText(contentText.ifBlank { "Downloading..." })
                .setProgress(100, progress.coerceIn(0, 100), progress == 0)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(openPi)
                .addAction(android.R.drawable.ic_delete, "Cancel", cancelPi)
                .build()
        }

        fun showCompletedNotification(context: Context, title: String, fileCount: Int) {
            createChannel(context)
            val openIntent = Intent(context, MainActivity::class.java)
            val openPi = PendingIntent.getActivity(
                context, 1, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("Download Complete")
                .setContentText("$fileCount file(s) saved: $title")
                .setAutoCancel(true)
                .setContentIntent(openPi)
                .build()
            try {
                NotificationManagerCompat.from(context).notify(NOTIFICATION_ID + 1, notification)
            } catch (_: SecurityException) {}
        }

        private fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Downloads",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "YouTube download progress"
                    setSound(null, null)
                }
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.createNotificationChannel(channel)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                // Signal cancellation via broadcast
                sendBroadcast(Intent("com.example.youtubedownloader.CANCEL_DOWNLOAD"))
                stopSelf()
            }
            ACTION_START -> {
                val notification = buildNotification(
                    this, "Starting download...", 0, "", "", ""
                )
                startForeground(NOTIFICATION_ID, notification)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}