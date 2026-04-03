package com.example.youtubedownloader.ui

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.youtubedownloader.DownloadLocation
import com.example.youtubedownloader.MainViewModel

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val isDark by vm.isDarkTheme.collectAsStateWithLifecycle()
    val isWifiOnly by vm.isWifiOnly.collectAsStateWithLifecycle()
    val speedLimit by vm.speedLimitKb.collectAsStateWithLifecycle()
    val concurrent by vm.concurrentDownloads.collectAsStateWithLifecycle()
    val fragments by vm.concurrentFragments.collectAsStateWithLifecycle()
    val downloadSubs by vm.downloadSubtitles.collectAsStateWithLifecycle()
    val embedThumb by vm.embedThumbnail.collectAsStateWithLifecycle()
    val autoPaste by vm.autoPaste.collectAsStateWithLifecycle()
    val language by vm.currentLanguage.collectAsStateWithLifecycle()
    val storage by vm.storageInfo.collectAsStateWithLifecycle()
    val isClearing by vm.isClearing.collectAsStateWithLifecycle()
    val hasCookies by vm.hasCookies.collectAsStateWithLifecycle()
    val dlLocation by vm.downloadLocation.collectAsStateWithLifecycle()
    val networkType by vm.networkType.collectAsStateWithLifecycle()
    val scheduleEnabled by vm.scheduleEnabled.collectAsStateWithLifecycle()
    val scheduleHour by vm.scheduleHour.collectAsStateWithLifecycle()
    val scheduleMinute by vm.scheduleMinute.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.updateStorageInfo() }

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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(Modifier.height(16.dp))

            // ── Header ──
            Text(
                "Settings",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = TextWhite,
                letterSpacing = (-0.5).sp
            )

            Spacer(Modifier.height(4.dp))

            // ── Appearance ──
            SettingsSection(
                title = "Appearance",
                icon = Icons.Outlined.Palette
            ) {
                SettingsToggleRow(
                    icon = Icons.Outlined.DarkMode,
                    label = "Dark Theme",
                    sublabel = "Use dark color scheme",
                    checked = isDark,
                    onToggle = { vm.toggleTheme() }
                )
            }

            // ── Language ──
            SettingsSection(
                title = "Language",
                icon = Icons.Outlined.Language
            ) {
                val languages = mapOf(
                    "en" to "English",
                    "hi" to "हिन्दी (Hindi)",
                    "bn" to "বাংলা (Bengali)",
                    "ta" to "தமிழ் (Tamil)",
                    "te" to "తెలుగు (Telugu)",
                    "mr" to "मराठी (Marathi)",
                    "gu" to "ગુજરાતી (Gujarati)",
                    "kn" to "ಕನ್ನಡ (Kannada)",
                    "ml" to "മലയാളം (Malayalam)",
                    "pa" to "ਪੰਜਾਬੀ (Punjabi)"
                )
                languages.forEach { (code, name) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { vm.setLanguage(code) }
                            .background(
                                if (language == code) CyanSurface
                                else Color.Transparent
                            )
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = language == code,
                            onClick = { vm.setLanguage(code) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = CyanPrimary,
                                unselectedColor = TextDark
                            ),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            name,
                            fontSize = 13.sp,
                            color = if (language == code) CyanLight else TextMuted,
                            fontWeight = if (language == code) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }

            // ── Network ──
            SettingsSection(
                title = "Network",
                icon = Icons.Outlined.Wifi
            ) {
                // Current network
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = CyanSurface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.SignalCellularAlt, null,
                            tint = CyanPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Connected: $networkType",
                            fontSize = 12.sp,
                            color = CyanLight,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                SettingsToggleRow(
                    icon = Icons.Outlined.WifiLock,
                    label = "Wi-Fi Only",
                    sublabel = "Only download on Wi-Fi",
                    checked = isWifiOnly,
                    onToggle = { vm.setWifiOnly(it) }
                )

                HorizontalDivider(
                    color = CardBorder,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                // Speed limit
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Speed Limit",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = TextLight
                        )
                        Text(
                            if (speedLimit == 0) "Unlimited"
                            else "${speedLimit} KB/s",
                            fontSize = 12.sp,
                            color = CyanMuted,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = CyanSurface
                    ) {
                        Text(
                            if (speedLimit == 0) "∞"
                            else "${speedLimit}KB/s",
                            color = CyanPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Slider(
                    value = speedLimit.toFloat(),
                    onValueChange = { vm.setSpeedLimit(it.toInt()) },
                    valueRange = 0f..10240f,
                    steps = 20,
                    colors = SliderDefaults.colors(
                        thumbColor = CyanPrimary,
                        activeTrackColor = CyanPrimary,
                        inactiveTrackColor = CardBorder
                    )
                )

                Text(
                    "0 = Unlimited, max = 10 MB/s",
                    fontSize = 10.sp,
                    color = TextDark
                )
            }

            // ── Download Settings ──
            SettingsSection(
                title = "Download",
                icon = Icons.Outlined.Download
            ) {
                // Save location
                Text(
                    "SAVE LOCATION",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = CyanMuted,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.height(6.dp))

                DownloadLocation.entries
                    .filter { it != DownloadLocation.CUSTOM }
                    .forEach { loc ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { vm.setDownloadLocation(loc) }
                                .background(
                                    if (dlLocation == loc) CyanSurface
                                    else Color.Transparent
                                )
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = dlLocation == loc,
                                onClick = { vm.setDownloadLocation(loc) },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = CyanPrimary,
                                    unselectedColor = TextDark
                                ),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "${loc.icon} ${loc.label}",
                                color = if (dlLocation == loc) CyanLight else TextMuted,
                                fontSize = 13.sp,
                                fontWeight = if (dlLocation == loc) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }

                HorizontalDivider(
                    color = CardBorder,
                    modifier = Modifier.padding(vertical = 10.dp)
                )

                // Concurrent downloads
                SettingsSliderRow(
                    label = "Simultaneous Downloads",
                    value = concurrent,
                    valueRange = 1f..5f,
                    steps = 3,
                    onValueChange = { vm.setConcurrentDownloads(it.toInt()) }
                )

                Spacer(Modifier.height(8.dp))

                // Concurrent fragments
                SettingsSliderRow(
                    label = "Download Threads",
                    value = fragments,
                    valueRange = 1f..64f,
                    steps = 15,
                    onValueChange = { vm.setConcurrentFragments(it.toInt()) }
                )

                Text(
                    "Higher = faster on fast connections",
                    fontSize = 10.sp,
                    color = TextDark
                )

                HorizontalDivider(
                    color = CardBorder,
                    modifier = Modifier.padding(vertical = 10.dp)
                )

                SettingsToggleRow(
                    icon = Icons.Outlined.Subtitles,
                    label = "Download Subtitles",
                    checked = downloadSubs,
                    onToggle = { vm.setDownloadSubtitles(it) }
                )
                SettingsToggleRow(
                    icon = Icons.Outlined.Image,
                    label = "Embed Thumbnail",
                    sublabel = "Embed cover art in file",
                    checked = embedThumb,
                    onToggle = { vm.setEmbedThumbnail(it) }
                )
                SettingsToggleRow(
                    icon = Icons.Outlined.ContentPaste,
                    label = "Auto-paste URL",
                    sublabel = "From clipboard on open",
                    checked = autoPaste,
                    onToggle = { vm.setAutoPaste(it) }
                )
            }

            // ── Schedule ──
            SettingsSection(
                title = "Scheduled Downloads",
                icon = Icons.Outlined.Schedule
            ) {
                SettingsToggleRow(
                    icon = Icons.Outlined.Alarm,
                    label = "Enable Schedule",
                    sublabel = "Auto-start queue at set time",
                    checked = scheduleEnabled,
                    onToggle = { vm.setSchedule(it) }
                )

                AnimatedVisibility(
                    visible = scheduleEnabled,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Start at %02d:%02d".format(scheduleHour, scheduleMinute),
                            fontSize = 13.sp,
                            color = CyanLight,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(0, 2, 6, 22).forEach { hour ->
                                FilterChip(
                                    selected = scheduleHour == hour,
                                    onClick = { vm.setSchedule(true, hour, 0) },
                                    label = {
                                        Text(
                                            "%02d:00".format(hour),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = CyanPrimary,
                                        selectedLabelColor = DarkNavy,
                                        containerColor = CardLight,
                                        labelColor = TextMuted
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        borderColor = CardBorder,
                                        selectedBorderColor = CyanPrimary,
                                        enabled = true,
                                        selected = scheduleHour == hour
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // ── YouTube Login ──
            SettingsSection(
                title = "YouTube Login",
                icon = Icons.Outlined.AccountCircle
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (hasCookies) SuccessSurface else CardLight.copy(alpha = 0.3f),
                    border = BorderStroke(
                        1.dp,
                        if (hasCookies) SuccessGreen.copy(alpha = 0.2f)
                        else CardBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
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
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    if (hasCookies) Icons.Default.CheckCircle
                                    else Icons.Default.AccountCircle,
                                    null,
                                    tint = if (hasCookies) SuccessGreen else TextMuted,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    if (hasCookies) "Signed in" else "Not signed in",
                                    color = if (hasCookies) SuccessGreen else TextLight,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    if (hasCookies) "Bot detection bypassed"
                                    else "May help with some errors",
                                    color = TextDark,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        if (hasCookies) {
                            TextButton(onClick = { vm.clearLoginCookies() }) {
                                Text(
                                    "Logout",
                                    color = ErrorRed,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        } else {
                            Button(
                                onClick = { vm.showLoginScreen() },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CyanPrimary,
                                    contentColor = DarkNavy
                                ),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
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
            }

            // ── Storage ──
            SettingsSection(
                title = "Storage",
                icon = Icons.Outlined.Storage
            ) {
                // Storage stats grid
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    StorageInfoRow("📦 Cache", storage.cacheSizeText)
                    StorageInfoRow("📊 Downloaded Today", storage.todayText)
                    StorageInfoRow("📈 Total Downloaded", storage.totalEverText)
                    StorageInfoRow("📂 Total Files", "${storage.totalCount} files")
                    StorageInfoRow("💾 Free Space", storage.freeSpaceText)
                }

                Spacer(Modifier.height(10.dp))

                Button(
                    onClick = { vm.clearAllCache() },
                    enabled = !isClearing && storage.cacheSize > 0,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ErrorRed.copy(alpha = 0.85f),
                        contentColor = Color.White,
                        disabledContainerColor = ErrorRed.copy(alpha = 0.2f),
                        disabledContentColor = TextDark
                    )
                ) {
                    if (isClearing) {
                        CircularProgressIndicator(
                            Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.DeleteSweep, null, Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Clear Cache",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(Modifier.height(60.dp))
        }
    }
}

// ── Reusable Settings Components ──

@Composable
private fun SettingsSection(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CardBorder, RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = CardDark.copy(alpha = 0.85f)
        )
    ) {
        Column(Modifier.padding(18.dp)) {
            // Section header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = CyanSurface,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        icon, null,
                        tint = CyanPrimary,
                        modifier = Modifier.padding(5.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = TextWhite,
                    letterSpacing = (-0.2).sp
                )
            }
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector? = null,
    label: String,
    sublabel: String? = null,
    checked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            if (icon != null) {
                Icon(
                    icon, null,
                    tint = TextMuted,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(10.dp))
            }
            Column {
                Text(
                    label,
                    fontSize = 13.sp,
                    color = TextLight,
                    fontWeight = FontWeight.Medium
                )
                if (sublabel != null) {
                    Text(
                        sublabel,
                        fontSize = 11.sp,
                        color = TextDark
                    )
                }
            }
        }
        Switch(
            checked = checked,
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

@Composable
private fun SettingsSliderRow(
    label: String,
    value: Int,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            color = TextLight
        )
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = CyanSurface
        ) {
            Text(
                "$value",
                color = CyanPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
            )
        }
    }
    Slider(
        value = value.toFloat(),
        onValueChange = onValueChange,
        valueRange = valueRange,
        steps = steps,
        colors = SliderDefaults.colors(
            thumbColor = CyanPrimary,
            activeTrackColor = CyanPrimary,
            inactiveTrackColor = CardBorder
        )
    )
}

@Composable
private fun StorageInfoRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            fontSize = 12.sp,
            color = TextMuted
        )
        Text(
            value,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextLight
        )
    }
}