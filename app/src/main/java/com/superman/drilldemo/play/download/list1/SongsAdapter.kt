package com.superman.drilldemo.play.download.list1



// (This file is the same as the "方案 A" SongsAdapter.kt from the previous answer)
// Ensure package names and imports (like for DownloadViewModel, DownloadUiState, Song, item_song.xml binding) are correct.
import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.recyclerview.widget.RecyclerView
import com.superman.drilldemo.databinding.ItemSongBinding

@UnstableApi
class SongsAdapter(
    private var songs: List<Song>,
    private val viewModel: DownloadViewModel,
    private val lifecycleOwner: LifecycleOwner,
    private val onItemClick: (Song) -> Unit, // For playing the song
    private val onDownloadClick: (Song) -> Unit,
    private val onPauseClick: (Song) -> Unit,
    private val onRemoveClick: (Song) -> Unit
) : RecyclerView.Adapter<SongsAdapter.SongViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val binding = ItemSongBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SongViewHolder(binding)
    }

    override fun getItemCount(): Int = songs.size

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        val song = songs[position]
        holder.bind(song)
    }

    override fun onViewRecycled(holder: SongViewHolder) {
        super.onViewRecycled(holder)
        holder.currentObserver?.let { observer ->
            holder.currentObservedLiveData?.removeObserver(observer)
        }
        holder.currentObservedLiveData = null
        holder.currentObserver = null
    }

    inner class SongViewHolder(private val binding: ItemSongBinding) : RecyclerView.ViewHolder(binding.root) {
        var currentObservedLiveData: LiveData<DownloadUiState?>? = null
        var currentObserver: Observer<DownloadUiState?>? = null

        init {
            itemView.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(songs[position])
                }
            }
        }

        fun bind(song: Song) {
            binding.songTitleTextView.text = song.title
            // binding.songArtistTextView.text = song.artist // If you add an artist TextView

            val songSpecificLiveData = viewModel.getDownloadStateLiveData(song.id)
            currentObserver?.let { currentObservedLiveData?.removeObserver(it) }

            val observer = Observer<DownloadUiState?> { uiState ->
                if (uiState != null) updateDownloadUi(uiState, song) else resetDownloadUi(song)
            }
            this.currentObserver = observer
            this.currentObservedLiveData = songSpecificLiveData
            songSpecificLiveData.observe(lifecycleOwner, observer)

            if (songSpecificLiveData.value == null) resetDownloadUi(song) // Initial state

            binding.downloadButton.setOnClickListener { onDownloadClick(song) }
            binding.pauseButton.setOnClickListener { onPauseClick(song) }
            binding.removeButton.setOnClickListener { onRemoveClick(song) }
        }

        private fun updateDownloadUi(uiState: DownloadUiState, song: Song) {
            binding.songProgressBar.isIndeterminate = false
            binding.downloadButton.visibility = View.VISIBLE
            binding.pauseButton.visibility = View.GONE
            binding.removeButton.visibility = View.GONE
            println("-----state: ${uiState.status} ,progress: ${uiState.percentDownloaded}")
            when (uiState.status) {
                Download.STATE_DOWNLOADING -> {
                    binding.statusTextView.text = "下载中: ${uiState.percentDownloaded.toInt()}%"
                    binding.songProgressBar.progress = uiState.percentDownloaded.toInt()
                    binding.downloadButton.text = "继续" // Or disable
                    binding.downloadButton.isEnabled = false // Let pause handle resume
                    binding.pauseButton.visibility = View.VISIBLE
                    binding.removeButton.visibility = View.VISIBLE
                }
                Download.STATE_COMPLETED -> {
                    binding.statusTextView.text = "已完成"
                    binding.songProgressBar.progress = 100
                    binding.downloadButton.text = "播放" // Indicates it's ready
                    binding.downloadButton.isEnabled = true
                    binding.removeButton.visibility = View.VISIBLE
                    binding.removeButton.isEnabled = true
                }
                Download.STATE_QUEUED -> {
                    binding.statusTextView.text = "排队中"
                    binding.songProgressBar.isIndeterminate = true
                    binding.downloadButton.text = "取消" // Or manage queue
                    binding.downloadButton.isEnabled = true // To allow cancel via remove
                    binding.removeButton.visibility = View.VISIBLE // As a way to cancel
                }
                Download.STATE_STOPPED -> {
                    binding.statusTextView.text = "已暂停"
                    binding.songProgressBar.progress = uiState.percentDownloaded.toInt()
                    binding.downloadButton.text = "继续"
                    binding.downloadButton.isEnabled = true
                    binding.removeButton.visibility = View.VISIBLE
                    binding.removeButton.isEnabled =true
                }
                Download.STATE_FAILED -> {
                    binding.statusTextView.text = "下载失败" // Reason: ${uiState.failureReason}
                    binding.songProgressBar.progress = uiState.percentDownloaded.toInt()
                    binding.downloadButton.text = "重试"
                    binding.downloadButton.isEnabled = true
                    binding.removeButton.visibility = View.VISIBLE
                }
                Download.STATE_REMOVING -> {
                    binding.statusTextView.text = "正在移除..."
                    binding.songProgressBar.isIndeterminate = true
                    binding.downloadButton.isEnabled = false
                    binding.pauseButton.visibility = View.GONE
                    binding.removeButton.isEnabled = false
                }
                else -> resetDownloadUi(song)
            }
            // Global pause override
            if (uiState.isPaused && (uiState.status == Download.STATE_DOWNLOADING || uiState.status == Download.STATE_QUEUED)) {
                binding.statusTextView.text = "已暂停 (全局)"
                binding.pauseButton.text = "继续全部" // Or some indicator
                // Potentially disable individual resume if globally paused
            } else if(uiState.status == Download.STATE_DOWNLOADING) {
                binding.pauseButton.text = "暂停"
            }
        }

        private fun resetDownloadUi(song: Song) {
            binding.statusTextView.text = "可下载"
            binding.songProgressBar.progress = 0
            binding.songProgressBar.isIndeterminate = false
            binding.downloadButton.text = "下载"
            binding.downloadButton.visibility = View.VISIBLE
            binding.downloadButton.isEnabled = true
            binding.pauseButton.visibility = View.GONE
            binding.removeButton.visibility = View.GONE
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun submitList(newSongs: List<Song>) {
        songs = newSongs
        notifyDataSetChanged() // For simplicity. Use DiffUtil in a real app.
    }
}
