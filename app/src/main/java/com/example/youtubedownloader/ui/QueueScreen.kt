package com.example.youtubedownloader.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.youtubedownloader.MainViewModel
import com.example.youtubedownloader.data.QueueItem
import com.example.youtubedownloader.service.DownloadService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(vm: MainViewModel) {
    val queueItems by vm.queueItems.collectAsStateWithLifecycle()
    val isRunning by vm.isServiceRunning.collectAsStateWithLifecycle()
    val serviceState by vm.serviceState.collectAsStateWithLifecycle()
    var showBatchDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(DarkNavy, DarkBlue, MediumBlue),
                    start = Offset(0f, 0f),
                    end = Offset(0f, Float.POSITIVE_INFINITY)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(16.dp))

            // ── Header ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Queue",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        color = TextWhite,
                        letterSpacing = (-0.5).sp
                    )
                    if (queueItems.isNotEmpty()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "${queueItems.size} item${if (queueItems.size != 1) "s" else ""}",
                            fontSize = 13.sp,
                            color = CyanMuted,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Batch add button
                    Surface(
                        onClick = { showBatchDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        color = CyanSurface,
                        border = BorderStroke(1.dp, CyanDark.copy(alpha = 0.3f))
                    ) {
                        Icon(
                            Icons.Default.PlaylistAdd, "Batch Add",
                            tint = CyanPrimary,
                            modifier = Modifier.padding(10.dp).size(20.dp)
                        )
                    }

                    // Start/Stop button
                    if (queueItems.any { it.status == "pending" }) {
                        Button(
                            onClick = {
                                if (isRunning) vm.cancelAllDownloads()
                                else vm.startQueueDownload()
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isRunning) ErrorRed else CyanPrimary,
                                contentColor = if (isRunning) Color.White else DarkNavy
                            ),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                            elevation = ButtonDefaults.buttonElevation(
                                defaultElevation = 3.dp,
                                pressedElevation = 6.dp
                            )
                        ) {
                            Icon(
                                if (isRunning) Icons.Default.Stop
                                else Icons.Default.PlayArrow,
                                null, Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (isRunning) "Stop" else "Start All",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Stats bar ──
            AnimatedVisibility(
                visible = isRunning && serviceState.activeDownloads.isNotEmpty(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, CyanDark.copy(alpha = 0.3f), RoundedCornerShape(14.dp)),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = CyanSurface
                    )
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatChip(
                            icon = Icons.Default.Download,
                            label = "${serviceState.activeDownloads.size}",
                            sublabel = "Active",
                            color = CyanPrimary
                        )
                        StatChip(
                            icon = Icons.Default.CheckCircle,
                            label = "${serviceState.completedCount}",
                            sublabel = "Done",
                            color = SuccessGreen
                        )
                        StatChip(
                            icon = Icons.Default.Error,
                            label = "${serviceState.failedCount}",
                            sublabel = "Failed",
                            color = ErrorRed
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // ── Queue list ──
            AnimatedContent(
                targetState = queueItems.isEmpty(),
                transitionSpec = {
                    fadeIn(tween(300)) togetherWith fadeOut(tween(200))
                },
                label = "queue_content"
            ) { isEmpty ->
                if (isEmpty) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(bottom = 80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = CyanSurface,
                                modifier = Modifier.size(80.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.Queue, null,
                                    modifier = Modifier
                                        .padding(20.dp)
                                        .size(40.dp),
                                    tint = CyanMuted
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Queue is empty",
                                color = TextLight,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Add videos from Home or Search tab",
                                color = TextDark,
                                fontSize = 13.sp
                            )
                        }
                    }
                } else {
                    Column {
                        // Action chips
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                onClick = { vm.clearCompletedFromQueue() },
                                shape = RoundedCornerShape(10.dp),
                                color = Color.Transparent,
                                border = BorderStroke(1.dp, CardBorder)
                            ) {
                                Text(
                                    "Clear Completed",
                                    color = TextMuted,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                            Surface(
                                onClick = { vm.retryAllFailed() },
                                shape = RoundedCornerShape(10.dp),
                                color = Color.Transparent,
                                border = BorderStroke(1.dp, CardBorder)
                            ) {
                                Text(
                                    "Retry Failed",
                                    color = CyanMuted,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 100.dp)
                        ) {
                            items(queueItems, key = { it.id }) { item ->
                                QueueItemCard(
                                    item = item,
                                    activeProgress = serviceState.activeDownloads[item.id],
                                    onRemove = { vm.removeFromQueue(item) },
                                    onRetry = { vm.retryFailed(item) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Batch URL Dialog ──
    if (showBatchDialog) {
        var batchText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showBatchDialog = false },
            containerColor = CardDark,
            shape = RoundedCornerShape(20.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = CyanSurface,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.PlaylistAdd, null,
                            tint = CyanPrimary,
                            modifier = Modifier.padding(6.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Batch Add URLs",
                        color = TextWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            },
            text = {
                Column {
                    Text(
                        "Paste multiple YouTube URLs (one per line):",
                        fontSize = 13.sp,
                        color = TextMuted,
                        lineHeight = 18.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = batchText,
                        onValueChange = { batchText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        placeholder = {
                            Text(
                                "https://youtube.com/watch?v=...\nhttps://youtu.be/...",
                                color = TextDark,
                                fontSize = 13.sp
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanPrimary,
                            unfocusedBorderColor = CardBorder,
                            cursorColor = CyanPrimary,
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextLight,
                            focusedContainerColor = CardLight.copy(alpha = 0.3f),
                            unfocusedContainerColor = CardLight.copy(alpha = 0.2f)
                        ),
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.addBatchToQueue(batchText)
                        showBatchDialog = false
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyanPrimary,
                        contentColor = DarkNavy
                    )
                ) {
                    Text("Add All", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }
}

@Composable
private fun StatChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    sublabel: String,
    color: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(14.dp), tint = color)
        Spacer(Modifier.width(6.dp))
        Column {
            Text(
                label,
                color = color,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Text(
                sublabel,
                color = color.copy(alpha = 0.7f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun QueueItemCard(
    item: QueueItem,
    activeProgress: DownloadService.DownloadProgress?,
    onRemove: () -> Unit,
    onRetry: () -> Unit
) {
    val statusConfig = remember(item.status) {
        when (item.status) {
            "pending" -> Triple(Icons.Default.Schedule, TextMuted, "Pending")
            "downloading" -> Triple(Icons.Default.Download, CyanPrimary, "Downloading")
            "completed" -> Triple(Icons.Default.CheckCircle, SuccessGreen, "Completed")
            "failed" -> Triple(Icons.Default.Error, ErrorRed, "Failed")
            "paused" -> Triple(Icons.Default.Pause, Color(0xFFFFB74D), "Paused")
            else -> Triple(Icons.Default.HelpOutline, TextMuted, item.status)
        }
    }
    val (statusIcon, statusColor, statusText) = statusConfig

    val borderColor = when (item.status) {
        "downloading" -> CyanDark.copy(alpha = 0.5f)
        "completed" -> SuccessGreen.copy(alpha = 0.2f)
        "failed" -> ErrorRed.copy(alpha = 0.2f)
        else -> CardBorder
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = CardDark.copy(alpha = 0.85f)
        )
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Thumbnail
                if (item.thumbnail != null) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(CardLight)
                    ) {
                        AsyncImage(
                            model = item.thumbnail,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        // Status overlay
                        Surface(
                            color = statusColor.copy(alpha = 0.9f),
                            shape = CircleShape,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(3.dp)
                                .size(18.dp)
                        ) {
                            Icon(
                                statusIcon, null,
                                modifier = Modifier.padding(3.dp),
                                tint = Color.White
                            )
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                }

                // Info
                Column(Modifier.weight(1f)) {
                    Text(
                        item.title ?: item.url,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = TextWhite,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 19.sp
                    )
                    Spacer(Modifier.height(5.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = statusColor.copy(alpha = 0.12f)
                        ) {
                            Row(
                                Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    statusIcon, null,
                                    Modifier.size(12.dp),
                                    tint = statusColor
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    statusText,
                                    fontSize = 11.sp,
                                    color = statusColor,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        if (activeProgress != null && activeProgress.speedText.isNotBlank()) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                activeProgress.speedText,
                                fontSize = 11.sp,
                                color = CyanPrimary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Error message
                    if (item.status == "failed" && !item.errorMessage.isNullOrBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            item.errorMessage,
                            fontSize = 11.sp,
                            color = ErrorRed.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Actions
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (item.status == "failed") {
                        IconButton(
                            onClick = onRetry,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                Icons.Default.Refresh, "Retry",
                                Modifier.size(18.dp),
                                tint = CyanPrimary
                            )
                        }
                    }
                    IconButton(
                        onClick = onRemove,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            Icons.Default.Close, "Remove",
                            Modifier.size(18.dp),
                            tint = TextDark
                        )
                    }
                }
            }

            // Progress bar
            if (item.status == "downloading" && activeProgress != null) {
                val animatedProgress by animateFloatAsState(
                    targetValue = (activeProgress.progress / 100f).coerceIn(0f, 1f),
                    animationSpec = tween(300),
                    label = "queue_progress"
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(ProgressTrack)
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(animatedProgress)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(CyanDark, CyanPrimary, CyanLight)
                                )
                            )
                    )
                }
            }
        }
    }
}