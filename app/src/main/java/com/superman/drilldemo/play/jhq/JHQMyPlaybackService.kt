package com.superman.drilldemo.play.jhq

// MyPlaybackService.kt

import androidx.media3.common.AudioAttributes
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import android.os.Bundle
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.superman.drilldemo.play.MyMediaPlayerManager

// 导入你的 MyMediaPlayerManager
// import com.yourpackage.MyMediaPlayerManager

@UnstableApi
class JHQMyPlaybackService : MediaSessionService() {

    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSession
    private lateinit var mediaPlayerManager: MyMediaPlayerManager // 你的均衡器管理器

    // --- 自定义命令 ---
    companion object {
        // 命令：设置指定频段的级别
        const val CUSTOM_COMMAND_SET_EQ_BAND_LEVEL = "com.example.action.SET_EQ_BAND_LEVEL"
        const val KEY_EQ_BAND_INDEX = "eq_band_index"
        const val KEY_EQ_BAND_LEVEL = "eq_band_level"

        // 命令：获取均衡器信息 (示例，可以根据需要拆分或扩展)
        const val CUSTOM_COMMAND_GET_EQ_INFO = "com.example.action.GET_EQ_INFO"
        const val KEY_EQ_NUM_BANDS = "eq_num_bands"
        const val KEY_EQ_BAND_RANGE_MIN = "eq_band_range_min"
        const val KEY_EQ_BAND_RANGE_MAX = "eq_band_range_max"
        const val KEY_EQ_PRESET_NAMES = "eq_preset_names" // ArrayList<String>
        const val KEY_EQ_CURRENT_LEVELS = "eq_current_levels" // ShortArray

        // 命令：应用预设
        const val CUSTOM_COMMAND_APPLY_EQ_PRESET = "com.example.action.APPLY_EQ_PRESET"
        const val KEY_EQ_PRESET_INDEX = "eq_preset_index"
    }

    override fun onCreate() {
        super.onCreate()

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .setHandleAudioBecomingNoisy(true)
            .build()

        // 初始化 MyMediaPlayerManager
        mediaPlayerManager = MyMediaPlayerManager(player)

        mediaSession = MediaSession.Builder(this, player)
            .setCallback(MyMediaSessionCallback()) // 设置回调以处理自定义命令
            .build()

        // 可以在这里加载用户上次的均衡器设置或默认设置
        // mediaPlayerManager.logBandInfo() // 调试用
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = mediaSession

    override fun onDestroy() {
        mediaPlayerManager.releaseEqualizer() // 释放均衡器资源
        mediaSession.run {
            this.player.release() // MediaSessionService 会处理 Player 的释放，但显式调用也无妨
            release()
        }
        super.onDestroy()
    }

    // --- MediaSession.Callback 用于处理自定义命令 ---
    private inner class MyMediaSessionCallback : MediaSession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            // 接受连接并添加自定义命令
            val availableSessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SessionCommand(CUSTOM_COMMAND_SET_EQ_BAND_LEVEL, Bundle.EMPTY))
                .add(SessionCommand(CUSTOM_COMMAND_GET_EQ_INFO, Bundle.EMPTY))
                .add(SessionCommand(CUSTOM_COMMAND_APPLY_EQ_PRESET, Bundle.EMPTY))
                .build()

            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(availableSessionCommands)
                .build()
        }


        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                CUSTOM_COMMAND_SET_EQ_BAND_LEVEL -> {
                    val bandIndex = args.getShort(KEY_EQ_BAND_INDEX, -1)
                    val bandLevel = args.getShort(KEY_EQ_BAND_LEVEL, 0)
                    if (bandIndex != (-1).toShort()) {
                        mediaPlayerManager.setBandLevel(bandIndex, bandLevel)
                        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                    }
                    return Futures.immediateFuture(SessionResult(SessionError.ERROR_BAD_VALUE))
                }

                CUSTOM_COMMAND_GET_EQ_INFO -> {
                    val numBands = mediaPlayerManager.getNumberOfBands() ?: 0
                    val bandRange = mediaPlayerManager.getBandLevelRange() // [min, max]
                    val presetNames = ArrayList<String>()
                    val numPresets = mediaPlayerManager.equalizer?.numberOfPresets ?: 0
                    if (numPresets > 0) {
                        for (i in 0 until numPresets) {
                            presetNames.add(mediaPlayerManager.getPresetName(i.toShort()) ?: "Preset $i")
                        }
                    }
                    val currentLevels = ShortArray(numBands.toInt()) { index ->
                        mediaPlayerManager.getBandLevel(index.toShort()) ?: 0
                    }


                    val resultData = Bundle().apply {
                        putShort(KEY_EQ_NUM_BANDS, numBands)
                        putShort(KEY_EQ_BAND_RANGE_MIN, bandRange?.get(0) ?: -1500)
                        putShort(KEY_EQ_BAND_RANGE_MAX, bandRange?.get(1) ?: 1500)
                        putStringArrayList(KEY_EQ_PRESET_NAMES, presetNames)
                        putShortArray(KEY_EQ_CURRENT_LEVELS, currentLevels)
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS, resultData))
                }

                CUSTOM_COMMAND_APPLY_EQ_PRESET -> {
                    val presetIndex = args.getShort(KEY_EQ_PRESET_INDEX, -1)
                    if (presetIndex != (-1).toShort()) {
                        mediaPlayerManager.setPreset(presetIndex)
                        // 重要：应用预设后，频段的级别会改变
                        // 客户端（UI）在收到成功回调后，应该重新获取 EQ_INFO 或至少是 CURRENT_LEVELS
                        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                    }
                    return Futures.immediateFuture(SessionResult(SessionError.ERROR_BAD_VALUE))
                }
            }
            return super.onCustomCommand(session, controller, customCommand, args) // 处理其他命令
        }

        // 你可以覆盖其他 MediaSession.Callback 方法来处理播放控制等
        // override fun onPlay(session: MediaSession, controller: MediaSession.ControllerInfo): ListenableFuture<SessionResult> { ... }
        // override fun onPause(session: MediaSession, controller: MediaSession.ControllerInfo): ListenableFuture<SessionResult> { ... }
    }
}
