package com.superman.drilldemo.play
import android.media.audiofx.Equalizer // 导入 Equalizer 类
import androidx.media3.common.C // 导入 Media3 常量，如 AUDIO_SESSION_ID_UNSET
import androidx.media3.exoplayer.ExoPlayer // 导入 ExoPlayer 类
import android.util.Log // 导入 Log 类用于日志记录
import androidx.media3.common.util.UnstableApi

@UnstableApi
class MyMediaPlayerManager(private val player: ExoPlayer) { // 构造函数，接收一个 ExoPlayer 实例

    // `equalizer` 变量用于存储 Equalizer 对象的实例。
    // 设为 public 是为了在 PlaySongService 中的 getAllPresetNames 示例中能直接访问 numberOfPresets。
    // 通常可以保持 private 或 internal。
    var equalizer: Equalizer? = null

    // `currentAudioSessionId` 用于存储当前 ExoPlayer 的音频会话 ID。
    private var currentAudioSessionId: Int = C.AUDIO_SESSION_ID_UNSET

    // `init` 模块在创建 `MyMediaPlayerManager` 实例时执行。
    init {
        // ... (这里是初始化和监听 audioSessionId 变化的代码) ...
        // 主要任务:
        // 1. 监听 `ExoPlayer` 的 `audioSessionId` 变化。
        //    当 ExoPlayer 开始播放或切换音轨时，audioSessionId 可能会改变。
        // 2. 如果 `audioSessionId` 发生变化并且是有效的，就调用 `setupEqualizer` 方法
        //    来创建或重新配置均衡器。
        // 3. 如果 `audioSessionId` 变为无效（例如播放停止），就调用 `releaseEqualizer` 方法
        //    来释放均衡器资源。
        // 4. 尝试在 `MyMediaPlayerManager` 初始化时就配置均衡器，
        //    以防 ExoPlayer 已经有一个有效的 `audioSessionId`。
    }

    // `setupEqualizer` 方法用于创建和初始化一个新的 `Equalizer` 效果实例。
    // 参数 `audioSessionId`: 从 ExoPlayer 获取到的有效音频会话 ID。
    // 这个方法通常由 `init` 块或 `audioSessionId` 的监听器在获得有效的会话 ID 时调用。
    private fun setupEqualizer(audioSessionId: Int) {
        // 1. 释放任何先前存在的均衡器实例。
        //    这很重要，特别是当 `audioSessionId` 改变时，确保旧的均衡器被清理。
        releaseEqualizer()

        try {
            // 2. 创建 `Equalizer` 对象。
            //    - 第一个参数 `0` 是效果的优先级，通常设为0。
            //    - 第二个参数 `audioSessionId` 将这个均衡器效果附加到 ExoPlayer 的音频流上。
            equalizer = Equalizer(0, audioSessionId)

            // 3. 启用均衡器。非常重要！如果不启用，后续对频段级别的所有更改都不会生效。
            equalizer?.enabled = true

            Log.d("Equalizer", "均衡器已为音频会话 ID 初始化: $audioSessionId")

            // 可选操作: 在这里可以应用一些初始配置，
            // 例如加载一个默认的预设，或者恢复用户之前保存的频段级别设置。

        } catch (e: Exception) {
            // 错误处理: 创建 `Equalizer` 对象可能会因为各种原因失败（例如，设备不支持，会话ID无效）。
            Log.e("Equalizer", "初始化均衡器时出错", e)
            equalizer = null // 如果创建失败，确保 `equalizer` 变量为 null。
        }
    }

    // `releaseEqualizer` 方法用于释放 `Equalizer` 效果所占用的系统资源。
    // 调用这个方法非常重要，尤其是在不再需要均衡器时，或者当 ExoPlayer 被销毁时，
    // 以避免内存泄漏和资源浪费。
    fun releaseEqualizer() {
        equalizer?.release() // 调用 `Equalizer`对象的 `release()` 方法来释放底层资源。
        equalizer = null     // 将 `equalizer` 变量设为 null，表示当前没有活动的均衡器实例。
        Log.d("Equalizer", "均衡器已释放")
    }

