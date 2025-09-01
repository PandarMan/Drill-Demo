package com.superman.drilldemo.play.download.list


import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import com.superman.drilldemo.play.download.DownloadUiState
import com.superman.drilldemo.play.download.DownloadUtil
import com.superman.drilldemo.play.download.MyDownloadService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

// DownloadUiState data class 保持不变 (从之前的回答中获取)
// @UnstableApi data class DownloadUiState(...)

@UnstableApi
class DownloadViewModel(application: Application) : AndroidViewModel(application), DownloadManager.Listener {

    private val appContext = application.applicationContext
    private val downloadManager: DownloadManager = DownloadUtil.getDownloadManager(appContext)

    // --- 核心修改：为每个 songId 维护一个 LiveData ---
    private val downloadStatesMap = ConcurrentHashMap<String, MutableLiveData<DownloadUiState?>>()

    init {
        downloadManager.addListener(this)
        // 可选: 初始化时加载所有已知的下载状态到 map 中
        loadInitialStatesForAllKnownDownloads()
    }

    private fun loadInitialStatesForAllKnownDownloads() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cursor = downloadManager.downloadIndex.getDownloads()
                while (cursor.moveToNext()) {
                    val download = cursor.download
                    val liveData = getOrCreateLiveDataForId(download.request.id)
                    updateLiveDataForDownload(download, downloadManager.downloadsPaused, liveData)
                }
                cursor.close()
            } catch (e: Exception) {
                Log.e("DownloadViewModel", "Error loading initial download states", e)
            }
        }
    }


    /**
     * 为给定的 songId 获取或创建一个 MutableLiveData 实例。
     * UI (Adapter/ViewHolder) 将观察这个返回的 LiveData。
     */
    fun getDownloadStateLiveData(songId: String): LiveData<DownloadUiState?> {
        return getOrCreateLiveDataForId(songId)
    }

    private fun getOrCreateLiveDataForId(songId: String): MutableLiveData<DownloadUiState?> {
        return downloadStatesMap.getOrPut(songId) {
            MutableLiveData<DownloadUiState?>().also {
                // 新创建 LiveData 时，立即获取一次状态
                fetchInitialStateForId(songId, it)
            }
        }
    }

    private fun fetchInitialStateForId(songId: String, liveData: MutableLiveData<DownloadUiState?>) {
        viewModelScope.launch {
            val download = withContext(Dispatchers.IO) {
                try {
                    downloadManager.downloadIndex.getDownload(songId)
                } catch (e: Exception) {
                    Log.e("DownloadViewModel", "Error fetching initial state for $songId", e)
                    null
                }
            }
            updateLiveDataForDownload(download, downloadManager.downloadsPaused, liveData)
        }
    }


    fun startOrResumeDownload(songId: String, songUrl: String, songTitle: String) {
        viewModelScope.launch {
            Log.d("DownloadViewModel", "Attempting to start or resume download for ID: $songId")
            val existingDownload = withContext(Dispatchers.IO) {
                try {
                    downloadManager.downloadIndex.getDownload(songId)
                } catch (e: Exception) { null }
            }

            if (existingDownload != null) {
                // ... (与之前相同的恢复逻辑: STATE_DOWNLOADING, STATE_STOPPED, STATE_FAILED etc.)
                // 例如:
                if (existingDownload.state == Download.STATE_STOPPED || existingDownload.state == Download.STATE_FAILED) {
                    downloadManager.setStopReason(songId, Download.STOP_REASON_NONE)
                    DownloadService.sendResumeDownloads(appContext, MyDownloadService::class.java, false)
                } else if (existingDownload.state == Download.STATE_QUEUED || existingDownload.state == Download.STATE_DOWNLOADING) {
                    Log.i("DownloadViewModel", "Download $songId already in progress or queued.")
                } else if (existingDownload.state == Download.STATE_COMPLETED) {
                    Log.i("DownloadViewModel", "Download $songId already completed.")
                }
            } else {
                val downloadRequest = DownloadRequest.Builder(songId, Uri.parse(songUrl))
                    .setMimeType(MimeTypes.AUDIO_MPEG) // Or infer
                    .setData(songTitle.toByteArray(Charsets.UTF_8))
                    .build()
                DownloadService.sendAddDownload(appContext, MyDownloadService::class.java, downloadRequest, false)
            }
            // 操作后，通过 DownloadManager.Listener 的回调来更新 LiveData
        }
    }

    fun pauseDownload(songId: String) {
        // 对于单个暂停，你可以设置 stop reason
        // downloadManager.setStopReason(songId, YOUR_PAUSE_REASON_IF_ANY)
        // 或者全局暂停
        Log.d("DownloadViewModel", "Pausing all downloads (action triggered for song: $songId)")
        DownloadService.sendPauseDownloads(appContext, MyDownloadService::class.java, false)
    }

    fun removeDownload(songId: String) {
        Log.d("DownloadViewModel", "Removing download for: $songId")
        DownloadService.sendRemoveDownload(appContext, MyDownloadService::class.java, songId, false)
    }

    // --- DownloadManager.Listener 实现 ---

    override fun onDownloadChanged(manager: DownloadManager, download: Download, finalException: Exception?) {
        Log.d("DownloadViewModel", "Listener onDownloadChanged - ID: ${download.request.id}, State: ${download.state}")
        if (finalException != null) {
            Log.e("DownloadViewModel", "Download failed for ${download.request.id}", finalException)
        }
        val liveData = downloadStatesMap[download.request.id] // 获取对应 ID 的 LiveData
        liveData?.let {
            updateLiveDataForDownload(download, manager.downloadsPaused, it)
        }
    }

    override fun onDownloadRemoved(manager: DownloadManager, download: Download) {
        Log.d("DownloadViewModel", "Listener onDownloadRemoved - ID: ${download.request.id}")
        val liveData = downloadStatesMap[download.request.id]
        liveData?.postValue(null) // 或者一个表示“未下载”的特定 DownloadUiState
        // 可选: 从 map 中移除 LiveData，如果确定不再需要 (但要小心，如果 ViewHolder 仍可能观察)
        // downloadStatesMap.remove(download.request.id)
    }

    override fun onDownloadsPausedChanged(manager: DownloadManager, downloadsPaused: Boolean) {
        Log.d("DownloadViewModel", "Listener onDownloadsPausedChanged - Paused: $downloadsPaused")
        // 更新所有已知的 LiveData 的 isPaused 状态
        refreshAllActiveDownloadStates(downloadsPaused)
    }

    override fun onIdle(manager: DownloadManager) {
        Log.d("DownloadViewModel", "DownloadManager is idle.")
        // 可能不需要特定操作，除非你想在所有下载完成后更新一些全局状态
    }

    override fun onInitialized(manager: DownloadManager) {
        Log.d("DownloadViewModel", "DownloadManager initialized.")
        // 可以在这里重新加载所有状态，以防在监听器添加前有变化
        loadInitialStatesForAllKnownDownloads()
    }

    override fun onRequirementsStateChanged(manager: DownloadManager, requirements: androidx.media3.exoplayer.scheduler.Requirements, notMetRequirements: Int) {
        Log.d("DownloadViewModel", "Listener onRequirementsStateChanged - Not met: $notMetRequirements")
        refreshAllActiveDownloadStates(manager.downloadsPaused) // 网络变化可能影响下载状态
    }


    private fun refreshAllActiveDownloadStates(downloadsGloballyPaused: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { // 确保在后台线程操作
            downloadStatesMap.forEach { (id, liveData) ->
                val download = try {
                    downloadManager.downloadIndex.getDownload(id)
                } catch (e: Exception) { null }
                updateLiveDataForDownload(download, downloadsGloballyPaused, liveData)
            }
        }
    }

    private fun updateLiveDataForDownload(
        download: Download?,
        downloadsGloballyPaused: Boolean,
        liveData: MutableLiveData<DownloadUiState?> // 直接传入要更新的 LiveData
    ) {
        if (download == null) {
            liveData.postValue(null)
            return
        }
        val title = try {
            String(download.request.data, Charsets.UTF_8)
        } catch (e: Exception) { "Unknown Title" }

        val uiState = DownloadUiState(
            downloadId = download.request.id,
            title = title,
            status = download.state,
            percentDownloaded = download.percentDownloaded,
            failureReason = download.failureReason,
            isPaused = downloadsGloballyPaused && (download.state == Download.STATE_QUEUED || download.state == Download.STATE_DOWNLOADING || download.state == Download.STATE_STOPPED)
        )
        liveData.postValue(uiState)
    }

    override fun onCleared() {
        super.onCleared()
        downloadManager.removeListener(this)
        downloadStatesMap.clear() // 清理 map
        Log.d("DownloadViewModel", "ViewModel cleared, listener removed, states map cleared.")
    }
}

