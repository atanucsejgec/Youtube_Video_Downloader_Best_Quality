package com.example.youtubedownloader

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.youtubedownloader.ui.MainApp
import com.example.youtubedownloader.ui.theme.YoutubeDownloaderTheme
import kotlinx.coroutines.flow.*

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

    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* handled */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIncomingIntent(intent)
        requestNotificationPermission()

        setContent {
            val vm: MainViewModel = viewModel()
            val isDark by vm.isDarkTheme.collectAsState()

            MaterialTheme(
                colorScheme = if (isDark) darkColorScheme(
                    primary = Color(0xFF00E5FF),
                    background = Color(0xFF0A1929),
                    surface = Color(0xFF101D2E)
                ) else lightColorScheme()
            ) {
                MainApp(vm = vm)
            }

            // Auto-paste from clipboard
            LaunchedEffect(Unit) {
                val clip = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val text = clip.primaryClip?.getItemAt(0)?.text?.toString()
                vm.checkClipboardForUrl(text)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
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