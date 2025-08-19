package com.superman.drilldemo.play.jhq
import androidx.media3.common.AudioAttributes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.superman.drilldemo.play.MyMediaPlayerManager

@UnstableApi
class PlaySongService222 : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer // Asume que ExoPlayer se inicializa aquí
    private var mediaPlayerManager: MyMediaPlayerManager? = null // Tu clase

    override fun onCreate() {
        super.onCreate()

        // 1. Inicializar ExoPlayer
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(AudioAttributes.DEFAULT, true) // Habilita manejo de audio focus
            .setHandleAudioBecomingNoisy(true)
            .build()

        // 2. Inicializar MyMediaPlayerManager DESPUÉS de ExoPlayer
        mediaPlayerManager = MyMediaPlayerManager(player)

        // 3. Inicializar MediaSession
        mediaSession = MediaSession.Builder(this, player)
            // .setSessionActivity(pendingIntent) // Opcional: intent para abrir la UI
            // .setCallback(MyMediaSessionCallback()) // Opcional: para manejar comandos personalizados
            .build()

        // ... (Otra lógica del servicio, como notificaciones)
    }

    // Devuelve el MediaSession para que los MediaControllers puedan conectarse
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        mediaPlayerManager?.releaseEqualizer() //  crucial liberar el ecualizador
        mediaSession?.run {
            player.release() // Asegúrate de que el player se libere ANTES de liberar la sesión
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    // --- Métodos para controlar el ecualizador desde fuera (ej. a través de un ViewModel o Activity) ---
    // Estos métodos podrían ser llamados a través de un Binder si no usas MediaController para comandos personalizados,
    // o podrías implementar comandos personalizados en MediaSession.Callback.

    fun setEqBandLevel(band: Short, level: Short) {
        mediaPlayerManager?.setBandLevel(band, level)
    }

    fun getEqBandLevel(band: Short): Short? {
        return mediaPlayerManager?.getBandLevel(band)
    }

    fun getEqNumberOfBands(): Short? {
        return mediaPlayerManager?.getNumberOfBands()
    }

    fun getEqBandLevelRange(): ShortArray? {
        return mediaPlayerManager?.getBandLevelRange()
    }

    fun getEqCenterFrequency(band: Short): Int? {
        return mediaPlayerManager?.getCenterFrequency(band)
    }

    fun applyEqPreset(presetIndex: Short) {
        mediaPlayerManager?.setPreset(presetIndex)
    }

    fun getEqPresetName(presetIndex: Short): String? {
        return mediaPlayerManager?.getPresetName(presetIndex)
    }

    fun logEqBands() {
        mediaPlayerManager?.logBandInfo()
    }

    // Si necesitas que tu UI obtenga todos los presets:
    fun getAllPresetNames(): List<String> {
        val presets = mutableListOf<String>()
        val numPresets = mediaPlayerManager?.equalizer?.numberOfPresets ?: 0
        for (i in 0 until numPresets) {
            mediaPlayerManager?.getPresetName(i.toShort())?.let { presets.add(it) }
        }
        return presets
    }
}
