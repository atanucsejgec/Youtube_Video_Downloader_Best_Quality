package com.example.youtubedownloader

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ContentValues
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter

/* ─────────────────────── DATA MODELS ─────────────────────── */
data class VideoDetails(
    val title: String,
    val thumbnail: String?,
    val duration: Long,
    val author: String?
)

data class PlaylistInfo(
    val title: String,
    val count: Int
)

data class PlaylistEntry(
    val id: String,
    val title: String
)

data class SavedFileInfo(
    val displayPath: String,
    val mimeType: String,
    val contentUri: String?,
    val absolutePath: String?,
    val fileSizeBytes: Long = 0L
)

data class StorageInfo(
    val cacheSize: Long,
    val cacheSizeText: String
)

enum class VideoQuality(
    val label: String,
    val formatArg: String,
    val isAudioOnly: Boolean = false
) {
    BEST("Best Quality (Auto)", "bestvideo+bestaudio/best"),
    MAX_QUALITY("Max Bitrate · Largest Size", "bestvideo+bestaudio/best"),
    UHD_4K(
        "4K · 2160p",
        "bestvideo[height<=2160][ext=mp4]+bestaudio[ext=m4a]/bestvideo[height<=2160]+bestaudio/best[height<=2160]"
    ),
    QHD_2K(
        "2K · 1440p",
        "bestvideo[height<=1440][ext=mp4]+bestaudio[ext=m4a]/bestvideo[height<=1440]+bestaudio/best[height<=1440]"
    ),
    FHD(
        "1080p · Full HD",
        "bestvideo[height<=1080][ext=mp4]+bestaudio[ext=m4a]/bestvideo[height<=1080]+bestaudio/best[height<=1080]"
    ),
    HD(
        "720p · HD",
        "bestvideo[height<=720][ext=mp4]+bestaudio[ext=m4a]/bestvideo[height<=720]+bestaudio/best[height<=720]"
    ),
    SD(
        "480p",
        "bestvideo[height<=480][ext=mp4]+bestaudio[ext=m4a]/bestvideo[height<=480]+bestaudio/best[height<=480]"
    ),
    LOW(
        "360p · Data Saver",
        "bestvideo[height<=360][ext=mp4]+bestaudio[ext=m4a]/bestvideo[height<=360]+bestaudio/best[height<=360]"
    ),
    SMALLEST(
        "High Quality · Low Size · AV1",
        "bestvideo[vcodec^=av01]+bestaudio/bestvideo+bestaudio/best"
    ),
    COMPATIBLE(
        "Most Compatible · H.264",
        "bestvideo[vcodec^=avc1]+bestaudio[acodec^=mp4a]/best"
    ),
    AUDIO_BEST("Audio · Best", "bestaudio/best", true),
    AUDIO_MP3("Audio · MP3", "bestaudio", true),
    AUDIO_M4A("Audio · M4A", "bestaudio[ext=m4a]/bestaudio", true);

    val isVideo: Boolean get() = !isAudioOnly
}

/* ── Browse state: what the user is doing in the search/info panel ── */
sealed interface BrowseState {
    data object Idle : BrowseState
    data object FetchingInfo : BrowseState
    data object Searching : BrowseState
    data class SearchResults(val results: List<SearchResult>, val query: String) : BrowseState
    data class InfoReady(
        val details: VideoDetails,
        val formatSizes: Map<VideoQuality, String>,
        val playlistInfo: PlaylistInfo?,
        val playlistEntries: List<PlaylistEntry> = emptyList()
    ) : BrowseState
    data class Error(val message: String) : BrowseState
}

/* ── Download state: what is actively downloading ── */
sealed interface DownloadState {
    data object Idle : DownloadState
    data class Downloading(
        val title: String,
        val thumbnail: String?,
        val progress: Float,
        val eta: Long = 0L,
        val line: String = "",
        val currentItem: Int = 1,
        val totalItems: Int = 1,
        val currentVideoTitle: String = "",
        val downloadSpeed: Double = 0.0
    ) : DownloadState
    data class Completed(
        val title: String,
        val savedLocation: String,
        val fileCount: Int,
        val failedCount: Int = 0,
        val lastFile: SavedFileInfo? = null,
        val downloadedSizeBytes: Long = 0L
    ) : DownloadState
    data class Failed(val title: String, val message: String) : DownloadState
}

/* ── Keep old UiState for engine init / compatibility ── */
sealed interface UiState {
    data object Idle : UiState
    data object Initializing : UiState
    data class Error(val message: String) : UiState
}

