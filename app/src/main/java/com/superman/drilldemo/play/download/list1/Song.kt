package com.superman.drilldemo.play.download.list1

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download

// (这个文件与前一个回答中的 Song.kt 基本相同)
data class Song(
    val id: String, // 唯一ID，也用作下载ID
    val title: String,
    val url: String, // 下载/播放链接
    val artist: String? = "未知艺术家",
    val coverUrl: String? = null // 可选的封面图片链接
)

@UnstableApi
data class DownloadUiState(
    val downloadId: String,
    val title: String,
    @Download.State val status: Int,
    val percentDownloaded: Float,
    @Download.FailureReason val failureReason: Int,
    val isPaused: Boolean // 是否因全局暂停而暂停
)