package com.example.youtubedownloader

import android.content.Context
import android.util.Log
import java.io.File

object CacheManager {

    private const val TAG = "CacheManager"

    /**
     * Returns total size of all cleanable data in bytes
     */
    fun getCleanableSize(context: Context): Long {
        var total = 0L
        getCleanableDirs(context).forEach { dir ->
            if (dir.exists()) total += dirSize(dir)
        }
        // Also count app cache
        total += dirSize(context.cacheDir)
        context.externalCacheDir?.let { total += dirSize(it) }
        return total
    }

    /**
     * Full cleanup — call on app open
     */
    fun autoCleanup(context: Context) {
        Log.d(TAG, "Starting auto-cleanup...")
        val before = getCleanableSize(context)

        // 1. Clean temp download folder
        cleanDir(getTempDir(context), "temp downloads")

        // 2. Clean yt-dlp cache
        cleanYtDlpCache(context)

        // 3. Clean Python bytecode cache (__pycache__)
        cleanPythonCache(context)

        // 4. Clean app cache directories
        cleanDir(context.cacheDir, "app cache")
        context.externalCacheDir?.let { cleanDir(it, "external cache") }

        // 5. Clean partial/incomplete downloads (files with .part, .ytdl extensions)
        cleanPartialFiles(context)

        val after = getCleanableSize(context)
        val freed = before - after
        Log.d(TAG, "✅ Cleanup done. Freed: ${formatBytes(freed)} " +
                "(${formatBytes(before)} → ${formatBytes(after)})")
    }

    /**
     * Deep cleanup — user triggered, more aggressive
     */
    fun deepCleanup(context: Context): Long {
        Log.d(TAG, "Starting DEEP cleanup...")
        val before = getCleanableSize(context)

        // Everything from auto cleanup
        autoCleanup(context)

        // Additionally clean:
        // 6. yt-dlp extracted packages (will re-extract on next init)
        // Be careful — don't delete the main python/yt-dlp binaries
        cleanYtDlpTempData(context)

        // 7. Clean any stale files in external files dir
        cleanStaleExternalFiles(context)

        val after = getCleanableSize(context)
        val freed = before - after
        Log.d(TAG, "✅ Deep cleanup done. Freed: ${formatBytes(freed)}")
        return freed
    }

    /* ═══════════════ Individual Cleaners ═══════════════ */

    private fun cleanYtDlpCache(context: Context) {
        // yt-dlp stores cache in various locations
        val possibleCacheDirs = listOf(
            File(context.noBackupFilesDir, "youtubedl-android/cache"),
            File(context.noBackupFilesDir, "youtubedl-android/tmp"),
            File(context.filesDir, "youtubedl-android/cache"),
            File(context.filesDir, "youtubedl-android/tmp"),
            File(context.cacheDir, "youtubedl-android"),
            // yt-dlp puts cache here too
            File(context.noBackupFilesDir, "youtubedl-android/yt-dlp/yt-dlp/.cache"),
            File(context.noBackupFilesDir, "youtubedl-android/packages/python/usr/tmp"),
        )

        possibleCacheDirs.forEach { dir ->
            if (dir.exists()) {
                cleanDir(dir, "yt-dlp cache: ${dir.name}")
            }
        }
    }

    private fun cleanPythonCache(context: Context) {
        // Recursively find and delete __pycache__ directories
        val baseDir = File(context.noBackupFilesDir, "youtubedl-android")
        if (baseDir.exists()) {
            deletePyCacheDirs(baseDir)
        }
    }

    private fun deletePyCacheDirs(dir: File) {
        if (!dir.isDirectory) return
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                if (file.name == "__pycache__" || file.name == ".cache") {
                    val size = dirSize(file)
                    file.deleteRecursively()
                    Log.d(TAG, "  Deleted ${file.name}: ${formatBytes(size)}")
                } else {
                    deletePyCacheDirs(file)
                }
            }
        }
    }

    private fun cleanPartialFiles(context: Context) {
        val externalDir = context.getExternalFilesDir(null) ?: return
        val partialExtensions = listOf(".part", ".ytdl", ".temp", ".tmp")

        externalDir.walkTopDown().forEach { file ->
            if (file.isFile && partialExtensions.any { ext ->
                    file.name.endsWith(ext, ignoreCase = true)
                }) {
                val size = file.length()
                file.delete()
                Log.d(TAG, "  Deleted partial: ${file.name} (${formatBytes(size)})")
            }
        }
    }

    private fun cleanYtDlpTempData(context: Context) {
        // Clean temporary extraction data but NOT the main binaries
        val ytdlDir = File(context.noBackupFilesDir, "youtubedl-android")
        if (!ytdlDir.exists()) return

        ytdlDir.listFiles()?.forEach { file ->
            // Keep essential dirs: packages, yt-dlp
            // Delete everything else
            if (file.isDirectory && file.name !in listOf("packages", "yt-dlp")) {
                val size = dirSize(file)
                file.deleteRecursively()
                Log.d(TAG, "  Deleted yt-dlp temp: ${file.name} (${formatBytes(size)})")
            }
            // Delete loose temp files
            if (file.isFile && (file.name.endsWith(".tmp") ||
                        file.name.endsWith(".log") ||
                        file.name.endsWith(".part"))
            ) {
                file.delete()
            }
        }
    }

    private fun cleanStaleExternalFiles(context: Context) {
        val externalDir = context.getExternalFilesDir(null) ?: return
        externalDir.listFiles()?.forEach { file ->
            if (file.isDirectory && file.name.startsWith("YTDownloader")) {
                // Clean everything in download-related dirs
                cleanDir(file, "stale external: ${file.name}")
            }
        }
    }

    /* ═══════════════ Helpers ═══════════════ */

    fun getTempDir(context: Context): File {
        return File(context.getExternalFilesDir(null), "YTDownloader_temp")
    }

    private fun getCleanableDirs(context: Context): List<File> {
        return listOf(
            getTempDir(context),
            File(context.noBackupFilesDir, "youtubedl-android/cache"),
            File(context.noBackupFilesDir, "youtubedl-android/tmp"),
            File(context.filesDir, "youtubedl-android/cache"),
            File(context.cacheDir, "youtubedl-android"),
        )
    }

    private fun cleanDir(dir: File, label: String) {
        if (!dir.exists()) return
        val size = dirSize(dir)
        var count = 0
        dir.listFiles()?.forEach { file ->
            if (file.deleteRecursively()) count++
        }
        if (count > 0) {
            Log.d(TAG, "  Cleaned $label: $count items, ${formatBytes(size)}")
        }
    }

    fun dirSize(dir: File): Long {
        if (!dir.exists()) return 0
        if (dir.isFile) return dir.length()
        var size = 0L
        dir.walkTopDown().forEach { file ->
            if (file.isFile) size += file.length()
        }
        return size
    }

    fun formatBytes(bytes: Long): String = when {
        bytes <= 0 -> "0 B"
        bytes < 1024 -> "$bytes B"
        bytes < 1024L * 1024 -> "%.1f KB".format(bytes / 1024.0)
        bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        else -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
    }
}