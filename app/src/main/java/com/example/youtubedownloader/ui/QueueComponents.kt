package com.example.youtubedownloader.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.youtubedownloader.QueueItem
import com.example.youtubedownloader.QueueItemStatus
import com.example.youtubedownloader.SavedFileInfo
import java.io.File

@Composable
fun DownloadQueueSection(
    queue: List<QueueItem>,
    onRemove: (String) -> Unit,
    onClearCompleted: () -> Unit
) {
    if (queue.isEmpty()) return

    val hasCompleted = queue.any {
        it.status == QueueItemStatus.COMPLETED || it.status == QueueItemStatus.FAILED
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CyanDark.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark.copy(alpha = 0.85f))
    ) {
        Column {
            // ── Header ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = CyanSurface,
                        shape = CircleShape,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Queue, null,
                            tint = CyanPrimary,
                            modifier = Modifier.padding(5.dp)
                        )
                    }
                    Text(
                        "Download List",
                        color = CyanLight,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Surface(
                        color = CyanDark.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            "${queue.size}",
                            color = CyanPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
                if (hasCompleted) {
                    TextButton(onClick = onClearCompleted) {
                        Text("Clear done", color = TextMuted, fontSize = 11.sp)
                    }
                }
            }

            HorizontalDivider(color = CardBorder)

            // ── Items ──
            queue.forEach { item ->
                QueueItemRow(
                    item = item,
                    onRemove = { onRemove(item.id) }
                )
                HorizontalDivider(color = CardBorder.copy(alpha = 0.4f))
            }
        }
    }
}

