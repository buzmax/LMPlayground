package com.druk.lmplayground.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat

class DownloadService : Service() {
    private val notificationManager by lazy { getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }
    private val downloadManager by lazy { ModelDownloadManager.getInstance(application) }
    
    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "download_channel"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification(0f))
        return START_NOT_STICKY
    }

    private fun createNotification(progress: Float): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading AI model")
            .setProgress(100, (progress * 100).toInt(), progress == 0f)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "AI Model Downloads",
            NotificationManager.IMPORTANCE_LOW
        )
        notificationManager.createNotificationChannel(channel)
    }

    override fun onBind(intent: Intent): IBinder? = null
}