package com.superman.drilldemo.play.download.list

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.recyclerview.widget.RecyclerView
import com.superman.drilldemo.databinding.ItemSongBinding
import com.superman.drilldemo.play.download.DownloadUiState

data class Song(
    val id: String,
    val title: String,
    val url: String,
    val artist: String? = null,
    val album: String? = null,
    val coverUrl: String? = null,
    // 你可以选择移除这个字段，完全依赖于 ViewHolder 从 ViewModel 的 LiveData 获取状态。
    // 如果保留，请确保它只作为一种临时的UI状态镜像。
    // var currentDownloadUiState: DownloadUiState? = null
)
// Your Song data class (example)
// data class Song(...) // 保持不变
@OptIn(UnstableApi::class)
class SongsAdapter
    (
    private var songs: List<Song>,
    private val viewModel: DownloadViewModel,
    private val lifecycleOwner: LifecycleOwner // Activity/Fragment 的 LifecycleOwner
) : RecyclerView.Adapter<SongsAdapter.SongViewHolder>() {

    @OptIn(UnstableApi::class)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val binding = ItemSongBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SongViewHolder(binding)
    }

    override fun getItemCount(): Int = songs.size

    @OptIn(UnstableApi::class)
    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        val song = songs[position]
        holder.bind(song)
    }

    // 当 ViewHolder 将要被回收时，移除它的观察者
    @OptIn(UnstableApi::class)
    override fun onViewRecycled(holder: SongViewHolder) {
        super.onViewRecycled(holder)
        holder.currentObserver?.let { observer ->
            holder.currentObservedLiveData?.removeObserver(observer)
        }
        holder.currentObservedLiveData = null
        holder.currentObserver = null
        Log.d("SongsAdapter", "View recycled for ID: ${holder.boundSongId}")
    }


    @UnstableApi
    inner class SongViewHolder(private val binding: ItemSongBinding) :
        RecyclerView.ViewHolder(binding.root) {
        var currentObservedLiveData: LiveData<DownloadUiState?>? = null
        var currentObserver: Observer<DownloadUiState?>? = null
        var boundSongId: String? = null // 用于调试或特殊情况

        fun bind(song: Song) {
            binding.songTitleTextView.text = song.title
            boundSongId = song.id

            // --- 关键修改：为每个 Item 获取并观察其特定的 LiveData ---
            val songSpecificLiveData = viewModel.getDownloadStateLiveData(song.id)

            // 如果 ViewHolder 被重用，先移除旧的观察者
            currentObserver?.let { currentObservedLiveData?.removeObserver(it) }

            // 创建新的观察者
            val observer = Observer<DownloadUiState?> { uiState ->
                // LiveData 更新时，直接更新UI (因为这个 LiveData 是针对这个 songId 的)
                if (uiState != null) {
                    updateDownloadUi(uiState, song)
                } else {
                    resetDownloadUi(song) // 如果状态为 null (例如下载被移除)
                }
            }
            this.currentObserver = observer
            this.currentObservedLiveData = songSpecificLiveData

            // 开始观察
            songSpecificLiveData.observe(lifecycleOwner, observer)

            // UI 初始化: 可以基于 LiveData 当前的值 (如果有)
            // 通常，getDownloadStateLiveData 在首次创建 LiveData 时会触发一次 fetchInitialStateForId
            // 所以 LiveData 很快就会有初始值。
            // 或者，你可以让 LiveData 在没有值时默认为一个“加载中”或“未下载”的 DownloadUiState。
            // 为了简单，我们依赖观察者在 LiveData 有值时更新。
            // 如果 LiveData 初始为 null，resetDownloadUi() 会被调用。
            if (songSpecificLiveData.value == null) {
                resetDownloadUi(song) // 确保在 LiveData 还没有值时 UI 有个默认状态
            }


            // --- 按钮点击事件 ---
            binding.downloadButton.setOnClickListener {
                viewModel.startOrResumeDownload(song.id, song.url, song.title)
            }
            binding.pauseButton.setOnClickListener {
                viewModel.pauseDownload(song.id)
            }
            binding.removeButton.setOnClickListener {
                viewModel.removeDownload(song.id)
            }
        }

        private fun updateDownloadUi(uiState: DownloadUiState, song: Song) {
            // song.currentDownloadUiState = uiState // 如果还需要在 Song 对象中暂存
            binding.songProgressBar.isIndeterminate = false
            println("------>>>> state: $uiState.status")
            when (uiState.status) {
                Download.STATE_DOWNLOADING -> {
                    binding.statusTextView.text = "下载中: ${uiState.percentDownloaded.toInt()}%"
                    binding.songProgressBar.progress = uiState.percentDownloaded.toInt()
                    binding.downloadButton.text = "继续" // 或禁用
                    binding.pauseButton.visibility = View.VISIBLE
                    binding.removeButton.visibility = View.VISIBLE
                }
                Download.STATE_COMPLETED -> {
                    binding.statusTextView.text = "已完成"
                    binding.songProgressBar.progress = 100
                    binding.downloadButton.text = "播放"
                    binding.pauseButton.visibility = View.GONE
                    binding.removeButton.visibility = View.VISIBLE
                    binding.removeButton.isEnabled = true
                }
                // ... (其他状态的 UI 更新逻辑，与之前方案类似) ...
                Download.STATE_QUEUED -> {
                    binding.statusTextView.text = "排队中"
                    binding.songProgressBar.isIndeterminate = true
                    binding.downloadButton.text = "取消"
                    binding.pauseButton.visibility = View.GONE
                    binding.removeButton.visibility = View.VISIBLE
                }
                Download.STATE_STOPPED -> {
                    binding.statusTextView.text = "已暂停"
                    binding.songProgressBar.progress = uiState.percentDownloaded.toInt()
                    binding.downloadButton.text = "继续"
                    binding.pauseButton.visibility = View.GONE
                    binding.removeButton.visibility = View.VISIBLE
                }
                Download.STATE_FAILED -> {
                    binding.statusTextView.text = "失败 (原因: ${uiState.failureReason})"
                    binding.songProgressBar.progress = uiState.percentDownloaded.toInt() // 或者0
                    binding.downloadButton.text = "重试"
                    binding.pauseButton.visibility = View.GONE
                    binding.removeButton.visibility = View.VISIBLE
                }
                Download.STATE_REMOVING -> {
                    binding.statusTextView.text = "正在移除..."
                    binding.songProgressBar.isIndeterminate = true
                    binding.downloadButton.isEnabled = false
                    binding.pauseButton.visibility = View.GONE
                    binding.removeButton.isEnabled = false
                }
                else -> {
                    resetDownloadUi(song)
                }
            }
        }

        private fun resetDownloadUi(song: Song) { // 传入 song 以便知道是哪个 item
            binding.statusTextView.text = "未下载 (ID: ${song.id})" // 只是示例
            binding.songProgressBar.progress = 0
            binding.songProgressBar.isIndeterminate = false
            binding.downloadButton.text = "下载"
            binding.downloadButton.isEnabled = true
            binding.pauseButton.visibility = View.GONE
            binding.removeButton.visibility = View.GONE
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun submitList(newSongs: List<Song>) {
        songs = newSongs
        // 考虑使用 DiffUtil 进行更高效的列表更新
        notifyDataSetChanged()
    }
}

