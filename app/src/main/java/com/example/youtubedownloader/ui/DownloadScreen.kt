package com.example.youtubedownloader.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.youtubedownloader.*
import java.io.File

/* ═══════════════ Root Screen ═══════════════ */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadScreen(vm: MainViewModel = viewModel()) {

    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val url by vm.url.collectAsStateWithLifecycle()
    val quality by vm.quality.collectAsStateWithLifecycle()
    val dlAsPlaylist by vm.downloadAsPlaylist.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("YouTube Downloader") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            if (uiState is UiState.Initializing) {
                BannerCard("Initializing engine…", showSpinner = true)
            }

            /* ── URL input ── */
            UrlInput(
                url = url,
                onUrlChange = vm::updateUrl,
                enabled = uiState !is UiState.Initializing
            )

            /* ── Fetch button ── */
            Button(
                onClick = { vm.fetchInfo() },
                enabled = url.isNotBlank()
                        && uiState !is UiState.FetchingInfo
                        && uiState !is UiState.Downloading
                        && uiState !is UiState.Initializing,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState is UiState.FetchingInfo) {
                    CircularProgressIndicator(
                        Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (uiState is UiState.FetchingInfo) "Fetching…" else "Fetch Video Info")
            }

            /* ── Video info card ── */
            val details = when (uiState) {
                is UiState.InfoReady -> (uiState as UiState.InfoReady).details
                is UiState.Downloading -> (uiState as UiState.Downloading).details
                is UiState.Completed -> (uiState as UiState.Completed).details
                else -> null
            }
            if (details != null) VideoInfoCard(details)

            /* ── Playlist info ── */
            val playlistInfo = when (uiState) {
                is UiState.InfoReady -> (uiState as UiState.InfoReady).playlistInfo
                else -> null
            }
            if (playlistInfo != null) {
                PlaylistCard(
                    info = playlistInfo,
                    downloadAsPlaylist = dlAsPlaylist,
                    onToggle = vm::setDownloadAsPlaylist
                )
            }

            /* ── Quality selector with sizes ── */
            val formatSizes = when (uiState) {
                is UiState.InfoReady -> (uiState as UiState.InfoReady).formatSizes
                else -> emptyMap()
            }
            if (uiState is UiState.InfoReady || uiState is UiState.Completed) {
                QualityDropdown(quality, formatSizes, vm::selectQuality)
            }

            /* ── Download button ── */
            if (uiState is UiState.InfoReady || uiState is UiState.Completed) {
                val sizeLabel = formatSizes[quality]
                Button(
                    onClick = { vm.startDownload() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Download, null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        buildString {
                            append("Download · ${quality.label}")
                            if (!sizeLabel.isNullOrBlank()) append(" · $sizeLabel")
                            if (dlAsPlaylist && playlistInfo != null) {
                                append(" × ${playlistInfo.count}")
                            }
                        }
                    )
                }
            }

            /* ── Progress ── */
            if (uiState is UiState.Downloading) {
                val st = uiState as UiState.Downloading
                ProgressSection(
                    progress = st.progress,
                    eta = st.eta,
                    line = st.line,
                    currentItem = st.currentItem,
                    totalItems = st.totalItems,
                    onCancel = vm::cancelDownload
                )
            }

            /* ── Completed ── */
            if (uiState is UiState.Completed) {
                val st = uiState as UiState.Completed
                CompletedSection(st.savedLocation, st.fileCount)
            }

            /* ── Error ── */
            if (uiState is UiState.Error) {
                ErrorSection(
                    msg = (uiState as UiState.Error).message,
                    onDismiss = vm::reset,
                    onRetry = vm::initEngine
                )
            }
        }
    }
}

/* ═══════════════ Sub-Composables ═══════════════ */

