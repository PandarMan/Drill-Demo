//package com.superman.drilldemo.play.jhq
//
//// PlayerViewModel.kt (或直接在 Activity/Fragment 中)
//import android.content.ComponentName
//import android.content.Context
//import android.os.Bundle
//import androidx.annotation.OptIn
//import androidx.core.content.ContextCompat
//import androidx.lifecycle.LiveData
//import androidx.lifecycle.MutableLiveData
//import androidx.lifecycle.ViewModel
//import androidx.lifecycle.viewModelScope
//import androidx.media3.common.util.UnstableApi
//import androidx.media3.session.MediaController
//import androidx.media3.session.SessionCommand
//import androidx.media3.session.SessionResult
//import androidx.media3.session.SessionToken
//import com.google.common.util.concurrent.ListenableFuture
//import kotlinx.coroutines.launch
//
//// 定义一个数据类来持有均衡器信息
//data class EqualizerInfo(
//    val numBands: Short = 0,
//    val minLevel: Short = -1500,
//    val maxLevel: Short = 1500,
//    val presetNames: List<String> = emptyList(),
//    val currentLevels: ShortArray = ShortArray(0)
//) {
//    // 重写 equals 和 hashCode 对于 LiveData 更新很重要
//    override fun equals(other: Any?): Boolean {
//        if (this === other) return true
//        if (javaClass != other?.javaClass) return false
//        other as EqualizerInfo
//        if (numBands != other.numBands) return false
//        if (minLevel != other.minLevel) return false
//        if (maxLevel != other.maxLevel) return false
//        if (presetNames != other.presetNames) return false
//        if (!currentLevels.contentEquals(other.currentLevels)) return false
//        return true
//    }
//    override fun hashCode(): Int {
//        var result = numBands.toInt()
//        result = 31 * result + minLevel
//        result = 31 * result + maxLevel
//        result = 31 * result + presetNames.hashCode()
//        result = 31 * result + currentLevels.contentHashCode()
//        return result
//    }
//}
//
//
//class PlayerViewModel(private val applicationContext: Context) : ViewModel() {
//
//    private var mediaController: MediaController? = null
//
//    private val _equalizerInfo = MutableLiveData<EqualizerInfo?>()
//    val equalizerInfo: LiveData<EqualizerInfo?> = _equalizerInfo
//
//    private val _commandError = MutableLiveData<String?>() // 用于显示错误
//    val commandError: LiveData<String?> = _commandError
//
//
//    init {
//        initializeMediaController()
//    }
//
//    @OptIn(UnstableApi::class)
//    private fun initializeMediaController() {
//        val sessionToken = SessionToken(applicationContext, ComponentName(applicationContext, MyPlaybackService::class.java))
//        val controllerFuture = MediaController.Builder(applicationContext, sessionToken).buildAsync()
//        controllerFuture.addListener(
//            {
//                try {
//                    mediaController = controllerFuture.get()
//                    // 控制器连接成功后，获取初始均衡器信息
//                    fetchEqualizerInfo()
//                } catch (e: Exception) {
//                    _commandError.postValue("Failed to connect MediaController: ${e.message}")
//                }
//            },
//            { runnable -> ContextCompat.getMainExecutor(applicationContext).execute(runnable) } // 在主线程执行
//        )
//    }
//
//    @OptIn(UnstableApi::class)
//    fun fetchEqualizerInfo() {
//        viewModelScope.launch {
//            val controller = mediaController ?: return@launch
//            val command = SessionCommand(MyPlaybackService.CUSTOM_COMMAND_GET_EQ_INFO, Bundle.EMPTY)
//            try {
//                val result = controller.sendCustomCommand(command, Bundle.EMPTY).await()
//                if (result.resultCode == SessionResult.RESULT_SUCCESS) {
//                    val data = result.resultData
//                    _equalizerInfo.postValue(
//                        EqualizerInfo(
//                            numBands = data.getShort(MyPlaybackService.KEY_EQ_NUM_BANDS, 0),
//                            minLevel = data.getShort(MyPlaybackService.KEY_EQ_BAND_RANGE_MIN, -1500),
//                            maxLevel = data.getShort(MyPlaybackService.KEY_EQ_BAND_RANGE_MAX, 1500),
//                            presetNames = data.getStringArrayList(MyPlaybackService.KEY_EQ_PRESET_NAMES) ?: emptyList(),
//                            currentLevels = data.getShortArray(MyPlaybackService.KEY_EQ_CURRENT_LEVELS) ?: ShortArray(0)
//                        )
//                    )
//                } else {
//                    _commandError.postValue("Failed to get EQ info: ${result.resultCode}")
//                }
//            } catch (e: Exception) {
//                _commandError.postValue("Error fetching EQ info: ${e.message}")
//            }
//        }
//    }
//
//    @OptIn(UnstableApi::class)
//    fun setEqualizerBandLevel(bandIndex: Short, level: Short) {
//        viewModelScope.launch {
//            val controller = mediaController ?: return@launch
//            val args = Bundle().apply {
//                putShort(MyPlaybackService.KEY_EQ_BAND_INDEX, bandIndex)
//                putShort(MyPlaybackService.KEY_EQ_BAND_LEVEL, level)
//            }
//            val command = SessionCommand(MyPlaybackService.CUSTOM_COMMAND_SET_EQ_BAND_LEVEL, Bundle.EMPTY)
//            try {
//                val result = controller.sendCustomCommand(command, args).await()
//                if (result.resultCode != SessionResult.RESULT_SUCCESS) {
//                    _commandError.postValue("Failed to set EQ band level: ${result.resultCode}")
//                }
//                // 设置成功后，可以考虑重新获取 currentLevels 或让 UI 局部更新
//                // 为了简单起见，这里不自动刷新，但实际应用中可能需要
//            } catch (e: Exception) {
//                _commandError.postValue("Error setting EQ band level: ${e.message}")
//            }
//        }
//    }
//
//    @OptIn(UnstableApi::class)
//    fun applyEqualizerPreset(presetIndex: Short) {
//        viewModelScope.launch {
//            val controller = mediaController ?: return@launch
//            val args = Bundle().apply {
//                putShort(MyPlaybackService.KEY_EQ_PRESET_INDEX, presetIndex)
//            }
//            val command = SessionCommand(MyPlaybackService.CUSTOM_COMMAND_APPLY_EQ_PRESET, Bundle.EMPTY)
//            try {
//                val result = controller.sendCustomCommand(command, args).await()
//                if (result.resultCode == SessionResult.RESULT_SUCCESS) {
//                    // 应用预设成功后，所有频段的级别都会改变
//                    // 必须重新获取均衡器信息以更新 UI
//                    fetchEqualizerInfo()
//                } else {
//                    _commandError.postValue("Failed to apply EQ preset: ${result.resultCode}")
//                }
//            } catch (e: Exception) {
//                _commandError.postValue("Error applying EQ preset: ${e.message}")
//            }
//        }
//    }
//
//
//    override fun onCleared() {
//        mediaController?.release()
//        mediaController = null
//        super.onCleared()
//    }
//}
