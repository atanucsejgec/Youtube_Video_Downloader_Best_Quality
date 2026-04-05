package com.example.youtubedownloader

import android.content.Context
import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import org.json.JSONObject

data class SearchResult(
    val id: String,
    val title: String,
    val uploader: String,
    val thumbnail: String?,
    val duration: Long,
    val viewCount: Long = 0L,
    val url: String
)

object SearchRepository {
    private const val TAG = "SearchRepository"

    fun isYouTubeUrl(text: String): Boolean {
        return text.contains("youtube.com/") ||
                text.contains("youtu.be/") ||
                text.contains("music.youtube.com/")
    }

    suspend fun search(
        context: Context,
        query: String,
        maxResults: Int = 15
    ): List<SearchResult> {
        return try {
            // If it's a direct URL, fetch single video info
            if (isYouTubeUrl(query)) {
                val result = fetchSingleVideoInfo(context, query)
                if (result != null) listOf(result) else emptyList()
            } else {
                // Search YouTube
                searchYouTube(context, query, maxResults)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Search failed: ${e.message}", e)
            emptyList()
        }
    }

    private fun fetchSingleVideoInfo(context: Context, url: String): SearchResult? {
        return try {
            val request = YoutubeDLRequest(url).apply {
                addOption("--dump-single-json")
                addOption("--no-playlist")
                addOption("--no-warnings")
                addOption("--force-ipv4")
                val cookieFile = CookieHelper.getCookieFile(context)
                if (cookieFile.exists() && cookieFile.length() > 100) {
                    addOption("--cookies", cookieFile.absolutePath)
                }
            }
            val result = YoutubeDL.getInstance().execute(request)
            parseVideoJson(JSONObject(result.out.trim()))
        } catch (e: Exception) {
            Log.e(TAG, "Single video fetch failed: ${e.message}")
            null
        }
    }

    private fun searchYouTube(
        context: Context,
        query: String,
        maxResults: Int
    ): List<SearchResult> {
        val searchUrl = "ytsearch${maxResults}:${query}"
        val request = YoutubeDLRequest(searchUrl).apply {
            addOption("--dump-single-json")
            addOption("--flat-playlist")
            addOption("--no-warnings")
            addOption("--force-ipv4")
            val cookieFile = CookieHelper.getCookieFile(context)
            if (cookieFile.exists() && cookieFile.length() > 100) {
                addOption("--cookies", cookieFile.absolutePath)
            }
        }
        val result = YoutubeDL.getInstance().execute(request)
        val json = JSONObject(result.out.trim())
        val entries = json.optJSONArray("entries") ?: return emptyList()
        val results = mutableListOf<SearchResult>()
        for (i in 0 until entries.length()) {
            val entry = entries.optJSONObject(i) ?: continue
            val parsed = parseSearchEntry(entry) ?: continue
            results.add(parsed)
        }
        return results
    }

    private fun parseVideoJson(json: JSONObject): SearchResult? {
        val id = json.optString("id", "") .ifBlank { return null }
        return SearchResult(
            id = id,
            title = json.optString("title", "Unknown"),
            uploader = json.optString("uploader", json.optString("channel", "Unknown")),
            thumbnail = getBestThumbnail(json),
            duration = json.optDouble("duration", 0.0).toLong(),
            viewCount = json.optLong("view_count", 0L),
            url = "https://www.youtube.com/watch?v=$id"
        )
    }

    private fun parseSearchEntry(json: JSONObject): SearchResult? {
        val id = json.optString("id", "").ifBlank { return null }
        val url = json.optString("url", "").let {
            if (it.startsWith("http")) it
            else "https://www.youtube.com/watch?v=$id"
        }
        // Get thumbnail from nested thumbnails array or direct thumbnail field
        val thumbnail = getBestThumbnail(json)
            ?: "https://i.ytimg.com/vi/$id/mqdefault.jpg"

        return SearchResult(
            id = id,
            title = json.optString("title", "Unknown"),
            uploader = json.optString("uploader", json.optString("channel", "Unknown")),
            thumbnail = thumbnail,
            duration = json.optDouble("duration", 0.0).toLong(),
            viewCount = json.optLong("view_count", 0L),
            url = url
        )
    }

    private fun getBestThumbnail(json: JSONObject): String? {
        // Try thumbnails array first
        val thumbnails = json.optJSONArray("thumbnails")
        if (thumbnails != null && thumbnails.length() > 0) {
            // Get medium quality thumbnail (not too big, not too small)
            var best: String? = null
            var bestWidth = 0
            for (i in 0 until thumbnails.length()) {
                val t = thumbnails.optJSONObject(i) ?: continue
                val url = t.optString("url", "")
                val width = t.optInt("width", 0)
                if (url.isNotBlank() && width in 160..640 && width > bestWidth) {
                    best = url
                    bestWidth = width
                }
            }
            if (best != null) return best
            // Fallback to last thumbnail
            thumbnails.optJSONObject(thumbnails.length() - 1)
                ?.optString("thumbnail")?.takeIf { it.isNotBlank() }?.let { return it }
        }
        // Direct thumbnail field
        return json.optString("thumbnail", "").takeIf { it.isNotBlank() }
    }
}