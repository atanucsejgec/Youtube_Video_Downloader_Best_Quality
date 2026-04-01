package com.example.youtubedownloader

import android.app.Application
import android.util.Log
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
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/* ───────── Data models ───────── */

data class VideoDetails(
    val title: String,
    val thumbnail: String?,
    val duration: Long,
    val author: String?
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
    data class InfoReady(val details: VideoDetails) : UiState
    data class Downloading(
        val details: VideoDetails,
        val progress: Float,
        val eta: Long = 0L,
        val line: String = ""
    ) : UiState
    data class Completed(val details: VideoDetails, val filePath: String) : UiState
    data class Error(val message: String) : UiState
}

/* ───────── ViewModel ───────── */

class MainViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        private const val TAG = "MainVM"
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _url = MutableStateFlow("")
    val url: StateFlow<String> = _url.asStateFlow()

    private val _quality = MutableStateFlow(VideoQuality.BEST)
    val quality: StateFlow<VideoQuality> = _quality.asStateFlow()

    @Volatile
    private var isEngineReady = false

    private var job: Job? = null
    private var processId: String? = null

    private val downloadDir: File
        get() = File(
            getApplication<Application>().getExternalFilesDir(null),
            "YTDownloader"
        ).also { if (!it.exists()) it.mkdirs() }

    private fun Throwable.fullTrace(): String {
        val sw = StringWriter()
        printStackTrace(PrintWriter(sw))
        return sw.toString()
    }

    init {
        initEngine()
    }

    fun initEngine() {
        viewModelScope.launch(Dispatchers.IO) {
            if (isEngineReady) {
                _uiState.value = UiState.Idle
                return@launch
            }

            _uiState.value = UiState.Initializing

            // ── Step 1: Init YoutubeDL ──
            try {
                Log.d(TAG, "Step 1: Initializing YoutubeDL...")
                YoutubeDL.getInstance().init(getApplication())
                Log.d(TAG, "Step 1: ✅ YoutubeDL initialized")
            } catch (e: Exception) {
                Log.e(TAG, "Step 1: ❌ FAILED", e)
                _uiState.value = UiState.Error(
                    "YoutubeDL init failed:\n${e.javaClass.simpleName}: ${e.message}\n\n${e.fullTrace()}"
                )
                return@launch
            }

            // ── Step 2: Init FFmpeg ──
            try {
                Log.d(TAG, "Step 2: Initializing FFmpeg...")
                FFmpeg.getInstance().init(getApplication())
                Log.d(TAG, "Step 2: ✅ FFmpeg initialized")
            } catch (e: Exception) {
                Log.e(TAG, "Step 2: ❌ FAILED", e)
                _uiState.value = UiState.Error(
                    "FFmpeg init failed:\n${e.javaClass.simpleName}: ${e.message}"
                )
                return@launch
            }

            isEngineReady = true
            Log.d(TAG, "✅ Engine ready!")

            // ── Step 3: Update yt-dlp to latest (now safe with Python 3.11) ──
            try {
                Log.d(TAG, "Step 3: Updating yt-dlp...")
                YoutubeDL.getInstance().updateYoutubeDL(
                    getApplication(),
                    YoutubeDL.UpdateChannel.STABLE
                )
                Log.d(TAG, "Step 3: ✅ yt-dlp updated")
            } catch (e: Exception) {
                Log.w(TAG, "Step 3: Update skipped: ${e.message}")
                // Non-fatal — bundled version might still work
            }

            _uiState.value = UiState.Idle
        }
    }

    /* ── Public actions ── */

    fun updateUrl(v: String) {
        _url.value = v
    }

    fun selectQuality(q: VideoQuality) {
        _quality.value = q
    }

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
                Log.d(TAG, "Fetching info for: $videoUrl")

                val request = YoutubeDLRequest(videoUrl).apply {
                    addOption("--no-playlist")
                    addOption("--force-ipv4")
                }
                val info = YoutubeDL.getInstance().getInfo(request)

                Log.d(TAG, "Got info: title=${info.title}")
                _uiState.value = UiState.InfoReady(
                    VideoDetails(
                        title = info.title ?: "Unknown",
                        thumbnail = info.thumbnail,
                        duration = (info.duration ?: 0).toLong(),
                        author = info.uploader
                    )
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

    fun startDownload() {
        val videoUrl = _url.value.trim()
        val q = _quality.value

        if (!isEngineReady) {
            _uiState.value = UiState.Error("Engine not ready")
            return
        }

        val details = when (val s = _uiState.value) {
            is UiState.InfoReady -> s.details
            is UiState.Completed -> s.details
            else -> {
                _uiState.value = UiState.Error("Fetch info first")
                return
            }
        }

        job?.cancel()
        job = viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = UiState.Downloading(details, 0f)
            try {
                val pid = System.currentTimeMillis().toString()
                processId = pid

                val request = YoutubeDLRequest(videoUrl).apply {
                    addOption("--no-playlist")
                    addOption("--force-ipv4")
                    addOption("-f", q.formatArg)
                    addOption(
                        "-o",
                        "${downloadDir.absolutePath}/%(title).150s.%(ext)s"
                    )
                    addOption("--restrict-filenames")
                    addOption("--no-mtime")

                    if (q.isAudioOnly && q == VideoQuality.AUDIO_MP3) {
                        addOption("-x")
                        addOption("--audio-format", "mp3")
                    } else if (!q.isAudioOnly) {
                        addOption("--merge-output-format", "mp4")
                    }
                }

                YoutubeDL.getInstance().execute(
                    request, pid
                ) { progress, eta, line ->
                    _uiState.value = UiState.Downloading(
                        details, progress, eta, line
                    )
                }

                val latest = downloadDir.listFiles()
                    ?.maxByOrNull { it.lastModified() }

                _uiState.value = UiState.Completed(
                    details,
                    latest?.absolutePath ?: downloadDir.absolutePath
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "download failed", e)
                _uiState.value = UiState.Error(
                    "Download failed:\n${e.javaClass.simpleName}: ${e.message}"
                )
            }
        }
    }

    fun cancelDownload() {
        processId?.let {
            try {
                YoutubeDL.getInstance().destroyProcessById(it)
            } catch (_: Exception) {}
        }
        job?.cancel()
        _uiState.value = UiState.Idle
    }

    fun reset() {
        job?.cancel()
        _url.value = ""
        if (isEngineReady) {
            _uiState.value = UiState.Idle
        } else {
            initEngine()
        }
    }
}