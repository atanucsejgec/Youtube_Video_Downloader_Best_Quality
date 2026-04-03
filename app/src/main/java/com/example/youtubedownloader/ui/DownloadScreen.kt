package com.example.youtubedownloader.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.youtubedownloader.DownloadLocation
import com.example.youtubedownloader.MainActivity
import com.example.youtubedownloader.MainViewModel
import com.example.youtubedownloader.PlaylistInfo
import com.example.youtubedownloader.SavedFileInfo
import com.example.youtubedownloader.StorageInfo
import com.example.youtubedownloader.UiState
import com.example.youtubedownloader.VideoDetails
import com.example.youtubedownloader.VideoQuality
import java.io.File

/* ═══════════════════════════════════════════════════════
 *  COLOR PALETTE — Refined
 * ═══════════════════════════════════════════════════════ */
val DarkNavy = Color(0xFF0A0E1A)
val DarkBlue = Color(0xFF0F1629)
val MediumBlue = Color(0xFF141D35)
val LightNavy = Color(0xFF1A2342)

val CyanPrimary = Color(0xFF00E5FF)
val CyanLight = Color(0xFF80F0FF)
val CyanDark = Color(0xFF0097A7)
val CyanMuted = Color(0xFF5CC8D4)
val CyanSurface = Color(0xFF0D2F35)

val CardDark = Color(0xFF111827)
val CardLight = Color(0xFF1E293B)
val CardBorder = Color(0xFF2A3550)

val TextWhite = Color(0xFFF0F4FF)
val TextLight = Color(0xFFCBD5E8)
val TextMuted = Color(0xFF8899B4)
val TextDark = Color(0xFF4B5C78)

val SuccessGreen = Color(0xFF4ADE80)
val SuccessSurface = Color(0xFF0A2E1A)

val ErrorRed = Color(0xFFFF6B6B)
val ErrorSurface = Color(0xFF2E0A0A)

val ProgressTrack = Color(0xFF1E2A40)
val ProgressTeal = Color(0xFF2DD4BF)

