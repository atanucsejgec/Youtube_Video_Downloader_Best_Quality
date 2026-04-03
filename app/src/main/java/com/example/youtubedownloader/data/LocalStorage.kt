package com.example.youtubedownloader.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicLong

// ══════════════════════════════════════
//  DATA CLASSES (replace Room entities)
// ══════════════════════════════════════

data class DownloadHistoryItem(
    val id: Long = 0,
    val videoId: String = "",
    val url: String = "",
    val title: String = "Unknown",
    val author: String? = null,
    val thumbnail: String? = null,
    val duration: Long = 0,
    val quality: String = "",
    val fileSize: Long = 0,
    val fileSizeText: String = "0 B",
    val filePath: String = "",
    val mimeType: String = "video/mp4",
    val contentUri: String? = null,
    val downloadedAt: Long = System.currentTimeMillis(),
    val status: String = "completed"
)

data class QueueItem(
    val id: Long = 0,
    val url: String = "",
    val title: String? = null,
    val thumbnail: String? = null,
    val quality: String = "BEST",
    val downloadSubtitles: Boolean = false,
    val embedThumbnail: Boolean = false,
    val status: String = "pending",
    val progress: Float = 0f,
    val speedText: String = "",
    val errorMessage: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
    val retryCount: Int = 0
)

data class FavoriteQuality(
    val channelName: String = "",
    val qualityName: String = "BEST",
    val lastUsed: Long = System.currentTimeMillis()
)

// ══════════════════════════════════════
//  JSON SERIALIZERS
// ══════════════════════════════════════

private fun DownloadHistoryItem.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("videoId", videoId)
    put("url", url)
    put("title", title)
    put("author", author ?: "")
    put("thumbnail", thumbnail ?: "")
    put("duration", duration)
    put("quality", quality)
    put("fileSize", fileSize)
    put("fileSizeText", fileSizeText)
    put("filePath", filePath)
    put("mimeType", mimeType)
    put("contentUri", contentUri ?: "")
    put("downloadedAt", downloadedAt)
    put("status", status)
}

private fun JSONObject.toHistoryItem(): DownloadHistoryItem = DownloadHistoryItem(
    id = optLong("id", 0),
    videoId = optString("videoId", ""),
    url = optString("url", ""),
    title = optString("title", "Unknown"),
    author = optString("author", "").ifBlank { null },
    thumbnail = optString("thumbnail", "").ifBlank { null },
    duration = optLong("duration", 0),
    quality = optString("quality", ""),
    fileSize = optLong("fileSize", 0),
    fileSizeText = optString("fileSizeText", "0 B"),
    filePath = optString("filePath", ""),
    mimeType = optString("mimeType", "video/mp4"),
    contentUri = optString("contentUri", "").ifBlank { null },
    downloadedAt = optLong("downloadedAt", 0),
    status = optString("status", "completed")
)

private fun QueueItem.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("url", url)
    put("title", title ?: "")
    put("thumbnail", thumbnail ?: "")
    put("quality", quality)
    put("downloadSubtitles", downloadSubtitles)
    put("embedThumbnail", embedThumbnail)
    put("status", status)
    put("progress", progress.toDouble())
    put("speedText", speedText)
    put("errorMessage", errorMessage ?: "")
    put("addedAt", addedAt)
    put("retryCount", retryCount)
}

private fun JSONObject.toQueueItem(): QueueItem = QueueItem(
    id = optLong("id", 0),
    url = optString("url", ""),
    title = optString("title", "").ifBlank { null },
    thumbnail = optString("thumbnail", "").ifBlank { null },
    quality = optString("quality", "BEST"),
    downloadSubtitles = optBoolean("downloadSubtitles", false),
    embedThumbnail = optBoolean("embedThumbnail", false),
    status = optString("status", "pending"),
    progress = optDouble("progress", 0.0).toFloat(),
    speedText = optString("speedText", ""),
    errorMessage = optString("errorMessage", "").ifBlank { null },
    addedAt = optLong("addedAt", 0),
    retryCount = optInt("retryCount", 0)
)

private fun FavoriteQuality.toJson(): JSONObject = JSONObject().apply {
    put("channelName", channelName)
    put("qualityName", qualityName)
    put("lastUsed", lastUsed)
}

private fun JSONObject.toFavoriteQuality(): FavoriteQuality = FavoriteQuality(
    channelName = optString("channelName", ""),
    qualityName = optString("qualityName", "BEST"),
    lastUsed = optLong("lastUsed", 0)
)

// ══════════════════════════════════════
//  LOCAL STORAGE (replaces Room)
// ══════════════════════════════════════

