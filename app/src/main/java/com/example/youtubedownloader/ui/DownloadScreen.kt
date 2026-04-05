package com.example.youtubedownloader.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.youtubedownloader.*

// ─────── COLORS ───────
val DarkNavy = Color(0xFF050D1A)
val DarkBlue = Color(0xFF0A1628)
val MediumBlue = Color(0xFF0D1F35)
val LightNavy = Color(0xFF102040)
val CyanPrimary = Color(0xFF00D4FF)
val CyanLight = Color(0xFF80E8FF)
val CyanMuted = Color(0xFF4DB8CC)
val CyanDark = Color(0xFF0099BB)
val CyanSurface = Color(0xFF00D4FF15)
val TextWhite = Color(0xFFF0F8FF)
val TextLight = Color(0xFFB8D4E8)
val TextMuted = Color(0xFF6B8FA8)
val TextDark = Color(0xFF3A5A72)
val CardDark = Color(0xFF0D1E30)
val CardLight = Color(0xFF152535)
val CardBorder = Color(0xFF1E3045)
val ProgressTrack = Color(0xFF1A2E42)
val ProgressTeal = Color(0xFF00B4A0)
val SuccessGreen = Color(0xFF00C87A)
val SuccessSurface = Color(0xFF00C87A15)
val ErrorRed = Color(0xFFFF4444)
val ErrorSurface = Color(0xFFFF444415)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadScreen(vm: MainViewModel = viewModel()) {
    // ── Collect all states ──
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val browseState by vm.browseState.collectAsStateWithLifecycle()
    val downloadState by vm.downloadState.collectAsStateWithLifecycle()
    val url by vm.url.collectAsStateWithLifecycle()
    val quality by vm.quality.collectAsStateWithLifecycle()
    val dlAsPlaylist by vm.downloadAsPlaylist.collectAsStateWithLifecycle()
    val dlLocation by vm.downloadLocation.collectAsStateWithLifecycle()
    val showSettings by vm.showSettings.collectAsStateWithLifecycle()
    val hasCookies by vm.hasCookies.collectAsStateWithLifecycle()
    val showLogin by vm.showLogin.collectAsStateWithLifecycle()
    val clipboardUrl by vm.clipboardUrl.collectAsStateWithLifecycle()
    val showPlaylistDialog by vm.showPlaylistDialog.collectAsStateWithLifecycle()
    val searchResultsVisible by vm.searchResultsVisible.collectAsStateWithLifecycle()
    val downloadQueue by vm.downloadQueue.collectAsStateWithLifecycle()

    val keyboardController = LocalSoftwareKeyboardController.current
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    // ── Notification permission ──
    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val shared = MainActivity.consumeSharedUrl()
        if (shared != null) vm.handleSharedUrl(shared)
        // Clipboard check on open
        vm.checkClipboard(clipboard.getText()?.text)
    }

    val sharedUrlState by MainActivity.sharedUrl.collectAsStateWithLifecycle()
    LaunchedEffect(sharedUrlState) {
        val shared = MainActivity.consumeSharedUrl()
        if (shared != null) vm.handleSharedUrl(shared)
    }

    // ── Clipboard dialog ──
    if (clipboardUrl != null) {
        ClipboardUrlDialog(
            url = clipboardUrl!!,
            onAdd = { vm.acceptClipboardUrl(clipboardUrl!!) },
            onDismiss = { vm.dismissClipboardDialog() }
        )
    }

    // ── Playlist selection dialog ──
    if (showPlaylistDialog) {
        val infoState = browseState as? BrowseState.InfoReady
        if (infoState?.playlistInfo != null) {
            PlaylistSelectionDialog(
                playlistInfo = infoState.playlistInfo,
                entries = infoState.playlistEntries,
                onDismiss = { vm.hidePlaylistDialog() },
                onDownloadSelected = { selected -> vm.startDownloadWithSelection(selected) }
            )
        }
    }

    // ── Login screen ──
    if (showLogin) {
        YouTubeLoginSheet(
            onDismiss = { vm.hideLoginScreen() },
            onLoggedIn = { vm.onLoginComplete() }
        )
        return
    }

    // ── Main UI ──
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(DarkNavy, DarkBlue, MediumBlue, LightNavy),
                    start = Offset(0f, 0f),
                    end = Offset(0f, Float.POSITIVE_INFINITY)
                )
            )
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 48.dp, start = 20.dp, end = 12.dp, bottom = 8.dp)
                ) {
                    Text(
                        "YT Downloader",
                        style = MaterialTheme.typography.titleLarge,
                        color = CyanPrimary,
                        modifier = Modifier.align(Alignment.CenterStart)
                    )
                    IconButton(
                        onClick = { vm.toggleSettings() },
                        modifier = Modifier.align(Alignment.CenterEnd)
                    ) {
                        Icon(
                            if (showSettings) Icons.Filled.Close else Icons.Outlined.Settings,
                            "Settings", tint = TextLight
                        )
                    }
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(Modifier.height(4.dp))

                /* ── Settings ── */
                AnimatedVisibility(
                    visible = showSettings,
                    enter = fadeIn() + slideInVertically(),
                    exit = fadeOut() + slideOutVertically()
                ) {
                    val storage by vm.storageInfo.collectAsStateWithLifecycle()
                    val clearing by vm.isClearing.collectAsStateWithLifecycle()
                    SettingsPanel(
                        dlLocation, vm::setDownloadLocation,
                        storage, clearing, vm::clearAllCache,
                        hasCookies,
                        onLogin = { vm.showLoginScreen() },
                        onLogout = { vm.clearLoginCookies() }
                    )
                }

                /* ── Engine Initializing ── */
                if (uiState is UiState.Initializing) {
                    GlowCard {
                        Row(
                            Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                Modifier.size(22.dp), color = CyanPrimary, strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(14.dp))
                            Text("Starting The App…", color = CyanLight,
                                style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                /* ── URL / Search Input ── */
                SearchUrlInputField(
                    url = url,
                    onUrlChange = vm::updateUrl,
                    enabled = uiState !is UiState.Initializing,
                    onSearch = {
                        keyboardController?.hide()
                        if (url.isNotBlank()) {
                            if (SearchRepository.isYouTubeUrl(url)) vm.fetchInfo()
                            else vm.performSearch(url)
                        }
                    },
                    onClear = { vm.updateUrl("") }
                )

                /* ── Search / Add Button ── */
                val isBrowseBusy = browseState is BrowseState.FetchingInfo ||
                        browseState is BrowseState.Searching

                CyanButton(
                    text = when {
                        browseState is BrowseState.FetchingInfo -> "Searching…"
                        browseState is BrowseState.Searching -> "Searching…"
                        else -> "Search Video"
                    },
                    icon = Icons.Default.Search,
                    loading = isBrowseBusy,
                    enabled = url.isNotBlank() && !isBrowseBusy
                            && uiState !is UiState.Initializing,
                    filled = false,
                    onClick = {
                        keyboardController?.hide()
                        if (url.isNotBlank()) {
                            if (SearchRepository.isYouTubeUrl(url)) vm.fetchInfo()
                            else vm.performSearch(url)
                        }
                    }
                )

                /* ── Search Results ── */
                val searchResults =
                    (browseState as? BrowseState.SearchResults)?.results ?: emptyList()
                SearchResultsList(
                    results = searchResults,
                    isVisible = searchResultsVisible,
                    onAddToQueue = { result ->
                        // Fetch full info for quality selection, then show info panel
                        vm.addSearchResultToQueue(result)
                    }
                )

                /* ── Video Info Panel ── */
                AnimatedVisibility(
                    visible = browseState is BrowseState.InfoReady,
                    enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 3 },
                    exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { it / 3 }
                ) {
                    val info = browseState as? BrowseState.InfoReady
                    if (info != null) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            // Video card
                            VideoCard(info.details)

                            // Playlist card
                            if (info.playlistInfo != null) {
                                PlaylistCard(
                                    info = info.playlistInfo,
                                    downloadAsPlaylist = dlAsPlaylist,
                                    onToggle = vm::setDownloadAsPlaylist,
                                    onSelectVideos = { vm.showPlaylistDialog() }
                                )
                            }

                            // Quality selector
                            QualitySelector(quality, info.formatSizes, vm::selectQuality)

                            // Location chip
                            LocationChip(dlLocation)

                            // Download / Add to Queue button
                            val isActivelyDownloading =
                                downloadState is DownloadState.Downloading ||
                                        downloadQueue.any { it.status == QueueItemStatus.WAITING }

                            val sizeLabel = info.formatSizes[quality]
                            CyanButton(
                                text = if (isActivelyDownloading) "ADD TO QUEUE" else "DOWNLOAD",
                                subtitle = buildString {
                                    append(quality.label)
                                    if (!sizeLabel.isNullOrBlank()) append(" · $sizeLabel")
                                    if (dlAsPlaylist && info.playlistInfo != null)
                                        append(" · ${info.playlistInfo.count} videos")
                                },
                                icon = if (isActivelyDownloading)
                                    Icons.Default.AddCircle else Icons.Default.Download,
                                filled = true,
                                onClick = { vm.startDownload() }
                            )
                        }
                    }
                }

                /* ── Browse Error ── */
                if (browseState is BrowseState.Error) {
                    ErrorCard(
                        msg = (browseState as BrowseState.Error).message,
                        onDismiss = { vm.reset() },
                        onRetry = { vm.fetchInfo() }
                    )
                }

                /* ── Engine Error ── */
                if (uiState is UiState.Error) {
                    ErrorCard(
                        msg = (uiState as UiState.Error).message,
                        onDismiss = { vm.reset() },
                        onRetry = { vm.initEngine() }
                    )
                }

                /* ══════════════════════════════════════
                   DOWNLOAD SECTION (always at bottom)
                   This shows independently of browse state
                   ══════════════════════════════════════ */

                /* ── Active Download Progress ── */
                AnimatedVisibility(
                    visible = downloadState is DownloadState.Downloading,
                    enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 2 },
                    exit = fadeOut(tween(200))
                ) {
                    val dl = downloadState as? DownloadState.Downloading
                    if (dl != null) {
                        DownloadProgressCard(
                            progress = dl.progress,
                            eta = dl.eta,
                            line = dl.line,
                            currentItem = dl.currentItem,
                            totalItems = dl.totalItems,
                            currentVideoTitle = dl.currentVideoTitle,
                            downloadSpeed = dl.downloadSpeed,
                            videoTitle = dl.title,
                            thumbnail = dl.thumbnail,
                            onCancel = { vm.cancelDownload() }
                        )
                    }
                }

                /* ── Completed Card ── */
                AnimatedVisibility(
                    visible = downloadState is DownloadState.Completed,
                    enter = fadeIn(tween(300)),
                    exit = fadeOut(tween(200))
                ) {
                    val done = downloadState as? DownloadState.Completed
                    if (done != null) {
                        CompletedCard(
                            savedLocation = done.savedLocation,
                            fileCount = done.fileCount,
                            failedCount = done.failedCount,
                            lastFile = done.lastFile,
                            downloadedSizeBytes = done.downloadedSizeBytes,
                            onDismiss = { vm.dismissDownloadResult() }
                        )
                    }
                }

                /* ── Failed Card ── */
                AnimatedVisibility(
                    visible = downloadState is DownloadState.Failed,
                    enter = fadeIn(tween(300)),
                    exit = fadeOut(tween(200))
                ) {
                    val failed = downloadState as? DownloadState.Failed
                    if (failed != null) {
                        ErrorCard(
                            msg = "Download failed: ${failed.message}",
                            onDismiss = { vm.dismissDownloadResult() },
                            onRetry = { vm.dismissDownloadResult() }
                        )
                    }
                }

                /* ── Download Queue ── */
                if (downloadQueue.isNotEmpty()) {
                    DownloadQueueSection(
                        queue = downloadQueue,
                        onRemove = vm::removeFromQueue,
                        onClearCompleted = vm::clearCompletedFromQueue
                    )
                }

                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

