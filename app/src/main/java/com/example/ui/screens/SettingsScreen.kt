package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainViewModel
import com.example.ui.SyncState
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.activity.compose.BackHandler(onBack = onBack)

    val autoPlay by viewModel.autoPlayEnabled.collectAsState()
    val bufferProfile by viewModel.bufferProfile.collectAsState()
    val videoQuality by viewModel.videoQuality.collectAsState()
    val lastSyncTime by viewModel.lastSyncTime.collectAsState()
    val totalChannelsCount by viewModel.totalChannelsCount.collectAsState()
    val syncState by viewModel.syncState.collectAsState()

    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
    val formattedSyncTime = remember(lastSyncTime) {
        if (lastSyncTime > 0) dateFormat.format(Date(lastSyncTime)) else "Never Synchronized"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .widthIn(max = 800.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            
            // 1. General Preferences Card
            SettingsGroupCard(title = "General Preferences") {
                // Auto Play toggle row
                SettingsToggleRow(
                    icon = Icons.Default.PlayArrow,
                    title = "Auto-Play on Launch",
                    subtitle = "Automatically resume last or first channel upon app start.",
                    checked = autoPlay,
                    onCheckedChange = { viewModel.setAutoPlay(it) },
                    testTag = "setting_autoplay"
                )
            }

            // 2. Buffering Preferences Card (for Low Internet / Buffer-Free Experience)
            SettingsGroupCard(title = "Streaming & Load Controls") {
                Text(
                    text = "Optimize buffer sizes based on internet connection speed to maintain a stutter-free experience:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                val bufferOptions = listOf(
                    Triple("stable", "Stable Mode", "Balanced buffering profile suitable for normal connections."),
                    Triple("low_latency", "Fast Start Mode", "Instantly starts playing by applying minimal pre-cache."),
                    Triple("low_internet", "Zero Buffer Mode", "Pre-loads stream buffer continuously to prevent pauses under weak lines.")
                )

                bufferOptions.forEach { (profileName, title, desc) ->
                    SettingsSelectorRow(
                        title = title,
                        subtitle = desc,
                        selected = bufferProfile == profileName,
                        onClick = { viewModel.setBufferProfile(profileName) },
                        testTag = "setting_buffer_$profileName"
                    )
                }
            }

            // 3. Max Resolution / Quality Constraint settings
            SettingsGroupCard(title = "Video Quality Rules") {
                Text(
                    text = "Restrict stream bitrates dynamically to conserve internet bandwidth or maximize resolution limits:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                val qualityOptions = listOf(
                    Triple("auto", "Auto Adaptation", "Adapts codec resolution dynamically based on bandwidth."),
                    Triple("high", "High Resolution (720p limits)", "Forces stream content details using clear, rich details."),
                    Triple("medium", "Medium Resolution (480p limits)", "Medium quality constraints, highly recommended for mobile lines."),
                    Triple("low", "Low Quality / Eco Mode (240p limits)", "Minimum bandwidth usage, ideal for extremely low speed lines.")
                )

                qualityOptions.forEach { (qualName, title, desc) ->
                    SettingsSelectorRow(
                        title = title,
                        subtitle = desc,
                        selected = videoQuality == qualName,
                        onClick = { viewModel.setVideoQuality(qualName) },
                        testTag = "setting_quality_$qualName"
                    )
                }
            }

            // 4. Data Sync and Offline Mode
            SettingsGroupCard(title = "Playlist Sources & Sync") {
                val m3uUrl by viewModel.playlistUrlState.collectAsState()
                val m3uFilePath by viewModel.m3uFilePathState.collectAsState()

                var tempUrl by remember(m3uUrl) { mutableStateOf(m3uUrl) }
                var tempFilePath by remember(m3uFilePath) { mutableStateOf(m3uFilePath) }

                Column(
                    modifier = Modifier.padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Local Cache Stats Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Local Offline Cache Statistics",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Cached Channels: $totalChannelsCount",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Last Synchronized: $formattedSyncTime",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    // 4a. Remote Playlist URL
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "M3U Remote URL",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = tempUrl,
                                onValueChange = { tempUrl = it },
                                placeholder = { Text("https://example.com/playlist.m3u") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("setting_remote_url_input"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                )
                            )

                            Button(
                                onClick = {
                                    viewModel.saveM3uUrl(tempUrl)
                                    viewModel.syncChannels(triggerBackgroundScan = true)
                                },
                                enabled = syncState !is SyncState.Syncing && tempUrl.isNotBlank(),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("sync_url_button")
                            ) {
                                Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Sync URL")
                            }
                        }
                    }

                    // 4b. Local File Path Parser
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "M3U Local File Path",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Load custom M3U playlist file directly from local storage/device path (e.g. /sdcard/Download/iptv.m3u).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = tempFilePath,
                                onValueChange = { tempFilePath = it },
                                placeholder = { Text("/storage/emulated/0/Download/playlist.m3u") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("setting_file_path_input"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                )
                            )

                            Button(
                                onClick = {
                                    viewModel.syncChannelsFromFile(tempFilePath, triggerBackgroundScan = true)
                                },
                                enabled = syncState !is SyncState.Syncing && tempFilePath.isNotBlank(),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("sync_file_button")
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Load File")
                            }
                        }
                    }

                    // Show sync status helper text
                    when (val state = syncState) {
                        is SyncState.Syncing -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = state.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        is SyncState.Success -> {
                            Text(
                                text = "✓ Database synced successfully! Playlists are loaded into cache.",
                                color = Color(0xFF4CAF50),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        is SyncState.Error -> {
                            Text(
                                text = "⚠️ Sync failed: ${state.message}.",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        else -> {}
                    }

                    val backgroundScanState by viewModel.backgroundScanState.collectAsState()
                    backgroundScanState?.let { scanMsg ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = scanMsg,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // 5. Remote TV Control Guide Card
            SettingsGroupCard(title = "Remote Control Action Mapping") {
                Column(
                    modifier = Modifier.padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "If you are using a physical TV Box Remote or Mobile Keyboard, the following keys are mapped for smart fluid actions:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    
                    RemoteGuideItem(
                        icon = Icons.Default.ArrowUpward,
                        keys = "[▲ / ▼]  D-Pad UP/DOWN",
                        action = "Zap to Next / Previous Channel instantly"
                    )
                    RemoteGuideItem(
                        icon = Icons.Default.ArrowBack,
                        keys = "[◀]  D-Pad LEFT",
                        action = "Hide channels list sidebar for Full-Stream display"
                    )
                    RemoteGuideItem(
                        icon = Icons.Default.ArrowForward,
                        keys = "[▶]  D-Pad RIGHT",
                        action = "Show channels list sidebar to browse while playing"
                    )
                    RemoteGuideItem(
                        icon = Icons.Default.CheckCircle,
                        keys = "[ENTER] or [DPAD CENTER]",
                        action = "Toggle channels list sidebar (Show / Hide)"
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
            
            // Developer attribution footer
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "App Developer",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    letterSpacing = 1.5.sp
                )
                Text(
                    text = "Faruk Hossain",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

@Composable
fun SettingsGroupCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Divider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp)
            content()
        }
    }
}

@Composable
fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    var isFocused by remember { mutableStateOf(false) }
    val rowScale by animateFloatAsState(if (isFocused) 1.02f else 1.0f)
    val rowBgColor by animateColorAsState(
        if (isFocused) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        else Color.Transparent
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(rowScale)
            .background(rowBgColor, RoundedCornerShape(8.dp))
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable { onCheckedChange(!checked) }
            .padding(8.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            thumbContent = {
                Icon(
                    imageVector = if (checked) Icons.Filled.Check else Icons.Filled.Close,
                    contentDescription = null,
                    modifier = Modifier.size(SwitchDefaults.IconSize),
                    tint = if (checked) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFFFF9800),
                checkedTrackColor = Color(0xFFFF9800).copy(alpha = 0.3f),
                uncheckedThumbColor = Color.LightGray,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                checkedIconColor = Color.White,
                uncheckedIconColor = Color.Gray
            ),
            modifier = Modifier.padding(end = 4.dp)
        )
    }
}

@Composable
fun SettingsSelectorRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    var isFocused by remember { mutableStateOf(false) }
    val rowScale by animateFloatAsState(if (isFocused) 1.02f else 1.0f)
    val rowBgColor by animateColorAsState(
        when {
            selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
            isFocused -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else -> Color.Transparent
        }
    )
    val borderColor = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(rowScale)
            .background(rowBgColor, RoundedCornerShape(8.dp))
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .selectable(selected = selected, onClick = onClick)
            .padding(10.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun RemoteGuideItem(
    icon: ImageVector,
    keys: String,
    action: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = keys,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = action,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
