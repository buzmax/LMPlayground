package com.druk.lmplayground.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.druk.lmplayground.models.ModelInfo

class DownloadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val downloadManager = ModelDownloadManager.getInstance(context)

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "download_channel"
        const val KEY_MODEL_URI = "model_uri"
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        createNotificationChannel()
        return ForegroundInfo(
            NOTIFICATION_ID,
            createNotification(0f)
        )
    }

    override suspend fun doWork(): Result {
        val modelUri = inputData.getString(KEY_MODEL_URI) ?: return Result.failure()
        
        try {
            downloadManager.download(ModelInfo(remoteUri = Uri.parse(modelUri)))
                .collect { status ->
                    setForeground(ForegroundInfo(NOTIFICATION_ID, createNotification(status.progress)))
                    if (status.state == ModelDownloadManager.DownloadStatus.State.ERROR) {
                        Result.failure()
                    }
                }
            return Result.success()
        } catch (e: Exception) {
            return Result.failure()
        }
    }

    private fun createNotification(progress: Float): Notification {
        return NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading AI model")
            .setProgress(100, (progress * 100).toInt(), progress == 0f)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "AI Model Downloads",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        notificationManager.createNotificationChannel(channel)
    }
}