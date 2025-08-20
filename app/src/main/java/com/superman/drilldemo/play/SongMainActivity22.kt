package com.superman.drilldemo.play // Asegúrate de que el nombre del paquete sea el correcto

import MediaPlaybackViewModel
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import com.superman.drilldemo.databinding.SongActivityMainBinding
import com.superman.drilldemo.play.PlaySongService
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class SongMainActivity22 : AppCompatActivity() {

    private val mediaViewModel: MediaPlaybackViewModel by viewModels()
    private val viewBinding by lazy { SongActivityMainBinding.inflate(layoutInflater) }

    private val sampleAudioUrl = "https://musicpress.site/musicpress/acoustic%20blues.mp3"
    private val sampleAudioUrl2 = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3"

    companion object {
        private const val TAG = "SongMainActivity22" // 日志 TAG
        const val ACTION_PLAY_SONG = "com.superman.drilldemo.play.ACTION_PLAY_SONG"
        const val EXTRA_SONG_URI = "com.superman.drilldemo.play.EXTRA_SONG_URI"
        const val EXTRA_SONG_TITLE = "com.superman.drilldemo.play.EXTRA_SONG_TITLE"
        const val EXTRA_SONG_ID = "com.superman.drilldemo.play.EXTRA_SONG_ID"
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Log.d(TAG, "Notification permission granted.")
                startPlaySongService()
            } else {
                Log.w(TAG, "Notification permission denied.")
                // 用户拒绝了权限。可以显示一个更友好的对话框，解释为什么需要权限，并可能提供一个按钮跳转到应用设置。
                showPermissionDeniedDialog()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(viewBinding.root)
        Log.d(TAG, "onCreate called. Intent: $intent")

        checkAndRequestNotificationPermission() // 检查并请求通知权限

        setupClickListeners()
        observeViewModel()

        // 在 onCreate 中处理初始 Intent
        // 只有在权限检查通过或不需要权限时，服务才会启动，然后这里可以安全地播放
        // 注意：如果权限请求是异步的，handleIntent 可能会在服务连接之前执行。
        // playRequestedSong 内部的 .first() 会等待连接。
        handleIntent(intent, isNewLaunch = true)
    }

    private fun startPlaySongService() {
        val serviceIntent = Intent(this, PlaySongService::class.java)
        try {
            ContextCompat.startForegroundService(this, serviceIntent)
            Log.d(TAG, "Attempting to start PlaySongService.")
        } catch (e: IllegalStateException) {
            // 这可能在Android 12+后台启动限制时发生，如果权限不在或应用不在豁免列表
            Log.e(TAG, "Failed to start PlaySongService in foreground", e)
            Toast.makeText(this, "Could not start playback service. Please check app permissions.", Toast.LENGTH_LONG).show()
        }
    }

    private fun checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // TIRAMISU 是 API 33
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    Log.d(TAG, "Notification permission already granted.")
                    startPlaySongService()
                }
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    Log.i(TAG, "Showing rationale for notification permission.")
                    // 显示一个解释性对话框
                    AlertDialog.Builder(this)
                        .setTitle("Permission Needed")
                        .setMessage("This app needs notification permission to show playback controls and current song information while playing in the background.")
                        .setPositiveButton("Grant") { _, _ ->
                            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        .setNegativeButton("Deny") { dialog, _ ->
                            dialog.dismiss()
                            Toast.makeText(this, "Playback notifications will not be available.", Toast.LENGTH_SHORT).show()
                        }
                        .show()
                }
                else -> {
                    Log.d(TAG, "Requesting notification permission.")
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            Log.d(TAG, "No runtime notification permission needed for API < 33.")
            startPlaySongService()
        }
    }

    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permission Denied")
            .setMessage("Notification permission was denied. Playback controls in the notification shade will not be available. You can grant the permission in App Settings.")
            .setPositiveButton("Open Settings") { _, _ ->
                // 跳转到应用设置页面
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package", packageName, null)
                intent.data = uri
                try {
                    startActivity(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Could not open app settings", e)
                    Toast.makeText(this, "Could not open app settings.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }


    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent) // 非常重要，更新 Activity 持有的 Intent
        Log.d(TAG, "onNewIntent called. New Intent: $intent")
        intent?.let { handleIntent(it, isNewLaunch = false) }
    }

    private fun handleIntent(intent: Intent?, isNewLaunch: Boolean) {
        Log.d(TAG, "handleIntent: isNewLaunch=$isNewLaunch, intentAction=${intent?.action}")
        if (intent == null && !isNewLaunch) {
            Log.d(TAG, "Null intent in onNewIntent, doing nothing.")
            return
        }

        val songUriString: String?
        val songTitle: String?
        val songId: String?

        if (intent?.action == ACTION_PLAY_SONG && intent.hasExtra(EXTRA_SONG_URI)) {
            songUriString = intent.getStringExtra(EXTRA_SONG_URI)
            songTitle = intent.getStringExtra(EXTRA_SONG_TITLE) ?: "Unknown Title (from Intent)"
            songId = intent.getStringExtra(EXTRA_SONG_ID) ?: songUriString // Fallback ID
            Log.d(TAG, "Intent to play specific song: $songTitle ($songId)")
        } else if (isNewLaunch) {
            // Activity 首次启动，且没有特定 Intent 或 Intent 不匹配，播放默认歌曲
            Log.d(TAG, "Activity launched or non-play intent on new launch, playing default song.")
            songUriString = sampleAudioUrl
            songTitle = "SoundHelix Song 1 (Default)"
            songId = "defaultSong1"
        } else {
            // onNewIntent 收到一个不符合 ACTION_PLAY_SONG 的 Intent
            Log.d(TAG, "Received non-play Intent in onNewIntent, doing nothing specific with playback.")
            return
        }

        if (songUriString != null && songId != null) {
            playRequestedSong(songUriString, songTitle ?: "Unknown Title", songId)
        } else {
            Log.w(TAG, "Song URI or ID is null after intent processing, cannot play.")
        }
    }

    private fun playRequestedSong(uriString: String, title: String, mediaId: String) {
        Log.d(TAG, "playRequestedSong: Preparing to play $title ($mediaId)")
        lifecycleScope.launch {
            Log.d(TAG, "playRequestedSong: Waiting for ViewModel connection...")
            try {
                mediaViewModel.isConnected.filter { connected ->
                    val controllerAvailable = mediaViewModel.mediaController != null
                    Log.d(TAG, "playRequestedSong: Connection status: $connected, Controller available: $controllerAvailable")
                    connected && controllerAvailable
                }.first() // 等待连接成功且 MediaController 可用
            } catch (e: Exception) {
                Log.e(TAG, "playRequestedSong: Exception while waiting for connection", e)
                Toast.makeText(this@SongMainActivity22, "Error connecting to playback service.", Toast.LENGTH_SHORT).show()
                return@launch
            }

            Log.d(TAG, "playRequestedSong: ViewModel connected. MediaController should be available.")
            val currentMediaController = mediaViewModel.mediaController
            if (currentMediaController == null) {
                Log.e(TAG, "playRequestedSong: MediaController is null even after isConnected. Cannot play.")
                Toast.makeText(this@SongMainActivity22, "Playback controller not available.", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val currentPlayingMediaItem = currentMediaController.currentMediaItem
            val isPlayingThisSong = currentPlayingMediaItem?.mediaId == mediaId

            Log.d(TAG, "playRequestedSong: Current Media ID: ${currentPlayingMediaItem?.mediaId}, Requested Media ID: $mediaId, IsPlayingThisSong: $isPlayingThisSong")

            if (isPlayingThisSong) {
                Log.d(TAG, "Song $mediaId ($title) is already the current item.")
                if (!currentMediaController.isPlaying) {
                    Log.d(TAG, "Starting playback for already set song: $title")
                    mediaViewModel.play()
                } else {
                    Log.d(TAG, "Song $title is already playing. No action needed.")
                    // 如果需要，可以在这里处理“点击已播放歌曲则从头播放”的逻辑，
                    // 例如：mediaViewModel.seekTo(0); mediaViewModel.play()
                }
            } else {
                Log.d(TAG, "Setting and playing new song: $title ($mediaId)")
                val newMediaItem = MediaItem.Builder()
                    .setUri(Uri.parse(uriString))
                    .setMediaId(mediaId)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(title)
                            .setArtist("Various Artists") // 你可以从 Intent 或其他地方获取
                            // .setArtworkUri(...) // 如果有封面图URI
                            .build()
                    )
                    .build()
                mediaViewModel.setMediaItem(newMediaItem)
                mediaViewModel.play()
            }
        }
    }

    private fun setupClickListeners() {
        viewBinding.playPauseButton.setOnClickListener {
            val controller = mediaViewModel.mediaController
            if (controller == null || controller.mediaItemCount == 0) {
                Log.d(TAG, "Play/Pause clicked, no media or controller, playing default song.")
                playRequestedSong(sampleAudioUrl, "SoundHelix Song 1 (Default)", "defaultSong1")
            } else {
                mediaViewModel.playPauseToggle()
            }
        }

        viewBinding.playPauseButton1.setOnClickListener {
            Log.d(TAG, "Play Song 2 button clicked.")
            playRequestedSong(sampleAudioUrl2, "SoundHelix Song 2 (VM) - Direct", "song2")
        }

        viewBinding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    viewBinding.currentTimeTextView.text = formatDuration(progress.toLong())
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                seekBar?.let {
                    mediaViewModel.seekTo(it.progress.toLong())
                }
            }
        })

        // 你可能还有其他按钮，例如 Next/Previous
        // viewBinding.nextButton.setOnClickListener { mediaViewModel.skipToNext() }
        // viewBinding.prevButton.setOnClickListener { mediaViewModel.skipToPrevious() }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    mediaViewModel.isConnected.collectLatest { connected ->
                        val status = if (connected) "Connected" else "Disconnected"
                        Log.d(TAG, "Media Service Connection: $status")
                        // Toast.makeText(this@SongMainActivity22, "$status to Media Service", Toast.LENGTH_SHORT).show()
                        // 你可以根据连接状态启用/禁用UI控件
                    }
                }

                launch {
                    mediaViewModel.isPlaying.collectLatest { isPlaying ->
                        Log.d(TAG, "IsPlaying state updated: $isPlaying")
                        updatePlayPauseButton(isPlaying)
                    }
                }

                launch {
                    mediaViewModel.currentMediaMetadata.collectLatest { metadata ->
                        Log.d(TAG, "Current MediaMetadata updated: ${metadata?.title}")
                        updateMediaMetadataUI(metadata)
                    }
                }

                launch {
                    mediaViewModel.currentPosition.collectLatest { position ->
                        if (!viewBinding.seekBar.isPressed) { // 仅当用户没有拖动SeekBar时更新
                            viewBinding.seekBar.progress = position.toInt()
                            viewBinding.currentTimeTextView.text = formatDuration(position)
                        }
                    }
                }

                launch {
                    mediaViewModel.duration.collectLatest { duration ->
                        Log.d(TAG, "Duration updated: $duration")
                        if (duration > 0) {
                            viewBinding.seekBar.max = duration.toInt()
                            viewBinding.totalTimeTextView.text = formatDuration(duration)
                        } else {
                            viewBinding.seekBar.max = 100 // Default max
                            viewBinding.totalTimeTextView.text = formatDuration(0) // Default text
                        }
                    }
                }

                launch {
                    mediaViewModel.playerState.collectLatest { state ->
                        Log.d(TAG, "Player state updated: $state")
                        // 例如，处理播放结束时重置进度条
                        if (state == Player.STATE_ENDED && !mediaViewModel.isPlaying.value) {
                            Log.d(TAG, "Player ended, resetting progress UI.")
                            // viewBinding.seekBar.progress = 0
                            // viewBinding.currentTimeTextView.text = formatDuration(0)
                            // 也可以让 currentPosition Flow 来处理这个，如果它在结束时变为0
                        }
                    }
                }
            }
        }
    }

    private fun updatePlayPauseButton(isPlaying: Boolean) {
//        viewBinding.playPauseButton.text = if (isPlaying) getString(R.string.pause) else getString(R.string.play)
        // 假设你有播放和暂停的图标资源
        // val iconRes = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        // viewBinding.playPauseButton.setIconResource(iconRes)
    }

    private fun updateMediaMetadataUI(mediaMetadata: MediaMetadata?) {
//        viewBinding.titleTextView.text = mediaMetadata?.title ?: getString(R.string.unknown_title)
//        viewBinding.artistTextView.text = mediaMetadata?.artist ?: getString(R.string.unknown_artist)
        // 你可能还想加载专辑封面图到 ImageView (例如使用 Glide)
        // mediaMetadata?.artworkUri?.let { artworkUri ->
        //     Glide.with(this).load(artworkUri).placeholder(R.drawable.default_album_art).into(viewBinding.albumArtImageView)
        // } ?: viewBinding.albumArtImageView.setImageResource(R.drawable.default_album_art)
    }

    private fun formatDuration(durationMs: Long): String {
        val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(durationMs.coerceAtLeast(0))
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }
}


