package com.example.youtubedownloader

import android.app.Application
import android.content.ContentValues
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.youtubedownloader.data.*
import com.example.youtubedownloader.service.DownloadService
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import java.io.*
import java.util.*

// ══════════════════════════════════════════════════════════
//  DATA MODELS
// ══════════════════════════════════════════════════════════

data class VideoDetails(
    val title: String,
    val thumbnail: String?,
    val duration: Long,
    val author: String?,
    val videoId: String? = null
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
    val absolutePath: String?
)

data class StorageInfo(
    val cacheSize: Long,
    val cacheSizeText: String,
    val totalEverDownloaded: Long = 0,
    val totalEverText: String = "0 B",
    val todayDownloaded: Long = 0,
    val todayText: String = "0 B",
    val totalCount: Int = 0,
    val freeSpace: Long = 0,
    val freeSpaceText: String = "0 B"
)

data class SearchResult(
    val id: String,
    val title: String,
    val thumbnail: String?,
    val duration: String,
    val author: String?,
    val url: String
)

// ══════════════════════════════════════════════════════════
//  VIDEO QUALITY ENUM
// ══════════════════════════════════════════════════════════

enum class VideoQuality(
    val label: String,
    val formatArg: String,
    val isAudioOnly: Boolean = false
) {
    BEST(
        "Best Quality (Auto)",
        "bestvideo+bestaudio/best"
    ),
    MAX_QUALITY(
        "Max Quality · Largest Size",
        "bestvideo+bestaudio/best"
    ),
    UHD_4K(
        "4K · 2160p",
        "bestvideo[height<=2160]+bestaudio/best[height<=2160]"
    ),
    QHD_2K(
        "2K · 1440p",
        "bestvideo[height<=1440]+bestaudio/best[height<=1440]"
    ),
    FHD(
        "1080p · Full HD",
        "bestvideo[height<=1080]+bestaudio/best[height<=1080]"
    ),
    HD(
        "720p · HD",
        "bestvideo[height<=720]+bestaudio/best[height<=720]"
    ),
    SD(
        "480p",
        "bestvideo[height<=480]+bestaudio/best[height<=480]"
    ),
    LOW(
        "360p · Data Saver",
        "bestvideo[height<=360]+bestaudio/best[height<=360]"
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

// ══════════════════════════════════════════════════════════
//  UI STATE
// ══════════════════════════════════════════════════════════

sealed interface UiState {
    data object Idle : UiState
    data object Initializing : UiState
    data object FetchingInfo : UiState

    data class InfoReady(
        val details: VideoDetails,
        val formatSizes: Map<VideoQuality, String>,
        val playlistInfo: PlaylistInfo?,
        val isDuplicate: Boolean = false
    ) : UiState

    data class Downloading(
        val details: VideoDetails,
        val progress: Float,
        val eta: Long = 0L,
        val line: String = "",
        val currentItem: Int = 1,
        val totalItems: Int = 1,
        val currentVideoTitle: String = "",
        val speedText: String = ""
    ) : UiState

    data class Completed(
        val details: VideoDetails,
        val savedLocation: String,
        val fileCount: Int,
        val failedCount: Int = 0,
        val lastFile: SavedFileInfo? = null
    ) : UiState

    data class Error(val message: String) : UiState
}

// ══════════════════════════════════════════════════════════
//  VIEW MODEL
// ══════════════════════════════════════════════════════════

class MainViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        private const val TAG = "MainVM"
    }

    // ── Reference to LocalStorage ──
    private val storage get() = App.storage

    // ══════════════════════════════════
    //  STATE FLOWS
    // ══════════════════════════════════

    // Core state
    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _url = MutableStateFlow("")
    val url: StateFlow<String> = _url.asStateFlow()

    private val _quality = MutableStateFlow(VideoQuality.BEST)
    val quality: StateFlow<VideoQuality> = _quality.asStateFlow()

    private val _downloadAsPlaylist = MutableStateFlow(false)
    val downloadAsPlaylist: StateFlow<Boolean> = _downloadAsPlaylist.asStateFlow()

    private val _downloadLocation = MutableStateFlow(AppPrefs.getLocation(app))
    val downloadLocation: StateFlow<DownloadLocation> = _downloadLocation.asStateFlow()

    private val _showSettings = MutableStateFlow(false)
    val showSettings: StateFlow<Boolean> = _showSettings.asStateFlow()

    private val _storageInfo = MutableStateFlow(StorageInfo(0L, "0 B"))
    val storageInfo: StateFlow<StorageInfo> = _storageInfo.asStateFlow()

    private val _isClearing = MutableStateFlow(false)
    val isClearing: StateFlow<Boolean> = _isClearing.asStateFlow()

    private val _hasCookies = MutableStateFlow(false)
    val hasCookies: StateFlow<Boolean> = _hasCookies.asStateFlow()

    private val _showLogin = MutableStateFlow(false)
    val showLogin: StateFlow<Boolean> = _showLogin.asStateFlow()

    // New feature states
    private val _isDarkTheme = MutableStateFlow(AppPrefs.isDarkTheme(app))
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    private val _isWifiOnly = MutableStateFlow(AppPrefs.isWifiOnly(app))
    val isWifiOnly: StateFlow<Boolean> = _isWifiOnly.asStateFlow()

    private val _speedLimitKb = MutableStateFlow(AppPrefs.getSpeedLimitKb(app))
    val speedLimitKb: StateFlow<Int> = _speedLimitKb.asStateFlow()

    private val _concurrentDownloads = MutableStateFlow(AppPrefs.getConcurrentDownloads(app))
    val concurrentDownloads: StateFlow<Int> = _concurrentDownloads.asStateFlow()

    private val _concurrentFragments = MutableStateFlow(AppPrefs.getConcurrentFragments(app))
    val concurrentFragments: StateFlow<Int> = _concurrentFragments.asStateFlow()

    private val _downloadSubtitles = MutableStateFlow(AppPrefs.downloadSubtitles(app))
    val downloadSubtitles: StateFlow<Boolean> = _downloadSubtitles.asStateFlow()

    private val _embedThumbnail = MutableStateFlow(AppPrefs.embedThumbnail(app))
    val embedThumbnail: StateFlow<Boolean> = _embedThumbnail.asStateFlow()

    private val _autoPaste = MutableStateFlow(AppPrefs.autoPaste(app))
    val autoPaste: StateFlow<Boolean> = _autoPaste.asStateFlow()

    private val _currentLanguage = MutableStateFlow(AppPrefs.getLanguage(app))
    val currentLanguage: StateFlow<String> = _currentLanguage.asStateFlow()

    private val _networkType = MutableStateFlow(NetworkMonitor.getNetworkType(app))
    val networkType: StateFlow<String> = _networkType.asStateFlow()

    // Search
    private val _searchResults = MutableStateFlow<List<SearchResult>>(emptyList())
    val searchResults: StateFlow<List<SearchResult>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Queue & History (from LocalStorage)
    val queueItems = App.storage.queueFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val historyItems = App.storage.historyFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Download service state
    val serviceState = DownloadService.serviceState
    val isServiceRunning = DownloadService.isRunning

    // Schedule
    private val _scheduleEnabled = MutableStateFlow(AppPrefs.isScheduleEnabled(app))
    val scheduleEnabled: StateFlow<Boolean> = _scheduleEnabled.asStateFlow()

    private val _scheduleHour = MutableStateFlow(AppPrefs.getScheduleHour(app))
    val scheduleHour: StateFlow<Int> = _scheduleHour.asStateFlow()

    private val _scheduleMinute = MutableStateFlow(AppPrefs.getScheduleMinute(app))
    val scheduleMinute: StateFlow<Int> = _scheduleMinute.asStateFlow()

    // ══════════════════════════════════
    //  INTERNAL STATE
    // ══════════════════════════════════

    @Volatile
    private var isEngineReady = false
    private var job: Job? = null
    private var processId: String? = null
    private var playlistEntries: List<PlaylistEntry> = emptyList()
    private var cachedFormatSizes: Map<VideoQuality, String> = emptyMap()
    private var cachedPlaylistInfo: PlaylistInfo? = null

    private val tempDir: File
        get() = CacheManager.getTempDir(getApplication())
            .also { if (!it.exists()) it.mkdirs() }

    private fun Throwable.fullTrace(): String {
        val sw = StringWriter()
        printStackTrace(PrintWriter(sw))
        return sw.toString()
    }

    // ══════════════════════════════════════════════════════════
    //  INIT
    // ══════════════════════════════════════════════════════════

    init {
        performAutoCleanup()
        initEngine()
        checkCookieStatus()
        monitorNetwork()
    }

    private fun monitorNetwork() {
        viewModelScope.launch {
            while (true) {
                _networkType.value = NetworkMonitor.getNetworkType(getApplication())
                delay(5000)
            }
        }
    }

    private fun performAutoCleanup() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                CacheManager.autoCleanup(getApplication())
                updateStorageInfo()
            } catch (e: Exception) {
                Log.w(TAG, "Auto-cleanup error: ${e.message}")
            }
        }
    }

    fun initEngine() {
        viewModelScope.launch(Dispatchers.IO) {
            if (isEngineReady) {
                _uiState.value = UiState.Idle
                return@launch
            }
            _uiState.value = UiState.Initializing
            try {
                YoutubeDL.getInstance().init(getApplication())
                FFmpeg.getInstance().init(getApplication())
                isEngineReady = true
                Log.d(TAG, "Engine ready")

                try {
                    YoutubeDL.getInstance()
                        .updateYoutubeDL(
                            getApplication(),
                            YoutubeDL.UpdateChannel.STABLE
                        )
                    Log.d(TAG, "yt-dlp updated")
                } catch (e: Exception) {
                    Log.w(TAG, "Update skipped: ${e.message}")
                }

                updateStorageInfo()
                _uiState.value = UiState.Idle

                // Auto-fetch if URL was shared while engine was initializing
                if (_url.value.isNotBlank()) fetchInfo()

            } catch (e: Exception) {
                Log.e(TAG, "Init failed", e)
                _uiState.value = UiState.Error("Init failed:\n${e.fullTrace()}")
            }
        }
    }

    // ══════════════════════════════════════════════════════════
    //  SETTINGS ACTIONS
    // ══════════════════════════════════════════════════════════

    fun toggleTheme() {
        val new = !_isDarkTheme.value
        _isDarkTheme.value = new
        AppPrefs.setDarkTheme(getApplication(), new)
    }

    fun setWifiOnly(v: Boolean) {
        _isWifiOnly.value = v
        AppPrefs.setWifiOnly(getApplication(), v)
    }

    fun setSpeedLimit(kb: Int) {
        _speedLimitKb.value = kb
        AppPrefs.setSpeedLimitKb(getApplication(), kb)
    }

    fun setConcurrentDownloads(n: Int) {
        val clamped = n.coerceIn(1, 5)
        _concurrentDownloads.value = clamped
        AppPrefs.setConcurrentDownloads(getApplication(), clamped)
    }

    fun setConcurrentFragments(n: Int) {
        val clamped = n.coerceIn(1, 64)
        _concurrentFragments.value = clamped
        AppPrefs.setConcurrentFragments(getApplication(), clamped)
    }

    fun setDownloadSubtitles(v: Boolean) {
        _downloadSubtitles.value = v
        AppPrefs.setDownloadSubtitles(getApplication(), v)
    }

    fun setEmbedThumbnail(v: Boolean) {
        _embedThumbnail.value = v
        AppPrefs.setEmbedThumbnail(getApplication(), v)
    }

    fun setAutoPaste(v: Boolean) {
        _autoPaste.value = v
        AppPrefs.setAutoPaste(getApplication(), v)
    }

    fun setLanguage(lang: String) {
        _currentLanguage.value = lang
        AppPrefs.setLanguage(getApplication(), lang)
    }

    fun setSchedule(
        enabled: Boolean,
        hour: Int = _scheduleHour.value,
        minute: Int = _scheduleMinute.value
    ) {
        _scheduleEnabled.value = enabled
        _scheduleHour.value = hour
        _scheduleMinute.value = minute
        AppPrefs.setScheduleEnabled(getApplication(), enabled)
        AppPrefs.setScheduleHour(getApplication(), hour)
        AppPrefs.setScheduleMinute(getApplication(), minute)

        com.example.youtubedownloader.worker.ScheduleWorker.schedule(getApplication())
    }

    // ══════════════════════════════════════════════════════════
    //  LOGIN / COOKIES
    // ══════════════════════════════════════════════════════════

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

    fun checkCookieStatus() {
        _hasCookies.value = CookieHelper.hasCookies(getApplication())
    }

    // ══════════════════════════════════════════════════════════
    //  BASIC ACTIONS
    // ══════════════════════════════════════════════════════════

    fun updateUrl(v: String) { _url.value = v }
    fun selectQuality(q: VideoQuality) { _quality.value = q }
    fun setDownloadAsPlaylist(v: Boolean) { _downloadAsPlaylist.value = v }

    fun toggleSettings() {
        _showSettings.value = !_showSettings.value
        if (_showSettings.value) updateStorageInfo()
    }

    fun setDownloadLocation(location: DownloadLocation) {
        _downloadLocation.value = location
        AppPrefs.setLocation(getApplication(), location)
    }

    fun handleSharedUrl(url: String) {
        _url.value = url
        if (isEngineReady && _uiState.value is UiState.Idle) {
            fetchInfo()
        }
    }

    // ══════════════════════════════════════════════════════════
    //  AUTO-PASTE
    // ══════════════════════════════════════════════════════════

    fun checkClipboardForUrl(clipText: String?) {
        if (!_autoPaste.value) return
        if (clipText.isNullOrBlank()) return
        if (_url.value.isNotBlank()) return

        val ytUrl = extractYouTubeUrl(clipText)
        if (ytUrl != null) {
            _url.value = ytUrl
        }
    }

    private fun extractYouTubeUrl(text: String): String? {
        val regex = Regex(
            """(https?://(www\.)?(youtube\.com|youtu\.be|m\.youtube\.com|music\.youtube\.com)\S+)"""
        )
        return regex.find(text)?.value
    }

    private fun isYouTubeUrl(url: String): Boolean =
        url.contains("youtube.com") ||
                url.contains("youtu.be") ||
                url.contains("music.youtube.com")

    // ══════════════════════════════════════════════════════════
    //  YOUTUBE SEARCH
    // ══════════════════════════════════════════════════════════

    fun updateSearchQuery(q: String) { _searchQuery.value = q }

    fun searchYouTube() {
        val query = _searchQuery.value.trim()
        if (query.isBlank() || !isEngineReady) return

        viewModelScope.launch(Dispatchers.IO) {
            _isSearching.value = true
            try {
                val request = YoutubeDLRequest("ytsearch10:$query").apply {
                    addOption("--dump-single-json")
                    addOption("--flat-playlist")
                    addOption("--no-warnings")
                    addOption("--force-ipv4")
                    addCookieOptions()
                }

                val result = YoutubeDL.getInstance().execute(request)
                val json = parseJson(result.out)
                val entries = json.optJSONArray("entries") ?: return@launch

                val results = mutableListOf<SearchResult>()
                for (i in 0 until entries.length()) {
                    val entry = entries.optJSONObject(i) ?: continue
                    val id = entry.optString("id", "")
                    if (id.isBlank()) continue

                    val duration = entry.optDouble("duration", 0.0).toLong()
                    val durationStr = if (duration > 0) formatDuration(duration) else ""

                    results.add(
                        SearchResult(
                            id = id,
                            title = entry.optString("title", "Unknown"),
                            thumbnail = entry.optString("thumbnail", null),
                            duration = durationStr,
                            author = entry.optString("uploader", null)
                                ?: entry.optString("channel", null),
                            url = "https://www.youtube.com/watch?v=$id"
                        )
                    )
                }

                _searchResults.value = results
            } catch (e: Exception) {
                Log.e(TAG, "Search failed", e)
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun selectSearchResult(result: SearchResult) {
        _url.value = result.url
        _searchResults.value = emptyList()
        _searchQuery.value = ""
        if (isEngineReady) fetchInfo()
    }

    // ══════════════════════════════════════════════════════════
    //  QUEUE MANAGEMENT
    // ══════════════════════════════════════════════════════════

    fun addToQueue(
        url: String? = null,
        title: String? = null,
        thumbnail: String? = null
    ) {
        val finalUrl = url ?: _url.value.trim()
        if (finalUrl.isBlank()) return

        viewModelScope.launch(Dispatchers.IO) {
            // Check duplicate in queue
            val existsInQueue = storage.countByUrl(finalUrl) > 0
            if (existsInQueue) {
                Log.w(TAG, "URL already in queue: $finalUrl")
                return@launch
            }

            storage.addToQueue(
                QueueItem(
                    id = 0, // will be assigned by LocalStorage
                    url = finalUrl,
                    title = title,
                    thumbnail = thumbnail,
                    quality = _quality.value.name,
                    downloadSubtitles = _downloadSubtitles.value,
                    embedThumbnail = _embedThumbnail.value
                )
            )
        }
    }

    fun addBatchToQueue(text: String) {
        val urls = text.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .mapNotNull { extractYouTubeUrl(it) ?: if (isYouTubeUrl(it)) it else null }
            .distinct()

        viewModelScope.launch(Dispatchers.IO) {
            urls.forEach { url ->
                val exists = storage.countByUrl(url) > 0
                if (!exists) {
                    storage.addToQueue(
                        QueueItem(
                            id = 0,
                            url = url,
                            quality = _quality.value.name,
                            downloadSubtitles = _downloadSubtitles.value,
                            embedThumbnail = _embedThumbnail.value
                        )
                    )
                }
            }
        }
    }

    fun addCurrentToQueue() {
        val details = when (val s = _uiState.value) {
            is UiState.InfoReady -> s.details
            is UiState.Downloading -> s.details
            else -> null
        }

        addToQueue(
            url = _url.value.trim(),
            title = details?.title,
            thumbnail = details?.thumbnail
        )
    }

    fun removeFromQueue(item: QueueItem) {
        viewModelScope.launch(Dispatchers.IO) {
            if (item.status == "downloading") {
                DownloadService.cancel(getApplication(), item.id)
            }
            storage.removeFromQueue(item.id)
        }
    }

    fun clearCompletedFromQueue() {
        viewModelScope.launch(Dispatchers.IO) {
            storage.clearCompletedQueue()
        }
    }

    fun retryFailed(item: QueueItem) {
        viewModelScope.launch(Dispatchers.IO) {
            storage.updateQueueStatus(item.id, "pending")
        }
    }

    fun retryAllFailed() {
        viewModelScope.launch(Dispatchers.IO) {
            storage.getRetryableQueue().forEach {
                storage.updateQueueStatus(it.id, "pending")
            }
        }
    }

    fun startQueueDownload() {
        if (!NetworkMonitor.canDownload(getApplication())) {
            _uiState.value = UiState.Error(
                if (AppPrefs.isWifiOnly(getApplication()))
                    "Wi-Fi only mode enabled. Connect to Wi-Fi to download."
                else "No internet connection"
            )
            return
        }
        DownloadService.start(getApplication())
    }

    fun cancelAllDownloads() {
        DownloadService.cancelAll(getApplication())
    }

    // ══════════════════════════════════════════════════════════
    //  DUPLICATE DETECTION
    // ══════════════════════════════════════════════════════════

    private fun checkDuplicate(url: String): Boolean {
        val videoId = extractVideoId(url) ?: return false
        return storage.findHistoryByVideoId(videoId) != null
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

    // ══════════════════════════════════════════════════════════
    //  FAVORITE QUALITY PER CHANNEL
    // ══════════════════════════════════════════════════════════

    private fun loadFavoriteQuality(channel: String?) {
        if (channel.isNullOrBlank()) return
        val fav = storage.getFavoriteQuality(channel) ?: return
        try {
            _quality.value = VideoQuality.valueOf(fav.qualityName)
        } catch (_: Exception) {}
    }

    private fun saveFavoriteQuality(channel: String?) {
        if (channel.isNullOrBlank()) return
        storage.saveFavoriteQuality(channel, _quality.value.name)
    }

    // ══════════════════════════════════════════════════════════
    //  HISTORY
    // ══════════════════════════════════════════════════════════

    fun deleteHistoryItem(item: DownloadHistoryItem) {
        viewModelScope.launch(Dispatchers.IO) {
            storage.deleteHistory(item.id)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            storage.clearAllHistory()
        }
    }

    // ══════════════════════════════════════════════════════════
    //  STORAGE INFO
    // ══════════════════════════════════════════════════════════

    fun updateStorageInfo() {
        viewModelScope.launch(Dispatchers.IO) {
            val ctx = getApplication<Application>()
            val cacheSize = CacheManager.getCleanableSize(ctx)
            val totalEver = AppPrefs.getTotalEverDownloaded(ctx)

            val startOfDay = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val todaySize = storage.getTodayDownloadedSize(startOfDay)
            val totalCount = storage.getTotalCount()

            val stat = StatFs(Environment.getExternalStorageDirectory().path)
            val freeSpace = stat.availableBlocksLong * stat.blockSizeLong

            _storageInfo.value = StorageInfo(
                cacheSize = cacheSize,
                cacheSizeText = CacheManager.formatBytes(cacheSize),
                totalEverDownloaded = totalEver,
                totalEverText = CacheManager.formatBytes(totalEver),
                todayDownloaded = todaySize,
                todayText = CacheManager.formatBytes(todaySize),
                totalCount = totalCount,
                freeSpace = freeSpace,
                freeSpaceText = CacheManager.formatBytes(freeSpace)
            )
        }
    }

    fun clearAllCache() {
        viewModelScope.launch(Dispatchers.IO) {
            _isClearing.value = true
            try {
                val freed = CacheManager.deepCleanup(getApplication())
                Log.d(TAG, "Deep cleanup freed: ${CacheManager.formatBytes(freed)}")
                updateStorageInfo()
            } catch (e: Exception) {
                Log.e(TAG, "Deep cleanup error", e)
            } finally {
                _isClearing.value = false
            }
        }
    }

    // ══════════════════════════════════════════════════════════
    //  FETCH VIDEO INFO
    // ══════════════════════════════════════════════════════════

    fun fetchInfo() {
        val videoUrl = _url.value.trim()
        if (videoUrl.isBlank()) {
            _uiState.value = UiState.Error("Enter a URL first")
            return
        }
        if (!isEngineReady) {
            _uiState.value = UiState.Error("Engine not ready. Wait for init.")
            return
        }

        job?.cancel()
        job = viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = UiState.FetchingInfo
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

                val isPlaylist = json.optString("_type") == "playlist" ||
                        json.has("entries")

                var playlistInfo: PlaylistInfo? = null
                var videoJson = json

                if (isPlaylist) {
                    val entries = json.optJSONArray("entries")
                    val count = json.optInt("playlist_count", entries?.length() ?: 0)
                    val plTitle = json.optString("title", "Playlist")
                    playlistInfo = PlaylistInfo(plTitle, count)
                    cachedPlaylistInfo = playlistInfo
                    _downloadAsPlaylist.value = true

                    val parsedEntries = mutableListOf<PlaylistEntry>()
                    if (entries != null) {
                        for (i in 0 until entries.length()) {
                            val entry = entries.optJSONObject(i) ?: continue
                            val id = entry.optString("id", "")
                            val title = entry.optString("title", "Video ${i + 1}")
                            if (id.isNotBlank()) {
                                parsedEntries.add(PlaylistEntry(id, title))
                            }
                        }
                    }
                    playlistEntries = parsedEntries

                    // Fetch first video details
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
                            val vResult = YoutubeDL.getInstance().execute(vReq)
                            videoJson = parseJson(vResult.out)
                        } catch (e: Exception) {
                            Log.w(TAG, "Couldn't get first video info: ${e.message}")
                        }
                    }
                } else {
                    cachedPlaylistInfo = null
                    playlistEntries = emptyList()
                    _downloadAsPlaylist.value = false
                }

                val videoId = extractVideoId(videoUrl)
                val author = videoJson.optString("uploader", null)
                    ?: videoJson.optString("channel", null)

                val details = VideoDetails(
                    title = videoJson.optString("title", "Unknown"),
                    thumbnail = videoJson.optString("thumbnail", null),
                    duration = videoJson.optDouble("duration", 0.0).toLong(),
                    author = author,
                    videoId = videoId
                )

                // Check duplicate
                val isDuplicate = checkDuplicate(videoUrl)

                // Load favorite quality for this channel
                loadFavoriteQuality(author)

                val formatSizes = calculateFormatSizes(videoJson)
                cachedFormatSizes = formatSizes

                _uiState.value = UiState.InfoReady(
                    details, formatSizes, playlistInfo, isDuplicate
                )

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "fetchInfo failed", e)
                _uiState.value = UiState.Error(
                    "Fetch failed:\n${e.javaClass.simpleName}: ${e.message}"
                )
            }
        }
    }

    // ══════════════════════════════════════════════════════════
    //  START DOWNLOAD
    // ══════════════════════════════════════════════════════════

    fun startDownload() {
        val videoUrl = _url.value.trim()
        val q = _quality.value
        val asPlaylist = _downloadAsPlaylist.value && playlistEntries.isNotEmpty()

        if (!isEngineReady) {
            _uiState.value = UiState.Error("Engine not ready")
            return
        }

        if (!NetworkMonitor.canDownload(getApplication())) {
            _uiState.value = UiState.Error(
                if (AppPrefs.isWifiOnly(getApplication()))
                    "Wi-Fi only mode is ON. Connect to Wi-Fi."
                else "No internet connection"
            )
            return
        }

        val details: VideoDetails = when (val s = _uiState.value) {
            is UiState.InfoReady -> s.details
            is UiState.Completed -> s.details
            else -> {
                _uiState.value = UiState.Error("Fetch info first")
                return
            }
        }

        // Save favorite quality for this channel
        viewModelScope.launch(Dispatchers.IO) {
            saveFavoriteQuality(details.author)
        }

        job?.cancel()
        job = viewModelScope.launch(Dispatchers.IO) {
            if (asPlaylist) {
                downloadPlaylistOneByOne(details, q)
            } else {
                downloadSingleAndSave(videoUrl, details, q)
            }

            // Auto-cleanup after download
            try {
                CacheManager.autoCleanup(getApplication())
                updateStorageInfo()
            } catch (_: Exception) {}
        }
    }

    fun cancelDownload() {
        processId?.let {
            try {
                YoutubeDL.getInstance().destroyProcessById(it)
            } catch (_: Exception) {}
        }
        job?.cancel()
        cleanTempDir()
        _uiState.value = UiState.Idle

        viewModelScope.launch(Dispatchers.IO) {
            try {
                CacheManager.autoCleanup(getApplication())
                updateStorageInfo()
            } catch (_: Exception) {}
        }
    }

    fun reset() {
        job?.cancel()
        _url.value = ""
        playlistEntries = emptyList()
        cachedPlaylistInfo = null
        cachedFormatSizes = emptyMap()
        _downloadAsPlaylist.value = false
        cleanTempDir()
        if (isEngineReady) _uiState.value = UiState.Idle else initEngine()
    }

    // ══════════════════════════════════════════════════════════
    //  BUILD REQUEST (shared between single & playlist)
    // ══════════════════════════════════════════════════════════

    private fun YoutubeDLRequest.addCookieOptions() {
        val cookieFile = CookieHelper.getCookieFile(getApplication())
        if (cookieFile.exists() && cookieFile.length() > 100) {
            addOption("--cookies", cookieFile.absolutePath)
        }
    }

    private fun buildRequest(videoUrl: String, q: VideoQuality): YoutubeDLRequest {
        val fragments = _concurrentFragments.value
        val speedLimit = _speedLimitKb.value

        return YoutubeDLRequest(videoUrl).apply {
            addOption("--no-playlist")
            addOption("--force-ipv4")
            addOption("-f", q.formatArg)
            addOption("-o", "${tempDir.absolutePath}/%(title).150s.%(ext)s")
            addOption("--restrict-filenames")
            addOption("--no-mtime")
            addOption("--no-cache-dir")
            addCookieOptions()

            // ── Speed boost ──
            addOption("--concurrent-fragments", fragments.toString())
            addOption("--buffer-size", "64K")
            addOption("--no-part")
            addOption("--no-check-certificates")
            addOption("--extractor-retries", "3")
            addOption("--retry-sleep", "linear=1::2")
            addOption("--throttled-rate", "100K")

            // ── Speed limit ──
            if (speedLimit > 0) {
                addOption("--limit-rate", "${speedLimit}K")
            }

            // ── Subtitles ──
            if (_downloadSubtitles.value) {
                addOption("--write-sub")
                addOption("--write-auto-sub")
                addOption("--sub-lang", "en,hi,bn")
                addOption("--convert-subs", "srt")
            }

            // ── Embed thumbnail ──
            if (_embedThumbnail.value) {
                addOption("--embed-thumbnail")
            }

            // ── Format / Container ──
            if (q == VideoQuality.AUDIO_MP3) {
                addOption("-x")
                addOption("--audio-format", "mp3")
            } else if (q.isAudioOnly) {
                // Best/M4A — keep original format
            } else if (q == VideoQuality.MAX_QUALITY) {
                addOption("-S", "quality,res,br")
                addOption("--merge-output-format", "mkv")
            } else {
                addOption("--merge-output-format", "mp4")
            }
        }
    }

    // ══════════════════════════════════════════════════════════
    //  SINGLE DOWNLOAD
    // ══════════════════════════════════════════════════════════

    private suspend fun downloadSingleAndSave(
        videoUrl: String,
        details: VideoDetails,
        q: VideoQuality
    ) {
        _uiState.value = UiState.Downloading(details, 0f)
        try {
            cleanTempDir()
            val pid = System.currentTimeMillis().toString()
            processId = pid

            val request = buildRequest(videoUrl, q)

            YoutubeDL.getInstance().execute(request, pid) { progress, eta, line ->
                val speed = SpeedTracker.parseSpeedFromLine(line)
                val speedText = if (speed > 0) SpeedTracker.formatSpeed(speed) else ""

                _uiState.value = UiState.Downloading(
                    details = details,
                    progress = progress,
                    eta = eta,
                    line = line,
                    speedText = speedText
                )
            }

            // Find downloaded file
            val downloadedFile = tempDir.listFiles()
                ?.filter { it.isFile && it.length() > 0 && !it.name.endsWith(".srt") }
                ?.maxByOrNull { it.lastModified() }

            var savedFile: SavedFileInfo? = null
            if (downloadedFile != null) {
                savedFile = saveToGallery(downloadedFile)
                val fileSize = downloadedFile.length()

                // Record in history
                val videoId = extractVideoId(videoUrl) ?: videoUrl
                storage.addHistory(
                    DownloadHistoryItem(
                        id = 0,
                        videoId = videoId,
                        url = videoUrl,
                        title = details.title,
                        author = details.author,
                        thumbnail = details.thumbnail,
                        duration = details.duration,
                        quality = q.label,
                        fileSize = fileSize,
                        fileSizeText = CacheManager.formatBytes(fileSize),
                        filePath = savedFile?.displayPath ?: "",
                        mimeType = savedFile?.mimeType ?: "video/mp4",
                        contentUri = savedFile?.contentUri
                    )
                )

                AppPrefs.addToTotalEverDownloaded(getApplication(), fileSize)
                downloadedFile.delete()
            }

            // Save subtitle files
            tempDir.listFiles()
                ?.filter { it.name.endsWith(".srt") }
                ?.forEach { srt ->
                    saveToGallery(srt)
                    srt.delete()
                }

            cleanTempDir()

            _uiState.value = UiState.Completed(
                details,
                savedFile?.displayPath ?: "Gallery",
                if (savedFile != null) 1 else 0,
                if (savedFile == null) 1 else 0,
                savedFile
            )
        } catch (e: CancellationException) {
            cleanTempDir()
            throw e
        } catch (e: Exception) {
            cleanTempDir()
            _uiState.value = UiState.Error(
                "Download failed:\n${e.javaClass.simpleName}: ${e.message}"
            )
        }
    }

    // ══════════════════════════════════════════════════════════
    //  PLAYLIST DOWNLOAD (one-by-one)
    // ══════════════════════════════════════════════════════════

    private suspend fun downloadPlaylistOneByOne(
        details: VideoDetails,
        q: VideoQuality
    ) {
        var savedCount = 0
        var failedCount = 0
        var lastFile: SavedFileInfo? = null
        val total = playlistEntries.size

        for ((index, entry) in playlistEntries.withIndex()) {
            currentCoroutineContext().ensureActive()
            val videoNum = index + 1

            _uiState.value = UiState.Downloading(
                details = details,
                progress = 0f,
                currentItem = videoNum,
                totalItems = total,
                currentVideoTitle = entry.title
            )

            try {
                cleanTempDir()
                val pid = "dl_${index}_${System.currentTimeMillis()}"
                processId = pid
                val videoUrl = "https://www.youtube.com/watch?v=${entry.id}"

                val request = buildRequest(videoUrl, q)

                YoutubeDL.getInstance().execute(request, pid) { progress, eta, line ->
                    val speed = SpeedTracker.parseSpeedFromLine(line)
                    val speedText = if (speed > 0) SpeedTracker.formatSpeed(speed) else ""

                    _uiState.value = UiState.Downloading(
                        details = details,
                        progress = progress,
                        eta = eta,
                        line = line,
                        currentItem = videoNum,
                        totalItems = total,
                        currentVideoTitle = entry.title,
                        speedText = speedText
                    )
                }

                val downloadedFile = tempDir.listFiles()
                    ?.filter { it.isFile && it.length() > 0 && !it.name.endsWith(".srt") }
                    ?.maxByOrNull { it.lastModified() }

                if (downloadedFile != null) {
                    val saved = saveToGallery(downloadedFile)
                    if (saved != null) {
                        savedCount++
                        lastFile = saved

                        // Record in history
                        storage.addHistory(
                            DownloadHistoryItem(
                                id = 0,
                                videoId = entry.id,
                                url = videoUrl,
                                title = entry.title,
                                author = details.author,
                                thumbnail = details.thumbnail,
                                duration = 0,
                                quality = q.label,
                                fileSize = downloadedFile.length(),
                                fileSizeText = CacheManager.formatBytes(downloadedFile.length()),
                                filePath = saved.displayPath,
                                mimeType = saved.mimeType,
                                contentUri = saved.contentUri
                            )
                        )
                        AppPrefs.addToTotalEverDownloaded(
                            getApplication(), downloadedFile.length()
                        )
                    } else {
                        failedCount++
                    }
                    downloadedFile.delete()
                } else {
                    failedCount++
                }

                // Save subtitles
                tempDir.listFiles()
                    ?.filter { it.name.endsWith(".srt") }
                    ?.forEach { srt ->
                        saveToGallery(srt)
                        srt.delete()
                    }

            } catch (e: CancellationException) {
                cleanTempDir()
                throw e
            } catch (e: Exception) {
                failedCount++
                Log.e(TAG, "Failed video ${index + 1}: ${e.message}")
            }
            cleanTempDir()
        }

        cleanTempDir()
        val location = lastFile?.displayPath?.substringBeforeLast('/') ?: "Gallery"
        _uiState.value = UiState.Completed(
            details, location, savedCount, failedCount, lastFile
        )
    }

    // ══════════════════════════════════════════════════════════
    //  GALLERY SAVE
    // ══════════════════════════════════════════════════════════

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
        val context = getApplication<Application>()
        val resolver = context.contentResolver
        val isAudio = mime.startsWith("audio")
        val isSrt = mime == "text/plain"
        val location = _downloadLocation.value
        val subfolder = AppPrefs.getSubfolder(context)

        val (collection, relativePath) = when {
            isSrt || location == DownloadLocation.DOWNLOADS ->
                MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to
                        "${Environment.DIRECTORY_DOWNLOADS}/$subfolder"

            location == DownloadLocation.CUSTOM ->
                MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to
                        "${Environment.DIRECTORY_DOWNLOADS}/$subfolder"

            isAudio || location == DownloadLocation.MUSIC ->
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to
                        "${Environment.DIRECTORY_MUSIC}/$subfolder"

            location == DownloadLocation.DCIM ->
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to
                        "${Environment.DIRECTORY_DCIM}/$subfolder"

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
    private fun saveToGalleryLegacy(sourceFile: File, mime: String): SavedFileInfo? {
        val context = getApplication<Application>()
        val location = _downloadLocation.value
        val subfolder = AppPrefs.getSubfolder(context)
        val isAudio = mime.startsWith("audio")

        val baseDir = when {
            location == DownloadLocation.MUSIC || isAudio ->
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

        MediaScannerConnection.scanFile(
            context, arrayOf(destFile.absolutePath), arrayOf(mime), null
        )

        return SavedFileInfo(
            displayPath = destFile.absolutePath,
            mimeType = mime,
            contentUri = null,
            absolutePath = destFile.absolutePath
        )
    }

    // ══════════════════════════════════════════════════════════
    //  FORMAT SIZE CALCULATION
    // ══════════════════════════════════════════════════════════

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
                if (tbr > 0 && durationSec > 0) {
                    filesize = (tbr * 1000.0 / 8.0 * durationSec).toLong()
                }
            }

            val hasVideo = vcodec != "none" && vcodec.isNotEmpty()
            val hasAudio = acodec != "none" && acodec.isNotEmpty()

            if (hasVideo && !hasAudio && height > 0) {
                videoFormats.add(Fmt(height, filesize, vcodec))
            }
            if (hasAudio && !hasVideo && filesize > bestAudioSize) {
                bestAudioSize = filesize
            }
        }

        // Resolution-based
        val targets = mapOf(
            VideoQuality.UHD_4K to 2160,
            VideoQuality.QHD_2K to 1440,
            VideoQuality.FHD to 1080,
            VideoQuality.HD to 720,
            VideoQuality.SD to 480,
            VideoQuality.LOW to 360
        )

        for ((quality, targetH) in targets) {
            val vf = videoFormats
                .filter { it.height in 1..targetH }
                .maxByOrNull { it.height }
            if (vf != null) {
                val total = vf.size + bestAudioSize
                if (total > 0) result[quality] = formatBytes(total)
            }
        }

        // Best = actual best available
        val bestVideo = videoFormats.maxByOrNull { it.height }
        if (bestVideo != null) {
            val total = bestVideo.size + bestAudioSize
            if (total > 0) {
                result[VideoQuality.BEST] =
                    "${formatBytes(total)} · ${bestVideo.height}p"
            }
        }

        // MAX_QUALITY = largest video by size
        val maxVideo = videoFormats.maxByOrNull { it.size }
        if (maxVideo != null) {
            val total = maxVideo.size + bestAudioSize
            if (total > 0) {
                result[VideoQuality.MAX_QUALITY] =
                    "${formatBytes(total)} · ${maxVideo.height}p"
            }
        }

        // Codec-specific: AV1
        val av1 = videoFormats
            .filter { it.codec.startsWith("av01") }
            .maxByOrNull { it.height }
        if (av1 != null) {
            val total = av1.size + bestAudioSize
            if (total > 0) {
                result[VideoQuality.SMALLEST] =
                    "${formatBytes(total)} · ${av1.height}p"
            }
        }

        // Codec-specific: H.264
        val h264 = videoFormats
            .filter { it.codec.startsWith("avc1") }
            .maxByOrNull { it.height }
        if (h264 != null) {
            val total = h264.size + bestAudioSize
            if (total > 0) {
                result[VideoQuality.COMPATIBLE] =
                    "${formatBytes(total)} · ${h264.height}p"
            }
        }

        // Audio sizes
        if (bestAudioSize > 0) {
            result[VideoQuality.AUDIO_BEST] = formatBytes(bestAudioSize)
            result[VideoQuality.AUDIO_M4A] = formatBytes(bestAudioSize)
            result[VideoQuality.AUDIO_MP3] = formatBytes(bestAudioSize)
        }

        return result
    }

    // ══════════════════════════════════════════════════════════
    //  HELPERS
    // ══════════════════════════════════════════════════════════

    private fun parseJson(raw: String): JSONObject {
        val trimmed = raw.trim()
        return try {
            JSONObject(trimmed)
        } catch (_: Exception) {
            val start = trimmed.indexOf('{')
            val end = trimmed.lastIndexOf('}')
            if (start >= 0 && end > start) {
                JSONObject(trimmed.substring(start, end + 1))
            } else {
                throw Exception("Could not parse JSON")
            }
        }
    }

    private fun cleanTempDir() {
        try {
            tempDir.listFiles()?.forEach { it.deleteRecursively() }
        } catch (_: Exception) {}
    }

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

    private fun formatBytes(bytes: Long): String = when {
        bytes <= 0 -> ""
        bytes < 1024L * 1024 -> "~%.0f KB".format(bytes / 1024.0)
        bytes < 1024L * 1024 * 1024 -> "~%.0f MB".format(bytes / (1024.0 * 1024.0))
        else -> "~%.1f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
    }

    private fun formatDuration(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s)
        else "%d:%02d".format(m, s)
    }
}