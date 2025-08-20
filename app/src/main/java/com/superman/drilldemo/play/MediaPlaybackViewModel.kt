import android.app.Application
import android.content.ComponentName
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.superman.drilldemo.play.PlaySongService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutionException

// Supongamos que tienes PlaySongService en este paquete
// import com.superman.drilldemo.play.PlaySongService (Asegúrate que la ruta sea correcta)

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class MediaPlaybackViewModel(application: Application) : AndroidViewModel(application) {

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _playerState = MutableStateFlow(Player.STATE_IDLE)
    val playerState: StateFlow<Int> = _playerState.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentMediaMetadata = MutableStateFlow<MediaMetadata?>(null)
    val currentMediaMetadata: StateFlow<MediaMetadata?> = _currentMediaMetadata.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private var mediaControllerFuture: ListenableFuture<MediaController>? = null
    var mediaController: MediaController? = null
        private set // Solo el ViewModel puede modificarlo

    private var positionUpdateJob: Job? = null

    init {
        // Asume que PlaySongService está en el mismo paquete o puedes obtener su ComponentName
        // Reemplaza con el ComponentName correcto de tu PlaySongService
        val componentName = ComponentName(getApplication(), PlaySongService::class.java)
        val sessionToken = SessionToken(
            getApplication(),
            // Debes reemplazar 'com.example.yourapp.PlaySongService' con el nombre completo de tu servicio.
            // Si tu PlaySongService está en com.superman.drilldemo.play:
//            ComponentName(getApplication(), "com.superman.drilldemo.play.PlaySongService")
            // O si conoces la clase directamente (mejor si está en el mismo módulo):
            // ComponentName(getApplication(), PlaySongService::class.java)
            componentName
        )
        mediaControllerFuture = MediaController.Builder(getApplication(), sessionToken).buildAsync()
        mediaControllerFuture?.addListener(
            {
                try {
                    val controller = mediaControllerFuture?.get() // Puede ser null si se cancela
                    if (controller != null) {
                        this.mediaController = controller
                        _isConnected.value = true
                        registerPlayerListener(controller)
                        updateInitialState(controller)
                    } else {
                        _isConnected.value = false
                    }
                } catch (e: ExecutionException) {
                    // Manejar error de conexión
                    _isConnected.value = false
                    e.printStackTrace()
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt() // Restaurar estado de interrupción
                    _isConnected.value = false
                    e.printStackTrace()
                }
            },
            MoreExecutors.directExecutor()
        )
    }

    private fun registerPlayerListener(controller: MediaController) {
        controller.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                if (isPlaying) {
                    startPositionUpdates()
                } else {
                    stopPositionUpdates()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                println("--->>>>onPlaybackStateChanged$playbackState,")
                _playerState.value = playbackState
                if (playbackState == Player.STATE_READY || playbackState == Player.STATE_BUFFERING) {
                    _duration.value = controller.duration
                }
            }

            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                _currentMediaMetadata.value = mediaMetadata
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                _currentMediaMetadata.value = mediaItem?.mediaMetadata
                _currentPosition.value = 0L // Reiniciar posición para el nuevo item
                _duration.value = controller.duration
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                    reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
                    if (controller.isPlaying) startPositionUpdates() // Asegurar que las actualizaciones continúen si está reproduciendo
                }
            }
        })
    }

    private fun updateInitialState(controller: MediaController) {
        _isPlaying.value = controller.isPlaying
        _playerState.value = controller.playbackState
        _currentMediaMetadata.value = controller.mediaMetadata
        _currentPosition.value = controller.currentPosition
        _duration.value = controller.duration
        if (controller.isPlaying) {
            startPositionUpdates()
        }
    }

    private fun startPositionUpdates() {
        stopPositionUpdates() // Detener cualquier actualización anterior
        positionUpdateJob = viewModelScope.launch {
            while (isActive && mediaController?.isPlaying == true) {
                _currentPosition.value = mediaController?.currentPosition ?: 0L
                delay(1000) // Actualizar cada segundo
            }
        }
    }

    private fun stopPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = null
    }

    // --- Métodos de control expuestos a la UI ---
    fun play() {
        mediaController?.play()
    }

    fun pause() {
        mediaController?.pause()
    }

    fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs)
        // Actualizar inmediatamente la UI, aunque el listener también lo hará
        _currentPosition.value = positionMs
    }

    fun playPauseToggle() {
        mediaController?.let {
            if (it.isPlaying) {
                it.pause()
            } else {
                // Si no hay media item, la UI debería manejar la carga de uno primero
                if (it.mediaItemCount == 0) {
                    // Aquí podrías tener lógica para añadir un MediaItem por defecto o
                    // indicar a la UI que necesita seleccionar una canción.
                    // Por simplicidad, asumimos que la UI ya ha cargado MediaItems.
                    // Si no es así, la UI debería llamar a un método como `setMediaItemAndPlay(mediaItem)`
                }
                it.play()
            }
        }
    }

    fun skipToNext() {
        mediaController?.seekToNextMediaItem()
    }

    fun skipToPrevious() {
        mediaController?.seekToPreviousMediaItem()
    }

    fun setMediaItem(mediaItem: MediaItem) {
        mediaController?.setMediaItem(mediaItem)
        mediaController?.prepare() // Preparar el nuevo MediaItem
    }

    fun setMediaItems(mediaItems: List<MediaItem>, startIndex: Int = 0, startPositionMs: Long = 0) {
        mediaController?.setMediaItems(mediaItems, startIndex, startPositionMs)
        mediaController?.prepare()
    }


    override fun onCleared() {
        super.onCleared()
        stopPositionUpdates()
        mediaControllerFuture?.let {
            MediaController.releaseFuture(it)
        }
        mediaController = null // Liberar la referencia
    }
}
