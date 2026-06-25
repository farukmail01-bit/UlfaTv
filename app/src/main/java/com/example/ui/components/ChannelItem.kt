package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.example.data.LiveChannel

fun getChannelInitials(name: String): String {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return "TV"
    
    // Split by punctuation or spaces to get clean words
    val parts = trimmed.split(Regex("[\\s-_:\\.|/]+")).filter { it.isNotEmpty() }
    
    return when {
        parts.size >= 3 -> {
            (parts[0].take(1) + parts[1].take(1) + parts[2].take(1)).uppercase()
        }
        parts.size == 2 -> {
            val p1 = parts[0]
            val p2 = parts[1]
            if (p1.length >= 2) {
                (p1.take(2) + p2.take(1)).uppercase()
            } else {
                (p1.take(1) + p2.take(2)).uppercase()
            }
        }
        else -> {
            trimmed.take(3).uppercase()
        }
    }
}

@Composable
fun ChannelLetterPlaceholder(
    name: String,
    modifier: Modifier = Modifier
) {
    val initials = remember(name) { getChannelInitials(name) }
    val colors = remember {
        listOf(
            Color(0xFFE91E63), // Pink Accent
            Color(0xFF9C27B0), // Purple Accent
            Color(0xFF673AB7), // Dark Purple Accent
            Color(0xFF3F51B5), // Indigo Accent
            Color(0xFF2196F3), // Bright Blue
            Color(0xFF009688), // Teal Accent
            Color(0xFFE65100), // Rich Orange
            Color(0xFF00C853), // Green Accent
            Color(0xFF1A237E)  // Navy Accent
        )
    }
    val itemBgColor = remember(name) {
        val hash = name.hashCode()
        colors[kotlin.math.abs(hash) % colors.size]
    }

    Box(
        modifier = modifier
            .background(
                brush = androidx.compose.ui.graphics.Brush.linearGradient(
                    colors = listOf(
                        itemBgColor,
                        itemBgColor.copy(alpha = 0.75f)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            maxLines = 1
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChannelItem(
    channel: LiveChannel,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val isTv = remember(context) {
        val uiModeManager = context.getSystemService(android.content.Context.UI_MODE_SERVICE) as? android.app.UiModeManager
        uiModeManager?.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION ||
        context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK)
    }

    var isFocused by remember { mutableStateOf(false) }
    
    // Scale up slightly and change border color when focused to simulate Android TV remote selection highlight
    val scale by animateFloatAsState(targetValue = if (isTv && isFocused) 1.04f else 1.0f)
    val cardBgColor by animateColorAsState(
        targetValue = when {
            isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            isTv && isFocused -> MaterialTheme.colorScheme.surfaceVariant
            else -> MaterialTheme.colorScheme.surface
        }
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            isTv && isFocused -> MaterialTheme.colorScheme.primary
            isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
            else -> Color.Transparent
        }
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .border(2.dp, borderColor, RoundedCornerShape(12.dp))
            .onFocusChanged { state ->
                if (isTv) {
                    isFocused = state.isFocused
                }
            }
            .then(if (isTv) Modifier.focusable() else Modifier)
            .combinedClickable(
                onClick = onSelect,
                onLongClick = onToggleFavorite
            )
            .testTag("channel_item_${channel.name.replace(" ", "_")}"),
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isTv && isFocused) 6.dp else 1.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(10.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Channel Logo / Icon
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                val logoModel = com.example.data.getLogoModel(channel.logoUrl, channel.name)
                val isLocalLogo = logoModel is Int
                SubcomposeAsyncImage(
                    model = logoModel,
                    contentDescription = "${channel.name} logo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(if (isLocalLogo) 6.dp else 4.dp),
                    error = {
                        ChannelLetterPlaceholder(name = channel.name, modifier = Modifier.fillMaxSize())
                    }
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Channel Name & Category info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    Text(
                        text = channel.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isSelected) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .background(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "NOW PLAYING",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(2.dp))
                
                Text(
                    text = channel.category,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Favorite Indicator Icon button
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier
                    .size(40.dp)
                    .testTag("favorite_button_${channel.name.replace(" ", "_")}")
            ) {
                Icon(
                    imageVector = if (channel.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = if (channel.isFavorite) "Remove from favorites" else "Add to favorites",
                    tint = if (channel.isFavorite) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}
