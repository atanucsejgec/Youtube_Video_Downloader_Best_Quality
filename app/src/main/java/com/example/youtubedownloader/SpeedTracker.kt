package com.example.youtubedownloader

class SpeedTracker {
    private var lastBytes = 0L
    private var lastTime = 0L
    private val speedHistory = mutableListOf<Double>()

    fun update(downloadedBytes: Long): String {
        val now = System.currentTimeMillis()
        if (lastTime == 0L) {
            lastTime = now
            lastBytes = downloadedBytes
            return ""
        }

        val timeDiff = (now - lastTime) / 1000.0
        if (timeDiff < 0.5) return getAverageSpeedText()

        val bytesDiff = downloadedBytes - lastBytes
        val speed = if (timeDiff > 0) bytesDiff / timeDiff else 0.0

        speedHistory.add(speed)
        if (speedHistory.size > 10) speedHistory.removeAt(0)

        lastTime = now
        lastBytes = downloadedBytes

        return getAverageSpeedText()
    }

    private fun getAverageSpeedText(): String {
        if (speedHistory.isEmpty()) return ""
        val avg = speedHistory.average()
        return formatSpeed(avg)
    }

    fun reset() {
        lastBytes = 0L
        lastTime = 0L
        speedHistory.clear()
    }

    companion object {
        fun formatSpeed(bytesPerSec: Double): String = when {
            bytesPerSec <= 0 -> ""
            bytesPerSec < 1024 -> "%.0f B/s".format(bytesPerSec)
            bytesPerSec < 1024 * 1024 -> "%.1f KB/s".format(bytesPerSec / 1024)
            bytesPerSec < 1024 * 1024 * 1024 -> "%.1f MB/s".format(bytesPerSec / (1024 * 1024))
            else -> "%.2f GB/s".format(bytesPerSec / (1024 * 1024 * 1024))
        }

        fun parseSpeedFromLine(line: String): Double {
            val regex = Regex("""([\d.]+)\s*(KiB|MiB|GiB|B)/s""")
            val match = regex.find(line) ?: return 0.0
            val value = match.groupValues[1].toDoubleOrNull() ?: return 0.0
            return when (match.groupValues[2]) {
                "B" -> value
                "KiB" -> value * 1024
                "MiB" -> value * 1024 * 1024
                "GiB" -> value * 1024 * 1024 * 1024
                else -> value
            }
        }
    }
}