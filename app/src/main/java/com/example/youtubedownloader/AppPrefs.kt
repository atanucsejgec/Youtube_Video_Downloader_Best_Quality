package com.example.youtubedownloader

import android.content.Context
import android.os.Environment

enum class DownloadLocation(val label: String, val icon: String) {
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
        CUSTOM -> Environment.DIRECTORY_DOWNLOADS
    }
}

object AppPrefs {
    private const val PREFS = "yt_downloader_prefs"

    // Keys
    private const val KEY_LOCATION = "download_location"
    private const val KEY_SUBFOLDER = "subfolder_name"
    private const val KEY_DARK_THEME = "dark_theme"
    private const val KEY_WIFI_ONLY = "wifi_only"
    private const val KEY_SPEED_LIMIT = "speed_limit_kb"
    private const val KEY_CONCURRENT_DOWNLOADS = "concurrent_downloads"
    private const val KEY_DOWNLOAD_SUBS = "download_subtitles"
    private const val KEY_EMBED_THUMBNAIL = "embed_thumbnail"
    private const val KEY_AUTO_PASTE = "auto_paste"
    private const val KEY_LANGUAGE = "app_language"
    private const val KEY_CONCURRENT_FRAGMENTS = "concurrent_fragments"
    private const val KEY_SCHEDULE_ENABLED = "schedule_enabled"
    private const val KEY_SCHEDULE_HOUR = "schedule_hour"
    private const val KEY_SCHEDULE_MINUTE = "schedule_minute"
    private const val KEY_SCHEDULE_WIFI_ONLY = "schedule_wifi_only"
    private const val KEY_TOTAL_EVER_DOWNLOADED = "total_ever_downloaded"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // Download Location
    fun getLocation(context: Context): DownloadLocation {
        val name = prefs(context).getString(KEY_LOCATION, DownloadLocation.DOWNLOADS.name)
        return try { DownloadLocation.valueOf(name!!) } catch (_: Exception) { DownloadLocation.DOWNLOADS }
    }
    fun setLocation(context: Context, loc: DownloadLocation) =
        prefs(context).edit().putString(KEY_LOCATION, loc.name).apply()

    fun getSubfolder(context: Context): String =
        prefs(context).getString(KEY_SUBFOLDER, "YTDownloader") ?: "YTDownloader"
    fun setSubfolder(context: Context, name: String) =
        prefs(context).edit().putString(KEY_SUBFOLDER, name).apply()

    fun getRelativePath(context: Context): String =
        "${getLocation(context).getBasePath()}/${getSubfolder(context)}"

    // Theme
    fun isDarkTheme(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DARK_THEME, true)
    fun setDarkTheme(context: Context, dark: Boolean) =
        prefs(context).edit().putBoolean(KEY_DARK_THEME, dark).apply()

    // Wi-Fi Only
    fun isWifiOnly(context: Context): Boolean =
        prefs(context).getBoolean(KEY_WIFI_ONLY, false)
    fun setWifiOnly(context: Context, v: Boolean) =
        prefs(context).edit().putBoolean(KEY_WIFI_ONLY, v).apply()

    // Speed Limit (0 = unlimited)
    fun getSpeedLimitKb(context: Context): Int =
        prefs(context).getInt(KEY_SPEED_LIMIT, 0)
    fun setSpeedLimitKb(context: Context, kb: Int) =
        prefs(context).edit().putInt(KEY_SPEED_LIMIT, kb).apply()

    // Concurrent Downloads
    fun getConcurrentDownloads(context: Context): Int =
        prefs(context).getInt(KEY_CONCURRENT_DOWNLOADS, 1)
    fun setConcurrentDownloads(context: Context, n: Int) =
        prefs(context).edit().putInt(KEY_CONCURRENT_DOWNLOADS, n).apply()

    // Concurrent Fragments
    fun getConcurrentFragments(context: Context): Int =
        prefs(context).getInt(KEY_CONCURRENT_FRAGMENTS, 16)
    fun setConcurrentFragments(context: Context, n: Int) =
        prefs(context).edit().putInt(KEY_CONCURRENT_FRAGMENTS, n).apply()

    // Subtitles
    fun downloadSubtitles(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DOWNLOAD_SUBS, false)
    fun setDownloadSubtitles(context: Context, v: Boolean) =
        prefs(context).edit().putBoolean(KEY_DOWNLOAD_SUBS, v).apply()

    // Embed Thumbnail
    fun embedThumbnail(context: Context): Boolean =
        prefs(context).getBoolean(KEY_EMBED_THUMBNAIL, false)
    fun setEmbedThumbnail(context: Context, v: Boolean) =
        prefs(context).edit().putBoolean(KEY_EMBED_THUMBNAIL, v).apply()

    // Auto-paste
    fun autoPaste(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO_PASTE, true)
    fun setAutoPaste(context: Context, v: Boolean) =
        prefs(context).edit().putBoolean(KEY_AUTO_PASTE, v).apply()

    // Language
    fun getLanguage(context: Context): String =
        prefs(context).getString(KEY_LANGUAGE, "en") ?: "en"
    fun setLanguage(context: Context, lang: String) =
        prefs(context).edit().putString(KEY_LANGUAGE, lang).apply()

    // Schedule
    fun isScheduleEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SCHEDULE_ENABLED, false)
    fun setScheduleEnabled(context: Context, v: Boolean) =
        prefs(context).edit().putBoolean(KEY_SCHEDULE_ENABLED, v).apply()
    fun getScheduleHour(context: Context): Int =
        prefs(context).getInt(KEY_SCHEDULE_HOUR, 2)
    fun setScheduleHour(context: Context, h: Int) =
        prefs(context).edit().putInt(KEY_SCHEDULE_HOUR, h).apply()
    fun getScheduleMinute(context: Context): Int =
        prefs(context).getInt(KEY_SCHEDULE_MINUTE, 0)
    fun setScheduleMinute(context: Context, m: Int) =
        prefs(context).edit().putInt(KEY_SCHEDULE_MINUTE, m).apply()
    fun isScheduleWifiOnly(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SCHEDULE_WIFI_ONLY, true)
    fun setScheduleWifiOnly(context: Context, v: Boolean) =
        prefs(context).edit().putBoolean(KEY_SCHEDULE_WIFI_ONLY, v).apply()

    // Total ever downloaded bytes
    fun getTotalEverDownloaded(context: Context): Long =
        prefs(context).getLong(KEY_TOTAL_EVER_DOWNLOADED, 0L)
    fun addToTotalEverDownloaded(context: Context, bytes: Long) {
        val current = getTotalEverDownloaded(context)
        prefs(context).edit().putLong(KEY_TOTAL_EVER_DOWNLOADED, current + bytes).apply()
    }
}