class LocalStorage(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("yt_local_db", Context.MODE_PRIVATE)

    private val idCounter = AtomicLong(
        prefs.getLong("_id_counter", 1)
    )

    private fun nextId(): Long {
        val id = idCounter.getAndIncrement()
        prefs.edit().putLong("_id_counter", id + 1).apply()
        return id
    }

    // ── Live Flows ──
    private val _historyFlow = MutableStateFlow<List<DownloadHistoryItem>>(emptyList())
    val historyFlow: Flow<List<DownloadHistoryItem>> = _historyFlow.asStateFlow()

    private val _queueFlow = MutableStateFlow<List<QueueItem>>(emptyList())
    val queueFlow: Flow<List<QueueItem>> = _queueFlow.asStateFlow()

    init {
        _historyFlow.value = loadHistory()
        _queueFlow.value = loadQueue()
    }

    // ══════════════════════════════════
    //  HISTORY
    // ══════════════════════════════════

    private fun loadHistory(): List<DownloadHistoryItem> {
        val json = prefs.getString("history", "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getJSONObject(it).toHistoryItem() }
                .sortedByDescending { it.downloadedAt }
        } catch (_: Exception) { emptyList() }
    }

    private fun saveHistory(list: List<DownloadHistoryItem>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs.edit().putString("history", arr.toString()).apply()
        _historyFlow.value = list.sortedByDescending { it.downloadedAt }
    }

    fun addHistory(item: DownloadHistoryItem): Long {
        val id = nextId()
        val withId = item.copy(id = id)
        val list = loadHistory().toMutableList()
        list.add(withId)
        saveHistory(list)
        return id
    }

    fun findHistoryByVideoId(videoId: String): DownloadHistoryItem? {
        return loadHistory().find { it.videoId == videoId }
    }

    fun deleteHistory(id: Long) {
        val list = loadHistory().toMutableList()
        list.removeAll { it.id == id }
        saveHistory(list)
    }

    fun clearAllHistory() {
        saveHistory(emptyList())
    }

    fun getTotalDownloadedSize(): Long {
        return loadHistory().sumOf { it.fileSize }
    }

    fun getTodayDownloadedSize(startOfDay: Long): Long {
        return loadHistory()
            .filter { it.downloadedAt >= startOfDay }
            .sumOf { it.fileSize }
    }

    fun getTotalCount(): Int {
        return loadHistory().size
    }

    // ══════════════════════════════════
    //  QUEUE
    // ══════════════════════════════════

    private fun loadQueue(): List<QueueItem> {
        val json = prefs.getString("queue", "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getJSONObject(it).toQueueItem() }
                .sortedBy { it.addedAt }
        } catch (_: Exception) { emptyList() }
    }

    private fun saveQueue(list: List<QueueItem>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs.edit().putString("queue", arr.toString()).apply()
        _queueFlow.value = list.sortedBy { it.addedAt }
    }

    fun addToQueue(item: QueueItem): Long {
        val id = nextId()
        val withId = item.copy(id = id)
        val list = loadQueue().toMutableList()
        list.add(withId)
        saveQueue(list)
        return id
    }

    fun updateQueueItem(item: QueueItem) {
        val list = loadQueue().toMutableList()
        val index = list.indexOfFirst { it.id == item.id }
        if (index >= 0) {
            list[index] = item
            saveQueue(list)
        }
    }

    fun updateQueueStatus(id: Long, status: String) {
        val list = loadQueue().toMutableList()
        val index = list.indexOfFirst { it.id == id }
        if (index >= 0) {
            list[index] = list[index].copy(status = status)
            saveQueue(list)
        }
    }

    fun updateQueueProgress(id: Long, progress: Float, speed: String) {
        val list = loadQueue().toMutableList()
        val index = list.indexOfFirst { it.id == id }
        if (index >= 0) {
            list[index] = list[index].copy(progress = progress, speedText = speed)
            saveQueue(list)
        }
    }

    fun markQueueFailed(id: Long, error: String) {
        val list = loadQueue().toMutableList()
        val index = list.indexOfFirst { it.id == id }
        if (index >= 0) {
            list[index] = list[index].copy(
                status = "failed",
                errorMessage = error,
                retryCount = list[index].retryCount + 1
            )
            saveQueue(list)
        }
    }

    fun removeFromQueue(id: Long) {
        val list = loadQueue().toMutableList()
        list.removeAll { it.id == id }
        saveQueue(list)
    }

    fun getPendingQueue(): List<QueueItem> {
        return loadQueue().filter { it.status == "pending" }
    }

    fun getRetryableQueue(): List<QueueItem> {
        return loadQueue().filter { it.status == "failed" && it.retryCount < 3 }
    }

    fun getDownloadingQueue(): List<QueueItem> {
        return loadQueue().filter { it.status == "downloading" }
    }

    fun countByUrl(url: String): Int {
        return loadQueue().count { it.url == url && it.status in listOf("pending", "downloading") }
    }

    fun clearCompletedQueue() {
        val list = loadQueue().toMutableList()
        list.removeAll { it.status == "completed" }
        saveQueue(list)
    }

    fun clearAllQueue() {
        saveQueue(emptyList())
    }

    // ══════════════════════════════════
    //  FAVORITE QUALITIES
    // ══════════════════════════════════

    private fun loadFavorites(): List<FavoriteQuality> {
        val json = prefs.getString("favorites", "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getJSONObject(it).toFavoriteQuality() }
        } catch (_: Exception) { emptyList() }
    }

    private fun saveFavorites(list: List<FavoriteQuality>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs.edit().putString("favorites", arr.toString()).apply()
    }

    fun getFavoriteQuality(channel: String): FavoriteQuality? {
        return loadFavorites().find {
            it.channelName.equals(channel, ignoreCase = true)
        }
    }

    fun saveFavoriteQuality(channel: String, quality: String) {
        val list = loadFavorites().toMutableList()
        list.removeAll { it.channelName.equals(channel, ignoreCase = true) }
        list.add(FavoriteQuality(channel, quality))
        saveFavorites(list)
    }
}