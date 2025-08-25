package com.superman.drilldemo.play.download



import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope // viewModelScope for launching coroutines
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * [DownloadUiState]
 * 类型: Data Class
 * 作用: 代表一个下载任务在 UI 层所需显示的状态信息。
 *      这有助于将原始的 Download 对象转换为更适合 UI 展示的结构。
 * 属性:
 *   - downloadId: String - 下载任务的唯一ID。
 *   - title: String - 下载内容的标题 (例如，从 Download.request.data 获取)。
 *   - status: Int - 下载的当前状态 (例如，Download.STATE_DOWNLOADING)。
 *   - percentDownloaded: Float - 下载进度的百分比。
 *   - failureReason: Int - 如果下载失败，失败的原因代码。
 *   - isPaused: Boolean - 指示此下载项是否因为全局暂停状态而应被视为已暂停。
 */
@UnstableApi
data class DownloadUiState(
    val downloadId: String,
    val title: String,
    val status: Int = Download.STATE_QUEUED,
    val percentDownloaded: Float = 0f,
    val failureReason: Int = Download.FAILURE_REASON_NONE,
    val isPaused: Boolean = false
)

/**
 * [DownloadViewModel]
 * 类型: AndroidViewModel
 * 作用: 负责处理与媒体下载相关的业务逻辑，并为 UI (Activity/Fragment) 提供下载状态数据。
 *      它与 DownloadManager 交互，监听下载变化，并更新 LiveData 供 UI 观察。
 *      继承自 AndroidViewModel 以便安全地访问 Application Context。
 *      实现了 DownloadManager.Listener 接口以接收下载事件的回调。
 *
 * @UnstableApi 注解表明它使用了 Media3 中可能在未来版本发生变化的 API。
 */
@UnstableApi
class DownloadViewModel(application: Application) : AndroidViewModel(application), DownloadManager.Listener {

    // --- 属性 (Properties) ---

    /**
     * [appContext]
     * 类型: Context
     * 作用: Application 的上下文，通过 AndroidViewModel 获取。
     *       用于初始化 DownloadManager (通过 DownloadUtil) 和发送 DownloadService 命令。
     * 可见性: private
     */
    private val appContext = application.applicationContext

    /**
     * [downloadManager]
     * 类型: DownloadManager
     * 作用: Media3 下载管理器，通过 DownloadUtil 获取的单例实例。
     *       用于管理所有下载任务，查询下载状态等。
     * 可见性: private
     */
    private val downloadManager: DownloadManager = DownloadUtil.getDownloadManager(appContext)

    /**
     * [_currentDownloadState]
     * 类型: MutableLiveData<DownloadUiState?>
     * 作用: 私有的、可变的 LiveData，用于存储当前关注的单个下载任务的 UI 状态。
     *       当下载状态更新时，会通过此 LiveData 通知观察者 (UI)。
     *       设计为可空，表示可能没有当前关注的下载或下载不存在。
     * 可见性: private
     */
    private val _currentDownloadState = MutableLiveData<DownloadUiState?>()

    /**
     * [currentDownloadState]
     * 类型: LiveData<DownloadUiState?>
     * 作用: 公开的、不可变的 LiveData，UI 层通过观察此 LiveData 来获取当前下载任务的 UI 状态。
     *       这是对 _currentDownloadState 的只读暴露。
     * 可见性: public (val)
     */
    val currentDownloadState: LiveData<DownloadUiState?> = _currentDownloadState

    // --- 初始化块 (Initializer Block) ---

    init {
        // 在 ViewModel 初始化时，将自身注册为 DownloadManager 的监听器。
        // 这样 ViewModel 就能接收到下载状态变化的实时回调。
        downloadManager.addListener(this)

        // 可选: 在 ViewModel 初始化时加载已知下载的初始状态。
        // 例如，如果 ViewModel 负责管理一系列下载，可以在这里遍历并获取它们的状态。
        // loadInitialDownloadStates() // 这是一个占位符，具体实现取决于需求
    }

    // --- 公共函数 (Public Functions) - 由 UI 调用 ---

