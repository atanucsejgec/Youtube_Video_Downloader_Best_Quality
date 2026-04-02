package com.example.youtubedownloader

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.youtubedownloader.ui.DownloadScreen
import com.example.youtubedownloader.ui.theme.YoutubeDownloaderTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainActivity : ComponentActivity() {

    companion object {
        private val _sharedUrl = MutableStateFlow<String?>(null)
        val sharedUrl: StateFlow<String?> = _sharedUrl.asStateFlow()

        fun consumeSharedUrl(): String? {
            val url = _sharedUrl.value
            _sharedUrl.value = null
            return url
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIncomingIntent(intent)

        setContent {
            YoutubeDownloaderTheme {
                DownloadScreen()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            Intent.ACTION_SEND -> {
                if (intent.type == "text/plain") {
                    val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                    val url = extractYouTubeUrl(text)
                    if (url != null) _sharedUrl.value = url
                }
            }
            Intent.ACTION_VIEW -> {
                val url = intent.data?.toString()
                if (url != null && isYouTubeUrl(url)) _sharedUrl.value = url
            }
        }
    }

    private fun extractYouTubeUrl(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val regex = Regex("""(https?://(www\.)?(youtube\.com|youtu\.be|m\.youtube\.com|music\.youtube\.com)\S+)""")
        return regex.find(text)?.value ?: if (isYouTubeUrl(text.trim())) text.trim() else null
    }

    private fun isYouTubeUrl(url: String): Boolean =
        url.contains("youtube.com") || url.contains("youtu.be") || url.contains("music.youtube.com")
}