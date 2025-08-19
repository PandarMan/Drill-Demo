package com.superman.drilldemo.play // Asegúrate de que el nombre del paquete sea el correcto

import MediaPlaybackViewModel
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import com.superman.drilldemo.databinding.SongActivityMainBinding
import com.superman.drilldemo.play.PlaySongService
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class SongMainActivity22 : AppCompatActivity() {

    private val mediaViewModel: MediaPlaybackViewModel by viewModels()
    private val viewBinding by lazy { SongActivityMainBinding.inflate(layoutInflater) }

    private val sampleAudioUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3"
    private val sampleAudioUrl2 = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(viewBinding.root)

        // Iniciar el servicio si aún no está en ejecución (opcional, depende de tu lógica)
        val serviceIntent = Intent(this, PlaySongService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)


        setupClickListeners()
        observeViewModel()
    }

    private fun setupClickListeners() {
        viewBinding.playPauseButton.setOnClickListener {
            // Cargar items si es la primera vez o si la lista está vacía
            // Esta lógica podría ser más sofisticada
            if (mediaViewModel.mediaController?.mediaItemCount == 0) {
                val mediaItem1 = MediaItem.Builder()
                    .setUri(Uri.parse(sampleAudioUrl))
                    .setMediaId("song1")
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle("SoundHelix Song 1 (VM)")
                            .setArtist("SoundHelix")
                            .setArtworkUri("https://images2017.cnblogs.com/blog/1035009/201708/1035009-20170804105602772-686911367.png".toUri())
                            .build()
                    )
                    .build()
                val mediaItem2 = MediaItem.Builder()
                    .setUri(Uri.parse(sampleAudioUrl2))
                    .setMediaId("song2")
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle("SoundHelix Song 2 (VM)")
                            .setArtist("SoundHelix")
                            .build()
                    )
                    .build()
                mediaViewModel.setMediaItems(listOf(mediaItem1, mediaItem2)) // Carga la lista y prepara
                mediaViewModel.play() // Inicia la reproducción después de establecer los items
            } else {
                mediaViewModel.playPauseToggle()
            }
        }

        viewBinding.playPauseButton1.setOnClickListener { // Botón para reproducir la canción 2
            val mediaItem2 = MediaItem.Builder()
                .setUri(Uri.parse(sampleAudioUrl2))
                .setMediaId("song2")
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle("SoundHelix Song 2 (VM) - Direct")
                        .setArtist("SoundHelix")
                        .build()
                )
                .build()
            mediaViewModel.setMediaItem(mediaItem2)
            mediaViewModel.play()
        }


        viewBinding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    // Actualiza la UI localmente para una respuesta más rápida
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

        // Otros listeners como skipNext, skipPrevious si los tienes
        // viewBinding.nextButton.setOnClickListener { mediaViewModel.skipToNext() }
        // viewBinding.prevButton.setOnClickListener { mediaViewModel.skipToPrevious() }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    mediaViewModel.isConnected.collectLatest { connected ->
                        if (connected) {
                            Toast.makeText(this@SongMainActivity22, "Connected to Media Service", Toast.LENGTH_SHORT).show()
                            // Puedes habilitar controles de UI aquí
                        } else {
                            // Podrías mostrar un estado de "conectando..." o deshabilitar controles
                            Toast.makeText(this@SongMainActivity22, "Disconnected from Media Service", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                launch {
                    mediaViewModel.isPlaying.collectLatest { isPlaying ->
                        updatePlayPauseButton(isPlaying)
                    }
                }

                launch {
                    mediaViewModel.currentMediaMetadata.collectLatest { metadata ->
                        updateMediaMetadataUI(metadata)
                    }
                }

                launch {
                    mediaViewModel.currentPosition.collectLatest { position ->
                        // Solo actualiza si el usuario no está arrastrando la SeekBar
                        if (!viewBinding.seekBar.isPressed) {
                            viewBinding.seekBar.progress = position.toInt()
                            viewBinding.currentTimeTextView.text = formatDuration(position)
                        }
                    }
                }

                launch {
                    mediaViewModel.duration.collectLatest { duration ->
                        if (duration > 0) {
                            viewBinding.seekBar.max = duration.toInt()
                            viewBinding.totalTimeTextView.text = formatDuration(duration)
                        } else {
                            viewBinding.seekBar.max = 100 // Valor por defecto
                            viewBinding.totalTimeTextView.text = "--:--"
                        }
                    }
                }
                launch {
                    mediaViewModel.playerState.collectLatest { state ->
                        // Manejar cambios de estado del reproductor si es necesario
                        // Por ejemplo, mostrar un spinner de carga para Player.STATE_BUFFERING
                        Log.d("SongMainActivity", "Player state: $state")
                        if (state == Player.STATE_ENDED && !mediaViewModel.isPlaying.value) {
                            viewBinding.seekBar.progress = 0
                            viewBinding.currentTimeTextView.text = formatDuration(0)
                        }
                    }
                }
            }
        }
    }

    private fun updatePlayPauseButton(isPlaying: Boolean) {
        viewBinding.playPauseButton.text = if (isPlaying) "Pause" else "Play"
    }

    private fun updateMediaMetadataUI(mediaMetadata: MediaMetadata?) {
//        viewBinding.titleTextView.text = mediaMetadata?.title ?: "Unknown Title"
        // Actualiza otros elementos de la UI como el artista, artwork (si tienes un ImageView)
        Log.d("SongMainActivity", "UI MediaMetadata: Title: ${mediaMetadata?.title}, Artist: ${mediaMetadata?.artist}")

        // Ejemplo con Glide para cargar artwork (necesitarías añadir la dependencia de Glide)
        // mediaMetadata?.artworkUri?.let {
        //     Glide.with(this).load(it).into(viewBinding.artworkImageView)
        // } ?: run {
        //     viewBinding.artworkImageView.setImageResource(R.drawable.default_artwork) // un placeholder
        // }
    }

    private fun formatDuration(durationMs: Long): String {
        val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(durationMs)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    // Ya no necesitas onStart/onStop para manejar el MediaController directamente en la Activity
    // override fun onStart() { super.onStart() }
    // override fun onStop() { super.onStop() }

    // El Handler para actualizar la SeekBar ya no es necesario aquí, el ViewModel lo maneja.
    // override fun onDestroy() { super.onDestroy() }
}

