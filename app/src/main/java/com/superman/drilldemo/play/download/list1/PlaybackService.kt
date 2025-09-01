package com.superman.drilldemo.play.download.list1


import android.content.Intent
import android.util.Log // Make sure Log is imported
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

@UnstableApi
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer

    private inner class MySessionCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            Log.d("PlaybackService", "MediaSession onConnect from: ${controller.packageName}")
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .build()
        }


        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>
        ): ListenableFuture<List<MediaItem>> {
            Log.d("PlaybackService", "onAddMediaItems called with ${mediaItems.size} items.")
            if (mediaItems.isNotEmpty()) {
                // Here, we could also build a playlist if multiple items are added
                // For simplicity, setting the list directly and preparing the first one.
                player.setMediaItems(mediaItems, true) // true to reset position and window
                player.prepare()
                // Consider if auto-play is desired here or if play command should come separately
                // player.play()
                return Futures.immediateFuture(mediaItems) // Return the accepted items
            }
            return Futures.immediateFuture(ImmutableList.of()) // Return empty list if no items added
        }

        // You can override other callbacks like onSetMediaItem, etc.
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("PlaybackService", "Service onCreate")
        initializePlayerAndSession()
    }

    private fun initializePlayerAndSession() {
        // --- 核心修正：确保从 PlayerManager 获取工厂 ---
        val streamingCacheDataSourceFactory = try {
            PlayerManager.getStreamingCacheDataSourceFactory(applicationContext)
        } catch (e: IllegalStateException) {
            Log.e("PlaybackService", "Failed to get CacheDataSourceFactory from PlayerManager: ${e.message}")
            // Fallback or rethrow, for now, log and potentially let it crash to highlight the issue.
            // In a production app, you might have a default non-caching factory as a fallback.
            throw e // Or handle more gracefully
        }
        // ----------------------------------------------------

        val mediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(streamingCacheDataSourceFactory) // 使用从 PlayerManager 获取的工厂

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(AudioAttributes.DEFAULT, true) // Handle audio focus
            .setHandleAudioBecomingNoisy(true) // Pause on headphones unplugged
            .build()

        // Log player state changes for debugging
        player.addListener(object: Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                Log.d("PlaybackService", "Player state changed to: $playbackState")
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                Log.d("PlaybackService", "Player isPlaying changed to: $isPlaying")
            }
        })

        mediaSession = MediaSession.Builder(this, player)
            .setCallback(MySessionCallback())
            // You can set a session ID if needed: .setId("your_unique_session_id")
            .build()

        Log.d("PlaybackService", "Player and MediaSession initialized using PlayerManager's factory.")
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.d("PlaybackService", "Task Removed. Player playWhenReady: ${player.playWhenReady}, isPlaying: ${player.isPlaying}")
        // Stop the service if it's not playing and the task is removed
        if (!player.isPlaying && !player.playWhenReady) { // More robust check
            stopSelf()
            Log.d("PlaybackService", "Service stopping itself as it's idle and task removed.")
        }
        // If you want the service to stop even if paused but not actively playing, adjust the condition.
    }

    override fun onDestroy() {
        Log.d("PlaybackService", "Service onDestroy: Releasing player and session.")
        mediaSession?.run {
            player.release() // Release the player
            release()        // Release the MediaSession
            mediaSession = null
        }
        super.onDestroy()
    }
}

