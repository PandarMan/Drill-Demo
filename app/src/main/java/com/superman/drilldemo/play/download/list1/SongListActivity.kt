package com.superman.drilldemo.play.download.list1


import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.superman.drilldemo.databinding.ActivitySongListBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@UnstableApi
class SongListActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySongListBinding
    private val downloadViewModel: DownloadViewModel by viewModels()
    private lateinit var songsAdapter: SongsAdapter

    // MediaController for interacting with PlaybackService
    private var mediaControllerFuture: ListenableFuture<MediaController>? = null
    private val mediaController: MediaController?
        get() = if (mediaControllerFuture?.isDone == true && mediaControllerFuture?.isCancelled == false) {
            try {
                mediaControllerFuture?.get()
            } catch (e: Exception) {
                Log.e("SongListActivity", "Error getting MediaController", e)
                null
            }
        } else null

    // Sample song list (replace with your actual data source)
    private val sampleSongs = listOf(
        Song(
            "song_001",
            "第一首歌 (MP3)",
            "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
            "艺术家1"
        ),
        Song(
            "song_002",
            "第二首歌 (MP3)",
            "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3",
            "艺术家2"
        ),
        Song(
            "song_003",
            "Big Buck Bunny (MP4 Video)",
            "https://rr5---sn-p5qddn7k.googlevideo.com/videoplayback?expire=1758273569&ei=wMvMaJyTOLmD0_wPgcH86As&ip=67.43.56.56&id=o-AFwtZ13DxMmpFckZyefGBaLIfvIdYNfAIo2juj9E214F&itag=137&source=youtube&requiressl=yes&xpc=EgVo2aDSNQ%3D%3D&cps=73&met=1758251968%2C&mh=eI&mm=31%2C26&mn=sn-p5qddn7k%2Csn-qja5mc-5h&ms=au%2Conr&mv=u&mvi=5&pl=27&rms=au%2Cau&gcr=us&bui=ATw7iSUsMilqWnOtoFNaasCIFKwmoEHOaULetQTXreCxrMPauswjlPwIh5MiOs62bsUSbuPySw13gaX6&spc=hcYD5TAGBUY-EldsN7_i&vprv=1&svpuc=1&mime=video%2Fmp4&rqh=1&gir=yes&clen=1145235&dur=231.800&lmt=1695914725276548&mt=1758250318&fvip=4&keepalive=yes&fexp=51552689%2C51565116%2C51565681%2C51580968&c=ANDROID&txp=4532434&sparams=expire%2Cei%2Cip%2Cid%2Citag%2Csource%2Crequiressl%2Cxpc%2Cgcr%2Cbui%2Cspc%2Cvprv%2Csvpuc%2Cmime%2Crqh%2Cgir%2Cclen%2Cdur%2Clmt&sig=AJfQdSswRQIhAKx0ojKR3__6EvR_z7tVZ3klr8PYStrHG7-cJNkORa13AiB-Nz20bl2uAmvDjQfwfx-IYvoUqrUc7UxtQhUUfSFPIA%3D%3D&lsparams=cps%2Cmet%2Cmh%2Cmm%2Cmn%2Cms%2Cmv%2Cmvi%2Cpl%2Crms&lsig=APaTxxMwRgIhAKF1L3tQusU8tQNc-fZ9TL5hA36QYaq6lRATIiDUfy38AiEAgcORt3_BNfJynqVqQMUzmJzzOus57kd7Ev8buOrgLHg%3D",
            "Blender Foundation"
        ),
        Song(
            "song_004",
            "Elephants Dream (MP4 Video)",
            "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
            "Blender Foundation"
        ),
        Song("song_005", "失效链接测试", "https://example.com/nonexistent.mp3", "测试者")
    )

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Toast.makeText(this, "通知权限已授予", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "通知权限被拒绝", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySongListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        setupRecyclerView()
        loadSongs()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun initializeMediaController() {
        if (mediaControllerFuture == null || mediaControllerFuture!!.isDone) { // Avoid re-initializing if already connecting/connected
            val sessionToken = SessionToken(this, ComponentName(this, PlaybackService::class.java))
            mediaControllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
            mediaControllerFuture?.addListener({
                val controller = mediaController // Use the property to get the controller
                if (controller != null) {
                    // binding.playerView.player = controller // If using PlayerView
                    // binding.playerView.visibility = View.VISIBLE

                    controller.addListener(object : Player.Listener {
                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            Log.d(
                                "SongListActivity",
                                "MediaController: Player isPlaying: $isPlaying"
                            )
                            // Update UI based on playback state from the service
                            // e.g., change a global play/pause button icon in the activity
                        }
                        // You can listen to other events like onMediaItemTransition, onPlaybackStateChanged
                    })
                    Log.d("SongListActivity", "MediaController connected to PlaybackService.")
                } else {
                    Log.e(
                        "SongListActivity",
                        "Failed to connect to MediaController after future completed."
                    )
                }
            }, ContextCompat.getMainExecutor(this)) // Ensures listener runs on main thread
        }
    }

    private fun setupRecyclerView() {
        songsAdapter = SongsAdapter(
            songs = emptyList(),
            viewModel = downloadViewModel,
            lifecycleOwner = this,
            onItemClick = { song -> playSongUsingService(song) },
            onDownloadClick = { song ->
                downloadViewModel.startOrResumeDownload(song.id, song.url, song.title)
                // Toast.makeText(this, "开始/继续下载: ${song.title}", Toast.LENGTH_SHORT).show() // Feedback can be via LiveData
            },
            onPauseClick = { song ->
                downloadViewModel.pauseDownload(song.id) // This pauses all downloads in DownloadManager
                // Toast.makeText(this, "暂停所有下载", Toast.LENGTH_SHORT).show()
            },
            onRemoveClick = { song ->
                downloadViewModel.removeDownload(song.id)
                // Toast.makeText(this, "移除下载: ${song.title}", Toast.LENGTH_SHORT).show()
            }
        )
        binding.songsRecyclerView.adapter = songsAdapter
    }

    private fun loadSongs() {
        binding.loadingProgressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            delay(500) // Simulate delay
            songsAdapter.submitList(sampleSongs)
            binding.loadingProgressBar.visibility = View.GONE
        }
    }

    private fun playSongUsingService(song: Song) {
        val controller = this.mediaController // Use the property
        if (controller == null) {
            Toast.makeText(this, "播放器服务未连接，请稍候...", Toast.LENGTH_SHORT).show()
            if (mediaControllerFuture == null || mediaControllerFuture!!.isDone) {
                initializeMediaController() // Attempt to connect if not already trying
            }
            return
        }

        val downloadStateLiveData = downloadViewModel.getDownloadStateLiveData(song.id)
        val currentUiState = downloadStateLiveData.value
        if (currentUiState != null && currentUiState.status == androidx.media3.exoplayer.offline.Download.STATE_COMPLETED) {
            Toast.makeText(this, "播放已下载 (服务): ${song.title}", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "在线播放 (服务): ${song.title}", Toast.LENGTH_SHORT).show()
        }

        val mediaItem = MediaItem.Builder()
            .setMediaId(song.id)
            .setUri(song.url)
            .build()

        // Send commands to the service
        controller.setMediaItem(mediaItem)
        // Service's MediaSession.Callback.onAddMediaItems will handle this,
        // and it should call player.prepare() and optionally player.play()
        // So, we might not need to call prepare and play directly on the controller here
        // if the service's callback handles it.
        // However, it's common to call prepare and play on the controller for explicitness.
        controller.prepare()
        controller.play()

        // if (binding.playerView.visibility == View.GONE) {
        //     binding.playerView.visibility = View.VISIBLE
        // }
    }

    override fun onStart() {
        super.onStart()
        // Initialize and connect to the MediaController when the Activity (re)starts
        initializeMediaController()
    }

    override fun onStop() {
        super.onStop()
        // It's generally better to release the controller future in onStop
        // to allow the service to continue playing in the background if it's a foreground service.
        // The service itself will manage the ExoPlayer instance.
        // binding.playerView.player = null // Important if using PlayerView
    }

    override fun onDestroy() {
        super.onDestroy()
        // Release the MediaController future to prevent leaks
        mediaControllerFuture?.let {
            MediaController.releaseFuture(it)
            mediaControllerFuture = null
        }
        Log.d("SongListActivity", "MediaController future released.")

        if (isFinishing) {
            Log.d("SongListActivity", "Activity is finishing, releasing global resources.")
            DownloadUtil.release() // Release download manager and its cache
            PlayerManager.releaseStreamingResources() // Release PlayerManager's streaming cache resources

            // Consider whether to explicitly stop the PlaybackService.
            // If it's designed to stop when idle and no longer bound, it might handle itself.
            // stopService(Intent(this, PlaybackService::class.java))
        }
    }

    companion object {
        fun start(content: Context) {
            content.startActivity(Intent(content, SongListActivity::class.java))
        }
    }
}
