package com.example.youtubedownloader.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.youtubedownloader.SavedFileInfo
import java.io.File

@Composable
fun CompletedCard(
    savedLocation: String,
    fileCount: Int,
    failedCount: Int,
    lastFile: SavedFileInfo?,
    downloadedSizeBytes: Long = 0L,
    onDismiss: (() -> Unit)? = null
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, SuccessGreen.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark.copy(alpha = 0.85f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    color = SuccessSurface, shape = CircleShape, modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.CheckCircle, null, tint = SuccessGreen,
                        modifier = Modifier.padding(4.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Saved to Gallery",
                        color = SuccessGreen, fontWeight = FontWeight.Bold, fontSize = 16.sp
                    )
                    if (downloadedSizeBytes > 0) {
                        Text(
                            "Size: ${formatFileSize(downloadedSizeBytes)}",
                            color = CyanMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                if (onDismiss != null) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Close, "Dismiss",
                            tint = TextDark, modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (fileCount > 1 || failedCount > 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        "✓ $fileCount saved",
                        color = SuccessGreen, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                    )
                    if (failedCount > 0) {
                        Text(
                            "✗ $failedCount failed",
                            color = ErrorRed, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Text(
                "📁 $savedLocation",
                color = TextMuted, fontSize = 12.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )

            if (lastFile != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = { openSavedFile(context, lastFile) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = Brush.horizontalGradient(listOf(CardBorder, CardBorder))
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextLight)
                    ) {
                        Icon(Icons.Outlined.FolderOpen, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Open", fontSize = 13.sp)
                    }
                    OutlinedButton(
                        onClick = { shareSavedFile(context, lastFile) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = Brush.horizontalGradient(listOf(CardBorder, CardBorder))
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextLight)
                    ) {
                        Icon(Icons.Default.Share, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Share", fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

private fun getFileUri(context: Context, fileInfo: SavedFileInfo): Uri {
    if (fileInfo.contentUri != null) return Uri.parse(fileInfo.contentUri)
    if (fileInfo.absolutePath != null)
        return FileProvider.getUriForFile(
            context, "${context.packageName}.provider", File(fileInfo.absolutePath)
        )
    throw IllegalStateException("No URI")
}

fun openSavedFile(context: Context, fileInfo: SavedFileInfo) {
    try {
        val uri = getFileUri(context, fileInfo)
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, fileInfo.mimeType)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }, "Open with"
            )
        )
    } catch (_: Exception) {
        Toast.makeText(context, "No app found to open this file", Toast.LENGTH_SHORT).show()
    }
}

fun shareSavedFile(context: Context, fileInfo: SavedFileInfo) {
    try {
        val uri = getFileUri(context, fileInfo)
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = fileInfo.mimeType
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }, "Share via"
            )
        )
    } catch (_: Exception) {
        Toast.makeText(context, "Failed to share", Toast.LENGTH_SHORT).show()
    }
}