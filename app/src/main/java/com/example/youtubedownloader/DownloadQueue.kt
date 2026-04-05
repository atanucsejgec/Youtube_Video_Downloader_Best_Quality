package com.example.youtubedownloader

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class QueueItemStatus {
    WAITING, DOWNLOADING, COMPLETED, FAILED
}

data class QueueItem(
    val id: String = System.currentTimeMillis().toString() + (Math.random() * 1000).toInt(),
    val url: String,
    val title: String = url,
    val thumbnail: String? = null,
    val quality: VideoQuality,
    val status: QueueItemStatus = QueueItemStatus.WAITING,
    val progress: Float = 0f,
    val errorMsg: String? = null,
    val savedFileInfo: SavedFileInfo? = null,
    val fileSizeBytes: Long = 0L,
    val downloadSpeedBps: Double = 0.0,
    // For playlist items
    val isPlaylist: Boolean = false,
    val selectedEntries: List<PlaylistEntry> = emptyList()
)

object DownloadQueue {
    private val _queue = MutableStateFlow<List<QueueItem>>(emptyList())
    val queue: StateFlow<List<QueueItem>> = _queue.asStateFlow()

    fun add(item: QueueItem) {
        _queue.value = _queue.value + item
    }

    fun remove(id: String) {
        _queue.value = _queue.value.filter { it.id != id }
    }

    fun update(id: String, updater: (QueueItem) -> QueueItem) {
        _queue.value = _queue.value.map { if (it.id == id) updater(it) else it }
    }

    fun getNext(): QueueItem? = _queue.value.firstOrNull { it.status == QueueItemStatus.WAITING }

    fun hasWaiting(): Boolean = _queue.value.any { it.status == QueueItemStatus.WAITING }

    fun isDownloading(): Boolean = _queue.value.any { it.status == QueueItemStatus.DOWNLOADING }

    fun clear() {
        _queue.value = emptyList()
    }

    fun clearCompleted() {
        _queue.value = _queue.value.filter {
            it.status != QueueItemStatus.COMPLETED && it.status != QueueItemStatus.FAILED
        }
    }
}