/* ═══════════════════════════════════════════════════════
 *  ROOT SCREEN
 * ═══════════════════════════════════════════════════════ */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadScreen(vm: MainViewModel = viewModel()) {
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val url by vm.url.collectAsStateWithLifecycle()
    val quality by vm.quality.collectAsStateWithLifecycle()
    val dlAsPlaylist by vm.downloadAsPlaylist.collectAsStateWithLifecycle()
    val dlLocation by vm.downloadLocation.collectAsStateWithLifecycle()
    val showSettings by vm.showSettings.collectAsStateWithLifecycle()
    val keyboardController = LocalSoftwareKeyboardController.current
    val hasCookies by vm.hasCookies.collectAsStateWithLifecycle()
    val showLogin by vm.showLogin.collectAsStateWithLifecycle()

    // Handle shared URL from YouTube
    LaunchedEffect(Unit) {
        val shared = MainActivity.consumeSharedUrl()
        if (shared != null) vm.handleSharedUrl(shared)
    }

    val sharedUrl by MainActivity.sharedUrl.collectAsStateWithLifecycle()
    LaunchedEffect(sharedUrl) {
        val shared = MainActivity.consumeSharedUrl()
        if (shared != null) vm.handleSharedUrl(shared)
    }

    if (showLogin) {
        YouTubeLoginSheet(
            onDismiss = { vm.hideLoginScreen() },
            onLoggedIn = { vm.onLoginComplete() }
        )
        return
    }

    // Gradient background
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
                // Custom top bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 48.dp, start = 20.dp, end = 12.dp, bottom = 12.dp)
                ) {
                    Column(modifier = Modifier.align(Alignment.CenterStart)) {
                        Text(
                            "Youtube Downloader",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = CyanPrimary,
                            letterSpacing = (-0.3).sp
                        )
                        Text(
                            "Download videos in any quality",
                            fontSize = 12.sp,
                            color = TextDark,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.2.sp
                        )
                    }
                    Surface(
                        onClick = { vm.toggleSettings() },
                        shape = RoundedCornerShape(12.dp),
                        color = if (showSettings) CyanSurface else Color.Transparent,
                        border = BorderStroke(
                            1.dp,
                            if (showSettings) CyanDark.copy(alpha = 0.4f)
                            else CardBorder
                        ),
                        modifier = Modifier.align(Alignment.CenterEnd)
                    ) {
                        Icon(
                            if (showSettings) Icons.Filled.Close
                            else Icons.Outlined.Settings,
                            "Settings",
                            tint = if (showSettings) CyanPrimary else TextLight,
                            modifier = Modifier.padding(10.dp).size(20.dp)
                        )
                    }
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(Modifier.height(4.dp))

                // Settings
                AnimatedVisibility(
                    visible = showSettings,
                    enter = fadeIn(tween(300)) + slideInVertically(tween(400)),
                    exit = fadeOut(tween(200)) + slideOutVertically(tween(300))
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

                // Initializing
                if (uiState is UiState.Initializing) {
                    GlowCard {
                        Row(
                            Modifier.padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                Modifier.size(22.dp),
                                color = CyanPrimary,
                                strokeWidth = 2.5.dp
                            )
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(
                                    "Starting Engine…",
                                    color = CyanLight,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "Preparing download engine",
                                    color = TextDark,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                // URL Input
                UrlInputField(
                    url = url,
                    onUrlChange = vm::updateUrl,
                    enabled = uiState !is UiState.Initializing
                            && uiState !is UiState.Downloading
                )

                // Fetch Button
                CyanButton(
                    text = if (uiState is UiState.FetchingInfo) "Searching…"
                    else "Search Video",
                    icon = Icons.Default.Search,
                    loading = uiState is UiState.FetchingInfo,
                    enabled = url.isNotBlank()
                            && uiState !is UiState.FetchingInfo
                            && uiState !is UiState.Downloading
                            && uiState !is UiState.Initializing,
                    filled = false,
                    onClick = {
                        keyboardController?.hide()
                        vm.fetchInfo()
                    }
                )

                // Video Info
                val details = when (uiState) {
                    is UiState.InfoReady -> (uiState as UiState.InfoReady).details
                    is UiState.Downloading -> (uiState as UiState.Downloading).details
                    is UiState.Completed -> (uiState as UiState.Completed).details
                    else -> null
                }

                AnimatedVisibility(
                    visible = details != null,
                    enter = fadeIn(tween(400)) + slideInVertically(tween(500)) { it / 3 }
                ) {
                    if (details != null) VideoCard(details)
                }

                // Playlist Info
                val playlistInfo = when (uiState) {
                    is UiState.InfoReady -> (uiState as UiState.InfoReady).playlistInfo
                    else -> null
                }
                if (playlistInfo != null) {
                    PlaylistCard(playlistInfo, dlAsPlaylist, vm::setDownloadAsPlaylist)
                }

                // Quality Selector
                val formatSizes = when (uiState) {
                    is UiState.InfoReady -> (uiState as UiState.InfoReady).formatSizes
                    else -> emptyMap()
                }
                if (uiState is UiState.InfoReady || uiState is UiState.Completed) {
                    QualitySelector(quality, formatSizes, vm::selectQuality)
                }

                // Download Location Chip
                if (uiState is UiState.InfoReady) {
                    LocationChip(dlLocation)
                }

                // Download Button
                if (uiState is UiState.InfoReady || uiState is UiState.Completed) {
                    val sizeLabel = formatSizes[quality]
                    CyanButton(
                        text = buildString {
                            append("DOWNLOAD")
                            if (dlAsPlaylist && playlistInfo != null)
                                append(" × ${playlistInfo.count}")
                        },
                        subtitle = buildString {
                            append(quality.label)
                            if (!sizeLabel.isNullOrBlank()) append(" · $sizeLabel")
                        },
                        icon = Icons.Default.Download,
                        filled = true,
                        onClick = { vm.startDownload() }
                    )
                }

                // Progress
                if (uiState is UiState.Downloading) {
                    val st = uiState as UiState.Downloading
                    DownloadProgress(
                        st.progress, st.eta, st.line,
                        st.currentItem, st.totalItems, st.currentVideoTitle,
                        onCancel = vm::cancelDownload
                    )
                }

                // Completed
                if (uiState is UiState.Completed) {
                    val st = uiState as UiState.Completed
                    CompletedCard(st.savedLocation, st.fileCount, st.failedCount, st.lastFile)
                }

                // Error
                if (uiState is UiState.Error) {
                    ErrorCard(
                        (uiState as UiState.Error).message,
                        onDismiss = vm::reset,
                        onRetry = vm::initEngine
                    )
                }

                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

/* ═══════════════════════════════════════════════════════
 *  GLOW CARD
 * ═══════════════════════════════════════════════════════ */
@Composable
private fun GlowCard(
    borderColor: Color = CardBorder,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = CardDark.copy(alpha = 0.88f)
        ),
    ) {
        content()
    }
}

/* ═══════════════════════════════════════════════════════
 *  URL INPUT
 * ═══════════════════════════════════════════════════════ */
@Composable
private fun UrlInputField(
    url: String,
    onUrlChange: (String) -> Unit,
    enabled: Boolean,
    vm: MainViewModel = viewModel()
) {
    val clipboard = LocalClipboardManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    GlowCard {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.Link, null,
                tint = if (url.isNotBlank()) CyanPrimary else TextDark,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))

            TextField(
                value = url,
                onValueChange = onUrlChange,
                placeholder = {
                    Text(
                        "Paste YouTube URL here…",
                        color = TextDark,
                        fontSize = 14.sp
                    )
                },
                singleLine = true,
                enabled = enabled,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Search
                ),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        if (url.isNotBlank()) {
                            keyboardController?.hide()
                            vm.fetchInfo()
                        }
                    }
                ),
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
                    color = TextWhite,
                    fontSize = 14.sp,
                    letterSpacing = 0.1.sp
                )
            )

            if (url.isNotBlank()) {
                IconButton(
                    onClick = { onUrlChange("") },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        Icons.Default.Close, "Clear",
                        tint = TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Surface(
                onClick = { clipboard.getText()?.text?.let { onUrlChange(it) } },
                shape = RoundedCornerShape(8.dp),
                color = CyanSurface,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    Icons.Default.ContentPaste, "Paste",
                    tint = CyanMuted,
                    modifier = Modifier.padding(8.dp).size(18.dp)
                )
            }
        }
    }
}

