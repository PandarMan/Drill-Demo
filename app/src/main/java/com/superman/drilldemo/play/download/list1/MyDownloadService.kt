package com.superman.drilldemo.play.download.list1



import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Scheduler
import com.superman.drilldemo.R // 确保 R 文件路径正确

@UnstableApi
class MyDownloadService : DownloadService(
    FOREGROUND_NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    NOTIFICATION_CHANNEL_ID,
    R.string.download_channel_name, // 定义在 strings.xml
    R.string.download_channel_description // 可选，定义在 strings.xml 或传 0
) {

    companion object {
        private const val TAG = "MyDownloadService"
        const val FOREGROUND_NOTIFICATION_ID = 1 // 前台服务通知ID，必须 > 0
        const val NOTIFICATION_CHANNEL_ID = "app_download_channel" // 与 strings.xml 和 DownloadViewModel 中使用的ID一致
    }

    private lateinit var notificationHelper: DownloadNotificationHelper

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate: DownloadService created.")
        // 初始化 DownloadNotificationHelper，它将用于创建通知
        notificationHelper = DownloadUtil.getDownloadNotificationHelper(this, NOTIFICATION_CHANNEL_ID)
        createNotificationChannel() // 创建通知渠道
    }

    /**
     * 返回此服务将使用的 DownloadManager。
     * DownloadUtil 应该负责 DownloadManager 的正确配置和单例管理。
     */
    override fun getDownloadManager(): DownloadManager {
        Log.d(TAG, "getDownloadManager: Providing DownloadManager instance via DownloadUtil.")
        return DownloadUtil.getDownloadManager(this)
    }

    /**
     * 返回一个可选的 Scheduler。
     * 如果返回 null，下载仅在服务运行时进行。
     * 如果需要后台调度（例如，仅在Wi-Fi连接时下载），则实现并返回一个 Scheduler。
     */
    override fun getScheduler(): Scheduler? {
        Log.d(TAG, "getScheduler: Returning null (no scheduler).")
        return null
    }

    /**
     * 创建并返回用于前台服务的通知。
     * 当有活动下载（正在下载或排队）时，此方法被调用以显示或更新前台通知。
     */
    override fun getForegroundNotification(
        downloads: List<Download>,
        notMetRequirements: Int
    ): Notification {
        Log.d(TAG, "getForegroundNotification: Active downloads: ${downloads.size}, Not met requirements: $notMetRequirements")

        val activeDownloads = downloads.filter {
            it.state == Download.STATE_DOWNLOADING || it.state == Download.STATE_QUEUED
        }

        val message = if (activeDownloads.isNotEmpty()) {
            val firstDownloadTitle = try {
                // 尝试从 request.data 解析标题 (假设它是 UTF-8 编码的字符串)
                if (activeDownloads[0].request.data.isNotEmpty()) {
                    Util.fromUtf8Bytes(activeDownloads[0].request.data)
                } else {
                    getString(R.string.default_download_title) // e.g., "Downloading media..."
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error parsing download title from request.data", e)
                getString(R.string.default_download_title)
            }
            getString(R.string.downloading_notification_title, firstDownloadTitle) // e.g., "Downloading: %1$s"
        } else {
            // 当没有活动下载时，前台服务通常会很快停止，或者显示一个不同的消息
            getString(R.string.download_notification_no_active_downloads) // e.g., "No active downloads"
        }

        // 调用正确签名的 buildProgressNotification 方法
        return notificationHelper.buildProgressNotification(
            this,
            R.drawable.ic_alarm_off, // 确保这个 drawable 存在且合适
            null,                                // contentIntent (可选)
            message,                             // 通知消息
            downloads,                           // 完整的下载列表
            notMetRequirements                   // DownloadService 传入的未满足的需求
        )
    }

    /**
     * 为 Android Oreo (API 26) 及以上版本创建通知渠道。
     * 这是发送任何通知前所必需的。
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelName = getString(R.string.download_channel_name)
            val channelDescription = getString(R.string.download_channel_description)
            // IMPORTANCE_LOW 通常适合下载通知，以减少干扰
            val importance = NotificationManager.IMPORTANCE_LOW

            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, importance).apply {
                description = channelDescription
                // 可选配置: setShowBadge(false), enableLights(true), setLightColor(Color.BLUE) 等
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (notificationManager == null) {
                Log.e(TAG, "Failed to get NotificationManager system service.")
                return
            }
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel '$NOTIFICATION_CHANNEL_ID' created.")
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy: DownloadService destroyed.")
        super.onDestroy()
    }
}