/* ─────────────────────── CLIPBOARD DIALOG ─────────────────────── */
@Composable
private fun ClipboardUrlDialog(
    url: String,
    onAdd: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardDark,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                "YouTube URL Detected",
                color = CyanPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Found a YouTube URL in your clipboard:", color = TextLight, fontSize = 13.sp)
                Surface(color = CardLight, shape = RoundedCornerShape(8.dp)) {
                    Text(
                        url, color = CyanMuted, fontSize = 11.sp,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(10.dp)
                    )
                }
                Text("Would you like to Search it?", color = TextMuted, fontSize = 12.sp)
            }
        },
        confirmButton = {
            Button(
                onClick = onAdd,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyanPrimary, contentColor = DarkNavy
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Search, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Search", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Dismiss", color = TextMuted)
            }
        }
    )
}

/* ─────────────────────── GLOW CARD ─────────────────────── */
@Composable
fun GlowCard(
    borderColor: Color = CardBorder,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark.copy(alpha = 0.85f))
    ) { content() }
}

/* ─────────────────────── SEARCH / URL INPUT ─────────────────────── */
@Composable
private fun SearchUrlInputField(
    url: String,
    onUrlChange: (String) -> Unit,
    enabled: Boolean,
    onSearch: () -> Unit,
    onClear: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    GlowCard {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = url,
                onValueChange = onUrlChange,
                placeholder = {
                    Text(
                        "Paste URL or Search YouTube…",
                        color = TextDark, fontSize = 13.sp
                    )
                },
                singleLine = true,
                enabled = enabled,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { if (url.isNotBlank()) onSearch() }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    cursorColor = CyanPrimary,
                    focusedTextColor = TextWhite,
                    unfocusedTextColor = TextLight,
                ),
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = TextWhite, fontSize = 14.sp
                )
            )
            if (url.isNotBlank()) {
                IconButton(onClick = onClear, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Close, "Clear", tint = TextMuted,
                        modifier = Modifier.size(20.dp))
                }
            }
            IconButton(
                onClick = { clipboard.getText()?.text?.let { onUrlChange(it) } },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Default.ContentPaste, "Paste", tint = TextMuted,
                    modifier = Modifier.size(20.dp))
            }
        }
    }
}