/* ═══════════════════════════════════════════════════════
 *  CYAN BUTTON
 * ═══════════════════════════════════════════════════════ */
@Composable
private fun CyanButton(
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
                .height(if (subtitle != null) 66.dp else 52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = CyanPrimary,
                contentColor = DarkNavy,
                disabledContainerColor = CyanDark.copy(alpha = 0.25f),
                disabledContentColor = TextDark
            ),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 6.dp,
                pressedElevation = 10.dp
            )
        ) {
            if (loading) {
                CircularProgressIndicator(
                    Modifier.size(20.dp),
                    color = DarkNavy,
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(10.dp))
            } else {
                Icon(icon, null, Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text,
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp,
                    letterSpacing = 1.2.sp
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = DarkNavy.copy(alpha = 0.65f)
                    )
                }
            }
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(16.dp),
            border = ButtonDefaults.outlinedButtonBorder.copy(
                brush = Brush.horizontalGradient(listOf(CyanDark, CyanMuted))
            ),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = CyanPrimary,
                disabledContentColor = TextDark
            )
        ) {
            if (loading) {
                CircularProgressIndicator(
                    Modifier.size(20.dp),
                    color = CyanPrimary,
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(10.dp))
            } else {
                Icon(icon, null, Modifier.size(20.dp), tint = CyanPrimary)
                Spacer(Modifier.width(10.dp))
            }
            Text(
                text,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = CyanPrimary,
                letterSpacing = 0.5.sp
            )
        }
    }
}

