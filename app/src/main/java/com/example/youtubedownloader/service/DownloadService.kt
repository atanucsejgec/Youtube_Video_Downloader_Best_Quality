package com.example.youtubedownloader.service

import android.app.*
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.os.*
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.example.youtubedownloader.*
import com.example.youtubedownloader.data.*
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Semaphore
import java.io.File

class DownloadService : Service() {

    companion object {
        const val TAG = "DownloadService"
        const val ACTION_START = "START"
        const val ACTION_CANCEL = "CANCEL"
        const val ACTION_CANCEL_ALL = "CANCEL_ALL"
        const val EXTRA_QUEUE_ID = "queue_id"
        const val NOTIF_ID = 1001
        const val COMPLETE_NOTIF_BASE = 2000

        private val _serviceState = MutableStateFlow(ServiceState())
        val serviceState: StateFlow<ServiceState> = _serviceState.asStateFlow()

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_START
            }
            context.startForegroundService(intent)
        }

        fun cancel(context: Context, queueId: Long) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_CANCEL
                putExtra(EXTRA_QUEUE_ID, queueId)
            }
            context.startService(intent)
        }

        fun cancelAll(context: Context) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_CANCEL_ALL
            }
            context.startService(intent)
        }
    }

    data class ServiceState(
        val activeDownloads: Map<Long, DownloadProgress> = emptyMap(),
        val totalInQueue: Int = 0,
        val completedCount: Int = 0,
        val failedCount: Int = 0
    )

    data class DownloadProgress(
        val queueId: Long,
        val title: String = "",
        val progress: Float = 0f,
        val speedText: String = "",
        val eta: Long = 0,
        val line: String = ""
    )

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeProcessIds = mutableMapOf<Long, String>()
    private var mainJob: Job? = null

    // ── Use LocalStorage instead of Room ──
    private val storage get() = App.storage

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIF_ID, buildNotification("Preparing downloads...", 0, 0))
        _isRunning.value = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startProcessing()
            ACTION_CANCEL -> {
                val queueId = intent.getLongExtra(EXTRA_QUEUE_ID, -1)
                if (queueId > 0) cancelSingle(queueId)
            }
            ACTION_CANCEL_ALL -> cancelAllDownloads()
        }
        return START_STICKY
    }

    private fun startProcessing() {
        if (mainJob?.isActive == true) return

        mainJob = scope.launch {
            try {
                processQueue()
            } finally {
                _isRunning.value = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private suspend fun processQueue() {
        val concurrentLimit = AppPrefs.getConcurrentDownloads(this@DownloadService)
        val semaphore = Semaphore(concurrentLimit)

        while (true) {
            // Check network
            if (!NetworkMonitor.canDownload(this@DownloadService)) {
                updateNotification("Waiting for network...", 0, 0)
                delay(5000)
                continue
            }

            // Get pending items
            val pending = storage.getPendingQueue()
            val retryable = storage.getRetryableQueue()
            val allItems = pending + retryable

            if (allItems.isEmpty()) {
                val downloading = storage.getDownloadingQueue()
                if (downloading.isEmpty()) break
                delay(1000)
                continue
            }

            val jobs = allItems.map { item ->
                scope.launch {
                    semaphore.acquire()
                    try {
                        downloadItem(item)
                    } finally {
                        semaphore.release()
                    }
                }
            }

            jobs.joinAll()

            // Check if more items were added
            val morePending = storage.getPendingQueue()
            if (morePending.isEmpty()) break
        }

        // Show completion notification
        val state = _serviceState.value
        showCompleteNotification(state.completedCount, state.failedCount)
    }

    private suspend fun downloadItem(item: QueueItem) {
        val pid = "dl_${item.id}_${System.currentTimeMillis()}"
        activeProcessIds[item.id] = pid

        storage.updateQueueStatus(item.id, "downloading")

        try {
            val tempDir = CacheManager.getTempDir(this@DownloadService)
            if (!tempDir.exists()) tempDir.mkdirs()

            // Clean temp before this download
            tempDir.listFiles()?.forEach { it.deleteRecursively() }

            val quality = try {
                VideoQuality.valueOf(item.quality)
            } catch (_: Exception) {
                VideoQuality.BEST
            }

            val fragments = AppPrefs.getConcurrentFragments(this@DownloadService)
            val speedLimit = AppPrefs.getSpeedLimitKb(this@DownloadService)

            val request = YoutubeDLRequest(item.url).apply {
                addOption("--no-playlist")
                addOption("--force-ipv4")
                addOption("-f", quality.formatArg)
                addOption("-o", "${tempDir.absolutePath}/%(title).150s.%(ext)s")
                addOption("--restrict-filenames")
                addOption("--no-mtime")
                addOption("--no-cache-dir")

                // Speed boost
                addOption("--concurrent-fragments", fragments.toString())
                addOption("--buffer-size", "64K")
                addOption("--no-part")
                addOption("--no-check-certificates")
                addOption("--extractor-retries", "3")
                addOption("--retry-sleep", "linear=1::2")
                addOption("--throttled-rate", "100K")

                // Speed limit
                if (speedLimit > 0) {
                    addOption("--limit-rate", "${speedLimit}K")
                }

                // Cookies
                val cookieFile = CookieHelper.getCookieFile(this@DownloadService)
                if (cookieFile.exists() && cookieFile.length() > 100) {
                    addOption("--cookies", cookieFile.absolutePath)
                }

                // Subtitles
                if (item.downloadSubtitles ||
                    AppPrefs.downloadSubtitles(this@DownloadService)
                ) {
                    addOption("--write-sub")
                    addOption("--write-auto-sub")
                    addOption("--sub-lang", "en,hi,bn")
                    addOption("--convert-subs", "srt")
                }

                // Embed thumbnail
                if (item.embedThumbnail ||
                    AppPrefs.embedThumbnail(this@DownloadService)
                ) {
                    addOption("--embed-thumbnail")
                }

                // Format/Container
                if (quality == VideoQuality.AUDIO_MP3) {
                    addOption("-x")
                    addOption("--audio-format", "mp3")
                } else if (quality.isAudioOnly) {
                    // keep original
                } else if (quality == VideoQuality.MAX_QUALITY) {
                    addOption("-S", "quality,res,br")
                    addOption("--merge-output-format", "mkv")
                } else {
                    addOption("--merge-output-format", "mp4")
                }
            }

            YoutubeDL.getInstance().execute(request, pid) { progress, eta, line ->
                val speed = SpeedTracker.parseSpeedFromLine(line)
                val speedText = if (speed > 0) SpeedTracker.formatSpeed(speed) else ""

                val dlProgress = DownloadProgress(
                    queueId = item.id,
                    title = item.title ?: "Downloading...",
                    progress = progress,
                    speedText = speedText,
                    eta = eta,
                    line = line
                )

                _serviceState.update { state ->
                    state.copy(
                        activeDownloads = state.activeDownloads + (item.id to dlProgress)
                    )
                }

                // Update queue progress in storage
                scope.launch {
                    storage.updateQueueProgress(item.id, progress, speedText)
                }

                updateNotification(
                    item.title ?: "Downloading...",
                    progress.toInt(),
                    _serviceState.value.activeDownloads.size
                )
            }

            // Find downloaded file
            val downloadedFile = tempDir.listFiles()
                ?.filter { it.isFile && it.length() > 0 && !it.name.endsWith(".srt") }
                ?.maxByOrNull { it.lastModified() }

            if (downloadedFile != null) {
                val saved = saveToGallery(downloadedFile)
                downloadedFile.delete()

                // Save subtitles too
                tempDir.listFiles()
                    ?.filter { it.name.endsWith(".srt") }
                    ?.forEach { srtFile ->
                        saveToGallery(srtFile)
                        srtFile.delete()
                    }

                if (saved != null) {
                    storage.updateQueueStatus(item.id, "completed")

                    // Extract video ID
                    val videoId = extractVideoId(item.url) ?: item.url
                    val fileSize = File(saved.absolutePath ?: "").let {
                        if (it.exists()) it.length() else 0L
                    }

                    // Record in history
                    storage.addHistory(
                        DownloadHistoryItem(
                            videoId = videoId,
                            url = item.url,
                            title = item.title ?: "Unknown",
                            thumbnail = item.thumbnail,
                            quality = item.quality,
                            fileSize = fileSize,
                            fileSizeText = CacheManager.formatBytes(fileSize),
                            filePath = saved.displayPath,
                            mimeType = saved.mimeType,
                            contentUri = saved.contentUri
                        )
                    )

                    AppPrefs.addToTotalEverDownloaded(
                        this@DownloadService, fileSize
                    )

                    _serviceState.update {
                        it.copy(completedCount = it.completedCount + 1)
                    }
                } else {
                    storage.markQueueFailed(item.id, "Failed to save to gallery")
                    _serviceState.update {
                        it.copy(failedCount = it.failedCount + 1)
                    }
                }
            } else {
                storage.markQueueFailed(item.id, "No file downloaded")
                _serviceState.update {
                    it.copy(failedCount = it.failedCount + 1)
                }
            }

            // Clean temp
            tempDir.listFiles()?.forEach { it.deleteRecursively() }

        } catch (e: CancellationException) {
            storage.updateQueueStatus(item.id, "paused")
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Download failed for ${item.url}", e)
            storage.markQueueFailed(item.id, e.message ?: "Unknown error")
            _serviceState.update {
                it.copy(failedCount = it.failedCount + 1)
            }
        } finally {
            activeProcessIds.remove(item.id)
            _serviceState.update { state ->
                state.copy(activeDownloads = state.activeDownloads - item.id)
            }
        }
    }

    private fun cancelSingle(queueId: Long) {
        activeProcessIds[queueId]?.let {
            try {
                YoutubeDL.getInstance().destroyProcessById(it)
            } catch (_: Exception) {}
        }
        scope.launch {
            storage.updateQueueStatus(queueId, "paused")
        }
    }

    private fun cancelAllDownloads() {
        activeProcessIds.values.forEach {
            try {
                YoutubeDL.getInstance().destroyProcessById(it)
            } catch (_: Exception) {}
        }
        mainJob?.cancel()
        scope.launch {
            storage.getDownloadingQueue().forEach {
                storage.updateQueueStatus(it.id, "paused")
            }
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ══════════════════════════════════
    //  NOTIFICATIONS
    // ══════════════════════════════════

    private fun buildNotification(
        title: String,
        progress: Int,
        activeCount: Int
    ): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = PendingIntent.getService(
            this, 1,
            Intent(this, DownloadService::class.java).apply {
                action = ACTION_CANCEL_ALL
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, App.CHANNEL_DOWNLOAD)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(
                if (activeCount > 1) "Downloading $activeCount videos"
                else title
            )
            .setContentText("$progress%")
            .setProgress(100, progress.coerceIn(0, 100), progress <= 0)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_delete, "Cancel All", cancelIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(
        title: String,
        progress: Int,
        activeCount: Int
    ) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(title, progress, activeCount))
    }

    private fun showCompleteNotification(completed: Int, failed: Int) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val text = buildString {
            append("$completed downloaded")
            if (failed > 0) append(" · $failed failed")
        }

        val notif = NotificationCompat.Builder(this, App.CHANNEL_COMPLETE)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Downloads Complete")
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()

        nm.notify(COMPLETE_NOTIF_BASE, notif)
    }

    // ══════════════════════════════════
    //  GALLERY SAVE
    // ══════════════════════════════════

    private fun saveToGallery(sourceFile: File): SavedFileInfo? {
        val mime = mimeOf(sourceFile.name)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveToGalleryQ(sourceFile, mime)
        } else {
            saveToGalleryLegacy(sourceFile, mime)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveToGalleryQ(sourceFile: File, mime: String): SavedFileInfo? {
        val resolver = contentResolver
        val isAudio = mime.startsWith("audio")
        val isSrt = mime == "text/plain"
        val location = AppPrefs.getLocation(this)
        val subfolder = AppPrefs.getSubfolder(this)

        val (collection, relativePath) = when {
            isSrt || location == DownloadLocation.DOWNLOADS ->
                MediaStore.Downloads.getContentUri(
                    MediaStore.VOLUME_EXTERNAL_PRIMARY
                ) to "${Environment.DIRECTORY_DOWNLOADS}/$subfolder"

            location == DownloadLocation.CUSTOM ->
                MediaStore.Downloads.getContentUri(
                    MediaStore.VOLUME_EXTERNAL_PRIMARY
                ) to "${Environment.DIRECTORY_DOWNLOADS}/$subfolder"

            isAudio || location == DownloadLocation.MUSIC ->
                MediaStore.Audio.Media.getContentUri(
                    MediaStore.VOLUME_EXTERNAL_PRIMARY
                ) to "${Environment.DIRECTORY_MUSIC}/$subfolder"

            location == DownloadLocation.DCIM ->
                MediaStore.Video.Media.getContentUri(
                    MediaStore.VOLUME_EXTERNAL_PRIMARY
                ) to "${Environment.DIRECTORY_DCIM}/$subfolder"

            else ->
                MediaStore.Video.Media.getContentUri(
                    MediaStore.VOLUME_EXTERNAL_PRIMARY
                ) to "${Environment.DIRECTORY_MOVIES}/$subfolder"
        }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, sourceFile.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val uri = resolver.insert(collection, values) ?: return null

        resolver.openOutputStream(uri)?.use { output ->
            sourceFile.inputStream().use { input -> input.copyTo(output) }
        }

        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, values, null, null)

        return SavedFileInfo(
            displayPath = "$relativePath/${sourceFile.name}",
            mimeType = mime,
            contentUri = uri.toString(),
            absolutePath = null
        )
    }

    @Suppress("DEPRECATION")
    private fun saveToGalleryLegacy(
        sourceFile: File,
        mime: String
    ): SavedFileInfo? {
        val location = AppPrefs.getLocation(this)
        val subfolder = AppPrefs.getSubfolder(this)
        val isAudio = mime.startsWith("audio")

        val baseDir = when {
            location == DownloadLocation.MUSIC || isAudio ->
                Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_MUSIC
                )
            location == DownloadLocation.DOWNLOADS ->
                Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS
                )
            location == DownloadLocation.DCIM ->
                Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DCIM
                )
            else ->
                Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_MOVIES
                )
        }

        val destDir = File(baseDir, subfolder).also { it.mkdirs() }
        val destFile = File(destDir, sourceFile.name)
        sourceFile.copyTo(destFile, overwrite = true)

        MediaScannerConnection.scanFile(
            this,
            arrayOf(destFile.absolutePath),
            arrayOf(mime),
            null
        )

        return SavedFileInfo(
            displayPath = destFile.absolutePath,
            mimeType = mime,
            contentUri = null,
            absolutePath = destFile.absolutePath
        )
    }

    // ══════════════════════════════════
    //  HELPERS
    // ══════════════════════════════════

    private fun mimeOf(name: String) = when {
        name.endsWith(".mp4") -> "video/mp4"
        name.endsWith(".webm") -> "video/webm"
        name.endsWith(".mkv") -> "video/x-matroska"
        name.endsWith(".mp3") -> "audio/mpeg"
        name.endsWith(".m4a") -> "audio/mp4"
        name.endsWith(".opus") -> "audio/opus"
        name.endsWith(".ogg") -> "audio/ogg"
        name.endsWith(".srt") -> "text/plain"
        else -> "video/mp4"
    }

    private fun extractVideoId(url: String): String? {
        val patterns = listOf(
            Regex("""[?&]v=([a-zA-Z0-9_-]{11})"""),
            Regex("""youtu\.be/([a-zA-Z0-9_-]{11})"""),
            Regex("""/shorts/([a-zA-Z0-9_-]{11})""")
        )
        for (p in patterns) {
            p.find(url)?.groupValues?.get(1)?.let { return it }
        }
        return null
    }

    override fun onDestroy() {
        scope.cancel()
        _isRunning.value = false
        super.onDestroy()
    }
}