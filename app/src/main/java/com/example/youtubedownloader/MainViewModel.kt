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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/* ═══════════════ Data Models ═══════════════ */

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

enum class VideoQuality(
    val label: String,
    val formatArg: String,
    val isAudioOnly: Boolean = false
) {
    BEST("Best Quality", "bestvideo+bestaudio/best"),
    FHD("1080p", "bestvideo[height<=1080]+bestaudio/best[height<=1080]"),
    HD("720p", "bestvideo[height<=720]+bestaudio/best[height<=720]"),
    SD("480p", "bestvideo[height<=480]+bestaudio/best[height<=480]"),
    LOW("360p", "bestvideo[height<=360]+bestaudio/best[height<=360]"),
    AUDIO_M4A("Audio · M4A", "bestaudio[ext=m4a]/bestaudio", true),
    AUDIO_MP3("Audio · MP3", "bestaudio", true);
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
        val totalItems: Int = 1
    ) : UiState

    data class Completed(
        val details: VideoDetails,
        val savedLocation: String,
        val fileCount: Int = 1
    ) : UiState

    data class Error(val message: String) : UiState
}

/* ═══════════════ ViewModel ═══════════════ */

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

    @Volatile
    private var isEngineReady = false
    private var job: Job? = null
    private var processId: String? = null
    private var detectedPlaylist: PlaylistInfo? = null

    private val tempDir: File
        get() = File(
            getApplication<Application>().getExternalFilesDir(null),
            "YTDownloader_temp"
        ).also { if (!it.exists()) it.mkdirs() }

    private fun Throwable.fullTrace(): String {
        val sw = StringWriter(); printStackTrace(PrintWriter(sw)); return sw.toString()
    }

    /* ── Init ── */

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

                // Clean up any leftover temp files
                cleanTempDir()

                _uiState.value = UiState.Idle
            } catch (e: Exception) {
                Log.e(TAG, "Init failed", e)
                _uiState.value = UiState.Error("Init failed:\n${e.fullTrace()}")
            }
        }
    }

    /* ── Public Actions ── */

    fun updateUrl(v: String) { _url.value = v }
    fun selectQuality(q: VideoQuality) { _quality.value = q }
    fun setDownloadAsPlaylist(v: Boolean) { _downloadAsPlaylist.value = v }

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
                // ── Step 1: Get JSON dump ──
                val request = YoutubeDLRequest(videoUrl).apply {
                    addOption("--dump-single-json")
                    addOption("--flat-playlist")
                    addOption("--no-warnings")
                    addOption("--force-ipv4")
                }
                val result = YoutubeDL.getInstance().execute(request)
                val json = parseJson(result.out)

                // ── Step 2: Check if playlist ──
                val isPlaylist = json.optString("_type") == "playlist" ||
                        json.has("entries")

                var playlistInfo: PlaylistInfo? = null
                var videoJson = json

                if (isPlaylist) {
                    val entries = json.optJSONArray("entries")
                    val count = json.optInt("playlist_count",
                        entries?.length() ?: 0)
                    val plTitle = json.optString("title", "Playlist")
                    playlistInfo = PlaylistInfo(plTitle, count)
                    detectedPlaylist = playlistInfo
                    _downloadAsPlaylist.value = true

                    Log.d(TAG, "Playlist detected: $plTitle ($count videos)")

                    // Get format info from first video
                    val firstId = entries?.optJSONObject(0)?.optString("id")
                    if (firstId != null) {
                        val vReq = YoutubeDLRequest(
                            "https://www.youtube.com/watch?v=$firstId"
                        ).apply {
                            addOption("--dump-single-json")
                            addOption("--no-playlist")
                            addOption("--no-warnings")
                            addOption("--force-ipv4")
                        }
                        val vResult = YoutubeDL.getInstance().execute(vReq)
                        videoJson = parseJson(vResult.out)
                    }
                } else {
                    detectedPlaylist = null
                    _downloadAsPlaylist.value = false
                }

                // ── Step 3: Parse video details ──
                val details = VideoDetails(
                    title = videoJson.optString("title", "Unknown"),
                    thumbnail = videoJson.optString("thumbnail", null),
                    duration = videoJson.optDouble("duration", 0.0).toLong(),
                    author = videoJson.optString("uploader", null)
                )

                // ── Step 4: Parse format sizes ──
                val formatSizes = calculateFormatSizes(videoJson)

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
        val asPlaylist = _downloadAsPlaylist.value

        if (!isEngineReady) {
            _uiState.value = UiState.Error("Engine not ready"); return
        }

        val details: VideoDetails
        val playlistInfo: PlaylistInfo?

        when (val s = _uiState.value) {
            is UiState.InfoReady -> {
                details = s.details; playlistInfo = s.playlistInfo
            }
            is UiState.Completed -> {
                details = s.details; playlistInfo = null
            }
            else -> {
                _uiState.value = UiState.Error("Fetch info first"); return
            }
        }

        val totalItems = if (asPlaylist) playlistInfo?.count ?: 1 else 1

        job?.cancel()
        job = viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = UiState.Downloading(
                details, 0f, totalItems = totalItems
            )

            try {
                // Clean temp before download
                cleanTempDir()

                val pid = System.currentTimeMillis().toString()
                processId = pid

                val request = YoutubeDLRequest(videoUrl).apply {
                    if (!asPlaylist) addOption("--no-playlist")
                    addOption("--force-ipv4")
                    addOption("-f", q.formatArg)
                    addOption("-o", "${tempDir.absolutePath}/%(title).150s.%(ext)s")
                    addOption("--restrict-filenames")
                    addOption("--no-mtime")

                    if (q.isAudioOnly && q == VideoQuality.AUDIO_MP3) {
                        addOption("-x")
                        addOption("--audio-format", "mp3")
                    } else if (!q.isAudioOnly) {
                        addOption("--merge-output-format", "mp4")
                    }
                }

                var currentItem = 1

                YoutubeDL.getInstance().execute(request, pid) { progress, eta, line ->
                    // Parse "Downloading item X of Y" for playlists
                    val itemMatch = Regex(
                        """Downloading (?:item|video) (\d+) of (\d+)"""
                    ).find(line)
                    if (itemMatch != null) {
                        currentItem = itemMatch.groupValues[1].toIntOrNull() ?: currentItem
                    }

                    _uiState.value = UiState.Downloading(
                        details = details,
                        progress = progress,
                        eta = eta,
                        line = line,
                        currentItem = currentItem,
                        totalItems = totalItems
                    )
                }

                // ── Save all downloaded files to Gallery ──
                val files = tempDir.listFiles()?.filter { it.isFile } ?: emptyList()
                var savedCount = 0
                var savedLocation = ""

                for (file in files) {
                    val loc = saveToGallery(file)
                    if (loc != null) {
                        savedCount++
                        savedLocation = loc
                        file.delete()
                        Log.d(TAG, "✅ Saved to gallery: $loc")
                    }
                }

                // Clean up temp dir
                cleanTempDir()

                if (savedCount == 0) {
                    savedLocation = tempDir.absolutePath
                }

                _uiState.value = UiState.Completed(
                    details, savedLocation, savedCount
                )

            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                Log.e(TAG, "Download failed", e)
                _uiState.value = UiState.Error(
                    "Download failed:\n${e.javaClass.simpleName}: ${e.message}"
                )
            }
        }
    }

    fun cancelDownload() {
        processId?.let {
            try { YoutubeDL.getInstance().destroyProcessById(it) } catch (_: Exception) {}
        }
        job?.cancel()
        _uiState.value = UiState.Idle
    }

    fun reset() {
        job?.cancel()
        _url.value = ""
        detectedPlaylist = null
        _downloadAsPlaylist.value = false
        if (isEngineReady) _uiState.value = UiState.Idle else initEngine()
    }

    /* ═══════════════ Format Size Calculation ═══════════════ */

    private fun calculateFormatSizes(json: JSONObject): Map<VideoQuality, String> {
        val result = mutableMapOf<VideoQuality, String>()
        val formatsArray = json.optJSONArray("formats") ?: return result
        val durationSec = json.optDouble("duration", 0.0).toLong()

        data class Fmt(val height: Int, val size: Long)

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
                videoFormats.add(Fmt(height, filesize))
            if (hasAudio && !hasVideo && filesize > bestAudioSize)
                bestAudioSize = filesize
        }

        val targets = mapOf(
            VideoQuality.FHD to 1080,
            VideoQuality.HD to 720,
            VideoQuality.SD to 480,
            VideoQuality.LOW to 360
        )

        for ((quality, targetH) in targets) {
            val vf = videoFormats.filter { it.height in 1..targetH }
                .maxByOrNull { it.height }
            if (vf != null) {
                val total = vf.size + bestAudioSize
                if (total > 0) result[quality] = formatBytes(total)
            }
        }

        val bestVideo = videoFormats.maxByOrNull { it.height }
        if (bestVideo != null) {
            val total = bestVideo.size + bestAudioSize
            if (total > 0) result[VideoQuality.BEST] = formatBytes(total)
        }

        if (bestAudioSize > 0) {
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

    /* ═══════════════ Gallery Save ═══════════════ */

    private fun saveToGallery(sourceFile: File): String? {
        val context = getApplication<Application>()
        val mime = mimeOf(sourceFile.name)
        val isVideo = mime.startsWith("video")
        val isAudio = mime.startsWith("audio")

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveToGalleryQ(sourceFile, mime, isVideo, isAudio)
        } else {
            saveToGalleryLegacy(sourceFile, mime, isVideo, isAudio)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveToGalleryQ(
        sourceFile: File, mime: String, isVideo: Boolean, isAudio: Boolean
    ): String? {
        val context = getApplication<Application>()
        val resolver = context.contentResolver

        val (collection, relativePath) = when {
            isVideo -> MediaStore.Video.Media.getContentUri(
                MediaStore.VOLUME_EXTERNAL_PRIMARY
            ) to "${Environment.DIRECTORY_MOVIES}/YTDownloader"

            isAudio -> MediaStore.Audio.Media.getContentUri(
                MediaStore.VOLUME_EXTERNAL_PRIMARY
            ) to "${Environment.DIRECTORY_MUSIC}/YTDownloader"

            else -> MediaStore.Downloads.getContentUri(
                MediaStore.VOLUME_EXTERNAL_PRIMARY
            ) to "${Environment.DIRECTORY_DOWNLOADS}/YTDownloader"
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

        return "$relativePath/${sourceFile.name}"
    }

    @Suppress("DEPRECATION")
    private fun saveToGalleryLegacy(
        sourceFile: File, mime: String, isVideo: Boolean, isAudio: Boolean
    ): String? {
        val context = getApplication<Application>()
        val baseDir = when {
            isVideo -> Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_MOVIES
            )
            isAudio -> Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_MUSIC
            )
            else -> Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS
            )
        }

        val destDir = File(baseDir, "YTDownloader").also { it.mkdirs() }
        val destFile = File(destDir, sourceFile.name)
        sourceFile.copyTo(destFile, overwrite = true)

        MediaScannerConnection.scanFile(
            context, arrayOf(destFile.absolutePath), arrayOf(mime), null
        )

        return destFile.absolutePath
    }

    /* ═══════════════ Helpers ═══════════════ */

    private fun parseJson(raw: String): JSONObject {
        val trimmed = raw.trim()
        return try {
            JSONObject(trimmed)
        } catch (e: Exception) {
            val start = trimmed.indexOf('{')
            val end = trimmed.lastIndexOf('}')
            if (start >= 0 && end > start)
                JSONObject(trimmed.substring(start, end + 1))
            else throw Exception("Could not parse video info JSON")
        }
    }

    private fun cleanTempDir() {
        try {
            tempDir.listFiles()?.forEach { it.delete() }
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
        name.endsWith(".wav") -> "audio/wav"
        else -> "video/mp4"
    }
}