/* ═══════════════════════════════════════════════════════
 *  VIDEO CARD
 * ═══════════════════════════════════════════════════════ */
@Composable
private fun VideoCard(d: VideoDetails) {
    GlowCard {
        Column {
            // Thumbnail with duration overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
            ) {
                d.thumbnail?.let { url ->
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                    )
                }

                // Gradient overlay at bottom
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))
                            )
                        )
                )

                // Duration badge
                if (d.duration > 0) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.8f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp)
                    ) {
                        Text(
                            formatDuration(d.duration),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Info
            Column(
                Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    d.title,
                    color = TextWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 22.sp,
                    letterSpacing = (-0.2).sp
                )

                d.author?.let { author ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = CyanSurface,
                            modifier = Modifier.size(22.dp)
                        ) {
                            Icon(
                                Icons.Outlined.AccountCircle, null,
                                tint = CyanMuted,
                                modifier = Modifier.padding(3.dp)
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            author,
                            color = TextMuted,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

/* ═══════════════════════════════════════════════════════
 *  PLAYLIST CARD
 * ═══════════════════════════════════════════════════════ */
@Composable
private fun PlaylistCard(
    info: PlaylistInfo,
    downloadAsPlaylist: Boolean,
    onToggle: (Boolean) -> Unit
) {
    GlowCard(borderColor = CyanDark.copy(alpha = 0.4f)) {
        Column(
            Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = CyanSurface,
                    shape = CircleShape,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        Icons.Default.PlaylistPlay, null,
                        tint = CyanPrimary,
                        modifier = Modifier.padding(7.dp)
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        "Playlist Detected",
                        color = CyanLight,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        letterSpacing = (-0.2).sp
                    )
                    Text(
                        "${info.title} · ${info.count} videos",
                        color = TextMuted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = CardLight.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            if (downloadAsPlaylist) "Download all ${info.count} videos"
                            else "Single video only",
                            color = TextLight,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        if (downloadAsPlaylist) {
                            Text(
                                "Videos download sequentially",
                                color = TextDark,
                                fontSize = 11.sp
                            )
                        }
                    }
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
            }
        }
    }
}

/* ═══════════════════════════════════════════════════════
 *  QUALITY SELECTOR
 * ═══════════════════════════════════════════════════════ */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QualitySelector(
    selected: VideoQuality,
    formatSizes: Map<VideoQuality, String>,
    onSelect: (VideoQuality) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "QUALITY",
            color = TextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            modifier = Modifier.padding(start = 4.dp)
        )

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            GlowCard(
                borderColor = if (expanded) CyanDark.copy(alpha = 0.5f)
                else CardBorder
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = CyanSurface,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                if (selected.isAudioOnly) Icons.Default.MusicNote
                                else Icons.Default.HighQuality,
                                null,
                                tint = CyanPrimary,
                                modifier = Modifier.padding(6.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                selected.label,
                                color = TextWhite,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            formatSizes[selected]?.let { size ->
                                Text(
                                    size,
                                    color = CyanMuted,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                    Icon(
                        if (expanded) Icons.Default.KeyboardArrowUp
                        else Icons.Default.KeyboardArrowDown,
                        null,
                        tint = TextMuted,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                containerColor = CardDark,
                shape = RoundedCornerShape(14.dp)
            ) {
                // Video Section
                QualitySectionHeader("🎬 Video")
                VideoQuality.entries.filter {
                    it.isVideo && !it.name.startsWith("BEST_") &&
                            it != VideoQuality.SMALLEST &&
                            it != VideoQuality.COMPATIBLE &&
                            it != VideoQuality.MAX_QUALITY
                }.forEach { q ->
                    QualityMenuItem(q, formatSizes[q], selected == q) {
                        onSelect(q); expanded = false
                    }
                }

                HorizontalDivider(
                    color = CardBorder,
                    modifier = Modifier.padding(vertical = 6.dp, horizontal = 12.dp)
                )

                // Smart Section
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

                HorizontalDivider(
                    color = CardBorder,
                    modifier = Modifier.padding(vertical = 6.dp, horizontal = 12.dp)
                )

                // Audio Section
                QualitySectionHeader("🎵 Audio Only")
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
        title,
        color = CyanPrimary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
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
                        Icon(
                            Icons.Default.Check, null,
                            Modifier.size(16.dp),
                            tint = CyanPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        q.label,
                        color = if (isSelected) CyanPrimary else TextLight,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 13.sp
                    )
                }
                if (!size.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = CyanSurface
                    ) {
                        Text(
                            size,
                            color = CyanMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        },
        onClick = onClick,
        leadingIcon = {
            Icon(
                if (q.isAudioOnly) Icons.Default.MusicNote
                else Icons.Default.Videocam,
                null,
                Modifier.size(18.dp),
                tint = TextDark
            )
        },
        colors = MenuDefaults.itemColors(textColor = TextLight),
        modifier = Modifier.background(
            if (isSelected) CyanSurface.copy(alpha = 0.3f) else Color.Transparent
        )
    )
}

/* ═══════════════════════════════════════════════════════
 *  LOCATION CHIP
 * ═══════════════════════════════════════════════════════ */
@Composable
private fun LocationChip(location: DownloadLocation) {
    Surface(
        color = CyanSurface,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, CyanDark.copy(alpha = 0.3f))
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.Folder, null,
                tint = CyanMuted,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Saving to: ${location.label}",
                color = CyanLight,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/* ═══════════════════════════════════════════════════════
 *  DOWNLOAD PROGRESS
 * ═══════════════════════════════════════════════════════ */
@Composable
private fun DownloadProgress(
    progress: Float, eta: Long, line: String,
    currentItem: Int, totalItems: Int, currentVideoTitle: String,
    onCancel: () -> Unit
) {
    val animatedProgress by animateFloatAsState(
        targetValue = (progress / 100f).coerceIn(0f, 1f),
        animationSpec = tween(300),
        label = "progress"
    )

    GlowCard(borderColor = CyanDark.copy(alpha = 0.5f)) {
        Column(
            Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Animated pulsing indicator
                    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                    val alpha by infiniteTransition.animateFloat(
                        initialValue = 0.4f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1000),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "pulse_alpha"
                    )
                    Surface(
                        shape = CircleShape,
                        color = CyanPrimary.copy(alpha = alpha * 0.2f),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Download, null,
                            tint = CyanPrimary,
                            modifier = Modifier.padding(6.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            "Downloading…",
                            color = CyanLight,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        if (eta > 0) {
                            Text(
                                "~${eta}s remaining",
                                color = TextDark,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                if (totalItems > 1) {
                    Surface(
                        color = CyanSurface,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            "$currentItem / $totalItems",
                            color = CyanPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            // Current video title
            if (totalItems > 1 && currentVideoTitle.isNotBlank()) {
                Text(
                    "▶ $currentVideoTitle",
                    color = TextMuted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Progress bar
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(ProgressTrack)
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(animatedProgress)
                            .clip(RoundedCornerShape(5.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(CyanDark, CyanPrimary, CyanLight)
                                )
                            )
                    )
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "${progress.toInt()}%",
                        color = CyanPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black
                    )
                    if (line.isNotBlank()) {
                        Text(
                            line,
                            color = TextDark,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false).padding(start = 12.dp)
                        )
                    }
                }
            }

            // Overall progress for playlists
            if (totalItems > 1) {
                val overallPercent = ((currentItem - 1) * 100f + progress) / totalItems
                val animatedOverall by animateFloatAsState(
                    targetValue = (overallPercent / 100f).coerceIn(0f, 1f),
                    animationSpec = tween(300),
                    label = "overall"
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(ProgressTrack)
                    ) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(animatedOverall)
                                .clip(RoundedCornerShape(2.dp))
                                .background(ProgressTeal)
                        )
                    }
                    Text(
                        "Overall: ${overallPercent.toInt()}%",
                        color = ProgressTeal,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Cancel
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = Brush.horizontalGradient(
                        listOf(ErrorRed.copy(alpha = 0.5f), ErrorRed.copy(alpha = 0.3f))
                    )
                ),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed)
            ) {
                Icon(Icons.Default.Cancel, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Cancel Download",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/* ═══════════════════════════════════════════════════════
 *  COMPLETED CARD
 * ═══════════════════════════════════════════════════════ */
@Composable
private fun CompletedCard(
    savedLocation: String, fileCount: Int,
    failedCount: Int, lastFile: SavedFileInfo?
) {
    val context = LocalContext.current

    GlowCard(borderColor = SuccessGreen.copy(alpha = 0.3f)) {
        Column(
            Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = SuccessSurface,
                    shape = CircleShape,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.CheckCircle, null,
                        tint = SuccessGreen,
                        modifier = Modifier.padding(6.dp)
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        "Download Complete!",
                        color = SuccessGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        letterSpacing = (-0.2).sp
                    )
                    Text(
                        "Saved to Gallery",
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }
            }

            // Stats
            if (fileCount > 1 || failedCount > 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = SuccessSurface
                    ) {
                        Text(
                            "✓ $fileCount saved",
                            color = SuccessGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    if (failedCount > 0) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = ErrorSurface
                        ) {
                            Text(
                                "✗ $failedCount failed",
                                color = ErrorRed,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // Path
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = CardLight.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.Folder, null,
                        tint = TextDark,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        savedLocation,
                        color = TextMuted,
                        fontSize = 11.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Buttons
            if (lastFile != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = { openSavedFile(context, lastFile) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = Brush.horizontalGradient(listOf(CardBorder, CardBorder))
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextLight)
                    ) {
                        Icon(Icons.Outlined.FolderOpen, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Open", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }

                    OutlinedButton(
                        onClick = { shareSavedFile(context, lastFile) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = Brush.horizontalGradient(listOf(CardBorder, CardBorder))
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextLight)
                    ) {
                        Icon(Icons.Default.Share, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Share", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

/* ═══════════════════════════════════════════════════════
 *  SETTINGS PANEL
 * ═══════════════════════════════════════════════════════ */
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
        Column(
            Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = CyanSurface,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Outlined.Settings, null,
                        tint = CyanPrimary,
                        modifier = Modifier.padding(5.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    "QUICK SETTINGS",
                    color = CyanPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    letterSpacing = 2.sp
                )
            }

            // Location
            Text(
                "SAVE LOCATION",
                color = TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            DownloadLocation.entries.filter { it != DownloadLocation.CUSTOM }.forEach { loc ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onLocationChange(loc) }
                        .background(
                            if (currentLocation == loc) CyanSurface
                            else Color.Transparent
                        )
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = currentLocation == loc,
                        onClick = { onLocationChange(loc) },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = CyanPrimary,
                            unselectedColor = TextDark
                        ),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "${loc.icon} ${loc.label}",
                        color = if (currentLocation == loc) CyanLight else TextMuted,
                        fontSize = 13.sp,
                        fontWeight = if (currentLocation == loc) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }

            HorizontalDivider(color = CardBorder)

            // Storage
            Text(
                "STORAGE",
                color = TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Surface(
                color = if (storageInfo.cacheSize > 50 * 1024 * 1024)
                    ErrorSurface else CardLight.copy(alpha = 0.4f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(
                    1.dp,
                    if (storageInfo.cacheSize > 50 * 1024 * 1024)
                        ErrorRed.copy(alpha = 0.2f) else CardBorder
                )
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Cache & temp files",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                        Text(
                            storageInfo.cacheSizeText,
                            color = if (storageInfo.cacheSize > 50 * 1024 * 1024) ErrorRed
                            else CyanPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                    Button(
                        onClick = onClearCache,
                        enabled = !isClearing && storageInfo.cacheSize > 0,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ErrorRed.copy(alpha = 0.8f),
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        if (isClearing) {
                            CircularProgressIndicator(
                                Modifier.size(16.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.DeleteSweep, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "Clear",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = CardBorder)

            // YouTube Login
            Text(
                "YOUTUBE LOGIN",
                color = TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Surface(
                color = if (hasCookies) SuccessSurface else CardLight.copy(alpha = 0.4f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(
                    1.dp,
                    if (hasCookies) SuccessGreen.copy(alpha = 0.15f) else CardBorder
                )
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = if (hasCookies) SuccessGreen.copy(alpha = 0.15f)
                            else CardLight,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                if (hasCookies) Icons.Default.CheckCircle
                                else Icons.Default.AccountCircle,
                                null,
                                tint = if (hasCookies) SuccessGreen else TextMuted,
                                modifier = Modifier.padding(6.dp)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                if (hasCookies) "Signed in" else "Not signed in",
                                color = if (hasCookies) SuccessGreen else TextLight,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                            Text(
                                if (hasCookies) "Bot detection bypassed"
                                else "May help fix errors",
                                color = TextDark,
                                fontSize = 11.sp
                            )
                        }
                    }
                    if (hasCookies) {
                        TextButton(onClick = onLogout) {
                            Text(
                                "Logout",
                                color = ErrorRed,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    } else {
                        Button(
                            onClick = onLogin,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CyanPrimary,
                                contentColor = DarkNavy
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Login, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "Sign in",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Credits
            val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Built with ❤️ by ",
                    color = TextDark,
                    fontSize = 11.sp
                )
                Text(
                    text = "Atanu Biswas",
                    color = CyanPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable {
                        uriHandler.openUri("https://www.instagram.com/atanubiswas7450?igsh=cjJ3eGd3ZnNzMjRp")
                    }
                )
            }
        }
    }
}

/* ═══════════════════════════════════════════════════════
 *  ERROR CARD
 * ═══════════════════════════════════════════════════════ */
@Composable
private fun ErrorCard(msg: String, onDismiss: () -> Unit, onRetry: () -> Unit) {
    GlowCard(borderColor = ErrorRed.copy(alpha = 0.4f)) {
        Column(
            Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = ErrorSurface,
                    shape = CircleShape,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Error, null,
                        tint = ErrorRed,
                        modifier = Modifier.padding(6.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        "Something went wrong",
                        color = ErrorRed,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        "Tap to copy error details",
                        color = TextDark,
                        fontSize = 11.sp
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = CardLight.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                SelectionContainer {
                    Text(
                        msg,
                        color = TextMuted,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier
                            .padding(12.dp)
                            .heightIn(max = 150.dp)
                            .verticalScroll(rememberScrollState())
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onRetry,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyanPrimary,
                        contentColor = DarkNavy
                    )
                ) {
                    Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Retry",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                TextButton(onClick = onDismiss) {
                    Text(
                        "Dismiss",
                        color = TextMuted,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

/* ═══════════════════════════════════════════════════════
 *  HELPERS
 * ═══════════════════════════════════════════════════════ */
private fun formatDuration(seconds: Long): String {
    val h = seconds / 3600; val m = (seconds % 3600) / 60; val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private fun getFileUri(context: Context, fileInfo: SavedFileInfo): Uri {
    if (fileInfo.contentUri != null) return Uri.parse(fileInfo.contentUri)
    if (fileInfo.absolutePath != null)
        return FileProvider.getUriForFile(
            context, "${context.packageName}.provider", File(fileInfo.absolutePath)
        )
    throw IllegalStateException("No URI")
}

private fun openSavedFile(context: Context, fileInfo: SavedFileInfo) {
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

private fun shareSavedFile(context: Context, fileInfo: SavedFileInfo) {
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