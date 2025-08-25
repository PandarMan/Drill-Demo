package com.superman.drilldemo.play.download

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels // For by viewModels()
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import com.superman.drilldemo.R


@UnstableApi
class DownloadWithViewModelActivity : AppCompatActivity() {

    private val downloadViewModel: DownloadViewModel by viewModels() // ViewModel KTX

    private lateinit var songStatusTextView: TextView
    private lateinit var songProgressBar: ProgressBar
    private lateinit var songTitleTextView: TextView

    // Example song details (can be passed to ViewModel or fetched by it)
    private val currentSongId = "sample_song_vm_002"
    private val currentSongUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3"
    private val currentSongTitle = "SoundHelix ViewModel Song"

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Toast.makeText(this, "Notification permission granted", Toast.LENGTH_SHORT).show()
                // Now start the download as permission is granted
                downloadViewModel.startOrResumeDownload(
                    currentSongId,
                    currentSongUrl,
                    currentSongTitle
                )
            } else {
                Toast.makeText(
                    this,
                    "Notification permission denied. Cannot show download progress.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_download_example) // Reuse or create a new layout

        songStatusTextView = findViewById(R.id.songStatusTextView)
        songProgressBar = findViewById(R.id.downloadProgressBar)
        songTitleTextView = findViewById(R.id.songTitleTextView) // Assuming you have this

        val startDownloadButton: Button = findViewById(R.id.startDownloadButton)
        val pauseDownloadButton: Button = findViewById(R.id.pauseDownloadButton)
        val resumeDownloadButton: Button =
            findViewById(R.id.resumeDownloadButton) // You might combine start/resume
        val cancelDownloadButton: Button = findViewById(R.id.cancelDownloadButton)

        songTitleTextView.text = "Song: $currentSongTitle (ID: $currentSongId)"

        startDownloadButton.setOnClickListener {
            // Check for notification permission first
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                when {
                    ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED -> {
                        // Permission is already granted, start download
                        downloadViewModel.startOrResumeDownload(
                            currentSongId,
                            currentSongUrl,
                            currentSongTitle
                        )
                    }

                    shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                        Toast.makeText(
                            this,
                            "Notification permission is needed for download progress.",
                            Toast.LENGTH_LONG
                        ).show()
                        requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }

                    else -> {
                        requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            } else {
                // No runtime permission needed for notifications before Android 13
                downloadViewModel.startOrResumeDownload(
                    currentSongId,
                    currentSongUrl,
                    currentSongTitle
                )
            }
        }

        pauseDownloadButton.setOnClickListener {
            // This will pause all downloads as per current ViewModel logic
            downloadViewModel.pauseDownload(currentSongId)
        }

        resumeDownloadButton.setOnClickListener {
            // This will resume all downloads
            downloadViewModel.startOrResumeDownload(currentSongId, currentSongUrl, currentSongTitle)
        }

        cancelDownloadButton.setOnClickListener {
            downloadViewModel.removeDownload(currentSongId)
        }

        // Observe LiveData from ViewModel
        downloadViewModel.currentDownloadState.observe(this) { downloadUiState ->
            updateUi(downloadUiState)
        }

        // Fetch initial state for the song this screen is interested in
        downloadViewModel.fetchDownloadState(currentSongId)
    }

    private fun updateUi(downloadState: DownloadUiState?) {
        if (downloadState == null || downloadState.downloadId != currentSongId) {
            songStatusTextView.text = "Status: Not downloaded or different song"
            songProgressBar.progress = 0
            songProgressBar.isIndeterminate = false
            return
        }

        var statusText = "Status: "
        val testStatus =
            if (downloadState.isPaused && downloadState.status != Download.STATE_COMPLETED && downloadState.status != Download.STATE_FAILED) {
                Download.STATE_STOPPED // Override status to show as "Paused" if globally paused
            } else {
                downloadState.status
            }

        when (testStatus) {
            Download.STATE_COMPLETED -> {
                statusText += "Completed"
                songProgressBar.progress = 100
                songProgressBar.isIndeterminate = false
            }

            Download.STATE_DOWNLOADING -> {
                statusText += "Downloading (${
                    String.format(
                        "%.1f",
                        downloadState.percentDownloaded
                    )
                }%)"
                songProgressBar.isIndeterminate = downloadState.percentDownloaded < 0
                songProgressBar.progress =
                    if (downloadState.percentDownloaded >= 0) downloadState.percentDownloaded.toInt() else 0
            }

            Download.STATE_FAILED -> {
                statusText += "Failed (Reason: ${downloadState.failureReason})"
                songProgressBar.progress = 0
                songProgressBar.isIndeterminate = false
            }

            Download.STATE_QUEUED -> {
                statusText += "Queued"
                songProgressBar.isIndeterminate = true
            }

            Download.STATE_STOPPED -> { // Covers both explicitly stopped and globally paused
                statusText += if (downloadState.isPaused) "Paused (Globally)" else "Paused/Stopped"
                songProgressBar.progress =
                    if (downloadState.percentDownloaded >= 0) downloadState.percentDownloaded.toInt() else 0
                songProgressBar.isIndeterminate = false
            }

            Download.STATE_REMOVING -> {
                statusText += "Removing..."
                songProgressBar.isIndeterminate = true
            }
            // ... other states if needed
            else -> {
                statusText += "Unknown (${downloadState.status})"
                songProgressBar.isIndeterminate = true
            }
        }
        songTitleTextView.text = "Song: ${downloadState.title} (ID: ${downloadState.downloadId})"
        songStatusTextView.text = statusText
        Log.d(
            "DownloadActivityVM",
            "UI Update - ID: ${downloadState.downloadId}, Status: $statusText"
        )
    }

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, DownloadWithViewModelActivity::class.java))
        }
    }
}
