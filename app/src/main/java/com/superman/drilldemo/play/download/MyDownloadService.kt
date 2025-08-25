package com.superman.drilldemo.play.download


import android.app.Notification
import android.app.NotificationChannel // 用于 Android O+ 的通知渠道创建 (通常在 Application 类)
import android.app.NotificationManager // 用于 Android O+ 的通知渠道创建 (通常在 Application 类)
import android.content.Context
import android.os.Build // 用于检查 SDK 版本以创建通知渠道
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.PlatformScheduler
import androidx.media3.exoplayer.scheduler.Scheduler
import com.superman.drilldemo.R // 假设 R 文件包含字符串和 drawable 资源

// --- 常量 (Constants) ---

/**
 * [JOB_ID]
 * 类型: Int (编译时常量)
 * 作用: 用于 DownloadService 和 PlatformScheduler 的作业 ID。
 *      在应用中应该是唯一的，如果使用多个 DownloadService 或 Scheduler。
 * 可见性: private (仅限此文件)
 */
private const val JOB_ID = 1

/**
 * [CHANNEL_ID]
 * 类型: String (编译时常量)
 * 作用: 下载通知所使用的通知渠道 ID。
 *      这个 ID 必须与在 Application 类中创建通知渠道时使用的 ID 完全一致。
 * 可见性: private (仅限此文件)
 */
private const val CHANNEL_ID = "media_download_channel" // 示例渠道 ID

/**
 * [MyDownloadService]
 * 类型: Class, 继承自 androidx.media3.exoplayer.offline.DownloadService
 * 作用: 这是应用中处理后台媒体下载的核心服务。
 *      它管理下载任务的生命周期，处理来自其他组件 (如 ViewModel 或 Activity) 的下载命令，
 *      并在下载进行时显示前台通知。
 *
 * 构造函数参数 (传递给父类 DownloadService):
 *   - foregroundNotificationId: Int - 前台通知的 ID (这里使用 JOB_ID)。
 *   - defaultForegroundNotificationUpdateInterval: Long - 前台通知更新的默认间隔 (毫秒)。
 *   - channelId: String - 用于前台通知的通知渠道 ID (这里使用 CHANNEL_ID)。
 *   - channelNameResourceId: Int - 通知渠道名称的字符串资源 ID。
 *   - channelDescriptionResourceId: Int - (可选) 通知渠道描述的字符串资源 ID (0 表示无描述)。
 *
 * @OptIn(UnstableApi::class) 注解表明它使用了 Media3 中可能在未来版本发生变化的 API。
 */
@androidx.annotation.OptIn(UnstableApi::class)
class MyDownloadService : DownloadService(
    JOB_ID, /* foregroundNotificationId */
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL, /* defaultForegroundNotificationUpdateInterval */
    CHANNEL_ID, /* channelId */
    R.string.download_notification_channel_name, /* channelNameResourceId */
    0 /* channelDescriptionResourceId (0 if no description string resource) */
) {

    // --- 重写 DownloadService 的核心方法 ---

    /**
     * [getDownloadManager]
     * 作用: 返回应用中用于管理下载的 DownloadManager 实例。
     *      这个方法在服务创建时被调用。
     *      我们从 DownloadUtil 获取全局的单例 DownloadManager。
     * 返回: DownloadManager - 应用的 DownloadManager 实例。
     */
    override fun getDownloadManager(): DownloadManager {
        // 从 DownloadUtil 获取已初始化的、全局唯一的 DownloadManager 实例。
        return DownloadUtil.getDownloadManager(this)
    }

    /**
     * [getScheduler]
     * 作用: 返回一个可选的 Scheduler 实例。
     *      如果提供了 Scheduler (例如 PlatformScheduler)，下载服务可以在满足特定条件时
     *      (例如，设备连接到 Wi-Fi 并且正在充电) 自动恢复未完成的下载，即使应用已关闭。
     * 返回: Scheduler? - Scheduler 实例，如果不需要调度则返回 null。
     */
    override fun getScheduler(): Scheduler? {
        // 根据 USE_SCHEDULER 常量决定是否启用调度器。
        // PlatformScheduler 使用 Android 的 JobScheduler API 来安排后台任务。
        return if (USE_SCHEDULER) PlatformScheduler(this, JOB_ID) else null
    }

    /**
     * [getForegroundNotification]
     * 作用: 当下载服务需要在前台运行时 (即有活动下载时)，创建并返回一个 Notification 对象。
     *      这个通知会向用户显示下载进度，并且是 Android 系统对前台服务的要求。
     * 参数:
     *   - downloads: MutableList<Download> - 当前所有活动或排队的下载任务列表。
     *   - notMetRequirements: Int - 未满足的调度要求 (如果有调度器的话)。
     * 返回: Notification - 要显示的前台通知。
     */
    override fun getForegroundNotification(
        downloads: MutableList<Download>,
        notMetRequirements: Int
    ): Notification {
        // 使用 DownloadUtil 获取 DownloadNotificationHelper 实例，
        // 并调用其 buildProgressNotification 方法来构建标准的下载进度通知。
        // 你可以根据需要进一步自定义这个通知的样式和行为。
        return DownloadUtil.getDownloadNotificationHelper(this, CHANNEL_ID)
            .buildProgressNotification(
                this, // Context
                R.drawable.ic_alarm_off, // 通知的小图标资源 ID
                null, // Optional: PendingIntent to open when notification is tapped
                null, // Optional: Message string for the notification content
                downloads, // List of current downloads to display progress for
                notMetRequirements // Information about unmet scheduler requirements
            )
    }

    // --- Companion Object (类似于 Java 中的静态成员) ---
    companion object {
        /**
         * [USE_SCHEDULER]
         * 类型: Boolean (编译时常量)
         * 作用: 一个标志，用于控制是否在 getScheduler() 方法中启用 PlatformScheduler。
         *       设置为 true 以允许下载在满足条件时自动恢复。
         *       设置为 false 则禁用此行为。
         */
        const val USE_SCHEDULER = true // 可以根据应用需求设置为 true 或 false
    }
}



