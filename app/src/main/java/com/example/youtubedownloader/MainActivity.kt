package com.example.youtubedownloader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import com.example.youtubedownloader.ui.DownloadScreen
import com.example.youtubedownloader.ui.theme.YoutubeDownloaderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            YoutubeDownloaderTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    DownloadScreen()
                }
            }
        }
    }
}