package com.example.youtubedownloader

import android.content.Context
import android.os.Environment

enum class DownloadLocation(
    val label: String,
    val icon: String
) {
    MOVIES("Movies/YTDownloader", "🎬"),
    DOWNLOADS("Downloads/YTDownloader", "📥"),
    MUSIC("Music/YTDownloader", "🎵"),
    DCIM("DCIM/YTDownloader", "📷"),
    CUSTOM("Custom folder…", "📂");

    fun getBasePath(): String = when (this) {
        MOVIES -> Environment.DIRECTORY_MOVIES
        DOWNLOADS -> Environment.DIRECTORY_DOWNLOADS
        MUSIC -> Environment.DIRECTORY_MUSIC
        DCIM -> Environment.DIRECTORY_DCIM
        CUSTOM -> Environment.DIRECTORY_DOWNLOADS // fallback
    }
}

object DownloadPrefs {
    private const val PREFS_NAME = "yt_downloader_prefs"
    private const val KEY_LOCATION = "download_location"
    private const val KEY_CUSTOM_PATH = "custom_path"
    private const val KEY_SUBFOLDER = "subfolder_name"

    fun getLocation(context: Context): DownloadLocation {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val name = prefs.getString(KEY_LOCATION, DownloadLocation.MOVIES.name)
        return try {
            DownloadLocation.valueOf(name!!)
        } catch (_: Exception) {
            DownloadLocation.MOVIES
        }
    }

    fun setLocation(context: Context, location: DownloadLocation) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LOCATION, location.name)
            .apply()
    }

    fun getCustomPath(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CUSTOM_PATH, null)
    }

    fun setCustomPath(context: Context, path: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CUSTOM_PATH, path)
            .apply()
    }

    fun getSubfolderName(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SUBFOLDER, "YTDownloader") ?: "YTDownloader"
    }

    fun setSubfolderName(context: Context, name: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SUBFOLDER, name)
            .apply()
    }

    fun getRelativePath(context: Context): String {
        val location = getLocation(context)
        val subfolder = getSubfolderName(context)
        return "${location.getBasePath()}/$subfolder"
    }
}