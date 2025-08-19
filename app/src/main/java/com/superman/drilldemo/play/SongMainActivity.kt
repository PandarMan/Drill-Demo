package com.superman.drilldemo // Asegúrate de que el nombre del paquete sea el correcto

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.superman.drilldemo.databinding.SongActivityMainBinding
import com.superman.drilldemo.play.PlaySongService
import java.util.concurrent.TimeUnit
// import com.superman.drilldemo.R // Importa tu archivo R si es necesario para los IDs de layout

class SongMainActivity : AppCompatActivity() {


    // private lateinit var titleTextView: TextView // Si quieres mostrar el título en la Activity

    private var mediaController: MediaController? = null
    private lateinit var controllerFuture: ListenableFuture<MediaController>

    private val sampleAudioUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3"
    private val sampleAudioUrl2 = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3"

    private val viewBinding by lazy { SongActivityMainBinding.inflate(layoutInflater) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(viewBinding.root)


        // titleTextView = findViewById(R.id.titleTextView) // Descomenta si tienes un TextView para el título


        viewBinding.playPauseButton.setOnClickListener {
            mediaController?.let { controller ->
                if (controller.isPlaying) {
                    controller.pause()
                } else {
                    if (controller.mediaItemCount == 0) {
                        // Construye MediaItems con metadatos para que la notificación los muestre
                        val mediaItem1 = MediaItem.Builder()
                            .setUri(Uri.parse(sampleAudioUrl))
                            .setMediaId("song1")
                            .setMediaMetadata(
                                androidx.media3.common.MediaMetadata.Builder()
                                    .setTitle("SoundHelix Song 1")
                                    .setArtist("SoundHelix")
                                    .setArtworkUri("https://upload-images.jianshu.io/upload_images/5809200-a99419bb94924e6d.jpg?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240".toUri())
                                    // .setAlbumArtUri(...) // Podrías añadir URI de artwork aquí
                                    .build()
                            )
                            .build()
                        val mediaItem2 = MediaItem.Builder()
                            .setUri(Uri.parse(sampleAudioUrl2))
                            .setMediaId("song2")
                            .setMediaMetadata(
                                androidx.media3.common.MediaMetadata.Builder()
                                    .setTitle("SoundHelix Song 2")
                                    .setArtist("SoundHelix")
                                    .build()
                            )
                            .build()
                        controller.setMediaItems(listOf(mediaItem1, mediaItem2))
                        controller.prepare()
                    }
                    controller.play()
                }
            }
        }

        viewBinding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    mediaController?.seekTo(progress.toLong())
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        val serviceIntent = Intent(this, PlaySongService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    override fun onStart() {
        super.onStart()
        val sessionToken = SessionToken(this, ComponentName(this, PlaySongService::class.java))
        controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture.addListener(
            {
                try {
                    mediaController = controllerFuture.get()
                    setupController()
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error getting MediaController", e)
                    Toast.makeText(this, "Error connecting to media service", Toast.LENGTH_SHORT).show()
                }
            },
            MoreExecutors.directExecutor()
        )
    }

    override fun onStop() {
        super.onStop()
        mediaController?.let {
            MediaController.releaseFuture(controllerFuture)
            mediaController = null
        }
    }

    private fun setupController() {
        mediaController?.let { controller ->
            updatePlayPauseButton(controller.isPlaying)
            updateSeekBar(controller.currentPosition, controller.duration)
            updateMediaMetadataUI(controller.mediaMetadata)

            controller.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    updatePlayPauseButton(isPlaying)
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY || playbackState == Player.STATE_BUFFERING) {
                        updateSeekBar(controller.currentPosition, controller.duration)
                    }
                    if (playbackState == Player.STATE_ENDED) {
                        viewBinding.seekBar.progress = 0
                        viewBinding.currentTimeTextView.text = formatDuration(0)
                        // titleTextView.text = "Player Idle"
                    }
                }

                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int
                ) {
                    updateSeekBar(newPosition.positionMs, controller.duration)
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    updateMediaMetadataUI(mediaItem?.mediaMetadata)
                    updateSeekBar(0, controller.duration) // Reset progress for new item
                }

                override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) {
                    updateMediaMetadataUI(mediaMetadata)
                }
            })
            scheduleSeekBarUpdate()
        }
    }

    private fun updatePlayPauseButton(isPlaying: Boolean) {
        viewBinding.playPauseButton.text = if (isPlaying) "Pause" else "Play"
    }

    private fun updateSeekBar(currentPosition: Long, duration: Long) {
        if (duration > 0) {
            viewBinding.seekBar.max = duration.toInt()
            viewBinding.seekBar.progress = currentPosition.toInt()
            viewBinding.currentTimeTextView.text = formatDuration(currentPosition)
            viewBinding.totalTimeTextView.text = formatDuration(duration)
        } else {
            viewBinding.seekBar.max = 100
            viewBinding.seekBar.progress = 0
            viewBinding.currentTimeTextView.text = formatDuration(0)
            viewBinding.totalTimeTextView.text = "--:--"
        }
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    private fun updateMediaMetadataUI(mediaMetadata: androidx.media3.common.MediaMetadata?) {
        // titleTextView.text = mediaMetadata?.title ?: "Unknown Title"
        Log.d("MainActivity", "UI MediaMetadata: Title: ${mediaMetadata?.title}, Artist: ${mediaMetadata?.artist}")
        // La notificación se actualiza desde el servicio
    }

    private val seekBarUpdateHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val seekBarUpdateRunnable = object : Runnable {
        override fun run() {
            mediaController?.let {
                if (it.isPlaying) {
                    updateSeekBar(it.currentPosition, it.duration)
                }
            }
            seekBarUpdateHandler.postDelayed(this, 1000)
        }
    }

    private fun scheduleSeekBarUpdate() {
        seekBarUpdateHandler.removeCallbacks(seekBarUpdateRunnable)
        seekBarUpdateHandler.post(seekBarUpdateRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        seekBarUpdateHandler.removeCallbacks(seekBarUpdateRunnable)
    }

    private fun formatDuration(durationMs: Long): String {
        val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(durationMs)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }
}

