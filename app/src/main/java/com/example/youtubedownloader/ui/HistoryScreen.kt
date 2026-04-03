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
import com.example.youtubedownloader.data.DownloadHistoryItem
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HistoryScreen(vm: MainViewModel) {
    val history by vm.historyItems.collectAsStateWithLifecycle()
    var showClearDialog by remember { mutableStateOf(false) }

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
                        "History",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        color = TextWhite,
                        letterSpacing = (-0.5).sp
                    )
                    if (history.isNotEmpty()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "${history.size} download${if (history.size != 1) "s" else ""}",
                            fontSize = 13.sp,
                            color = CyanMuted,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.3.sp
                        )
                    }
                }
                if (history.isNotEmpty()) {
                    Surface(
                        onClick = { showClearDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        color = ErrorRed.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.25f))
                    ) {
                        Row(
                            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.DeleteSweep, null,
                                tint = ErrorRed.copy(alpha = 0.85f),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Clear",
                                color = ErrorRed.copy(alpha = 0.85f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Content ──
            AnimatedContent(
                targetState = history.isEmpty(),
                transitionSpec = {
                    fadeIn(tween(300)) togetherWith fadeOut(tween(200))
                },
                label = "history_content"
            ) { isEmpty ->
                if (isEmpty) {
                    // Empty state
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
                                    Icons.Outlined.History, null,
                                    modifier = Modifier
                                        .padding(20.dp)
                                        .size(40.dp),
                                    tint = CyanMuted
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "No downloads yet",
                                color = TextLight,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = (-0.2).sp
                            )
                            Text(
                                "Downloaded videos will appear here",
                                color = TextDark,
                                fontSize = 13.sp,
                                letterSpacing = 0.1.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 100.dp)
                    ) {
                        items(history, key = { it.id }) { item ->
                            HistoryItemCard(
                                item = item,
                                onDelete = { vm.deleteHistoryItem(item) },
                                onRedownload = { vm.updateUrl(item.url) }
                            )
                        }
                    }
                }
            }
        }
    }

    // ── Clear All Dialog ──
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor = CardDark,
            shape = RoundedCornerShape(20.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = ErrorSurface,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.DeleteSweep, null,
                            tint = ErrorRed,
                            modifier = Modifier.padding(6.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Clear History?",
                        color = TextWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            },
            text = {
                Text(
                    "This will remove all download history. Downloaded files won't be deleted.",
                    color = TextMuted,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.clearAllHistory()
                        showClearDialog = false
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ErrorRed,
                        contentColor = Color.White
                    )
                ) {
                    Text("Clear All", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }
}

@Composable
private fun HistoryItemCard(
    item: DownloadHistoryItem,
    onDelete: () -> Unit,
    onRedownload: () -> Unit
) {
    val dateFormat = remember {
        SimpleDateFormat("dd MMM yyyy · HH:mm", Locale.getDefault())
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CardBorder, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = CardDark.copy(alpha = 0.85f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail
            item.thumbnail?.let {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(CardLight)
                ) {
                    AsyncImage(
                        model = it,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(Modifier.width(14.dp))
            }

            // Info
            Column(Modifier.weight(1f)) {
                Text(
                    item.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = TextWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp
                )
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = CyanSurface
                    ) {
                        Text(
                            item.quality,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyanPrimary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        item.fileSizeText,
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    dateFormat.format(Date(item.downloadedAt)),
                    fontSize = 11.sp,
                    color = TextDark,
                    letterSpacing = 0.2.sp
                )
            }

            Spacer(Modifier.width(8.dp))

            // Actions
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(
                    onClick = onRedownload,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        Icons.Default.Download, "Re-download",
                        Modifier.size(18.dp),
                        tint = CyanPrimary
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        Icons.Default.Delete, "Delete",
                        Modifier.size(18.dp),
                        tint = ErrorRed.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}