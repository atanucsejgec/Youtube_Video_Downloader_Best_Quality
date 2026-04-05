package com.example.youtubedownloader.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.youtubedownloader.SearchResult

@Composable
fun SearchResultsList(
    results: List<SearchResult>,
    isVisible: Boolean,
    onAddToQueue: (SearchResult) -> Unit
) {
    AnimatedVisibility(
        visible = isVisible && results.isNotEmpty(),
        enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { -20 },
        exit = fadeOut(tween(250)) + slideOutVertically(tween(250)) { -20 }
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, CardBorder, RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardDark.copy(alpha = 0.95f))
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${results.size} results",
                        color = CyanMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                HorizontalDivider(color = CardBorder)

                // Show up to 15 results
                results.take(15).forEach { result ->
                    SearchResultItem(
                        result = result,
                        onAdd = { onAddToQueue(result) }
                    )
                    HorizontalDivider(color = CardBorder.copy(alpha = 0.5f))
                }
            }
        }
    }
}

@Composable
fun SearchResultItem(
    result: SearchResult,
    onAdd: () -> Unit
) {
    var adding by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Thumbnail
        Box(
            modifier = Modifier
                .width(80.dp)
                .height(50.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(CardLight)
        ) {
            result.thumbnail?.let { url ->
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            // Duration badge
            if (result.duration > 0) {
                Surface(
                    color = Color.Black.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(3.dp)
                ) {
                    Text(
                        formatDurationShort(result.duration),
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
        }

        // Info
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                result.title,
                color = TextWhite,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 17.sp
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    Icons.Default.Person,
                    null,
                    tint = TextDark,
                    modifier = Modifier.size(11.dp)
                )
                Text(
                    result.uploader,
                    color = TextDark,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Add button
        AnimatedContent(
            targetState = adding,
            transitionSpec = {
                fadeIn(tween(200)) togetherWith fadeOut(tween(200))
            },
            label = "add_btn"
        ) { isAdding ->
            if (isAdding) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    color = CyanPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Surface(
                    color = CyanSurface,
                    shape = CircleShape,
                    modifier = Modifier
                        .size(36.dp)
                        .clickable {
                            adding = true
                            onAdd()
                        }
                ) {
                    Icon(
                        Icons.Default.Add,
                        "Add to queue",
                        tint = CyanPrimary,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    }
}

private fun formatDurationShort(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}