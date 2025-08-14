package com.superman.exoplayerdemo

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.superman.drilldemo.databinding.ActivityFistBinding
import java.util.concurrent.ExecutionException

/**
 *
 * @author 张学阳
 * @date : 2025/8/10
 * @description:
 */
class FirstActivity : AppCompatActivity() {
    private val viewBinding by lazy {
        ActivityFistBinding.inflate(layoutInflater)
    }
    private var mediaControllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    // 示例媒体列表
    private val mediaItems = listOf(
        MediaItem.Builder()
            .setMediaId("audio_1")
            .setUri("http://music.163.com/song/media/outer/url?id=447925558.mp3") // 替换为你的音频 URL，例如一个 MP3 链接
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("Awesome Track 1")
                    .setArtist("Artist A")
                    .setArtworkUri(Uri.parse("YOUR_ARTWORK_URL_1_HERE")) // 可选
                    .build()
            )
            .build(),
        MediaItem.Builder()
            .setMediaId("audio_2")
            .setUri("https://www.cambridgeenglish.org/images/153149-movers-sample-listening-test-vol2.mp3") // 替换为你的音频 URL
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("Another Great Song")
                    .setArtist("Artist B")
                    .build()
            )
            .build()
        // 你也可以使用本地资源
        // MediaItem.fromUri(Uri.parse("android.resource://${packageName}/${R.raw.your_audio_file}"))
    )

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Log.d(TAG, "Notification permission granted.")
                // 权限被授予，可以继续
                startPlaybackServiceAndInitializeController()
            } else {
                Log.w(TAG, "Notification permission denied.")
                Toast.makeText(this, "Notification permission is required for playback controls.", Toast.LENGTH_LONG).show()
                // 用户拒绝了权限，你可能需要禁用相关功能或再次解释为什么需要它
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(viewBinding.root)

        // 请求通知权限 (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                Log.d(TAG, "Notification permission already granted.")
                startPlaybackServiceAndInitializeController()
            } else if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                // 向用户解释为什么需要这个权限
                Toast.makeText(this, "Notification permission is needed to show playback controls.", Toast.LENGTH_LONG).show()
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                // 直接请求权限
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            // Android 13 以下不需要特殊权限来显示通知 (但前台服务权限是需要的)
            startPlaybackServiceAndInitializeController()
        }

        setupButtonClickListeners()
    }


    private fun startPlaybackServiceAndInitializeController() {
        // 启动 PlaybackService
        val serviceIntent = Intent(this, PlaybackService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        Log.d(TAG, "PlaybackService started.")
        // 初始化 MediaController
        initializeMediaController()
    }

    private fun initializeMediaController() {
        if (mediaController != null) return // 避免重复初始化

        val sessionToken = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        mediaControllerFuture = MediaController.Builder(this, sessionToken).buildAsync()

        mediaControllerFuture?.addListener({
            try {
                mediaController = mediaControllerFuture?.get()
                if (mediaController == null) {
                    Log.e(TAG, "MediaController is null after future completed.")
                    viewBinding.statusText.text = "Error: Could not connect to MediaController"
                    return@addListener
                }
                Log.d(TAG, "MediaController connected: ${mediaController?.isConnected}")

                // 将 PlayerView 与 MediaController (间接通过 Player) 关联
                // 注意：PlayerView 通常直接与一个 Player 实例关联。
                // 如果 Service 中的 Player 是唯一的播放源，并且 Activity 只是一个控制器，
                // Activity 中的 PlayerView 应该连接到 Service 中的 Player 实例。
                // 这里我们通过 MediaController 来控制 Service 中的 Player。
                // 要让 PlayerView 显示 Service 中 Player 的状态，需要将 PlayerView 的 player
                // 设置为 MediaController 内部获取到的 Player 代理，或者 Activity 也创建一个 Player
                // 实例并将其作为 MediaController 的一部分（但这会更复杂）。
                // 一个更简单的方式是，如果 Service 只有一个 Player，Activity 也连接到这个 Player。
                // 但这里我们仅通过 Controller 控制。

                // playerView.player = mediaController // MediaController 不是 Player 类型
                // 要更新 UI，需要监听 MediaController 的 Player 事件
                mediaController?.addListener(playerListener)
                updateUiBasedOnPlayerState() // 初始 UI 更新

                // 如果 MediaController 连接成功，可以准备播放列表
                if (mediaController?.isConnected == true && (mediaController?.mediaItemCount ?: 0) == 0) {
                    mediaController?.setMediaItems(mediaItems, true) // 第二个参数 true 表示重置播放位置
                    mediaController?.prepare()
                    Log.d(TAG, "Media items set and prepared via MediaController.")
                }
            } catch (e: ExecutionException) {
                Log.e(TAG, "Error initializing MediaController (ExecutionException)", e.cause ?: e)
                viewBinding.statusText.text = "Error: ${e.message}"
            } catch (e: InterruptedException) {
                Log.e(TAG, "Error initializing MediaController (InterruptedException)", e)
                Thread.currentThread().interrupt()
                viewBinding.statusText.text = "Error: Connection interrupted"
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing MediaController (Exception)", e)
                viewBinding.statusText.text = "Error: ${e.message}"
            }
        }, MoreExecutors.directExecutor()) // 或者使用 ContextCompat.getMainExecutor(this)
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            viewBinding.statusText.text = if (isPlaying) "Status: Playing" else "Status: Paused"
            viewBinding.playButton.isEnabled = !isPlaying
            viewBinding.pauseButton.isEnabled = isPlaying
            Log.d(TAG, "Controller: isPlaying changed to $isPlaying")
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val stateString = when (playbackState) {
                Player.STATE_IDLE -> "Idle"
                Player.STATE_BUFFERING -> "Buffering"
                Player.STATE_READY -> "Ready"
                Player.STATE_ENDED -> "Ended"
                else -> "Unknown"
            }
            Log.d(TAG, "Controller: Playback state changed to $stateString")
            if (viewBinding.statusText.text.toString().startsWith("Status:")) { // 避免覆盖其他状态信息
                // 可以添加更详细的状态
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            viewBinding.statusText.text = "Now Playing: ${mediaItem?.mediaMetadata?.title ?: "Unknown Title"}"
            Log.d(TAG, "Controller: Media item transitioned to ${mediaItem?.mediaId}")
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            Log.e(TAG, "Controller: Player error", error)
            viewBinding.statusText.text = "Error: ${error.localizedMessage}"
            Toast.makeText(this@FirstActivity, "Player Error: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    private fun updateUiBasedOnPlayerState() {
        mediaController?.let {
            val isPlaying = it.isPlaying
            viewBinding.playButton.isEnabled = !isPlaying && it.isCommandAvailable(Player.COMMAND_PLAY_PAUSE)
            viewBinding.pauseButton.isEnabled = isPlaying && it.isCommandAvailable(Player.COMMAND_PLAY_PAUSE)
            viewBinding.nextButton.isEnabled = it.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT)

            val currentItem = it.currentMediaItem
            viewBinding.statusText.text = if (isPlaying) "Playing: ${currentItem?.mediaMetadata?.title}"
            else "Paused: ${currentItem?.mediaMetadata?.title ?: "No media"}"
        }
    }

    private fun setupButtonClickListeners() {
        viewBinding.playButton.setOnClickListener {
            mediaController?.play()
            Log.d(TAG, "Play button clicked")
        }
        viewBinding.pauseButton.setOnClickListener {
            mediaController?.pause()
            Log.d(TAG, "Pause button clicked")
        }
        viewBinding.nextButton.setOnClickListener {
            if (mediaController?.hasNextMediaItem() == true) {
                mediaController?.seekToNextMediaItem()
                Log.d(TAG, "Next button clicked")
            } else {
                Toast.makeText(this, "No next item", Toast.LENGTH_SHORT).show()
            }
        }
    }
    override fun onStart() {
        super.onStart()
        // 如果权限已授予，则在此处初始化，以防 Activity 重建
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED) {
                if (mediaController == null || mediaControllerFuture == null) { // 避免重复初始化
                    startPlaybackServiceAndInitializeController()
                }
            }
        } else {
            if (mediaController == null || mediaControllerFuture == null) {
                startPlaybackServiceAndInitializeController()
            }
        }
        Log.d(TAG, "MainActivity onStart")
    }


    override fun onStop() {
        super.onStop()
        // 释放 MediaController
        releaseMediaController()
        Log.d(TAG, "MainActivity onStop")
    }

    private fun releaseMediaController() {
        mediaController?.removeListener(playerListener)
        mediaControllerFuture?.let {
            MediaController.releaseFuture(it)
            Log.d(TAG, "MediaController future released.")
        }
        // MediaController 本身不需要显式调用 release()，
        // releaseFuture 会处理与 Service 的解绑。
        mediaController = null
        mediaControllerFuture = null
    }
    companion object {
        const val TAG = "FirstActivity"

        fun start(context: Context) {
            context.startActivity(Intent(context, FirstActivity::class.java))
        }
    }
}