    /**
     * [startOrResumeDownload]
     * 作用: 启动一个新的下载任务，或者如果任务已存在且已停止/失败，则尝试恢复它。
     *      使用 viewModelScope.launch 启动一个与 ViewModel生命周期绑定的协程。
     * 参数:
     *   - songId: String - 下载内容的唯一 ID。
     *   - songUrl: String - 下载内容的 URL。
     *   - songTitle: String - 下载内容的标题 (会存储在 DownloadRequest.data 中)。
     * 返回: Unit
     */
    fun startOrResumeDownload(songId: String, songUrl: String, songTitle: String) {
        viewModelScope.launch { // 在 ViewModel 的协程作用域内执行
            // 从 DownloadIndex 异步获取指定 ID 的现有下载信息
            val existingDownload = withContext(Dispatchers.IO) { // 切换到 IO 线程进行磁盘/网络操作
                downloadManager.downloadIndex.getDownload(songId)
            }

            if (existingDownload != null) { // 如果下载已存在
                // 如果下载已停止或失败，则发送恢复所有下载的命令
                // (Media3 的 DownloadService 通常是全局恢复)
                if (existingDownload.state == Download.STATE_STOPPED || existingDownload.state == Download.STATE_FAILED) {
                    Log.d("DownloadViewModel", "Resuming download for: $songId (via global resume)")
                    DownloadService.sendResumeDownloads(
                        appContext,
                        MyDownloadService::class.java, // 你的 DownloadService 实现类
                        false // foreground: false，服务会自行管理前台状态
                    )
                } else if (existingDownload.state == Download.STATE_QUEUED || existingDownload.state == Download.STATE_DOWNLOADING) {
                    Log.d("DownloadViewModel", "Download already in progress or queued: $songId")
                    // 可选: 通知 UI 或显示消息表明下载已在进行中
                } else {
                    Log.d("DownloadViewModel", "Download for $songId in unhandled state to resume: ${existingDownload.state}")
                }
            } else { // 如果下载不存在，则创建新的下载请求
                Log.d("DownloadViewModel", "Starting new download for: $songId")
                val downloadRequest = DownloadRequest.Builder(songId, Uri.parse(songUrl))
                    .setMimeType(MimeTypes.AUDIO_MPEG) // 指定媒体类型，有助于播放器处理
                    .setData(songTitle.toByteArray()) // 将标题存储在请求的 data 字段中
                    .build()

                // 发送添加下载的命令给 DownloadService
                DownloadService.sendAddDownload(
                    appContext,
                    MyDownloadService::class.java,
                    downloadRequest,
                    false
                )
            }
            // 在发起操作后，立即获取并更新此下载ID的UI状态
            fetchDownloadState(songId)
        }
    }

    /**
     * [pauseDownload]
     * 作用: 发送暂停所有下载任务的命令给 DownloadService。
     *      Media3 的 DownloadService 通常提供全局暂停功能。
     *      注意: 这个函数名可能暗示暂停单个下载，但实际行为是全局暂停。
     *           如果需要单个暂停且不仅仅是停止(STATE_STOPPED)，逻辑会更复杂。
     * 参数:
     *   - songId: String - (当前未使用，但保留以表示意图或未来扩展)
     * 返回: Unit
     */
    fun pauseDownload(songId: String) {
        Log.d("DownloadViewModel", "Pausing all downloads (action triggered for song: $songId)")
        DownloadService.sendPauseDownloads(
            appContext,
            MyDownloadService::class.java,
            false
        )
        // 暂停后，DownloadManager.Listener 的 onDownloadsPausedChanged 会被调用，
        // 从而触发 UI 更新。
    }

    /**
     * [removeDownload]
     * 作用: 发送移除指定下载任务的命令给 DownloadService。
     * 参数:
     *   - songId: String - 要移除的下载任务的 ID。
     * 返回: Unit
     */
    fun removeDownload(songId: String) {
        Log.d("DownloadViewModel", "Removing download for: $songId")
        DownloadService.sendRemoveDownload(
            appContext,
            MyDownloadService::class.java,
            songId,
            false
        )
        // 移除后，DownloadManager.Listener 的 onDownloadRemoved 会被调用。
    }

    /**
     * [fetchDownloadState]
     * 作用: 异步获取指定下载 ID 的当前状态，并更新 LiveData (currentDownloadState)。
     *      这通常在用户操作后或需要刷新特定下载项UI时调用。
     * 参数:
     *   - downloadId: String - 要查询状态的下载 ID。
     * 返回: Unit
     */
    fun fetchDownloadState(downloadId: String) {
        viewModelScope.launch {
            val download = withContext(Dispatchers.IO) {
                downloadManager.downloadIndex.getDownload(downloadId)
            }
            // 使用获取到的 Download 对象和全局暂停状态来更新 LiveData
            updateLiveDataForDownload(download, downloadManager.downloadsPaused)
        }
    }

