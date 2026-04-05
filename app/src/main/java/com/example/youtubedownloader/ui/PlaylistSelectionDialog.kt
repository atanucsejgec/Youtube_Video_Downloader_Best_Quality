package com.example.youtubedownloader.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.youtubedownloader.PlaylistEntry
import com.example.youtubedownloader.PlaylistInfo

@Composable
fun PlaylistSelectionDialog(
    playlistInfo: PlaylistInfo,
    entries: List<PlaylistEntry>,
    onDismiss: () -> Unit,
    onDownloadSelected: (List<PlaylistEntry>) -> Unit
) {
    val selectedIds = remember { mutableStateListOf<String>().also { list ->
        entries.forEach { list.add(it.id) } // All selected by default
    }}

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f)
                .border(1.dp, CyanDark.copy(alpha = 0.5f), RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardDark)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Select Videos",
                                color = CyanPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Text(
                                playlistInfo.title,
                                color = TextMuted,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, "Close", tint = TextMuted)
                        }
                    }

                    // Select All / Deselect All
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { selectedIds.clear(); selectedIds.addAll(entries.map { it.id }) },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            border = ButtonDefaults.outlinedButtonBorder.copy(
                                brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                    listOf(CyanDark, CyanDark)
                                )
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanPrimary)
                        ) {
                            Icon(Icons.Default.SelectAll, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Select All", fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = { selectedIds.clear() },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            border = ButtonDefaults.outlinedButtonBorder.copy(
                                brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                    listOf(CardBorder, CardBorder)
                                )
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextMuted)
                        ) {
                            Icon(Icons.Default.Deselect, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Deselect All", fontSize = 12.sp)
                        }
                    }

                    Text(
                        "${selectedIds.size} of ${entries.size} selected",
                        color = CyanMuted,
                        fontSize = 11.sp
                    )
                }

                HorizontalDivider(color = CardBorder)

                // Video list
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    itemsIndexed(entries) { index, entry ->
                        val isSelected = selectedIds.contains(entry.id)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (isSelected) selectedIds.remove(entry.id)
                                    else selectedIds.add(entry.id)
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { checked ->
                                    if (checked) selectedIds.add(entry.id)
                                    else selectedIds.remove(entry.id)
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = CyanPrimary,
                                    uncheckedColor = TextDark,
                                    checkmarkColor = DarkNavy
                                )
                            )
                            Text(
                                "${index + 1}.",
                                color = TextDark,
                                fontSize = 12.sp,
                                modifier = Modifier.width(28.dp)
                            )
                            Text(
                                entry.title,
                                color = if (isSelected) TextWhite else TextMuted,
                                fontSize = 13.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        HorizontalDivider(color = CardBorder.copy(alpha = 0.3f))
                    }
                }

                HorizontalDivider(color = CardBorder)

                // Bottom button
                Button(
                    onClick = {
                        val selected = entries.filter { selectedIds.contains(it.id) }
                        onDownloadSelected(selected)
                    },
                    enabled = selectedIds.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyanPrimary,
                        contentColor = DarkNavy,
                        disabledContainerColor = CyanDark.copy(alpha = 0.3f)
                    )
                ) {
                    Icon(Icons.Default.Download, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Download ${selectedIds.size} Videos",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}