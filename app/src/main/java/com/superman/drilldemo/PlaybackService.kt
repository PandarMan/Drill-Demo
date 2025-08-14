package com.superman.exoplayerdemo

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 *
 * @author 张学阳
 * @date : 2025/8/10
 * @description:
 */
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private  val player: ExoPlayer by lazy {
        ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true // 自动处理音频焦点
            )
            .build().also {
                // 添加播放器监听器 (可选, 用于调试或自定义行为)
                it.addListener(object : Player.Listener {
                    @OptIn(UnstableApi::class)
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        Log.d(TAG, "Player state changed: $playbackState")
                    }

                    @OptIn(UnstableApi::class)
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        Log.d(TAG, "Player isPlaying: $isPlaying")
                    }
                })
            }
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate called")
        // 2. 初始化 MediaSession
        // 获取一个 PendingIntent 来启动你的主 Activity
        val sessionActivityPendingIntent =
            packageManager?.getLaunchIntentForPackage(packageName)?.let { sessionIntent ->
                PendingIntent.getActivity(
                    this,
                    0,
                    sessionIntent,
                    PendingIntent.FLAG_IMMUTABLE
                )
            } ?: return

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivityPendingIntent) // 点击通知时打开的 Activity
            // 你可以在这里设置自定义的 MediaSession.Callback 如果需要的话
            // .setCallback(MyCustomMediaSessionCallback())
            .build()
        Log.d(TAG, "Player and MediaSession initialized")
    }

    // 当有控制器 (如 Activity, Android Auto, 系统UI) 连接时，返回 MediaSession
    @OptIn(UnstableApi::class)
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        Log.d(TAG, "onGetSession called by: ${controllerInfo.packageName}")
        return mediaSession
    }


    // 当 Service 即将被销毁时调用
    @OptIn(UnstableApi::class)
    override fun onDestroy() {
        Log.d(TAG, "onDestroy called")
        mediaSession?.run {
            // 在 MediaSession 释放前，确保 Player 被释放
            // MediaSession 通常会在其 release() 方法中处理 Player 的释放
            // 但明确调用 player.release() 是一个好习惯
            if (player.playbackState != Player.STATE_IDLE) {
                player.stop()
            }
            player.release()
            release() // 释放 MediaSession
            mediaSession = null
        }
        super.onDestroy()
    }

    // (可选) 处理任务移除，例如当用户从最近任务列表中划掉应用时
    @OptIn(UnstableApi::class)
    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.d(TAG, "onTaskRemoved called")
        // 如果没有在播放或没有媒体项，则停止服务以避免不必要的资源占用
        if (!player.playWhenReady || player.mediaItemCount == 0) {
            Log.d(TAG, "Stopping service because player is not active.")
            stopSelf() // 停止服务
        }
        // 否则，如果正在播放，服务会继续在后台运行 (因为它是前台服务)
    }

    // 可以在这里添加自定义的 MediaSession.Callback 实现类
    // private class MyCustomMediaSessionCallback : MediaSession.Callback {
    //     // 实现需要自定义的回调方法，例如 onAddMediaItems
    //     override fun onAddMediaItems(
    //         mediaSession: MediaSession,
    //         controller: MediaSession.ControllerInfo,
    //         mediaItems: MutableList<MediaItem>
    //     ): ListenableFuture<MutableList<MediaItem>> {
    //         // 处理添加媒体项的请求
    //         val updatedMediaItems = mediaItems.map { mediaItem ->
    //             // 假设我们在这里根据 mediaId 获取实际的 URI 或更丰富的元数据
    //             if (mediaItem.requestMetadata.mediaUri == null) {
    //                 mediaItem.buildUpon()
    //                     .setUri("some_default_uri_if_not_provided")
    //                     .build()
    //             } else {
    //                 mediaItem
    //             }
    //         }.toMutableList()
    //         return Futures.immediateFuture(updatedMediaItems)
    //     }
    // }
    companion object {
        const val TAG = "PlaybackService"
    }
}