    /**
     * [refreshAllTrackedDownloadsStates]
     * 作用: 刷新 ViewModel 当前正在追踪的所有下载项的状态。
     *      例如，在 DownloadManager 初始化完成或全局暂停状态改变后调用。
     *      (当前示例主要关注 _currentDownloadState 的刷新)
     * 返回: Unit
     */
    fun refreshAllTrackedDownloadsStates() {
        // 示例: 如果 _currentDownloadState 有值，则刷新它的状态
        _currentDownloadState.value?.downloadId?.let { currentId ->
            fetchDownloadState(currentId)
        }
        // 如果 ViewModel 管理一个下载列表 (例如在 Map或List 中)，
        // 则应遍历该列表/映射，并为每个项调用 fetchDownloadState。
    }

    // --- 私有辅助函数 (Private Helper Functions) ---

    /**
     * [updateLiveDataForDownload]
     * 作用: 根据给定的 Download 对象和全局暂停状态，创建或更新 DownloadUiState，
     *      并将其发布到 _currentDownloadState LiveData。
     * 参数:
     *   - download: Download? - 从 DownloadManager 获取的下载对象，可能为 null (如果不存在)。
     *   - downloadsGloballyPaused: Boolean - 指示当前所有下载是否已全局暂停。
     * 返回: Unit
     */
    private fun updateLiveDataForDownload(download: Download?, downloadsGloballyPaused: Boolean) {
        if (download == null) {
            // 如果下载对象为 null (例如，下载被移除或从未开始):
            // 检查当前 LiveData 是否就是针对这个被移除的 ID，如果是，则将其值设为 null
            // (或者一个表示"未下载"的默认 DownloadUiState)。
            // 注意: 这里的条件 `_currentDownloadState.value?.downloadId == _currentDownloadState.value?.downloadId`
            // 应该修改为检查 `downloadId` (如果能传入被移除的 ID) 是否与当前 LiveData 的 ID 匹配。
            // 例如: if (_currentDownloadState.value?.downloadId == idOfRemovedDownload) { ... }
            // 在当前实现中，如果 download 为 null，且它就是 _currentDownloadState 代表的那个，
            // 我们可以直接 postValue(null)。
            _currentDownloadState.postValue(null) // 清除 LiveData 或设置为默认"未下载"状态
            return
        }

        // 尝试从 DownloadRequest 的 data 字段中恢复存储的标题
        val title = try {
            String(download.request.data) // data 字段存储的是 ByteArray
        } catch (e: Exception) {
            Log.e("DownloadViewModel", "Failed to parse title from download data for ${download.request.id}", e)
            "Unknown Title" // 获取失败时的默认标题
        }

        // 创建 DownloadUiState 对象
        val uiState = DownloadUiState(
            downloadId = download.request.id,
            title = title,
            status = download.state,
            percentDownloaded = download.percentDownloaded,
            failureReason = download.failureReason,
            // 判断此下载项是否应被视为“暂停”：
            // 如果全局下载已暂停，并且此下载项的状态是可能正在运行或排队的状态。
            isPaused = downloadsGloballyPaused && (download.state == Download.STATE_QUEUED || download.state == Download.STATE_DOWNLOADING || download.state == Download.STATE_STOPPED)
        )
        // 使用 postValue 将新的 UI 状态发布到 LiveData (postValue 可以在后台线程调用)
        _currentDownloadState.postValue(uiState)

        // 如果 ViewModel 管理一个下载列表/映射:
        // val currentMap = _allDownloadsState.value.toMutableMap()
        // currentMap[download.request.id] = uiState
        // _allDownloadsState.value = currentMap // 对于 StateFlow
    }

    // --- DownloadManager.Listener 实现 ---
    // 这些回调方法会在 DownloadManager 的状态发生变化时被调用。
    // ViewModel 作为监听者，可以在这些回调中更新其 LiveData。

    /**
     * [onInitialized]
     * DownloadManager.Listener 回调。
     * 作用: 当 DownloadManager 完成初始化时调用。
     *      此时可以安全地查询下载索引或执行其他依赖于已初始化 DownloadManager 的操作。
     */
    override fun onInitialized(manager: DownloadManager) {
        Log.d("DownloadViewModel", "DownloadManager initialized in ViewModel")
        // DownloadManager 准备就绪，可以刷新所有追踪的下载项的初始状态。
        refreshAllTrackedDownloadsStates()
    }