    // --- 控制均衡器的方法 (类的公共 API，供外部调用) ---

    // `setBandLevel` 方法用于为指定的频段设置增益级别。
    // 参数:
    //   - `band`: Short 类型，要调整的频段的索引 (0 代表第一个频段，1 代表第二个，以此类推)。
    //   - `levelMillibels`: Short 类型，期望的增益级别，单位是毫分贝 (mB)。
    //                     (100 mB = 1 dB)。正值增加该频段的音量，负值则减小。
    fun setBandLevel(band: Short, levelMillibels: Short) {
        // 仅当均衡器已初始化并启用时才执行操作。
        if (equalizer?.enabled == true) {
            try {
                equalizer?.setBandLevel(band, levelMillibels)
                Log.d("Equalizer", "设置频段 $band 的级别为 $levelMillibels mB")
            } catch (e: Exception) {
                // 可能的错误包括：频段索引无效，或者设置的级别超出了设备允许的范围。
                Log.e("Equalizer", "设置频段 $band 级别时出错", e)
            }
        } else {
            Log.w("Equalizer", "均衡器未启用或未初始化，无法设置频段级别。")
        }
    }

    // `getBandLevel` 方法用于获取指定频段当前的增益级别。
    // 参数:
    //   - `band`: Short 类型，要查询的频段的索引。
    // 返回: Short? 类型，指定频段当前的增益级别 (单位: 毫分贝)，
    //        如果出错或均衡器未准备好，则返回 null。
    fun getBandLevel(band: Short): Short? {
        return if (equalizer?.enabled == true) {
            try {
                equalizer?.getBandLevel(band)
            } catch (e: Exception) {
                Log.e("Equalizer", "获取频段 $band 级别时出错", e)
                null
            }
        } else {
            Log.w("Equalizer", "均衡器未启用或未初始化，无法获取频段级别。")
            null
        }
    }

    // `getNumberOfBands` 方法用于获取当前设备上均衡器支持的总频段数量。
    // 返回: Short? 类型，频段的数量。如果均衡器未准备好，则返回 null。
    // 这个值对于动态构建用户界面（例如，决定需要创建多少个 SeekBar）至关重要。
    fun getNumberOfBands(): Short? {
        return equalizer?.numberOfBands
    }

    // `getBandLevelRange` 方法用于获取可以应用于任何频段的增益级别范围 (最小值和最大值)。
    // 返回: ShortArray? 类型，一个包含两个 `short` 值的数组: `[最小级别毫分贝, 最大级别毫分贝]`。
    //        如果均衡器未准备好，则返回 null。
    //        例如: `[-1500, 1500]` 表示增益范围是 -15dB 到 +15dB。
    //        这个信息对于在 UI 中设置 SeekBar 的最小和最大限制很有用。
    fun getBandLevelRange(): ShortArray? {
        return equalizer?.bandLevelRange
    }

    // `getCenterFrequency` 方法用于获取指定频段的中心频率。
    // 参数:
    //   - `band`: Short 类型，要查询的频段的索引。
    // 返回: Int? 类型，指定频段的中心频率，单位是毫赫兹 (mHz)。如果出错，则返回 null。
    //        为了在 UI 中显示，通常会将其转换为赫兹 (Hz) (通过除以 1000)。
    //        例如: 60000 mHz = 60 Hz。
    fun getCenterFrequency(band: Short): Int? {
        return if (equalizer?.enabled == true) {
            try {
                equalizer?.getCenterFreq(band)
            } catch (e: Exception) {
                Log.e("Equalizer", "获取频段 $band 中心频率时出错", e)
                null
            }
        } else {
            Log.w("Equalizer", "均衡器未启用或未初始化，无法获取中心频率。")
            null
        }
    }

