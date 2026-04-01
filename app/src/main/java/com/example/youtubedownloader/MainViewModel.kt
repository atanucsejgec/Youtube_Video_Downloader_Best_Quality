package com.example.youtubedownloader

import android.app.Application
import android.content.ContentValues
import android.media.MediaScannerConnection
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/* ═══════════════════════════════════════════════════════
 *  DATA MODELS
 * ═══════════════════════════════════════════════════════ */

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
    val absolutePath: String?
)

enum class VideoQuality(
    val label: String,
    val formatArg: String,
    val isAudioOnly: Boolean = false
) {
    // ── Video Qualities ──
    BEST_8K(
        "8K · Best Possible",
        "bestvideo[height>=4320]+bestaudio/bestvideo+bestaudio/best"
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

    // ── Codec-Specific (better quality or compatibility) ──
    BEST_AV1(
        "Best AV1 · Highest Quality",
        "bestvideo[vcodec^=av01]+bestaudio/bestvideo+bestaudio/best"
    ),
    BEST_H264(
        "Best H.264 · Most Compatible",
        "bestvideo[vcodec^=avc1]+bestaudio[acodec^=mp4a]/best"
    ),
    BEST_VP9(
        "Best VP9",
        "bestvideo[vcodec^=vp9]+bestaudio/bestvideo+bestaudio/best"
    ),
    BEST_HDR(
        "Best HDR",
        "bestvideo[dynamic_range=HDR]+bestaudio/bestvideo+bestaudio/best"
    ),

    // ── Audio Only ──
    AUDIO_BEST(
        "Audio · Best Quality",
        "bestaudio/best",
        true
    ),
    AUDIO_M4A(
        "Audio · M4A (AAC)",
        "bestaudio[ext=m4a]/bestaudio",
        true
    ),
    AUDIO_MP3(
        "Audio · MP3",
        "bestaudio",
        true
    ),
    AUDIO_OPUS(
        "Audio · Opus (Smallest)",
        "bestaudio[acodec=opus]/bestaudio",
        true
    );

    val isVideo: Boolean get() = !isAudioOnly
}

sealed interface UiState {
    data object Idle : UiState
    data object Initializing : UiState
    data object FetchingInfo : UiState

    data class InfoReady(
        val details: VideoDetails,
        val formatSizes: Map<VideoQuality, String>,
        val playlistInfo: PlaylistInfo?
    ) : UiState

    data class Downloading(
        val details: VideoDetails,
        val progress: Float,
        val eta: Long = 0L,
        val line: String = "",
        val currentItem: Int = 1,
        val totalItems: Int = 1,
        val currentVideoTitle: String = ""
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

/* ═══════════════════════════════════════════════════════
 *  VIEW MODEL
 * ═══════════════════════════════════════════════════════ */

class MainViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        private const val TAG = "MainVM"
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _url = MutableStateFlow("")
    val url: StateFlow<String> = _url.asStateFlow()

    private val _quality = MutableStateFlow(VideoQuality.HD)
    val quality: StateFlow<VideoQuality> = _quality.asStateFlow()

    private val _downloadAsPlaylist = MutableStateFlow(false)
    val downloadAsPlaylist: StateFlow<Boolean> = _downloadAsPlaylist.asStateFlow()

    private val _downloadLocation = MutableStateFlow(
        DownloadPrefs.getLocation(app)
    )
    val downloadLocation: StateFlow<DownloadLocation> = _downloadLocation.asStateFlow()

    private val _showSettings = MutableStateFlow(false)
    val showSettings: StateFlow<Boolean> = _showSettings.asStateFlow()

    @Volatile
    private var isEngineReady = false
    private var job: Job? = null
    private var processId: String? = null

    private var playlistEntries: List<PlaylistEntry> = emptyList()
    private var cachedFormatSizes: Map<VideoQuality, String> = emptyMap()
    private var cachedPlaylistInfo: PlaylistInfo? = null

    private val tempDir: File
        get() = File(
            getApplication<Application>().getExternalFilesDir(null),
            "YTDownloader_temp"
        ).also { if (!it.exists()) it.mkdirs() }

    private fun Throwable.fullTrace(): String {
        val sw = StringWriter(); printStackTrace(PrintWriter(sw)); return sw.toString()
    }

    /* ═══════════════ INIT ═══════════════ */

    init {
        initEngine()
    }

    fun initEngine() {
        viewModelScope.launch(Dispatchers.IO) {
            if (isEngineReady) { _uiState.value = UiState.Idle; return@launch }
            _uiState.value = UiState.Initializing

            try {
                YoutubeDL.getInstance().init(getApplication())
                FFmpeg.getInstance().init(getApplication())
                isEngineReady = true
                Log.d(TAG, "✅ Engine ready")

                try {
                    YoutubeDL.getInstance()
                        .updateYoutubeDL(getApplication(), YoutubeDL.UpdateChannel.STABLE)
                    Log.d(TAG, "✅ yt-dlp updated")
                } catch (e: Exception) {
                    Log.w(TAG, "Update skipped: ${e.message}")
                }

                cleanTempDir()
                _uiState.value = UiState.Idle
            } catch (e: Exception) {
                Log.e(TAG, "Init failed", e)
                _uiState.value = UiState.Error("Init failed:\n${e.fullTrace()}")
            }
        }
    }

    /* ═══════════════ PUBLIC ACTIONS ═══════════════ */

    fun updateUrl(v: String) { _url.value = v }
    fun selectQuality(q: VideoQuality) { _quality.value = q }
    fun setDownloadAsPlaylist(v: Boolean) { _downloadAsPlaylist.value = v }
    fun toggleSettings() { _showSettings.value = !_showSettings.value }

    fun setDownloadLocation(location: DownloadLocation) {
        _downloadLocation.value = location
        DownloadPrefs.setLocation(getApplication(), location)
    }

    fun handleSharedUrl(url: String) {
        _url.value = url
        // Auto-fetch if engine is ready
        if (isEngineReady && _uiState.value is UiState.Idle) {
            fetchInfo()
        }
    }

    fun fetchInfo() {
        val videoUrl = _url.value.trim()
        if (videoUrl.isBlank()) {
            _uiState.value = UiState.Error("Enter a URL first"); return
        }
        if (!isEngineReady) {
            _uiState.value = UiState.Error("Engine not ready. Wait for init."); return
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
                            if (id.isNotBlank()) parsedEntries.add(PlaylistEntry(id, title))
                        }
                    }
                    playlistEntries = parsedEntries

                    if (parsedEntries.isNotEmpty()) {
                        try {
                            val vReq = YoutubeDLRequest(
                                "https://www.youtube.com/watch?v=${parsedEntries[0].id}"
                            ).apply {
                                addOption("--dump-single-json")
                                addOption("--no-playlist")
                                addOption("--no-warnings")
                                addOption("--force-ipv4")
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

                val details = VideoDetails(
                    title = videoJson.optString("title", "Unknown"),
                    thumbnail = videoJson.optString("thumbnail", null),
                    duration = videoJson.optDouble("duration", 0.0).toLong(),
                    author = videoJson.optString("uploader", null)
                )

                val formatSizes = calculateFormatSizes(videoJson)
                cachedFormatSizes = formatSizes

                _uiState.value = UiState.InfoReady(details, formatSizes, playlistInfo)

            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                Log.e(TAG, "fetchInfo failed", e)
                _uiState.value = UiState.Error(
                    "Fetch failed:\n${e.javaClass.simpleName}: ${e.message}"
                )
            }
        }
    }

    fun startDownload() {
        val videoUrl = _url.value.trim()
        val q = _quality.value
        val asPlaylist = _downloadAsPlaylist.value && playlistEntries.isNotEmpty()

        if (!isEngineReady) {
            _uiState.value = UiState.Error("Engine not ready"); return
        }

        val details: VideoDetails = when (val s = _uiState.value) {
            is UiState.InfoReady -> s.details
            is UiState.Completed -> s.details
            else -> {
                _uiState.value = UiState.Error("Fetch info first"); return
            }
        }

        job?.cancel()
        job = viewModelScope.launch(Dispatchers.IO) {
            if (asPlaylist) {
                downloadPlaylistOneByOne(details, q)
            } else {
                downloadSingleAndSave(videoUrl, details, q)
            }
        }
    }

    fun cancelDownload() {
        processId?.let {
            try { YoutubeDL.getInstance().destroyProcessById(it) } catch (_: Exception) {}
        }
        job?.cancel()
        cleanTempDir()
        _uiState.value = UiState.Idle
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

    /* ═══════════════ ONE-BY-ONE PLAYLIST ═══════════════ */

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

                val request = YoutubeDLRequest(videoUrl).apply {
                    addOption("--no-playlist")
                    addOption("--force-ipv4")
                    addOption("-f", q.formatArg)
                    addOption("-o", "${tempDir.absolutePath}/%(title).150s.%(ext)s")
                    addOption("--restrict-filenames")
                    addOption("--no-mtime")
                    addOption("--no-cache-dir")

                    if (q.isAudioOnly && q == VideoQuality.AUDIO_MP3) {
                        addOption("-x")
                        addOption("--audio-format", "mp3")
                    } else if (q.isAudioOnly && q == VideoQuality.AUDIO_OPUS) {
                        addOption("-x")
                        addOption("--audio-format", "opus")
                    } else if (q.isAudioOnly) {
                        // M4A or best audio — no conversion
                    } else {
                        addOption("--merge-output-format", "mp4")
                    }
                }

                YoutubeDL.getInstance().execute(request, pid) { progress, eta, line ->
                    _uiState.value = UiState.Downloading(
                        details = details, progress = progress, eta = eta,
                        line = line, currentItem = videoNum, totalItems = total,
                        currentVideoTitle = entry.title
                    )
                }

                val downloadedFile = tempDir.listFiles()
                    ?.filter { it.isFile && it.length() > 0 }
                    ?.maxByOrNull { it.lastModified() }

                if (downloadedFile != null) {
                    val saved = saveToGallery(downloadedFile)
                    if (saved != null) {
                        savedCount++; lastFile = saved
                    } else failedCount++
                    downloadedFile.delete()
                } else failedCount++

            } catch (e: CancellationException) {
                cleanTempDir(); throw e
            } catch (e: Exception) {
                failedCount++
                Log.e(TAG, "Failed video $videoNum: ${e.message}")
            }
            cleanTempDir()
        }

        cleanTempDir()
        val location = lastFile?.displayPath?.substringBeforeLast('/') ?: "Gallery"
        _uiState.value = UiState.Completed(details, location, savedCount, failedCount, lastFile)
    }

    /* ═══════════════ SINGLE DOWNLOAD ═══════════════ */

    private suspend fun downloadSingleAndSave(
        videoUrl: String, details: VideoDetails, q: VideoQuality
    ) {
        _uiState.value = UiState.Downloading(details, 0f)
        try {
            cleanTempDir()
            val pid = System.currentTimeMillis().toString()
            processId = pid

            val request = YoutubeDLRequest(videoUrl).apply {
                addOption("--no-playlist")
                addOption("--force-ipv4")
                addOption("-f", q.formatArg)
                addOption("-o", "${tempDir.absolutePath}/%(title).150s.%(ext)s")
                addOption("--restrict-filenames")
                addOption("--no-mtime")
                addOption("--no-cache-dir")

                if (q.isAudioOnly && q == VideoQuality.AUDIO_MP3) {
                    addOption("-x")
                    addOption("--audio-format", "mp3")
                } else if (q.isAudioOnly && q == VideoQuality.AUDIO_OPUS) {
                    addOption("-x")
                    addOption("--audio-format", "opus")
                } else if (q.isAudioOnly) {
                    // M4A or best audio — no conversion needed
                } else {
                    addOption("--merge-output-format", "mp4")
                }
            }

            YoutubeDL.getInstance().execute(request, pid) { progress, eta, line ->
                _uiState.value = UiState.Downloading(
                    details = details, progress = progress, eta = eta, line = line
                )
            }

            val downloadedFile = tempDir.listFiles()
                ?.filter { it.isFile && it.length() > 0 }
                ?.maxByOrNull { it.lastModified() }

            var savedFile: SavedFileInfo? = null
            if (downloadedFile != null) {
                savedFile = saveToGallery(downloadedFile)
                downloadedFile.delete()
            }
            cleanTempDir()

            _uiState.value = UiState.Completed(
                details, savedFile?.displayPath ?: "Gallery",
                if (savedFile != null) 1 else 0,
                if (savedFile == null) 1 else 0,
                savedFile
            )
        } catch (e: CancellationException) { cleanTempDir(); throw e }
        catch (e: Exception) {
            cleanTempDir()
            _uiState.value = UiState.Error(
                "Download failed:\n${e.javaClass.simpleName}: ${e.message}"
            )
        }
    }

    /* ═══════════════ GALLERY SAVE ═══════════════ */

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
        val isVideo = mime.startsWith("video")
        val isAudio = mime.startsWith("audio")

        // ── Use user's chosen download location ──
        val location = _downloadLocation.value
        val subfolder = DownloadPrefs.getSubfolderName(context)

        val (collection, relativePath) = when {
            location == DownloadLocation.CUSTOM -> {
                // For custom, still use MediaStore but with Downloads
                MediaStore.Downloads.getContentUri(
                    MediaStore.VOLUME_EXTERNAL_PRIMARY
                ) to "${Environment.DIRECTORY_DOWNLOADS}/$subfolder"
            }
            isAudio || location == DownloadLocation.MUSIC -> {
                MediaStore.Audio.Media.getContentUri(
                    MediaStore.VOLUME_EXTERNAL_PRIMARY
                ) to "${Environment.DIRECTORY_MUSIC}/$subfolder"
            }
            location == DownloadLocation.DCIM -> {
                MediaStore.Video.Media.getContentUri(
                    MediaStore.VOLUME_EXTERNAL_PRIMARY
                ) to "${Environment.DIRECTORY_DCIM}/$subfolder"
            }
            location == DownloadLocation.DOWNLOADS -> {
                MediaStore.Downloads.getContentUri(
                    MediaStore.VOLUME_EXTERNAL_PRIMARY
                ) to "${Environment.DIRECTORY_DOWNLOADS}/$subfolder"
            }
            else -> {
                // MOVIES (default)
                if (isVideo) {
                    MediaStore.Video.Media.getContentUri(
                        MediaStore.VOLUME_EXTERNAL_PRIMARY
                    ) to "${Environment.DIRECTORY_MOVIES}/$subfolder"
                } else {
                    MediaStore.Downloads.getContentUri(
                        MediaStore.VOLUME_EXTERNAL_PRIMARY
                    ) to "${Environment.DIRECTORY_DOWNLOADS}/$subfolder"
                }
            }
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
        val subfolder = DownloadPrefs.getSubfolderName(context)
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

    /* ═══════════════ FORMAT SIZES ═══════════════ */

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

            if (hasVideo && !hasAudio && height > 0)
                videoFormats.add(Fmt(height, filesize, vcodec))
            if (hasAudio && !hasVideo && filesize > bestAudioSize)
                bestAudioSize = filesize
        }

        // ── Resolution-based qualities ──
        val targets = mapOf(
            VideoQuality.BEST_8K to 9999,
            VideoQuality.UHD_4K to 2160,
            VideoQuality.QHD_2K to 1440,
            VideoQuality.FHD to 1080,
            VideoQuality.HD to 720,
            VideoQuality.SD to 480,
            VideoQuality.LOW to 360
        )

        for ((quality, targetH) in targets) {
            val vf = if (quality == VideoQuality.BEST_8K) {
                videoFormats.maxByOrNull { it.height }
            } else {
                videoFormats.filter { it.height in 1..targetH }
                    .maxByOrNull { it.height }
            }
            if (vf != null) {
                val total = vf.size + bestAudioSize
                if (total > 0) result[quality] = formatBytes(total)
            }
        }

        // ── Codec-specific qualities ──
        val av1 = videoFormats.filter { it.codec.startsWith("av01") }
            .maxByOrNull { it.height }
        if (av1 != null) {
            val total = av1.size + bestAudioSize
            if (total > 0) result[VideoQuality.BEST_AV1] =
                "${formatBytes(total)} · ${av1.height}p"
        }

        val h264 = videoFormats.filter { it.codec.startsWith("avc1") }
            .maxByOrNull { it.height }
        if (h264 != null) {
            val total = h264.size + bestAudioSize
            if (total > 0) result[VideoQuality.BEST_H264] =
                "${formatBytes(total)} · ${h264.height}p"
        }

        val vp9 = videoFormats.filter { it.codec.startsWith("vp9") || it.codec.startsWith("vp09") }
            .maxByOrNull { it.height }
        if (vp9 != null) {
            val total = vp9.size + bestAudioSize
            if (total > 0) result[VideoQuality.BEST_VP9] =
                "${formatBytes(total)} · ${vp9.height}p"
        }

        // HDR — estimate same as best
        val bestVideo = videoFormats.maxByOrNull { it.height }
        if (bestVideo != null) {
            val total = bestVideo.size + bestAudioSize
            if (total > 0) result[VideoQuality.BEST_HDR] = formatBytes(total)
        }

        // ── Audio qualities ──
        if (bestAudioSize > 0) {
            result[VideoQuality.AUDIO_BEST] = formatBytes(bestAudioSize)
            result[VideoQuality.AUDIO_M4A] = formatBytes(bestAudioSize)
            result[VideoQuality.AUDIO_MP3] = formatBytes(bestAudioSize)
            result[VideoQuality.AUDIO_OPUS] = formatBytes((bestAudioSize * 0.7).toLong())
        }

        return result
    }
    private fun formatBytes(bytes: Long): String = when {
        bytes <= 0 -> ""
        bytes < 1024L * 1024 -> "~%.0f KB".format(bytes / 1024.0)
        bytes < 1024L * 1024 * 1024 -> "~%.0f MB".format(bytes / (1024.0 * 1024.0))
        else -> "~%.1f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
    }

    /* ═══════════════ HELPERS ═══════════════ */

    private fun parseJson(raw: String): JSONObject {
        val trimmed = raw.trim()
        return try { JSONObject(trimmed) } catch (_: Exception) {
            val start = trimmed.indexOf('{')
            val end = trimmed.lastIndexOf('}')
            if (start >= 0 && end > start) JSONObject(trimmed.substring(start, end + 1))
            else throw Exception("Could not parse JSON")
        }
    }

    private fun cleanTempDir() {
        try { tempDir.listFiles()?.forEach { it.delete() } } catch (_: Exception) {}
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
}