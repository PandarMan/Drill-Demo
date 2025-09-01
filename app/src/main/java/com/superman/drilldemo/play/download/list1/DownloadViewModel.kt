package com.superman.drilldemo.play.download.list1


import android.app.Application
import android.app.Notification
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import com.superman.drilldemo.R // 确保 R 文件路径正确
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

@UnstableApi
class DownloadViewModel(application: Application) : AndroidViewModel(application), DownloadManager.Listener {

    private val appContext = application.applicationContext
    private val downloadManager: DownloadManager = DownloadUtil.getDownloadManager(appContext)

    // 为每个 songId 维护一个 LiveData，用于UI更新
    private val downloadStatesMap = ConcurrentHashMap<String, MutableLiveData<DownloadUiState?>>()

    init {
        downloadManager.addListener(this) // 注册监听器
        // 初始化时加载所有已知下载的状态
        loadInitialStatesForAllKnownDownloads()
        Log.d("DownloadViewModel", "DownloadManager listener added.")
    }

    private fun loadInitialStatesForAllKnownDownloads() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cursor = downloadManager.downloadIndex.getDownloads()
                Log.d("DownloadViewModel", "Found ${cursor.count} initial downloads in index.")
                while (cursor.moveToNext()) {
                    val download = cursor.download
                    val liveData = getOrCreateLiveDataForId(download.request.id) // 确保为每个ID创建LiveData
                    updateUiStateForDownload(download, downloadManager.downloadsPaused, liveData)
                }
                cursor.close()
            } catch (e: Exception) {
                Log.e("DownloadViewModel", "Error loading initial download states from index", e)
            }
        }
    }

    /**
     * 为给定的 songId 获取或创建一个 MutableLiveData 实例。
     * RecyclerView Adapter 中的 ViewHolder 将观察这个返回的 LiveData。
     */
    fun getDownloadStateLiveData(songId: String): LiveData<DownloadUiState?> {
        return getOrCreateLiveDataForId(songId)
    }

    private fun getOrCreateLiveDataForId(songId: String): MutableLiveData<DownloadUiState?> {
        return downloadStatesMap.getOrPut(songId) {
            MutableLiveData<DownloadUiState?>().also {
                // 新创建 LiveData 时，立即获取一次该ID的当前状态
                fetchInitialStateForId(songId, it)
            }
        }
    }

    /**
     * 为特定的 songId 获取初始状态并更新其 LiveData。
     */
    private fun fetchInitialStateForId(songId: String, liveData: MutableLiveData<DownloadUiState?>) {
        viewModelScope.launch { // 默认在主线程，IO操作切换上下文
            val download = withContext(Dispatchers.IO) { // IO操作在后台线程
                try {
                    downloadManager.downloadIndex.getDownload(songId)
                } catch (e: Exception) {
                    Log.e("DownloadViewModel", "Error fetching initial state for $songId from index", e)
                    null
                }
            }
            updateUiStateForDownload(download, downloadManager.downloadsPaused, liveData)
        }
    }

    fun startOrResumeDownload(songId: String, songUrl: String, songTitle: String) {
        viewModelScope.launch { // 通常在主线程发起，但内部IO操作会切换
            Log.d("DownloadViewModel", "Attempting to start or resume download for ID: $songId, URL: $songUrl")
            val existingDownload = withContext(Dispatchers.IO) {
                try {
                    downloadManager.downloadIndex.getDownload(songId)
                } catch (e: Exception) {
                    Log.w("DownloadViewModel", "Error checking for existing download $songId", e)
                    null
                }
            }

            if (existingDownload != null) {
                Log.d("DownloadViewModel", "Existing download for $songId found with state: ${existingDownload.state}")
                when (existingDownload.state) {
                    Download.STATE_STOPPED, Download.STATE_FAILED -> {
                        // 如果已停止或失败，尝试恢复
                        downloadManager.setStopReason(songId, Download.STOP_REASON_NONE) // 清除停止原因
                        DownloadService.sendResumeDownloads(appContext, MyDownloadService::class.java, false)
                        Log.i("DownloadViewModel", "Resuming download for $songId")
                    }
                    Download.STATE_QUEUED, Download.STATE_DOWNLOADING -> {
                        Log.i("DownloadViewModel", "Download $songId already in progress or queued.")
                        // 可以选择切换到该下载的详情或不做操作
                    }
                    Download.STATE_COMPLETED -> {
                        Log.i("DownloadViewModel", "Download $songId is already completed.")
                        // 可以触发播放或其他操作
                    }
                    else -> {
                        Log.w("DownloadViewModel", "Download $songId in unexpected state: ${existingDownload.state} for resume attempt.")
                    }
                }
            } else {
                // 没有现有下载，创建新的下载请求
                val downloadRequest = DownloadRequest.Builder(songId, Uri.parse(songUrl))
                    .setMimeType(MimeTypes.AUDIO_MPEG) // 根据你的媒体类型调整
                    .setData(songTitle.toByteArray(Charsets.UTF_8)) // 将标题作为数据存储，用于通知等
                    .build()
                DownloadService.sendAddDownload(appContext, MyDownloadService::class.java, downloadRequest, false)
                Log.i("DownloadViewModel", "Adding new download request for $songId: $songTitle")
            }
            // 状态更新将通过 DownloadManager.Listener 的回调来驱动 LiveData
        }
    }

    fun pauseDownload(songId: String) {
        // ExoPlayer 的 DownloadManager 通常是全局暂停所有下载
        Log.d("DownloadViewModel", "Pausing all downloads (action triggered for song: $songId)")
        DownloadService.sendPauseDownloads(appContext, MyDownloadService::class.java, false)
    }

    fun removeDownload(songId: String) {
        Log.d("DownloadViewModel", "Removing download for ID: $songId")
        DownloadService.sendRemoveDownload(appContext, MyDownloadService::class.java, songId, false)
    }

    // --- DownloadManager.Listener 实现 ---

    override fun onDownloadChanged(manager: DownloadManager, download: Download, finalException: Exception?) {
        val downloadId = download.request.id
        Log.d("DownloadViewModel", "Listener onDownloadChanged - ID: $downloadId, State: ${download.state}, Percent: ${download.percentDownloaded}")
        if (finalException != null) {
            Log.e("DownloadViewModel", "Download failed for $downloadId with exception:", finalException)
        }

        // 更新对应 ID 的 LiveData
        downloadStatesMap[downloadId]?.let { liveData ->
            updateUiStateForDownload(download, manager.downloadsPaused, liveData)
        }

        // --- 处理单独的完成/失败通知 ---
        val notificationHelper = DownloadUtil.getDownloadNotificationHelper(appContext, MyDownloadService.NOTIFICATION_CHANNEL_ID)
        val notification: Notification? = when (download.state) {
            Download.STATE_COMPLETED -> {
                Log.i("DownloadViewModel", "Download COMPLETED: $downloadId")
                notificationHelper.buildDownloadCompletedNotification(
                    appContext,
                    R.drawable.ic_launcher_foreground, // 你需要这个 drawable
                    null, // contentIntent (可选)
                    Util.fromUtf8Bytes(download.request.data) // 从 request.data 获取标题
                )
            }
            Download.STATE_FAILED -> {
                Log.e("DownloadViewModel", "Download FAILED: $downloadId, Reason: ${download.failureReason}")
                notificationHelper.buildDownloadFailedNotification(
                    appContext,
                    R.drawable.ic_default_thumb, // 你需要这个 drawable
                    null, // contentIntent (可选)
                    Util.fromUtf8Bytes(download.request.data)
                )
            }
            else -> null // 其他状态由前台服务通知处理，或不发单独通知
        }

        notification?.let {
            // 为完成/失败的通知使用一个与前台服务不同的、基于下载ID的唯一通知ID
            // 这样它们可以被单独管理和取消
            val uniqueNotificationId = MyDownloadService.FOREGROUND_NOTIFICATION_ID + downloadId.hashCode() + 1
            Log.d("DownloadViewModel", "Posting final status notification (ID: $uniqueNotificationId) for download: $downloadId")
            try {
                NotificationManagerCompat.from(appContext).notify(uniqueNotificationId, it)
            } catch (e: SecurityException) {
                Log.e("DownloadViewModel", "SecurityException posting notification for $downloadId. Check POST_NOTIFICATIONS permission.", e)
            }
        }
    }

    override fun onDownloadRemoved(manager: DownloadManager, download: Download) {
        val downloadId = download.request.id
        Log.d("DownloadViewModel", "Listener onDownloadRemoved - ID: $downloadId")

        // 更新 LiveData，通常设置为 null 或一个表示“未下载”的状态
        downloadStatesMap[downloadId]?.postValue(null)
        // 可选: 从 map 中移除这个 LiveData，如果确定这个 ID 不会再被请求
        // downloadStatesMap.remove(downloadId)

        // --- 取消之前可能为这个下载显示的单独通知 (完成/失败的通知) ---
        val uniqueNotificationId = MyDownloadService.FOREGROUND_NOTIFICATION_ID + downloadId.hashCode() + 1
        NotificationManagerCompat.from(appContext).cancel(uniqueNotificationId)
        Log.d("DownloadViewModel", "Cancelled final status notification (ID: $uniqueNotificationId) for removed download: $downloadId")
    }

    override fun onDownloadsPausedChanged(manager: DownloadManager, downloadsPaused: Boolean) {
        Log.d("DownloadViewModel", "Listener onDownloadsPausedChanged - Globally Paused: $downloadsPaused")
        // 当全局暂停状态改变时，需要更新所有受影响的下载项的UI状态
        refreshAllActiveDownloadStates(downloadsPaused)
    }

    override fun onIdle(manager: DownloadManager) {
        Log.d("DownloadViewModel", "DownloadManager is now idle (all tasks finished or paused).")
        // 可以在此更新一些全局UI，例如隐藏一个总的下载进度指示器
    }

    override fun onInitialized(manager: DownloadManager) {
        Log.d("DownloadViewModel", "DownloadManager listener initialized and caught up.")
        // 确保所有已知下载的状态都是最新的
        loadInitialStatesForAllKnownDownloads()
    }

    override fun onRequirementsStateChanged(
        manager: DownloadManager,
        requirements: androidx.media3.exoplayer.scheduler.Requirements,
        notMetRequirements: Int
    ) {
        Log.d("DownloadViewModel", "Listener onRequirementsStateChanged - Not met requirements: $notMetRequirements")
        // 下载条件（如网络）变化可能会影响下载是否能进行，更新UI状态
        refreshAllActiveDownloadStates(manager.downloadsPaused)
    }

    /**
     * 当全局暂停状态改变或需求状态改变时，刷新所有当前活动下载的UI状态。
     */
    private fun refreshAllActiveDownloadStates(downloadsGloballyPaused: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { // 在后台线程遍历和获取状态
            downloadStatesMap.forEach { (id, liveData) ->
                val download = try {
                    downloadManager.downloadIndex.getDownload(id)
                } catch (e: Exception) { null } // 防御性编程
                // 在主线程更新 LiveData
                withContext(Dispatchers.Main) {
                    updateUiStateForDownload(download, downloadsGloballyPaused, liveData)
                }
            }
        }
    }

    /**
     * 辅助方法，根据 Download 对象更新对应的 LiveData<DownloadUiState?>
     */
    private fun updateUiStateForDownload(
        download: Download?,
        downloadsGloballyPaused: Boolean,
        liveData: MutableLiveData<DownloadUiState?>
    ) {
        if (download == null) {
            liveData.postValue(null) // 如果下载信息不存在，则将 LiveData 置为 null
            return
        }
        val title = try {
            if (download.request.data.isNotEmpty()) Util.fromUtf8Bytes(download.request.data) else "未知标题"
        } catch (e: Exception) { "解析标题错误" }

        val uiState = DownloadUiState(
            downloadId = download.request.id,
            title = title,
            status = download.state,
            percentDownloaded = download.percentDownloaded,
            failureReason = download.failureReason,
            // isPaused 的逻辑可以更精确：如果下载本身是活动的（下载中/排队）并且全局暂停了，才认为是isPaused
            isPaused = downloadsGloballyPaused && (download.state == Download.STATE_DOWNLOADING || download.state == Download.STATE_QUEUED || download.state == Download.STATE_STOPPED)
        )
        liveData.postValue(uiState)
    }

    override fun onCleared() {
        super.onCleared()
        downloadManager.removeListener(this) // 非常重要：移除监听器以避免内存泄漏
        downloadStatesMap.clear() // 清理 LiveData 引用
        Log.d("DownloadViewModel", "ViewModel cleared, DownloadManager listener removed, states map cleared.")
    }
}