    /**
     * [onDownloadChanged]
     * DownloadManager.Listener 回调。
     * 作用: 当某个下载任务的状态发生变化时 (例如，进度更新、完成、失败等) 调用。
     * 参数:
     *   - manager: DownloadManager - 触发事件的 DownloadManager 实例。
     *   - download: Download - 状态已改变的 Download 对象。
     *   - finalException: Exception? - 如果下载失败，这里会包含最终的异常信息。
     */
    override fun onDownloadChanged(manager: DownloadManager, download: Download, finalException: Exception?) {
        Log.d("DownloadViewModel", "onDownloadChanged in ViewModel - ID: ${download.request.id}, State: ${download.state}, Percent: ${download.percentDownloaded}")
        if (finalException != null) {
            Log.e("DownloadViewModel", "Download failed for ${download.request.id}", finalException)
        }
        // 更新与此 Download 对象对应的 LiveData
        updateLiveDataForDownload(download, manager.downloadsPaused)
    }

    /**
     * [onDownloadRemoved]
     * DownloadManager.Listener 回调。
     * 作用: 当某个下载任务被移除时调用。
     */
    override fun onDownloadRemoved(manager: DownloadManager, download: Download) {
        Log.d("DownloadViewModel", "onDownloadRemoved in ViewModel - ID: ${download.request.id}")
        // 当下载被移除时，更新对应的 LiveData，通常是将其状态设为 null 或“未下载”。
        // 这里的 `download` 对象仍然包含被移除下载的请求信息。
        // 我们需要一种方式告诉 updateLiveDataForDownload 这是针对特定 ID 的移除。
        // 一个简单的处理是，如果 _currentDownloadState 的 ID 与被移除的 ID 匹配，则设为 null。
        if (_currentDownloadState.value?.downloadId == download.request.id) {
            _currentDownloadState.postValue(null)
        }
        // 或者，更通用的方式是: updateLiveDataForDownload(null, manager.downloadsPaused, download.request.id)
        // 并在 updateLiveDataForDownload 中处理 idOfRemovedDownload。
        // 当前简单处理:
        // updateLiveDataForDownload(null, manager.downloadsPaused) // 这会清除 _currentDownloadState
        // 如果它没有 downloadId 匹配逻辑
    }

    /**
     * [onDownloadsPausedChanged]
     * DownloadManager.Listener 回调。
     * 作用: 当所有下载任务的全局暂停状态发生改变时 (即所有下载被暂停或所有下载被恢复) 调用。
     * 参数:
     *   - manager: DownloadManager - 触发事件的 DownloadManager 实例。
     *   - downloadsPaused: Boolean - true 表示所有下载已暂停，false 表示已恢复。
     */
    override fun onDownloadsPausedChanged(manager: DownloadManager, downloadsPaused: Boolean) {
        Log.d("DownloadViewModel", "onDownloadsPausedChanged in ViewModel - Paused: $downloadsPaused")
        // 这是全局状态。需要更新所有相关 DownloadUiState 对象的 isPaused 标志。
        // 最简单的方式是重新获取并评估所有追踪的下载项的状态。
        refreshAllTrackedDownloadsStates()
    }

    /**
     * [onIdle]
     * DownloadManager.Listener 回调。
     * 作用: 当 DownloadManager 变为空闲状态 (即没有活动的下载任务) 时调用。
     */
    override fun onIdle(manager: DownloadManager) {
        Log.d("DownloadViewModel", "DownloadManager is idle in ViewModel")
        // 可以在这里执行一些清理操作或更新 UI 以反映空闲状态。
    }

    // --- ViewModel 生命周期回调 ---

    /**
     * [onCleared]
     * ViewModel 生命周期回调。
     * 作用: 当 ViewModel 不再被使用并即将被销毁时调用。
     *      这是进行资源清理的最佳位置，例如移除监听器。
     */
    override fun onCleared() {
        super.onCleared()
        // 非常重要: 移除之前添加的 DownloadManager 监听器，以防止内存泄漏。
        downloadManager.removeListener(this)
        Log.d("DownloadViewModel", "ViewModel cleared, DownloadManager listener removed.")
    }
}


