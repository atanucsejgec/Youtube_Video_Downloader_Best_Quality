package com.example.youtubedownloader.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

@Composable
fun DownloadProgressCard(
    progress: Float,
    eta: Long,
    line: String,
    currentItem: Int,
    totalItems: Int,
    currentVideoTitle: String,
    downloadSpeed: Double,
    videoTitle: String,
    thumbnail: String?,
    onCancel: () -> Unit
) {
    val animatedProgress by animateFloatAsState(
        targetValue = (progress / 100f).coerceIn(0f, 1f),
        animationSpec = tween(300),
        label = "progress"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CyanDark.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark.copy(alpha = 0.85f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Title row with thumbnail
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Mini thumbnail
                if (thumbnail != null) {
                    Box(
                        modifier = Modifier
                            .width(56.dp)
                            .height(36.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(CardLight)
                    ) {
                        AsyncImage(
                            model = thumbnail,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = CyanPrimary,
                            strokeWidth = 2.dp
                        )
                        Text(
                            "Downloading",
                            color = CyanLight,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        if (totalItems > 1) {
                            Surface(
                                color = CyanDark.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    "$currentItem/$totalItems",
                                    color = CyanPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        if (totalItems > 1 && currentVideoTitle.isNotBlank())
                            currentVideoTitle else videoTitle,
                        color = TextMuted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(ProgressTrack)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animatedProgress)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            Brush.horizontalGradient(listOf(CyanDark, CyanPrimary, CyanLight))
                        )
                )
            }

            // Stats: percent + speed + ETA
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${progress.toInt()}%",
                    color = CyanPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold
                )
                if (downloadSpeed > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.Speed, null,
                            tint = CyanMuted, modifier = Modifier.size(14.dp)
                        )
                        Text(
                            formatSpeed(downloadSpeed),
                            color = CyanMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                if (eta > 0) {
                    Text(
                        "ETA ${formatEta(eta)}",
                        color = TextMuted, fontSize = 12.sp
                    )
                }
            }

            // Overall progress bar for playlists
            if (totalItems > 1) {
                val overallPercent = ((currentItem - 1) * 100f + progress) / totalItems
                val animatedOverall by animateFloatAsState(
                    targetValue = (overallPercent / 100f).coerceIn(0f, 1f),
                    animationSpec = tween(300), label = "overall"
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Overall", color = ProgressTeal, fontSize = 11.sp)
                        Text("${overallPercent.toInt()}%", color = ProgressTeal, fontSize = 11.sp)
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(ProgressTrack)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(animatedOverall)
                                .clip(RoundedCornerShape(2.dp))
                                .background(ProgressTeal)
                        )
                    }
                }
            }

            // Status line from yt-dlp
            if (line.isNotBlank()) {
                Text(
                    line, color = TextDark, fontSize = 10.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }

            // Cancel
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = Brush.horizontalGradient(
                        listOf(ErrorRed.copy(alpha = 0.5f), ErrorRed.copy(alpha = 0.3f))
                    )
                ),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed)
            ) {
                Icon(Icons.Default.Cancel, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Cancel Download", fontSize = 13.sp)
            }
        }
    }
}

private fun formatEta(seconds: Long): String = when {
    seconds < 60 -> "${seconds}s"
    seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
    else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
}