@Composable
private fun UrlInput(url: String, onUrlChange: (String) -> Unit, enabled: Boolean) {
    val clipboard = LocalClipboardManager.current
    OutlinedTextField(
        value = url,
        onValueChange = onUrlChange,
        label = { Text("YouTube URL") },
        placeholder = { Text("Paste video or playlist URL") },
        singleLine = true,
        enabled = enabled,
        trailingIcon = {
            Row {
                if (url.isNotBlank()) {
                    IconButton(onClick = { onUrlChange("") }) {
                        Icon(Icons.Default.Clear, "Clear")
                    }
                }
                IconButton(onClick = {
                    clipboard.getText()?.text?.let { onUrlChange(it) }
                }) {
                    Icon(Icons.Default.ContentPaste, "Paste")
                }
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun VideoInfoCard(d: VideoDetails) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column {
            d.thumbnail?.let { thumbUrl ->
                AsyncImage(
                    model = thumbUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                )
            }
            Column(
                Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    d.title,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                d.author?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (d.duration > 0) {
                    Text(
                        "Duration: ${formatDuration(d.duration)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistCard(
    info: PlaylistInfo,
    downloadAsPlaylist: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.PlaylistPlay, null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        "Playlist Detected",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        "${info.title} · ${info.count} videos",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (downloadAsPlaylist) "Download all ${info.count} videos"
                    else "Download single video only",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Switch(
                    checked = downloadAsPlaylist,
                    onCheckedChange = onToggle
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QualityDropdown(
    selected: VideoQuality,
    formatSizes: Map<VideoQuality, String>,
    onSelect: (VideoQuality) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        val sizeLabel = formatSizes[selected]
        OutlinedTextField(
            value = if (!sizeLabel.isNullOrBlank()) "${selected.label}  ·  $sizeLabel"
            else selected.label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Quality") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            VideoQuality.entries.forEach { q ->
                val size = formatSizes[q]
                DropdownMenuItem(
                    text = {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(q.label)
                            if (!size.isNullOrBlank()) {
                                Text(
                                    size,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    },
                    onClick = { onSelect(q); expanded = false },
                    leadingIcon = {
                        Icon(
                            if (q.isAudioOnly) Icons.Default.MusicNote
                            else Icons.Default.Videocam,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun ProgressSection(
    progress: Float,
    eta: Long,
    line: String,
    currentItem: Int,
    totalItems: Int,
    onCancel: () -> Unit
) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Downloading…", fontWeight = FontWeight.SemiBold)
                if (totalItems > 1) {
                    Text(
                        "Video $currentItem / $totalItems",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            LinearProgressIndicator(
                progress = { (progress / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
            )

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("${progress.toInt()}%", style = MaterialTheme.typography.bodySmall)
                if (eta > 0) Text(
                    "ETA ${eta}s", style = MaterialTheme.typography.bodySmall
                )
            }

            if (line.isNotBlank()) {
                Text(
                    line,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedButton(onClick = onCancel, Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Cancel, null)
                Spacer(Modifier.width(6.dp))
                Text("Cancel")
            }
        }
    }
}

@Composable
private fun CompletedSection(savedLocation: String, fileCount: Int) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.CheckCircle, null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text("Saved to Gallery!", fontWeight = FontWeight.Bold)
            }

            if (fileCount > 1) {
                Text(
                    "$fileCount files saved",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Text(
                "📁 $savedLocation",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ErrorSection(msg: String, onDismiss: () -> Unit, onRetry: () -> Unit = {}) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(8.dp))
                Text("Error", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
            }

            SelectionContainer {
                Text(
                    msg,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .heightIn(max = 200.dp)
                        .verticalScroll(rememberScrollState())
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onRetry) {
                    Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Retry")
                }
                TextButton(onClick = onDismiss) { Text("Dismiss") }
            }
        }
    }
}

@Composable
private fun BannerCard(text: String, showSpinner: Boolean = false) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (showSpinner) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
            }
            Text(text)
        }
    }
}

/* ═══════════════ Helpers ═══════════════ */

private fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s)
    else "%d:%02d".format(m, s)
}