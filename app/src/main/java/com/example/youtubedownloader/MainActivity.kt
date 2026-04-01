package com.example.youtubedownloader

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
                Surface(color = MaterialTheme.colorScheme.background) {
                    DownloadScreen()
                }
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
            // YouTube Share button → ACTION_SEND with text
            Intent.ACTION_SEND -> {
                if (intent.type == "text/plain") {
                    val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                    val url = extractYouTubeUrl(text)
                    if (url != null) {
                        _sharedUrl.value = url
                    }
                }
            }
            // Direct URL open
            Intent.ACTION_VIEW -> {
                val url = intent.data?.toString()
                if (url != null && isYouTubeUrl(url)) {
                    _sharedUrl.value = url
                }
            }
        }
    }

    private fun extractYouTubeUrl(text: String?): String? {
        if (text.isNullOrBlank()) return null
        // YouTube share text often looks like:
        // "Check out this video: https://youtu.be/xxxxx"
        // or just the URL
        val regex = Regex(
            """(https?://(www\.)?(youtube\.com|youtu\.be|m\.youtube\.com|music\.youtube\.com)\S+)"""
        )
        val match = regex.find(text)
        return match?.value ?: if (isYouTubeUrl(text.trim())) text.trim() else null
    }

    private fun isYouTubeUrl(url: String): Boolean {
        return url.contains("youtube.com") ||
                url.contains("youtu.be") ||
                url.contains("music.youtube.com")
    }
}