package com.superman.drilldemo

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.superman.drilldemo.play.download.DownloadUtil

/**
 *
 * @author 张学阳
 * @date : 2025/8/24
 * @description:
 */
class MyApplication: Application() {
    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        // Initialize DownloadUtil (which creates DownloadManager, Cache etc.)
        // This ensures they are created once with the application context.
        DownloadUtil.getDownloadManager(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "media_download_channel", // Must match CHANNEL_ID in DownloadService & Helper
                getString(R.string.download_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW // Or other importance
            )
            // channel.description = "Channel for media downloads" // Optional
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
}