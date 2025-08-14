package com.superman.drilldemo

import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.util.Log
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Player.REPEAT_MODE_ALL
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import com.superman.drilldemo.databinding.ActivityMainBinding
import com.superman.exoplayerdemo.FirstActivity

@UnstableApi
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val player by lazy {
        val renderersFactory = DefaultRenderersFactory(this)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)

        ExoPlayer.Builder(this, renderersFactory).build().also {
            it.repeatMode = REPEAT_MODE_ALL
        }
    }

    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.playerView.player = player
        player.addListener(object : Player.Listener {
            //播放状态监听
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                super.onIsPlayingChanged(isPlaying)
                binding.tvListState.text = "播放状态：${player.mediaItemCount + 1}首，当前：${player.currentMediaItemIndex}"

            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                val stateString: String = when (playbackState) {
                    ExoPlayer.STATE_IDLE -> "ExoPlayer.STATE_IDLE      -"
                    ExoPlayer.STATE_BUFFERING -> "ExoPlayer.STATE_BUFFERING -"
                    ExoPlayer.STATE_READY -> "ExoPlayer.STATE_READY     -"
                    ExoPlayer.STATE_ENDED -> "ExoPlayer.STATE_ENDED     -"
                    else -> "UNKNOWN_STATE             -"
                }
                Log.d("onPlaybackStateChanged", "changed state to $stateString")
            }
            @OptIn(UnstableApi::class)
            override fun onPlayerError(error: PlaybackException) {
                // 检查错误码是否与 HTTP 状态相关
                if (error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS) {
                    // 获取根本原因，它通常是 HttpDataSource.HttpDataSourceException 的子类
                    val cause = error.cause
                    if (cause is HttpDataSource.InvalidResponseCodeException) {
                        val responseCode = cause.responseCode
                        if (responseCode == 404) {
                            // 特别处理 404 错误
                            Log.e("ExoPlayerError", "媒体未找到 (404)，URL: ${cause.dataSpec.uri}")
                            // 在这里可以向用户显示 "媒体资源未找到" 的提示
                        } else {
                            // 处理其他 HTTP 错误码
                            Log.e(
                                "ExoPlayerError",
                                "HTTP 错误，响应码: $responseCode，URL: ${cause.dataSpec.uri}"
                            )
                        }
                    } else if (cause is HttpDataSource.CleartextNotPermittedException) {
                        Log.e("ExoPlayerError", "不允许明文 HTTP 流量，URL: ${cause.dataSpec.uri}")
                        // 提示用户检查网络安全配置或使用 HTTPS
                    } else {
                        Log.e(
                            "ExoPlayerError",
                            "未知的 HTTP 数据源错误，URL: ${error.printStackTrace()}"
                        )
                    }
                } else if (error.errorCodeName.startsWith("ERROR_CODE_IO")) {
                    // 处理其他类型的 I/O 错误 (例如网络连接超时等)
                    Log.e("ExoPlayerError", "媒体源 I/O 错误: ${error.localizedMessage}")
                } else {
                    // 处理其他类型的播放器错误
                    Log.e("ExoPlayerError", "播放器错误: ${error.localizedMessage}")
                }
            }
        })
        //播放到某个位置执行事件
        player.createMessage { messageType: Int, payload: Any? -> }
            .setLooper(Looper.getMainLooper())
            .setPosition(/* mediaItemIndex= */ 0, /* positionMs= */ 120000)
//            .setPayload(customPayloadData)
            .setDeleteAfterDelivery(false)
            .send()
        val videoUri = Uri.parse("asset:///video_20250522_164023.mp4") // 注意三个斜杠
        val mediaItem111 = MediaItem.fromUri(videoUri)

        val mediaItem = MediaItem.fromUri("https://vjs.zencdn.net/v/oceans.mp4")
        val mediaItem00 = MediaItem.fromUri("http://video.chinanews.com/flv/2019/04/23/400/111773_web.mp4")
        val mediaItem1 =
            MediaItem.fromUri("http://music.163.com/song/media/outer/url?id=447925558.mp3")
        val mediaItem2 =
            MediaItem.fromUri("https://www.cambridgeenglish.org/images/153149-movers-sample-listening-test-vol2.mp3")
        player.addMediaItem(mediaItem111)
//        player.addMediaItem(mediaItem00)
//        player.addMediaItem(mediaItem1)
//        player.addMediaItem(mediaItem2)
        player.prepare()

//        player.setMediaItem(mediaItem3)
        binding.play.setOnClickListener {
            player.play()
        }
        binding.pause.setOnClickListener {
            player.pause()
        }

        binding.next.setOnClickListener {
            if (player.hasNextMediaItem()) {
//                player.seekToNext()
                player.seekToNextMediaItem()
            }
        }
        binding.previous.setOnClickListener {
            if (player.hasPreviousMediaItem()) {
//                player.seekToPrevious()
                player.seekToPreviousMediaItem()
            }

        }

        binding.firstAct.setOnClickListener {
            FirstActivity.start(this)
        }
        binding.progress
    }

    override fun onDestroy() {
        super.onDestroy()
        player.release() //释放
    }

}