/* ─────────────────────── CYAN BUTTON ─────────────────────── */
@Composable
fun CyanButton(
    text: String,
    icon: ImageVector,
    filled: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
    loading: Boolean = false,
    subtitle: String? = null
) {
    if (filled) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(if (subtitle != null) 64.dp else 52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = CyanPrimary, contentColor = DarkNavy,
                disabledContainerColor = CyanDark.copy(alpha = 0.3f),
                disabledContentColor = TextDark
            ),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 4.dp, pressedElevation = 8.dp
            )
        ) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(20.dp), color = DarkNavy, strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
            } else {
                Icon(icon, null, Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text, fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = 1.sp)
                if (subtitle != null) {
                    Text(
                        subtitle, fontSize = 11.sp, fontWeight = FontWeight.Medium,
                        color = DarkNavy.copy(alpha = 0.7f)
                    )
                }
            }
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(14.dp),
            border = ButtonDefaults.outlinedButtonBorder.copy(
                brush = Brush.horizontalGradient(listOf(CyanDark, CyanMuted))
            ),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = CyanPrimary, disabledContentColor = TextDark
            )
        ) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(20.dp), color = CyanPrimary, strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
            } else {
                Icon(icon, null, Modifier.size(20.dp), tint = CyanPrimary)
                Spacer(Modifier.width(10.dp))
            }
            Text(text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = CyanPrimary)
        }
    }
}