    // `setPreset` 方法用于应用一个预定义的均衡器预设。
    // 参数:
    //   - `presetIndex`: Short 类型，要应用的预设的索引 (0 代表第一个预设，以此类推)。
    //                  可以通过 `equalizer.numberOfPresets` 获取预设的总数。
    fun setPreset(presetIndex: Short) {
        if (equalizer?.enabled == true) {
            try {
                // 检查预设索引是否在有效范围内。
                if (presetIndex < (equalizer?.numberOfPresets ?: 0)) {
                    equalizer?.usePreset(presetIndex)
                    Log.d("Equalizer", "正在使用预设: ${getPresetName(presetIndex)}")
                } else {
                    Log.w("Equalizer", "预设索引 $presetIndex 超出范围。")
                }
            } catch (e: Exception) {
                Log.e("Equalizer", "设置预设 $presetIndex 时出错", e)
            }
        } else {
            Log.w("Equalizer", "均衡器未启用或未初始化，无法设置预设。")
        }
    }

    // `getPresetName` 方法用于获取指定预设的可读名称。
    // 参数:
    //   - `presetIndex`: Short 类型，要查询的预设的索引。
    // 返回: String? 类型，预设的名称 (例如 "Rock", "Pop", "Jazz")。如果出错，则返回 null。
    //        这个方法对于在 UI 中（例如 Spinner 或预设选择列表）显示预设名称很有用。
    fun getPresetName(presetIndex: Short): String? {
        return if (equalizer?.enabled == true) {
            try {
                if (presetIndex < (equalizer?.numberOfPresets ?: 0)) {
                    equalizer?.getPresetName(presetIndex)
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.e("Equalizer", "获取预设索引 $presetIndex 名称时出错", e)
                null
            }
        } else {
            Log.w("Equalizer", "均衡器未启用或未初始化，无法获取预设名称。")
            null
        }
    }

    // --- 示例函数和辅助工具 ---

    // `setBassBoostExample` 方法是一个示例，演示如何实现一个简单的低音增强功能。
    // 它假设第一个频段 (索引 0) 是控制低音的频段。
    // 参数:
    //   - `boostMillibels`: Short 类型，低音增强的量，单位是毫分贝。默认为 300mB (+3dB)。
    fun setBassBoostExample(boostMillibels: Short = 300) {
        val numberOfBands = getNumberOfBands()
        // 确保至少有一个频段可用。
        if (numberOfBands != null && numberOfBands > 0) {
            val bandLevelRange = getBandLevelRange()
            if (bandLevelRange != null) {
                val minLevel = bandLevelRange[0]
                val maxLevel = bandLevelRange[1]
                // 确保目标增强级别在设备允许的范围内。
                val targetLevel = Math.min(maxLevel.toInt(), Math.max(minLevel.toInt(), boostMillibels.toInt())).toShort()
                // 对第一个频段应用增强。
                setBandLevel(0, targetLevel)
                Log.d("Equalizer", "低音频段 (0) 设置为 $targetLevel mB")
            }
        }
    }

    // `logBandInfo` 方法用于在 Logcat 中打印出所有均衡器频段的详细信息。
    // 这对于调试和了解特定设备上均衡器的能力非常有用。
    fun logBandInfo() {
        // 如果无法获取频段数量，则直接返回。
        val numBands = getNumberOfBands() ?: return
        // 如果无法获取频段级别范围，则直接返回。
        val bandLevelRange = getBandLevelRange() ?: return

        Log.d("Equalizer", "频段数量: $numBands")
        Log.d("Equalizer", "频段级别范围: ${bandLevelRange[0]}mB 到 ${bandLevelRange[1]}mB")

        // 遍历每个频段。
        for (i in 0 until numBands) {
            val band = i.toShort()
            // 获取中心频率并转换为赫兹。
            val centerFreqHz = (getCenterFrequency(band) ?: 0) / 1000
            // 获取当前频段的级别。
            val currentLevel = getBandLevel(band)
            Log.d("Equalizer", "频段 $i: 中心频率=${centerFreqHz}Hz, 当前级别=${currentLevel}mB")
        }
    }
}
