package com.example.youtubedownloader

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.example.youtubedownloader.data.LocalStorage

class App : Application() {

    companion object {
        const val CHANNEL_DOWNLOAD = "download_channel"
        const val CHANNEL_COMPLETE = "complete_channel"
        lateinit var storage: LocalStorage
            private set
    }

    override fun onCreate() {
        super.onCreate()
        storage = LocalStorage(this)
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val downloadChannel = NotificationChannel(
            CHANNEL_DOWNLOAD,
            "Downloads",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Download progress"
            setShowBadge(false)
        }

        val completeChannel = NotificationChannel(
            CHANNEL_COMPLETE,
            "Download Complete",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Download completed notifications"
        }

        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(downloadChannel)
        nm.createNotificationChannel(completeChannel)
    }
}