/* ─────────────────────── VIDEO CARD ─────────────────────── */
@Composable
private fun VideoCard(d: VideoDetails) {
    GlowCard {
        Column {
            Box(modifier = Modifier.fillMaxWidth().height(210.dp)) {
                d.thumbnail?.let { url ->
                    AsyncImage(
                        model = url, contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    )
                }
                if (d.duration > 0) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.75f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp)
                    ) {
                        Text(
                            formatDuration(d.duration), color = Color.White,
                            fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    d.title, color = TextWhite, fontWeight = FontWeight.Bold,
                    fontSize = 16.sp, maxLines = 2,
                    overflow = TextOverflow.Ellipsis, lineHeight = 22.sp
                )
                d.author?.let { author ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.AccountCircle, null,
                            tint = CyanMuted, modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(author, color = TextMuted, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

/* ─────────────────────── PLAYLIST CARD ─────────────────────── */
@Composable
private fun PlaylistCard(
    info: PlaylistInfo,
    downloadAsPlaylist: Boolean,
    onToggle: (Boolean) -> Unit,
    onSelectVideos: () -> Unit
) {
    GlowCard(borderColor = CyanDark.copy(alpha = 0.4f)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = CyanSurface, shape = CircleShape, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.PlaylistPlay, null, tint = CyanPrimary,
                        modifier = Modifier.padding(6.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Playlist Detected", color = CyanLight,
                        fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        "${info.title} · ${info.count} videos",
                        color = TextMuted, fontSize = 12.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
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
                    color = TextLight, fontSize = 13.sp
                )
                Switch(
                    checked = downloadAsPlaylist,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = CyanPrimary,
                        checkedTrackColor = CyanDark.copy(alpha = 0.4f),
                        uncheckedThumbColor = TextMuted,
                        uncheckedTrackColor = CardBorder
                    )
                )
            }
            if (downloadAsPlaylist) {
                OutlinedButton(
                    onClick = onSelectVideos,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = Brush.horizontalGradient(listOf(CyanDark, CyanDark))
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanPrimary),
                    contentPadding = PaddingValues(vertical = 10.dp)
                ) {
                    Icon(Icons.Default.Checklist, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Select Specific Videos", fontSize = 13.sp)
                }
                Text(
                    "ℹ Videos download one-by-one to save storage",
                    color = TextDark, fontSize = 11.sp
                )
            }
        }
    }
}

/* ─────────────────────── QUALITY SELECTOR ─────────────────────── */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QualitySelector(
    selected: VideoQuality,
    formatSizes: Map<VideoQuality, String>,
    onSelect: (VideoQuality) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "QUALITY", color = TextMuted, fontSize = 11.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp,
            modifier = Modifier.padding(start = 4.dp)
        )
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            GlowCard {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (selected.isAudioOnly) Icons.Default.MusicNote
                            else Icons.Default.HighQuality,
                            null, tint = CyanPrimary, modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                selected.label, color = TextWhite,
                                fontWeight = FontWeight.SemiBold, fontSize = 14.sp
                            )
                            formatSizes[selected]?.let { size ->
                                Text(size, color = CyanMuted, fontSize = 11.sp)
                            }
                        }
                    }
                    Icon(
                        Icons.Default.KeyboardArrowDown, null,
                        tint = TextMuted, modifier = Modifier.size(22.dp)
                    )
                }
            }
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                containerColor = CardDark,
                shape = RoundedCornerShape(12.dp)
            ) {
                QualitySectionHeader("▶ Video")
                VideoQuality.entries.filter {
                    it.isVideo && !it.name.startsWith("BEST_")
                            && it != VideoQuality.SMALLEST
                            && it != VideoQuality.COMPATIBLE
                            && it != VideoQuality.MAX_QUALITY
                }.forEach { q ->
                    QualityMenuItem(q, formatSizes[q], selected == q) {
                        onSelect(q); expanded = false
                    }
                }
                HorizontalDivider(color = CardBorder, modifier = Modifier.padding(vertical = 4.dp))
                QualitySectionHeader("⚡ Smart")
                listOfNotNull(
                    VideoQuality.entries.find { it.name == "MAX_QUALITY" },
                    VideoQuality.entries.find { it.name == "SMALLEST" },
                    VideoQuality.entries.find { it.name == "COMPATIBLE" }
                ).forEach { q ->
                    QualityMenuItem(q, formatSizes[q], selected == q) {
                        onSelect(q); expanded = false
                    }
                }
                HorizontalDivider(color = CardBorder, modifier = Modifier.padding(vertical = 4.dp))
                QualitySectionHeader("♫ Audio Only")
                VideoQuality.entries.filter { it.isAudioOnly }.forEach { q ->
                    QualityMenuItem(q, formatSizes[q], selected == q) {
                        onSelect(q); expanded = false
                    }
                }
            }
        }
    }
}

