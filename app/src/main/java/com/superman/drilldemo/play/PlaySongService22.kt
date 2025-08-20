package com.superman.drilldemo.play // 请确保包名与你的项目一致

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.audiofx.Equalizer
import android.os.Build
import android.os.Bundle
import android.widget.RemoteViews
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.upstream.DefaultAllocator
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.superman.drilldemo.MainActivity // 确保这是你的主 Activity
import com.superman.drilldemo.R // **确保导入你的 R 文件**

private const val TAG = "PlaybackService"
private const val NOTIFICATION_ID = 123 // 保持不变
private const val NOTIFICATION_CHANNEL_ID = "playback_channel_custom" // 可以区分
private const val NOTIFICATION_CHANNEL_NAME = "Playback Custom"

@OptIn(UnstableApi::class)
class PlaySongService : MediaSessionService() {
    companion object {
        private const val TAG = "PlaybackService"
        // ... 其他常量 ...

        // 自定义命令 Action
        const val CUSTOM_COMMAND_SET_EQ_GAIN = "com.superman.drilldemo.SET_EQ_GAIN"

        // 自定义命令参数 Key
        const val KEY_EQ_GAIN_MB = "eq_gain_mb"
    }

    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer // 改为 lateinit

    private var notificationManager: NotificationManager? = null

    // 自定义 MediaSession.Callback (与之前相同)
    private inner class CustomMediaSessionCallback : MediaSession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            Log.d(TAG, "onAddMediaItems: ${mediaItems.joinToString { it.mediaId }}")
            return Futures.immediateFuture(mediaItems)
        }

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val availableSessionCommands =
                MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                    .add(SessionCommand(CUSTOM_COMMAND_SET_EQ_GAIN, Bundle.EMPTY))
                    .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(availableSessionCommands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                CUSTOM_COMMAND_SET_EQ_GAIN -> {
                    if (args.containsKey(KEY_EQ_GAIN_MB)) {
                        val gainMillibels = args.getShort(KEY_EQ_GAIN_MB)
                        this@PlaySongService.setEqualizerGain(gainMillibels) // 调用 Service 中的方法
                        return Futures.immediateFuture(
                            SessionResult(
                                SessionResult.RESULT_SUCCESS,
                                Bundle.EMPTY
                            )
                        )
                    } else {
                        Log.w(TAG, "$CUSTOM_COMMAND_SET_EQ_GAIN missing $KEY_EQ_GAIN_MB argument")
                        return Futures.immediateFuture(
                            SessionResult(
                                SessionError.ERROR_BAD_VALUE,
                                Bundle.EMPTY
                            )
                        )
                    }
                }
                // ... 处理其他自定义命令 ...
            }
            return Futures.immediateFuture(
                SessionResult(
                    SessionError.ERROR_BAD_VALUE,
                    Bundle.EMPTY
                )
            )
        }
    }


    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate called")
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()

        val loadControl = DefaultLoadControl.Builder()
            .setAllocator(DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE))
            .setBufferDurationsMs(
                30_000, // minBufferMs: 最小缓冲时长，增加这个值可以抵抗网络波动
                60_000, // maxBufferMs: 最大缓冲时长
                2_500,  // bufferForPlaybackMs: 开始播放前需要缓冲的时长
                1_500   // bufferForPlaybackAfterRebufferMs: 再缓冲后开始播放前需要缓冲的时长
            )
            .setTargetBufferBytes(DefaultLoadControl.DEFAULT_TARGET_BUFFER_BYTES) // 可以尝试增加
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val renderersFactory = DefaultRenderersFactory(this)
            // PREFER 会优先使用扩展渲染器 (如果可用且支持格式)
            // 如果没有可用的扩展渲染器支持该格式，它仍然会尝试平台解码器
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
        // 或者，如果你只想使用扩展渲染器，并且如果它们不可用或不支持格式就失败：
        // .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)

        player = ExoPlayer.Builder(this, renderersFactory)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .setLoadControl(loadControl)
            .build().also { exoPlayer ->
                exoPlayer.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        Log.d(TAG, "Player state changed: $playbackState")
                        // MediaSessionService 会在其内部监听器中处理通知更新
                        // 我们只需要确保 createNotification 能正确反映状态
                        if (playbackState == Player.STATE_ENDED) {
                            // 可选处理
                        }

                        if (playbackState == Player.STATE_READY || playbackState == Player.STATE_BUFFERING) {
                            // 播放器准备好或正在缓冲时，可以尝试初始化/更新均衡器
                            // 因为此时 audioSessionId 应该可用了
                            if (isEqualizerGainEnabled) {
                                setupEqualizerInternal(currentAppliedGain)
                            }
                        } else if (playbackState == Player.STATE_IDLE) {
                            releaseEqualizerInternal()
                        }
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        Log.d(TAG, "Player isPlaying: $isPlaying")
                        // MediaSessionService 会在其内部监听器中处理通知更新
                    }

                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        Log.d(TAG, "Media item transitioned")
                        // MediaSessionService 会在其内部监听器中处理通知更新
                    }

                    override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) {
                        Log.d(TAG, "Media metadata changed")
                        // MediaSessionService 会在其内部监听器中处理通知更新
                    }

                    override fun onAudioSessionIdChanged(audioSessionId: Int) {
                        super.onAudioSessionIdChanged(audioSessionId)
                        // 当 audioSessionId 变化时，如果均衡器已启用，需要重新应用
                        if (isEqualizerGainEnabled) {
                            setupEqualizerInternal(currentAppliedGain, forceRecreate = true)
                        } else {
                            releaseEqualizerInternal() // 如果之前有，确保释放
                        }
                    }
                })
            }

        player.repeatMode = Player.REPEAT_MODE_ALL
        val sessionActivityPendingIntent =
            packageManager?.getLaunchIntentForPackage(packageName)?.let { sessionIntent ->
                PendingIntent.getActivity(this, 0, sessionIntent, PendingIntent.FLAG_IMMUTABLE)
            }

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivityPendingIntent ?: createDefaultOpenAppPendingIntent())
            .setCallback(CustomMediaSessionCallback())