@Composable
fun QueueItemRow(
    item: QueueItem,
    onRemove: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // ── Main row: status icon + info + remove ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Status indicator
            StatusIcon(status = item.status, progress = item.progress)

            // Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.title.take(60),
                    color = when (item.status) {
                        QueueItemStatus.COMPLETED -> SuccessGreen
                        QueueItemStatus.FAILED    -> ErrorRed
                        QueueItemStatus.DOWNLOADING -> CyanLight
                        else                      -> TextLight
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp
                )

                Spacer(Modifier.height(2.dp))

                // Sub info row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Quality badge
                    Surface(
                        color = CardLight,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            item.quality.label.take(18),
                            color = TextDark,
                            fontSize = 9.sp,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }

                    when (item.status) {
                        QueueItemStatus.DOWNLOADING -> {
                            if (item.downloadSpeedBps > 0) {
                                Text(
                                    "• ${formatSpeed(item.downloadSpeedBps)}",
                                    color = CyanMuted,
                                    fontSize = 10.sp
                                )
                            }
                        }
                        QueueItemStatus.COMPLETED -> {
                            if (item.fileSizeBytes > 0) {
                                Text(
                                    "• ${formatFileSize(item.fileSizeBytes)}",
                                    color = SuccessGreen.copy(alpha = 0.8f),
                                    fontSize = 10.sp
                                )
                            }
                        }
                        QueueItemStatus.FAILED -> {
                            item.errorMsg?.let { err ->
                                Text(
                                    "• ${err.take(28)}",
                                    color = ErrorRed.copy(alpha = 0.8f),
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        QueueItemStatus.WAITING -> {
                            Text(
                                "• Waiting…",
                                color = TextDark,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }

            // Remove button (not shown while downloading)
            if (item.status != QueueItemStatus.DOWNLOADING) {
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        Icons.Default.Close, "Remove",
                        tint = TextDark,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }

        // ── Progress bar while downloading ──
        if (item.status == QueueItemStatus.DOWNLOADING) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(ProgressTrack)
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth((item.progress / 100f).coerceIn(0f, 1f))
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            Brush.horizontalGradient(listOf(CyanDark, CyanPrimary))
                        )
                )
            }
        }

        // ── Open & Share buttons — only for completed items ──
        AnimatedVisibility(
            visible = item.status == QueueItemStatus.COMPLETED
                    && item.savedFileInfo != null,
            enter = fadeIn(tween(300)) + expandVertically(tween(300)),
            exit = fadeOut(tween(200)) + shrinkVertically(tween(200))
        ) {
            item.savedFileInfo?.let { fileInfo ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 42.dp), // align with text (after status icon)
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Open button
                    OutlinedButton(
                        onClick = { openFile(context, fileInfo) },
                        modifier = Modifier
                            .weight(1f)
                            .height(32.dp),
                        shape = RoundedCornerShape(8.dp),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = Brush.horizontalGradient(
                                listOf(
                                    CardBorder,
                                    CardBorder
                                )
                            )
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = TextLight
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            Icons.Outlined.FolderOpen, null,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Open", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }

                    // Share button
                    OutlinedButton(
                        onClick = { shareFile(context, fileInfo) },
                        modifier = Modifier
                            .weight(1f)
                            .height(32.dp),
                        shape = RoundedCornerShape(8.dp),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = Brush.horizontalGradient(
                                listOf(
                                    CardBorder,
                                    CardBorder
                                )
                            )
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = TextLight
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            Icons.Default.Share, null,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Share", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

/* ─────────────────────── STATUS ICON ─────────────────────── */
@Composable
private fun StatusIcon(status: QueueItemStatus, progress: Float) {
    Box(
        modifier = Modifier.size(36.dp),
        contentAlignment = Alignment.Center
    ) {
        when (status) {
            QueueItemStatus.WAITING -> {
                Surface(
                    color = CardLight,
                    shape = CircleShape,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        Icons.Outlined.HourglassEmpty, null,
                        tint = TextMuted,
                        modifier = Modifier.padding(7.dp)
                    )
                }
            }

            QueueItemStatus.DOWNLOADING -> {
                CircularProgressIndicator(
                    progress = { (progress / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.size(34.dp),
                    color = CyanPrimary,
                    trackColor = CardLight,
                    strokeWidth = 3.dp
                )
                Text(
                    "${progress.toInt()}%",
                    color = CyanPrimary,
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            QueueItemStatus.COMPLETED -> {
                Surface(
                    color = SuccessSurface,
                    shape = CircleShape,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        Icons.Default.CheckCircle, null,
                        tint = SuccessGreen,
                        modifier = Modifier.padding(7.dp)
                    )
                }
            }

            QueueItemStatus.FAILED -> {
                Surface(
                    color = ErrorSurface,
                    shape = CircleShape,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        Icons.Default.Error, null,
                        tint = ErrorRed,
                        modifier = Modifier.padding(7.dp)
                    )
                }
            }
        }
    }
}

/* ─────────────────────── FILE ACTIONS ─────────────────────── */
private fun getFileUri(context: Context, fileInfo: SavedFileInfo): Uri {
    if (fileInfo.contentUri != null) return Uri.parse(fileInfo.contentUri)
    if (fileInfo.absolutePath != null)
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            File(fileInfo.absolutePath)
        )
    throw IllegalStateException("No URI available")
}

private fun openFile(context: Context, fileInfo: SavedFileInfo) {
    try {
        val uri = getFileUri(context, fileInfo)
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, fileInfo.mimeType)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
                "Open with"
            )
        )
    } catch (_: Exception) {
        Toast.makeText(context, "No app found to open this file", Toast.LENGTH_SHORT).show()
    }
}

private fun shareFile(context: Context, fileInfo: SavedFileInfo) {
    try {
        val uri = getFileUri(context, fileInfo)
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = fileInfo.mimeType
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
                "Share via"
            )
        )
    } catch (_: Exception) {
        Toast.makeText(context, "Failed to share", Toast.LENGTH_SHORT).show()
    }
}

/* ─────────────────────── FORMAT HELPERS ─────────────────────── */
fun formatSpeed(bps: Double): String = when {
    bps <= 0 -> ""
    bps < 1024 * 1024 -> "%.1f KB/s".format(bps / 1024)
    else -> "%.2f MB/s".format(bps / (1024 * 1024))
}

fun formatFileSize(bytes: Long): String = when {
    bytes <= 0 -> ""
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    else -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
}