@Composable
private fun QualitySectionHeader(title: String) {
    Text(
        title, color = CyanPrimary, fontSize = 11.sp,
        fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
    )
}

@Composable
private fun QualityMenuItem(
    q: VideoQuality,
    size: String?,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isSelected) {
                        Icon(Icons.Default.Check, null, Modifier.size(16.dp), tint = CyanPrimary)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        q.label,
                        color = if (isSelected) CyanPrimary else TextLight,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 13.sp
                    )
                }
                if (!size.isNullOrBlank()) {
                    Text(size, color = CyanMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        },
        onClick = onClick,
        leadingIcon = {
            Icon(
                if (q.isAudioOnly) Icons.Default.MusicNote else Icons.Default.Videocam,
                null, Modifier.size(18.dp), tint = TextDark
            )
        },
        colors = MenuDefaults.itemColors(textColor = TextLight)
    )
}

/* ─────────────────────── LOCATION CHIP ─────────────────────── */
@Composable
private fun LocationChip(location: DownloadLocation) {
    Surface(
        color = CyanSurface,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.border(1.dp, CyanDark.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Folder, null, tint = CyanMuted, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Saving to: ${location.label}", color = CyanLight, fontSize = 12.sp)
        }
    }
}

/* ─────────────────────── SETTINGS PANEL ─────────────────────── */
@Composable
private fun SettingsPanel(
    currentLocation: DownloadLocation,
    onLocationChange: (DownloadLocation) -> Unit,
    storageInfo: StorageInfo,
    isClearing: Boolean,
    onClearCache: () -> Unit,
    hasCookies: Boolean,
    onLogin: () -> Unit,
    onLogout: () -> Unit
) {
    GlowCard(borderColor = CyanDark.copy(alpha = 0.3f)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                "⚙ SETTINGS", color = CyanPrimary,
                fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 2.sp
            )
            Text("Save location", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            DownloadLocation.entries.filter { it != DownloadLocation.CUSTOM }.forEach { loc ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = currentLocation == loc,
                        onClick = { onLocationChange(loc) },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = CyanPrimary, unselectedColor = TextDark
                        )
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "${loc.icon} ${loc.label}",
                        color = if (currentLocation == loc) CyanLight else TextMuted,
                        fontSize = 13.sp
                    )
                }
            }
            HorizontalDivider(color = CardBorder)
            Text("Storage", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Surface(
                color = if (storageInfo.cacheSize > 50 * 1024 * 1024)
                    ErrorSurface else CardLight.copy(alpha = 0.5f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Cache & temp files", color = TextMuted, fontSize = 11.sp)
                        Text(
                            storageInfo.cacheSizeText,
                            color = if (storageInfo.cacheSize > 50 * 1024 * 1024)
                                ErrorRed else CyanPrimary,
                            fontWeight = FontWeight.Bold, fontSize = 18.sp
                        )
                    }
                    Button(
                        onClick = onClearCache,
                        enabled = !isClearing && storageInfo.cacheSize > 0,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ErrorRed.copy(alpha = 0.8f),
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        if (isClearing) {
                            CircularProgressIndicator(
                                Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.DeleteSweep, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Clear", fontSize = 12.sp)
                        }
                    }
                }
            }
            HorizontalDivider(color = CardBorder)
            Text("YouTube Login", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(
                "Sign in to fix bot detection errors on some networks",
                color = TextDark, fontSize = 11.sp
            )
            Surface(
                color = if (hasCookies) SuccessSurface else CardLight.copy(alpha = 0.5f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (hasCookies) Icons.Default.CheckCircle
                            else Icons.Default.AccountCircle,
                            null,
                            tint = if (hasCookies) SuccessGreen else TextMuted,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (hasCookies) "Signed in" else "Not signed in",
                                color = if (hasCookies) SuccessGreen else TextLight,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                maxLines = 1
                            )
                            Text(
                                if (hasCookies) "Bot detection bypassed"
                                else "May get errors on some networks",
                                color = TextMuted,
                                fontSize = 11.sp,
                                lineHeight = 13.sp
                            )
                        }
                    }

                    Spacer(Modifier.width(8.dp))

                    if (hasCookies) {
                        TextButton(
                            onClick = onLogout,
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Text("Logout", color = ErrorRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = onLogin,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(32.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CyanPrimary, contentColor = DarkNavy
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                        ) {
                            Text("Login", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Built with ❤ by ", color = TextDark, fontSize = 11.sp)
                Text(
                    "Atanu Biswas",
                    color = CyanPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable {
                        uriHandler.openUri("https://www.instagram.com/atanubiswas7450?igsh=cjJ3eGd3ZnNzMjRp")
                    }
                )
            }
        }
    }
}

/* ─────────────────────── ERROR CARD ─────────────────────── */
@Composable
fun ErrorCard(
    msg: String,
    onDismiss: () -> Unit,
    onRetry: () -> Unit
) {
    GlowCard(borderColor = ErrorRed.copy(alpha = 0.4f)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = ErrorSurface, shape = CircleShape, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.Error, null, tint = ErrorRed,
                        modifier = Modifier.padding(4.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text("Error", color = ErrorRed, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            SelectionContainer {
                Text(
                    msg, color = TextMuted, fontSize = 11.sp,
                    modifier = Modifier
                        .heightIn(max = 150.dp)
                        .verticalScroll(rememberScrollState())
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onRetry,
                    shape = RoundedCornerShape(8.dp),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = Brush.horizontalGradient(listOf(CyanDark, CyanDark))
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanPrimary)
                ) {
                    Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Retry", fontSize = 12.sp)
                }
                TextButton(onClick = onDismiss) {
                    Text("Dismiss", color = TextMuted, fontSize = 12.sp)
                }
            }
        }
    }
}

/* ─────────────────────── HELPERS ─────────────────────── */
fun formatDuration(seconds: Long): String {
    val h = seconds / 3600; val m = (seconds % 3600) / 60; val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}