//             .setBitmapLoader(...) // 推荐添加 BitmapLoader 用于专辑封面
            .setBitmapLoader(GlideBitmapLoader(this))
            .build()

//        setMediaNotificationProvider(CustomNotificationProvider1(this))

        Log.d(TAG, "Player and MediaSession initialized with CUSTOM notification provider")
    }

    private inner class CustomNotificationProvider : MediaNotification.Provider {
        override fun createNotification(
            mediaSession: MediaSession,
            customLayout: ImmutableList<CommandButton>,
            actionFactory: MediaNotification.ActionFactory,
            onNotificationChangedCallback: MediaNotification.Provider.Callback // 这个回调用于provider内部更新
        ): MediaNotification {
            Log.d(TAG, "createNotification (CUSTOM) called")

            val smallContentView = RemoteViews(packageName, R.layout.custom_notification_layout)

            val currentPlayer = mediaSession.player
            val metadata = currentPlayer.mediaMetadata

            // --- Populate Small Content View ---
            smallContentView.setTextViewText(
                R.id.notification_title,
                metadata.title ?: "Unknown Title"
            )
            smallContentView.setTextViewText(
                R.id.notification_artist,
                metadata.artist ?: "Unknown Artist"
            )
            smallContentView.setImageViewResource(
                R.id.notification_album_art,
                R.drawable.ic_alarm_off
            ) // Placeholder

            if (currentPlayer.isPlaying) {
                smallContentView.setImageViewResource(
                    R.id.notification_play_pause_button,
                    R.drawable.ic_pause_filled
                )
//                smallContentView.setOnClickPendingIntent(R.id.notification_play_pause_button,
//                    actionFactory.createMediaAction(currentPlayer, Player.COMMAND_PLAY_PAUSE))
            } else {
                smallContentView.setImageViewResource(
                    R.id.notification_play_pause_button,
                    R.drawable.ic_play_arrow
                )
//                smallContentView.setOnClickPendingIntent(R.id.notification_play_pause_button,
//                    actionFactory.createMediaAction(currentPlayer, Player.COMMAND_PLAY_PAUSE))
            }

            if (currentPlayer.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)) {
//                smallContentView.setOnClickPendingIntent(R.id.notification_next_button,
//                    actionFactory.createMediaAction(currentPlayer, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM))
                smallContentView.setImageViewResource(
                    R.id.notification_next_button,
                    R.drawable.ic_skip_next
                )
                smallContentView.setViewVisibility(
                    R.id.notification_next_button,
                    android.view.View.VISIBLE
                )
            } else {
                smallContentView.setViewVisibility(
                    R.id.notification_next_button,
                    android.view.View.GONE
                )
            }

            val notificationBuilder =
                NotificationCompat.Builder(this@PlaySongService, NOTIFICATION_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_default_thumb) // **CRITICAL**
                    .setContentIntent(mediaSession.sessionActivity)
//                .setDeleteIntent(actionFactory.createMediaAction(currentPlayer, Player.COMMAND_STOP)) // Optional: what happens when notification is dismissed
                    .setSilent(true)
                    .setOngoing(currentPlayer.isPlaying) // Makes the notification non-dismissable if playing
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
//                .setStyle(NotificationCompat.DecoratedCustomViewStyle()) // Important for custom views
                    .setCustomContentView(smallContentView)
            // MediaSessionService will add standard actions (like play/pause) for lock screen etc.
            // based on available commands. You don't *usually* need to add them manually here
            // if DecoratedCustomViewStyle is used and your RemoteViews handle the main controls.

            return MediaNotification(NOTIFICATION_ID, notificationBuilder.build())
        }

        override fun handleCustomCommand(
            session: MediaSession,
            action: String,
            extras: android.os.Bundle
        ): Boolean {
            Log.d(TAG, "handleCustomCommand: action=$action")
            // Handle any custom actions you might have defined for your notification buttons
            // if they don't map directly to Player.COMMAND_*
            return false
        }
    }


    private fun createDefaultOpenAppPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
        return PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        Log.d(TAG, "onGetSession called by: ${controllerInfo.packageName}")
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.d(TAG, "onTaskRemoved called")
        if (!player.playWhenReady || player.mediaItemCount == 0) {
            Log.d(TAG, "Stopping service because player is not active.")
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy called")
        mediaSession?.run {
            if (this@PlaySongService::player.isInitialized) { // Check if player was initialized
                if (player.playbackState != Player.STATE_IDLE) {
                    player.stop()
                }
                player.release()
            }
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW // Use LOW to prevent sound/vibration for media controls
            )
            channel.description = "Channel for custom media playback controls"
            channel.setSound(null, null)
            channel.enableLights(false)
            channel.enableVibration(false)
            notificationManager?.createNotificationChannel(channel)
            Log.d(TAG, "Custom Notification channel created.")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Log.d(TAG, "onStartCommand received: ${intent?.action}")
        return START_STICKY
    }

    // Equalizer 相关
    private var equalizer: Equalizer? = null
    private var isEqualizerGainEnabled: Boolean = false
    private val defaultGainLevel: Short = 0 // 默认增益级别 (0 dB)
    private var currentAppliedGain: Short = defaultGainLevel

    private fun releaseEqualizerInternal() {
        try {
            equalizer?.enabled = false
            equalizer?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing Equalizer", e)
        }
        equalizer = null
        isEqualizerGainEnabled = false
        currentAppliedGain = defaultGainLevel // 重置应用增益记录
        Log.d(TAG, "Equalizer released.")
    }

    /**
     * 初始化或重新配置 Equalizer。
     * @param gainMillibels 要应用到所有频段的增益值 (mB)。
     * @param forceRecreate 如果为 true，即使 Equalizer 实例已存在且 audioSessionId 相同，也会强制重新创建。
     *                      这在 audioSessionId 刚刚变为有效时很有用。
     */
    private fun setupEqualizerInternal(gainMillibels: Short, forceRecreate: Boolean = false) {
        if (!this::player.isInitialized || player.audioSessionId == C.AUDIO_SESSION_ID_UNSET) {
            Log.w(
                TAG,
                "Cannot setup Equalizer: Player not ready or audio session ID not available."
            )
            // 可以考虑在 audioSessionId 可用时重试
            return
        }

        val audioSessionId = player.audioSessionId
        Log.d(
            TAG,
            "Attempting to setup Equalizer with gain $gainMillibels mB for session ID: $audioSessionId"
        )

        try {
            if (forceRecreate || equalizer == null
//                || equalizer?.audioSessionId != audioSessionId
            ) {
                equalizer?.release() // 释放旧的实例
                equalizer = Equalizer(0, audioSessionId) // 优先级 0
                Log.d(TAG, "Equalizer created for session ID: $audioSessionId")
            }

            equalizer?.enabled = true // 确保启用

            val numBands = equalizer?.numberOfBands ?: 0
            if (numBands > 0) {
                for (i in 0 until numBands) {
                    equalizer?.setBandLevel(i.toShort(), gainMillibels)
                }
                currentAppliedGain = gainMillibels
                isEqualizerGainEnabled = (gainMillibels != defaultGainLevel) // 更新状态
                Log.d(TAG, "Equalizer gain set to $gainMillibels mB for $numBands bands.")
            } else {
                Log.w(TAG, "Equalizer has no bands for session ID: $audioSessionId")
                // 这种情况可能发生在某些设备或音频路由下
                releaseEqualizerInternal() // 没有频段，则释放并禁用
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up Equalizer", e)
            releaseEqualizerInternal() // 出错时也释放
        }
    }

    /**
     * 设置均衡器增益。
     * @param gainMillibels 增益值 (mB)。例如 500 表示 5dB。0 表示默认无增益。
     */
    fun setEqualizerGain(gainMillibels: Short) {
        Log.i(TAG, "Request to set equalizer gain to: $gainMillibels mB")
        if (gainMillibels == currentAppliedGain && equalizer != null && equalizer?.enabled == true && gainMillibels != defaultGainLevel) {
            Log.d(TAG, "Equalizer gain already set to $gainMillibels mB. No change needed.")
            return // 如果值相同且已启用（非默认），则不重复设置
        }

        if (gainMillibels == defaultGainLevel) {
            isEqualizerGainEnabled = false // 标记为禁用增益（恢复默认）
            releaseEqualizerInternal()
        } else {
            isEqualizerGainEnabled = true // 标记为启用增益
            // 立即尝试设置，如果 audioSessionId 此时不可用，
            // Player.Listener 中的 onAudioSessionIdChanged 或 onPlaybackStateChanged 会处理
            setupEqualizerInternal(gainMillibels, forceRecreate = true)
        }
    }

}

