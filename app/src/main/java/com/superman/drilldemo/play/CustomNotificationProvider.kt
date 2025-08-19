
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.RemoteViews
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.Player
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.superman.drilldemo.R

@OptIn(UnstableApi::class)
class CustomNotificationProvider1(private val context: Context) : MediaNotification.Provider {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

    companion object {
        const val NOTIFICATION_ID = 123 // 你选择的通知 ID
        const val NOTIFICATION_CHANNEL_ID = "playback_channel_custom" // 你选择的渠道 ID
    }

    override fun createNotification(
        mediaSession: MediaSession,
        customLayout: ImmutableList<CommandButton>, // 通常对于完全自定义的通知，这个参数可能不直接使用
        actionFactory: MediaNotification.ActionFactory,
        onNotificationChangedCallback: MediaNotification.Provider.Callback // 用于异步更新通知
    ): MediaNotification {
        // --- 1. 获取播放器和元数据 ---
        val player = mediaSession.player
        val metadata = player.mediaMetadata
//        val albumArtBitmap: Bitmap? = metadata.artworkData // 或者从 metadata.artworkUri 异步加载

        // --- 2. 创建 RemoteViews (自定义布局) ---
        // 小通知布局
        val smallContentView = RemoteViews(context.packageName, R.layout.custom_notification_layout)
        smallContentView.setTextViewText(R.id.notification_title, metadata.title ?: "未知标题")
        smallContentView.setTextViewText(R.id.notification_artist, metadata.artist ?: "未知艺术家")
//        if (metadata.artworkData != null) {
//            try {
//                coverBitmap = BitmapFactory.decodeByteArray(metadata.artworkData, 0, metadata.artworkData!!.size)
//                if (coverBitmap != null) {
//                    bitmapSource = "metadata.artworkData"
//                    Log.i("CustomNotificationProvider", "Successfully decoded Bitmap from metadata.artworkData.")
//                }
//            } catch (e: Exception) {
//                Log.e("CustomNotificationProvider", "Error decoding Bitmap from artworkData", e)
//            }
//        }
//        if (albumArtBitmap != null) {
//            smallContentView.setImageViewBitmap(R.id.notification_album_art, albumArtBitmap)
//        } else {
//            smallContentView.setImageViewResource(R.id.notification_album_art, R.drawable.ic_default_thumb) // 默认封面
//        }

        // --- 3. 设置播放/暂停按钮 ---
        val playPauseViewIdSmall = R.id.notification_play_pause_button

        if (player.isPlaying) {
            smallContentView.setImageViewResource(playPauseViewIdSmall, R.drawable.ic_pause_filled)
            val pausePendingIntent = actionFactory.createMediaActionPendingIntent(mediaSession, Player.COMMAND_PLAY_PAUSE.toLong())
            smallContentView.setOnClickPendingIntent(playPauseViewIdSmall, pausePendingIntent)
        } else {
            smallContentView.setImageViewResource(playPauseViewIdSmall, R.drawable.ic_alarm_off)
            val playPendingIntent = actionFactory.createMediaActionPendingIntent(mediaSession, Player.COMMAND_PLAY_PAUSE.toLong())
            smallContentView.setOnClickPendingIntent(playPauseViewIdSmall, playPendingIntent)
        }

        // --- 4. 设置其他控制按钮 (例如：上一首、下一首) ---
        // 上一首
        if (player.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)) {
            val prevPendingIntent = actionFactory.createMediaActionPendingIntent(mediaSession, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM.toLong())
            smallContentView.setOnClickPendingIntent(R.id.notification_prev_button_small, prevPendingIntent)
            smallContentView.setImageViewResource(R.id.notification_prev_button_small, R.drawable.ic_skip_previous)
            smallContentView.setViewVisibility(R.id.notification_prev_button_small, android.view.View.VISIBLE)
        } else {
            smallContentView.setViewVisibility(R.id.notification_prev_button_small, android.view.View.GONE)
        }

        // 下一首 (与上一首类似)
        if (player.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)) {
            val nextPendingIntent = actionFactory.createMediaActionPendingIntent(mediaSession, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM.toLong())
            smallContentView.setOnClickPendingIntent(R.id.notification_next_button, nextPendingIntent)
            smallContentView.setImageViewResource(R.id.notification_next_button, R.drawable.ic_skip_next)
            smallContentView.setViewVisibility(R.id.notification_next_button, android.view.View.VISIBLE)
        } else {
            smallContentView.setViewVisibility(R.id.notification_next_button, android.view.View.GONE)
        }

        // --- 5. 构建 NotificationCompat.Builder ---
        val notificationBuilder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alarm_off) // **非常重要：必须设置有效的小图标**
            .setContentIntent(mediaSession.sessionActivity) // 点击通知时打开的 Activity
//            .setDeleteIntent(actionFactory.createMediaAction(player, Player.COMMAND_STOP)) // 可选：通知被清除时的操作
            .setSilent(true) // 通常媒体通知是静默的
            .setOngoing(player.isPlaying) // 如果正在播放，通知不可清除
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // 在锁屏上显示内容
            .setStyle(NotificationCompat.DecoratedCustomViewStyle()) // **重要：用于自定义视图**
            .setCustomContentView(smallContentView)
//            .setCustomBigContentView(bigContentView) // 设置展开视图

        // 如果需要，可以添加标准的媒体按钮，系统会在锁屏等地方使用它们
        // mediaSession.player.availableCommands.toList().forEach { command ->
        //     val pendingIntent = actionFactory.createMediaAction(mediaSession.player, command)
        //     // notificationBuilder.addAction(createActionForCommand(command, pendingIntent))
        // }


        return MediaNotification(NOTIFICATION_ID, notificationBuilder.build())
    }

    override fun handleCustomCommand(
        session: MediaSession,
        action: String,
        extras: Bundle
    ): Boolean {
        // 例如：处理你在 RemoteViews 中通过 setOnClickPendingIntent 设置的自定义广播 action
        // if (action == "your.custom.ACTION_FAVORITE") {
        //     // 处理收藏逻辑
        //     return true
        // }
        return false // 如果没有处理，返回 false
    }

    // 可选：创建通知渠道 (应该在 Service 的 onCreate 中调用一次)
    fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            if (notificationManager.getNotificationChannel(NOTIFICATION_CHANNEL_ID) == null) {
                val channel = android.app.NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "自定义播放控制", // 渠道名称
                    android.app.NotificationManager.IMPORTANCE_LOW // 重要性，LOW 可以避免声音
                )
                channel.description = "用于自定义媒体播放的通知渠道"
                channel.setSound(null, null) // 无声音
                channel.enableLights(false)
                channel.enableVibration(false)
                notificationManager.createNotificationChannel(channel)
            }
        }
    }
}