/* ─────────────────────── VIEW MODEL ─────────────────────── */
class MainViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        private const val TAG = "MainVM"
        private const val PREFS_QUALITY = "last_quality"
        private const val PREFS_NAME = "yt_vm_prefs"
    }

    /* ── Engine state ── */
    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /* ── Browse state (search / info panel) ── */
    private val _browseState = MutableStateFlow<BrowseState>(BrowseState.Idle)
    val browseState: StateFlow<BrowseState> = _browseState.asStateFlow()

    /* ── Download state (active download + completed) ── */
    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    /* ── URL bar ── */
    private val _url = MutableStateFlow("")
    val url: StateFlow<String> = _url.asStateFlow()

    /* ── Quality ── */
    private val _quality = MutableStateFlow(loadLastQuality())
    val quality: StateFlow<VideoQuality> = _quality.asStateFlow()

    /* ── Playlist toggle ── */
    private val _downloadAsPlaylist = MutableStateFlow(false)
    val downloadAsPlaylist: StateFlow<Boolean> = _downloadAsPlaylist.asStateFlow()

    /* ── Download location ── */
    private val _downloadLocation = MutableStateFlow(DownloadPrefs.getLocation(app))
    val downloadLocation: StateFlow<DownloadLocation> = _downloadLocation.asStateFlow()

    /* ── Settings ── */
    private val _showSettings = MutableStateFlow(false)
    val showSettings: StateFlow<Boolean> = _showSettings.asStateFlow()

    /* ── Storage ── */
    private val _storageInfo = MutableStateFlow(StorageInfo(0L, "0 B"))
    val storageInfo: StateFlow<StorageInfo> = _storageInfo.asStateFlow()

    private val _isClearing = MutableStateFlow(false)
    val isClearing: StateFlow<Boolean> = _isClearing.asStateFlow()

    /* ── Auth ── */
    private val _hasCookies = MutableStateFlow(false)
    val hasCookies: StateFlow<Boolean> = _hasCookies.asStateFlow()

    private val _showLogin = MutableStateFlow(false)
    val showLogin: StateFlow<Boolean> = _showLogin.asStateFlow()

    /* ── Clipboard ── */
    private val _clipboardUrl = MutableStateFlow<String?>(null)
    val clipboardUrl: StateFlow<String?> = _clipboardUrl.asStateFlow()

    /* ── Playlist dialog ── */
    private val _showPlaylistDialog = MutableStateFlow(false)
    val showPlaylistDialog: StateFlow<Boolean> = _showPlaylistDialog.asStateFlow()

    /* ── Search results visibility ── */
    private val _searchResultsVisible = MutableStateFlow(false)
    val searchResultsVisible: StateFlow<Boolean> = _searchResultsVisible.asStateFlow()

    /* ── Queue ── */
    val downloadQueue = DownloadQueue.queue

    /* ── Internal ── */
    @Volatile private var isEngineReady = false
    private var fetchJob: Job? = null       // for fetching / searching
    private var downloadJob: Job? = null    // for the active download
    private var currentProcessId: String? = null
    private var currentSpeedBps: Double = 0.0

    private val tempDir: File
        get() = CacheManager.getTempDir(getApplication()).also { if (!it.exists()) it.mkdirs() }

    private fun Throwable.fullTrace(): String {
        val sw = StringWriter(); printStackTrace(PrintWriter(sw)); return sw.toString()
    }

    /* ── Cancel from notification ── */
    private val cancelReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { cancelDownload() }
    }

    init {
        performAutoCleanup()
        initEngine()
        checkCookieStatus()
        registerCancelReceiver()
        // Start watching queue for new items
        observeQueue()
    }

    override fun onCleared() {
        super.onCleared()
        try { getApplication<Application>().unregisterReceiver(cancelReceiver) } catch (_: Exception) {}
    }

    /* ════════════════════════════════════════════════════════
       QUEUE OBSERVER — starts next download when one finishes
       ════════════════════════════════════════════════════════ */
    private fun observeQueue() {
        viewModelScope.launch {
            // Watch only queue changes
            DownloadQueue.queue.collect { queue ->
                val nextWaiting = queue.firstOrNull { it.status == QueueItemStatus.WAITING }
                val isAlreadyDownloading = queue.any { it.status == QueueItemStatus.DOWNLOADING }

                if (nextWaiting != null && !isAlreadyDownloading
                    && downloadJob?.isActive != true
                    && isEngineReady
                ) {
                    processQueueItem(nextWaiting)
                }
            }
        }
    }
    private fun checkAndStartNext() {
        viewModelScope.launch {
            // Small delay to ensure downloadJob is fully done
            delay(100)
            val queue = DownloadQueue.queue.value
            val isAlreadyDownloading = queue.any { it.status == QueueItemStatus.DOWNLOADING }
            val nextWaiting = queue.firstOrNull { it.status == QueueItemStatus.WAITING }

            if (nextWaiting != null && !isAlreadyDownloading
                && downloadJob?.isActive != true
                && isEngineReady
            ) {
                processQueueItem(nextWaiting)
            }
        }
    }

    private fun processQueueItem(item: QueueItem) {
        // Double-check nothing is already downloading
        val isAlreadyDownloading = DownloadQueue.queue.value
            .any { it.status == QueueItemStatus.DOWNLOADING }
        if (isAlreadyDownloading || downloadJob?.isActive == true) return

        downloadJob = viewModelScope.launch(Dispatchers.IO) {
            DownloadQueue.update(item.id) { it.copy(status = QueueItemStatus.DOWNLOADING) }
            DownloadService.startService(getApplication())
            try {
                if (item.isPlaylist && item.selectedEntries.isNotEmpty()) {
                    executePlaylistDownload(item)
                } else {
                    executeSingleDownload(item)
                }
            } catch (e: CancellationException) {
                DownloadQueue.update(item.id) {
                    it.copy(status = QueueItemStatus.FAILED, errorMsg = "Cancelled")
                }
                cleanTempDir()
                _downloadState.value = DownloadState.Idle
            } catch (e: Exception) {
                Log.e(TAG, "Download failed: ${e.message}", e)
                DownloadQueue.update(item.id) {
                    it.copy(status = QueueItemStatus.FAILED, errorMsg = e.message)
                }
                cleanTempDir()
                _downloadState.value = DownloadState.Failed(item.title, e.message ?: "Unknown error")
            } finally {
                DownloadService.stopService(getApplication())
                try {
                    CacheManager.autoCleanup(getApplication())
                    updateStorageInfo()
                } catch (_: Exception) {}
                // Always check for next item after this job ends
                checkAndStartNext()
            }
        }
    }

    /* ════════════════════════════════════════════════════════
       SINGLE DOWNLOAD EXECUTION
       ════════════════════════════════════════════════════════ */
    private suspend fun executeSingleDownload(item: QueueItem) {
        cleanTempDir()
        val pid = "dl_${System.currentTimeMillis()}"
        currentProcessId = pid
        currentSpeedBps = 0.0

        _downloadState.value = DownloadState.Downloading(
            title = item.title,
            thumbnail = item.thumbnail,
            progress = 0f,
            currentVideoTitle = item.title
        )

        var lastNotifTime = 0L
        val request = buildDownloadRequest(item.url, item.quality)

        YoutubeDL.getInstance().execute(request, pid) { progress, eta, line ->
            val speed = parseSpeedFromLine(line)
            if (speed > 0) currentSpeedBps = speed

            val current = _downloadState.value
            if (current is DownloadState.Downloading) {
                _downloadState.value = current.copy(
                    progress = progress,
                    eta = eta,
                    line = line,
                    downloadSpeed = currentSpeedBps
                )
            }
            DownloadQueue.update(item.id) {
                it.copy(progress = progress, downloadSpeedBps = currentSpeedBps)
            }

            val now = System.currentTimeMillis()
            if (now - lastNotifTime > 500) {
                lastNotifTime = now
                DownloadService.updateNotification(
                    getApplication(), item.title, progress.toInt(),
                    if (currentSpeedBps > 0) formatSpeed(currentSpeedBps) else "",
                    if (eta > 0) "${eta}s" else "",
                    getQueueInfo()
                )
            }
        }

        // Save file
        val downloadedFile = tempDir.listFiles()
            ?.filter { it.isFile && it.length() > 0 }
            ?.maxByOrNull { it.lastModified() }

        val fileSizeBytes = downloadedFile?.length() ?: 0L
        val savedFile = downloadedFile?.let { f ->
            saveToGallery(f).also { f.delete() }
        }
        cleanTempDir()

        DownloadQueue.update(item.id) {
            it.copy(
                status = if (savedFile != null) QueueItemStatus.COMPLETED else QueueItemStatus.FAILED,
                progress = 100f,
                savedFileInfo = savedFile,
                fileSizeBytes = fileSizeBytes,
                errorMsg = if (savedFile == null) "Save failed" else null
            )
        }

        val location = savedFile?.displayPath?.substringBeforeLast('/') ?: "Gallery"

        // Check if next item is waiting
        val hasNext = DownloadQueue.queue.value
            .any { it.status == QueueItemStatus.WAITING }

        _downloadState.value = DownloadState.Completed(
            title = item.title,
            savedLocation = location,
            fileCount = if (savedFile != null) 1 else 0,
            failedCount = if (savedFile == null) 1 else 0,
            lastFile = savedFile,
            downloadedSizeBytes = fileSizeBytes
        )

        DownloadService.showCompletedNotification(
            getApplication(), item.title,
            if (savedFile != null) 1 else 0
        )

        if (hasNext) {
            // Has next item — show completed briefly then go idle
            // checkAndStartNext() in finally block will handle starting it
            delay(1500)
            _downloadState.value = DownloadState.Idle
        }
        // If no next item, keep Completed visible until user dismisses
        // checkAndStartNext() in finally will find nothing waiting and do nothing
    }
    /* ════════════════════════════════════════════════════════
       PLAYLIST DOWNLOAD EXECUTION
       ════════════════════════════════════════════════════════ */
    private suspend fun executePlaylistDownload(item: QueueItem) {
        val entries = item.selectedEntries
        val total = entries.size
        var savedCount = 0
        var failedCount = 0
        var lastFile: SavedFileInfo? = null
        var totalSizeBytes = 0L

        for ((index, entry) in entries.withIndex()) {
            currentCoroutineContext().ensureActive()
            val videoNum = index + 1

            _downloadState.value = DownloadState.Downloading(
                title = item.title,
                thumbnail = item.thumbnail,
                progress = 0f,
                currentItem = videoNum,
                totalItems = total,
                currentVideoTitle = entry.title
            )
            DownloadQueue.update(item.id) {
                it.copy(progress = (index.toFloat() / total) * 100f)
            }

            try {
                cleanTempDir()
                val pid = "pl_${index}_${System.currentTimeMillis()}"
                currentProcessId = pid
                val videoUrl = "https://www.youtube.com/watch?v=${entry.id}"
                val request = buildDownloadRequest(videoUrl, item.quality, noPlaylist = true)
                var lastNotifTime = 0L

                YoutubeDL.getInstance().execute(request, pid) { progress, eta, line ->
                    val speed = parseSpeedFromLine(line)
                    if (speed > 0) currentSpeedBps = speed

                    val current = _downloadState.value
                    if (current is DownloadState.Downloading) {
                        _downloadState.value = current.copy(
                            progress = progress,
                            eta = eta,
                            line = line,
                            downloadSpeed = currentSpeedBps
                        )
                    }

                    val now = System.currentTimeMillis()
                    if (now - lastNotifTime > 500) {
                        lastNotifTime = now
                        DownloadService.updateNotification(
                            getApplication(), entry.title, progress.toInt(),
                            if (currentSpeedBps > 0) formatSpeed(currentSpeedBps) else "",
                            if (eta > 0) "${eta}s" else "",
                            "$videoNum/$total"
                        )
                    }
                }

                val downloadedFile = tempDir.listFiles()
                    ?.filter { it.isFile && it.length() > 0 }
                    ?.maxByOrNull { it.lastModified() }

                if (downloadedFile != null) {
                    totalSizeBytes += downloadedFile.length()
                    val saved = saveToGallery(downloadedFile)
                    downloadedFile.delete()
                    if (saved != null) { savedCount++; lastFile = saved } else failedCount++
                } else failedCount++

            } catch (e: CancellationException) {
                cleanTempDir(); throw e
            } catch (e: Exception) {
                failedCount++
                Log.e(TAG, "Playlist video ${index + 1} failed: ${e.message}")
            }
            cleanTempDir()
        }

        cleanTempDir()
        val location = lastFile?.displayPath?.substringBeforeLast('/') ?: "Gallery"

        DownloadQueue.update(item.id) {
            it.copy(
                status = if (savedCount > 0) QueueItemStatus.COMPLETED else QueueItemStatus.FAILED,
                progress = 100f,
                savedFileInfo = lastFile,
                fileSizeBytes = totalSizeBytes
            )
        }

        _downloadState.value = DownloadState.Completed(
            title = item.title,
            savedLocation = location,
            fileCount = savedCount,
            failedCount = failedCount,
            lastFile = lastFile,
            downloadedSizeBytes = totalSizeBytes
        )

        DownloadService.showCompletedNotification(getApplication(), item.title, savedCount)

        val hasNext = DownloadQueue.queue.value
            .any { it.status == QueueItemStatus.WAITING }

        if (hasNext) {
            delay(1500)
            _downloadState.value = DownloadState.Idle
        }
    }


    /* ════════════════════════════════════════════════════════
       FETCH INFO — fully independent from download state
       ════════════════════════════════════════════════════════ */
    fun fetchInfo() {
        val videoUrl = _url.value.trim()
        if (videoUrl.isBlank()) return
        if (!isEngineReady) {
            _browseState.value = BrowseState.Error("Engine not ready. Wait for init.")
            return
        }

        // Non-YouTube → search
        if (!SearchRepository.isYouTubeUrl(videoUrl)) {
            performSearch(videoUrl)
            return
        }

        _searchResultsVisible.value = false
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch(Dispatchers.IO) {
            _browseState.value = BrowseState.FetchingInfo
            try {
                val request = YoutubeDLRequest(videoUrl).apply {
                    addOption("--dump-single-json")
                    addOption("--flat-playlist")
                    addOption("--no-warnings")
                    addOption("--force-ipv4")
                    addCookieOptions()
                }
                val result = YoutubeDL.getInstance().execute(request)
                val json = parseJson(result.out)

                val isPlaylist = json.optString("_type") == "playlist" || json.has("entries")
                var playlistInfo: PlaylistInfo? = null
                var videoJson = json

                if (isPlaylist) {
                    val entries = json.optJSONArray("entries")
                    val count = json.optInt("playlist_count", entries?.length() ?: 0)
                    val plTitle = json.optString("title", "Playlist")
                    playlistInfo = PlaylistInfo(plTitle, count)
                    _downloadAsPlaylist.value = true

                    val parsedEntries = mutableListOf<PlaylistEntry>()
                    if (entries != null) {
                        for (i in 0 until entries.length()) {
                            val entry = entries.optJSONObject(i) ?: continue
                            val id = entry.optString("id", "")
                            val title = entry.optString("title", "Video ${i + 1}")
                            if (id.isNotBlank()) parsedEntries.add(PlaylistEntry(id, title))
                        }
                    }

                    // Get first video details for display
                    if (parsedEntries.isNotEmpty()) {
                        try {
                            val vReq = YoutubeDLRequest(
                                "https://www.youtube.com/watch?v=${parsedEntries[0].id}"
                            ).apply {
                                addOption("--dump-single-json")
                                addOption("--no-playlist")
                                addOption("--no-warnings")
                                addOption("--force-ipv4")
                                addCookieOptions()
                            }
                            videoJson = parseJson(YoutubeDL.getInstance().execute(vReq).out)
                        } catch (e: Exception) {
                            Log.w(TAG, "First video info failed: ${e.message}")
                        }
                    }

                    val details = VideoDetails(
                        title = videoJson.optString("title", playlistInfo.title),
                        thumbnail = videoJson.optString("thumbnail").takeIf { it.isNotBlank() },
                        duration = videoJson.optDouble("duration", 0.0).toLong(),
                        author = videoJson.optString("uploader").takeIf { it.isNotBlank() }
                    )
                    val formatSizes = calculateFormatSizes(videoJson)
                    _browseState.value = BrowseState.InfoReady(
                        details, formatSizes, playlistInfo, parsedEntries
                    )
                } else {
                    _downloadAsPlaylist.value = false
                    val details = VideoDetails(
                        title = json.optString("title", "Unknown"),
                        thumbnail = json.optString("thumbnail").takeIf { it.isNotBlank() },
                        duration = json.optDouble("duration", 0.0).toLong(),
                        author = json.optString("uploader").takeIf { it.isNotBlank() }
                    )
                    val formatSizes = calculateFormatSizes(json)
                    _browseState.value = BrowseState.InfoReady(details, formatSizes, null, emptyList())
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                Log.e(TAG, "fetchInfo failed", e)
                _browseState.value = BrowseState.Error(
                    "Fetch failed:\n${e.javaClass.simpleName}: ${e.message}"
                )
            }
        }
    }

    /* ════════════════════════════════════════════════════════
       SEARCH — also independent from download state
       ════════════════════════════════════════════════════════ */
    fun performSearch(query: String) {
        if (query.isBlank()) return
        if (!isEngineReady) {
            _browseState.value = BrowseState.Error("Engine not ready")
            return
        }
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch(Dispatchers.IO) {
            _browseState.value = BrowseState.Searching
            try {
                val results = SearchRepository.search(getApplication(), query)
                _browseState.value = BrowseState.SearchResults(results, query)
                _searchResultsVisible.value = results.isNotEmpty()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                Log.e(TAG, "Search failed", e)
                _browseState.value = BrowseState.Error("Search failed: ${e.message}")
            }
        }
    }

    /* ════════════════════════════════════════════════════════
       ADD TO QUEUE
       ════════════════════════════════════════════════════════ */
    fun addCurrentInfoToQueue(selectedEntries: List<PlaylistEntry>? = null) {
        val state = _browseState.value as? BrowseState.InfoReady ?: return
        val isPlaylist = selectedEntries != null && selectedEntries.isNotEmpty()

        val item = QueueItem(
            url = _url.value.trim(),
            title = state.details.title,
            thumbnail = state.details.thumbnail,
            quality = _quality.value,
            isPlaylist = isPlaylist,
            selectedEntries = selectedEntries ?: emptyList()
        )

        // Reset browse panel
        _browseState.value = BrowseState.Idle
        _url.value = ""
        _downloadAsPlaylist.value = false
        _showPlaylistDialog.value = false

        // Add to queue — observer will pick it up if nothing downloading
        // If something is downloading, finally block will pick it up when done
        DownloadQueue.add(item)
    }
    fun addSearchResultToQueue(result: SearchResult) {
        // We need to fetch info for this result to get format sizes,
        // so set the URL and trigger fetchInfo
        _url.value = result.url
        _searchResultsVisible.value = false
        _browseState.value = BrowseState.Idle
        fetchInfo()
    }

    fun removeFromQueue(id: String) { DownloadQueue.remove(id) }
    fun clearCompletedFromQueue() { DownloadQueue.clearCompleted() }

    /* ════════════════════════════════════════════════════════
       DOWNLOAD BUTTON — triggers queue add
       ════════════════════════════════════════════════════════ */
    fun startDownload() {
        val state = _browseState.value as? BrowseState.InfoReady ?: return
        val isPlaylist = _downloadAsPlaylist.value &&
                state.playlistEntries.isNotEmpty()

        if (isPlaylist) {
            _showPlaylistDialog.value = true
        } else {
            addCurrentInfoToQueue(null)
        }
    }

    fun startDownloadWithSelection(selectedEntries: List<PlaylistEntry>) {
        _showPlaylistDialog.value = false
        addCurrentInfoToQueue(selectedEntries)
    }

    /* ════════════════════════════════════════════════════════
       CANCEL
       ════════════════════════════════════════════════════════ */
    fun cancelDownload() {
        currentProcessId?.let {
            try { YoutubeDL.getInstance().destroyProcessById(it) } catch (_: Exception) {}
        }
        downloadJob?.cancel()
        cleanTempDir()
        DownloadQueue.queue.value
            .firstOrNull { it.status == QueueItemStatus.DOWNLOADING }
            ?.let { item ->
                DownloadQueue.update(item.id) {
                    it.copy(status = QueueItemStatus.FAILED, errorMsg = "Cancelled")
                }
            }
        DownloadService.stopService(getApplication())
        _downloadState.value = DownloadState.Idle
    }

    /* ════════════════════════════════════════════════════════
       RESET BROWSE PANEL
       ════════════════════════════════════════════════════════ */
    fun reset() {
        fetchJob?.cancel()
        _url.value = ""
        _downloadAsPlaylist.value = false
        _searchResultsVisible.value = false
        _browseState.value = BrowseState.Idle
    }

    fun dismissDownloadResult() {
        if (_downloadState.value is DownloadState.Completed ||
            _downloadState.value is DownloadState.Failed
        ) {
            _downloadState.value = DownloadState.Idle
            checkAndStartNext()
        }
    }

    /* ════════════════════════════════════════════════════════
       URL / QUALITY
       ════════════════════════════════════════════════════════ */
    fun updateUrl(v: String) {
        _url.value = v
        if (v.isBlank()) {
            _searchResultsVisible.value = false
        }
    }

    fun selectQuality(q: VideoQuality) {
        _quality.value = q
        saveLastQuality(q)
    }

    fun setDownloadAsPlaylist(v: Boolean) { _downloadAsPlaylist.value = v }

    /* ════════════════════════════════════════════════════════
       CLIPBOARD
       ════════════════════════════════════════════════════════ */
    fun checkClipboard(clipText: String?) {
        if (clipText.isNullOrBlank()) return
        val url = extractYouTubeUrl(clipText) ?: return
        if (url != _url.value) _clipboardUrl.value = url
    }

    fun dismissClipboardDialog() { _clipboardUrl.value = null }

    fun acceptClipboardUrl(url: String) {
        _url.value = url
        _clipboardUrl.value = null
        if (isEngineReady) fetchInfo()
    }

    /* ════════════════════════════════════════════════════════
       SETTINGS / AUTH / MISC
       ════════════════════════════════════════════════════════ */
    fun showLoginScreen() { _showLogin.value = true }
    fun hideLoginScreen() { _showLogin.value = false }
    fun onLoginComplete() {
        _showLogin.value = false
        _hasCookies.value = CookieHelper.hasCookies(getApplication())
    }
    fun clearLoginCookies() {
        CookieHelper.clearCookies(getApplication())
        _hasCookies.value = false
    }
    fun checkCookieStatus() { _hasCookies.value = CookieHelper.hasCookies(getApplication()) }

    fun toggleSettings() {
        _showSettings.value = !_showSettings.value
        if (_showSettings.value) updateStorageInfo()
    }

    fun setDownloadLocation(location: DownloadLocation) {
        _downloadLocation.value = location
        DownloadPrefs.setLocation(getApplication(), location)
    }

    fun showPlaylistDialog() { _showPlaylistDialog.value = true }
    fun hidePlaylistDialog() { _showPlaylistDialog.value = false }
    fun hideSearchResults() { _searchResultsVisible.value = false }

    fun handleSharedUrl(url: String) {
        _url.value = url
        _searchResultsVisible.value = false
        if (isEngineReady) fetchInfo()
    }

    /* ════════════════════════════════════════════════════════
       ENGINE INIT
       ════════════════════════════════════════════════════════ */
    fun initEngine() {
        viewModelScope.launch(Dispatchers.IO) {
            if (isEngineReady) { _uiState.value = UiState.Idle; return@launch }
            _uiState.value = UiState.Initializing
            try {
                YoutubeDL.getInstance().init(getApplication())
                FFmpeg.getInstance().init(getApplication())
                isEngineReady = true
                Log.d(TAG, "Engine ready")
                try {
                    YoutubeDL.getInstance()
                        .updateYoutubeDL(getApplication(), YoutubeDL.UpdateChannel.STABLE)
                } catch (e: Exception) {
                    Log.w(TAG, "Update skipped: ${e.message}")
                }
                updateStorageInfo()
                _uiState.value = UiState.Idle
                // Check queue after engine ready
                val next = DownloadQueue.getNext()
                if (next != null) processQueueItem(next)
            } catch (e: Exception) {
                Log.e(TAG, "Init failed", e)
                _uiState.value = UiState.Error("Init failed:\n${e.fullTrace()}")
            }
        }
    }

    /* ════════════════════════════════════════════════════════
       STORAGE
       ════════════════════════════════════════════════════════ */
    private fun performAutoCleanup() {
        viewModelScope.launch(Dispatchers.IO) {
            try { CacheManager.autoCleanup(getApplication()); updateStorageInfo() }
            catch (e: Exception) { Log.w(TAG, "Cleanup error: ${e.message}") }
        }
    }

    fun updateStorageInfo() {
        viewModelScope.launch(Dispatchers.IO) {
            val size = CacheManager.getCleanableSize(getApplication())
            _storageInfo.value = StorageInfo(size, CacheManager.formatBytes(size))
        }
    }

    fun clearAllCache() {
        viewModelScope.launch(Dispatchers.IO) {
            _isClearing.value = true
            try { CacheManager.deepCleanup(getApplication()); updateStorageInfo() }
            catch (e: Exception) { Log.e(TAG, "Deep cleanup error", e) }
            finally { _isClearing.value = false }
        }
    }

    /* ════════════════════════════════════════════════════════
       BUILD REQUEST
       ════════════════════════════════════════════════════════ */
    private fun buildDownloadRequest(
        url: String,
        quality: VideoQuality,
        noPlaylist: Boolean = true
    ): YoutubeDLRequest {
        return YoutubeDLRequest(url).apply {
            if (noPlaylist) addOption("--no-playlist")
            addOption("--force-ipv4")
            when {
                quality == VideoQuality.AUDIO_MP3 -> {
                    addOption("-x")
                    addOption("--audio-format", "mp3")
                    addOption("-f", "bestaudio/best")
                }
                quality.isAudioOnly -> {
                    addOption("-f", quality.formatArg)
                }
                quality == VideoQuality.MAX_QUALITY -> {
                    addOption("-f", "bestvideo+bestaudio/best")
                    addOption("-S", "quality,res,br,fps")
                    addOption("--merge-output-format", "mkv")
                }
                quality == VideoQuality.BEST -> {
                    addOption("-f", "bestvideo+bestaudio/best")
                    addOption("-S", "res,fps,br")
                    addOption("--merge-output-format", "mp4")
                }
                else -> {
                    val targetHeight = Regex("height<=(\\d+)")
                        .find(quality.formatArg)?.groupValues?.get(1)?.toIntOrNull() ?: 1080
                    addOption(
                        "-f",
                        "bestvideo[height<=${targetHeight}][ext=mp4]+bestaudio[ext=m4a]/" +
                                "bestvideo[height<=${targetHeight}]+bestaudio/" +
                                "best[height<=${targetHeight}]"
                    )
                    addOption("-S", "height:${targetHeight},fps,br,res")
                    addOption("--merge-output-format", "mp4")
                }
            }
            addOption("-o", "${tempDir.absolutePath}/%(title).150s.%(ext)s")
            addOption("--restrict-filenames")
            addOption("--no-mtime")
            addOption("--no-cache-dir")
            addOption("--continue") // resume support
            addCookieOptions()
            // Speed options
            addOption("--concurrent-fragments", "4")
            addOption("--buffer-size", "16K")
            addOption("--no-check-certificates")
            addOption("--extractor-retries", "3")
            addOption("--retry-sleep", "linear=1::2")
        }
    }

    /* ════════════════════════════════════════════════════════
       HELPERS
       ════════════════════════════════════════════════════════ */
    private fun YoutubeDLRequest.addCookieOptions() {
        val f = CookieHelper.getCookieFile(getApplication())
        if (f.exists() && f.length() > 100) addOption("--cookies", f.absolutePath)
    }

    private fun parseSpeedFromLine(line: String): Double {
        Regex("([\\d.]+)\\s*MiB/s").find(line)?.let {
            return (it.groupValues[1].toDoubleOrNull() ?: 0.0) * 1024 * 1024
        }
        Regex("([\\d.]+)\\s*KiB/s").find(line)?.let {
            return (it.groupValues[1].toDoubleOrNull() ?: 0.0) * 1024
        }
        Regex("([\\d.]+)\\s*B/s").find(line)?.let {
            return it.groupValues[1].toDoubleOrNull() ?: 0.0
        }
        return 0.0
    }

    private fun getQueueInfo(): String {
        val waiting = DownloadQueue.queue.value.count { it.status == QueueItemStatus.WAITING }
        return if (waiting > 0) "$waiting in queue" else ""
    }

    private fun formatSpeed(bps: Double): String = when {
        bps <= 0 -> ""
        bps < 1024 * 1024 -> "%.1f KB/s".format(bps / 1024)
        else -> "%.2f MB/s".format(bps / (1024 * 1024))
    }

    private fun parseJson(raw: String): JSONObject {
        val t = raw.trim()
        return try { JSONObject(t) } catch (_: Exception) {
            val s = t.indexOf('{'); val e = t.lastIndexOf('}')
            if (s >= 0 && e > s) JSONObject(t.substring(s, e + 1))
            else throw Exception("Could not parse JSON")
        }
    }

    private fun cleanTempDir() {
        try { tempDir.listFiles()?.forEach { it.deleteRecursively() } } catch (_: Exception) {}
    }

    private fun registerCancelReceiver() {
        try {
            val filter = IntentFilter("com.example.youtubedownloader.CANCEL_DOWNLOAD")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                getApplication<Application>().registerReceiver(
                    cancelReceiver,
                    filter,
                    Context.RECEIVER_NOT_EXPORTED
                )
            } else {
                // For API < 33, use ContextCompat which handles the flag safely
                androidx.core.content.ContextCompat.registerReceiver(
                    getApplication<Application>(),
                    cancelReceiver,
                    filter,
                    androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
                )
            }
        } catch (_: Exception) {}
    }

    private fun extractYouTubeUrl(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val regex =
            Regex("""(https?://(www\.)?(youtube\.com|youtu\.be|m\.youtube\.com|music\.youtube\.com)\S+)""")
        return regex.find(text)?.value
            ?: if (SearchRepository.isYouTubeUrl(text.trim())) text.trim() else null
    }

    private fun saveLastQuality(q: VideoQuality) {
        getApplication<Application>()
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(PREFS_QUALITY, q.name).apply()
    }

    private fun loadLastQuality(): VideoQuality {
        val name = getApplication<Application>()
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(PREFS_QUALITY, VideoQuality.FHD.name)
        return try { VideoQuality.valueOf(name!!) } catch (_: Exception) { VideoQuality.FHD }
    }

    private fun mimeOf(name: String) = when {
        name.endsWith(".mp4") -> "video/mp4"
        name.endsWith(".webm") -> "video/webm"
        name.endsWith(".mkv") -> "video/x-matroska"
        name.endsWith(".mp3") -> "audio/mpeg"
        name.endsWith(".m4a") -> "audio/mp4"
        name.endsWith(".opus") -> "audio/opus"
        name.endsWith(".ogg") -> "audio/ogg"
        else -> "video/mp4"
    }

    /* ════════════════════════════════════════════════════════
       FORMAT SIZES
       ════════════════════════════════════════════════════════ */
    private fun calculateFormatSizes(json: JSONObject): Map<VideoQuality, String> {
        val result = mutableMapOf<VideoQuality, String>()
        val formatsArray = json.optJSONArray("formats") ?: return result
        val durationSec = json.optDouble("duration", 0.0).toLong()

        data class Fmt(val height: Int, val size: Long, val codec: String)

        val videoFormats = mutableListOf<Fmt>()
        var bestAudioSize = 0L

        for (i in 0 until formatsArray.length()) {
            val fmt = formatsArray.getJSONObject(i)
            val vcodec = fmt.optString("vcodec", "none")
            val acodec = fmt.optString("acodec", "none")
            val height = fmt.optInt("height", 0)
            var filesize = fmt.optLong("filesize", 0)
            if (filesize <= 0) filesize = fmt.optLong("filesize_approx", 0)
            if (filesize <= 0) {
                val tbr = fmt.optDouble("tbr", 0.0)
                if (tbr > 0 && durationSec > 0)
                    filesize = (tbr * 1000.0 / 8.0 * durationSec).toLong()
            }
            val hasVideo = vcodec != "none" && vcodec.isNotEmpty()
            val hasAudio = acodec != "none" && acodec.isNotEmpty()
            if (hasVideo && !hasAudio && height > 0) videoFormats.add(Fmt(height, filesize, vcodec))
            if (hasAudio && !hasVideo && filesize > bestAudioSize) bestAudioSize = filesize
        }

        val targets = mapOf(
            VideoQuality.UHD_4K to 2160, VideoQuality.QHD_2K to 1440,
            VideoQuality.FHD to 1080, VideoQuality.HD to 720,
            VideoQuality.SD to 480, VideoQuality.LOW to 360
        )
        for ((quality, targetH) in targets) {
            val vf = videoFormats.filter { it.height <= targetH }.maxByOrNull { it.height }
            if (vf != null) {
                val total = vf.size + bestAudioSize
                if (total > 0) result[quality] = formatBytes(total)
            }
        }
        videoFormats.maxByOrNull { it.height }?.let { best ->
            val total = best.size + bestAudioSize
            if (total > 0) result[VideoQuality.BEST] = "${formatBytes(total)} · ${best.height}p"
        }
        videoFormats.maxByOrNull { it.size }?.let { max ->
            val total = max.size + bestAudioSize
            if (total > 0) result[VideoQuality.MAX_QUALITY] = "${formatBytes(total)} · ${max.height}p"
        }
        videoFormats.filter { it.codec.startsWith("av01") }.maxByOrNull { it.height }?.let { av1 ->
            val total = av1.size + bestAudioSize
            if (total > 0) result[VideoQuality.SMALLEST] = "${formatBytes(total)} · ${av1.height}p"
        }
        videoFormats.filter { it.codec.startsWith("avc1") }.maxByOrNull { it.height }?.let { h264 ->
            val total = h264.size + bestAudioSize
            if (total > 0) result[VideoQuality.COMPATIBLE] = "${formatBytes(total)} · ${h264.height}p"
        }
        if (bestAudioSize > 0) {
            result[VideoQuality.AUDIO_BEST] = formatBytes(bestAudioSize)
            result[VideoQuality.AUDIO_M4A] = formatBytes(bestAudioSize)
            result[VideoQuality.AUDIO_MP3] = formatBytes(bestAudioSize)
        }
        return result
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes <= 0 -> ""
        bytes < 1024L * 1024 -> "~%.0f KB".format(bytes / 1024.0)
        bytes < 1024L * 1024 * 1024 -> "~%.0f MB".format(bytes / (1024.0 * 1024.0))
        else -> "~%.1f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
    }

    /* ════════════════════════════════════════════════════════
       GALLERY SAVE
       ════════════════════════════════════════════════════════ */
    private fun saveToGallery(sourceFile: File): SavedFileInfo? {
        val mime = mimeOf(sourceFile.name)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            saveToGalleryQ(sourceFile, mime)
        else saveToGalleryLegacy(sourceFile, mime)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveToGalleryQ(sourceFile: File, mime: String): SavedFileInfo? {
        val context = getApplication<Application>()
        val resolver = context.contentResolver
        val isAudio = mime.startsWith("audio")
        val location = _downloadLocation.value
        val subfolder = DownloadPrefs.getSubfolderName(context)

        val (collection, relativePath) = when {
            isAudio || location == DownloadLocation.MUSIC ->
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to
                        "${Environment.DIRECTORY_MUSIC}/$subfolder"
            location == DownloadLocation.DCIM ->
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to
                        "${Environment.DIRECTORY_DCIM}/$subfolder"
            location == DownloadLocation.DOWNLOADS ->
                MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to
                        "${Environment.DIRECTORY_DOWNLOADS}/$subfolder"
            else ->
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to
                        "${Environment.DIRECTORY_MOVIES}/$subfolder"
        }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, sourceFile.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: return null
        try {
            resolver.openOutputStream(uri)?.use { out ->
                sourceFile.inputStream().use { it.copyTo(out) }
            }
        } catch (e: IOException) {
            resolver.delete(uri, null, null); return null
        }
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return SavedFileInfo(
            "$relativePath/${sourceFile.name}", mime,
            uri.toString(), null, sourceFile.length()
        )
    }

    @Suppress("DEPRECATION")
    private fun saveToGalleryLegacy(sourceFile: File, mime: String): SavedFileInfo? {
        val context = getApplication<Application>()
        val location = _downloadLocation.value
        val subfolder = DownloadPrefs.getSubfolderName(context)
        val isAudio = mime.startsWith("audio")
        val baseDir = when {
            isAudio || location == DownloadLocation.MUSIC ->
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            location == DownloadLocation.DOWNLOADS ->
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            location == DownloadLocation.DCIM ->
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
            else ->
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        }
        val destDir = File(baseDir, subfolder).also { it.mkdirs() }
        val destFile = File(destDir, sourceFile.name)
        sourceFile.copyTo(destFile, overwrite = true)
        MediaScannerConnection.scanFile(context, arrayOf(destFile.absolutePath), arrayOf(mime), null)
        return SavedFileInfo(destFile.absolutePath, mime, null, destFile.absolutePath, destFile.length())
    }
}