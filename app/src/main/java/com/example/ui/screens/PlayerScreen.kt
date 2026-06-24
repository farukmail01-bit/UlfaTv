package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.view.KeyEvent
import coil.compose.rememberAsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import com.example.ui.components.ChannelLetterPlaceholder
import com.example.data.LiveChannel
import com.example.ui.MainViewModel
import com.example.ui.SyncState
import com.example.ui.components.CategoryTab
import com.example.ui.components.ChannelItem
import com.example.ui.player.VideoPlayer

data class PlayerParams(
    val url: String?,
    val bufferProfile: String,
    val videoQuality: String,
    val resizeMode: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    viewModel: MainViewModel,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val syncState by viewModel.syncState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val selectedChannel by viewModel.selectedChannel.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val channels by viewModel.uiChannels.collectAsState()
    val bufferProfile by viewModel.bufferProfile.collectAsState()
    val videoQuality by viewModel.videoQuality.collectAsState()

    var resizeMode by remember { mutableStateOf(0) } // 0 = FIT, 3 = ZOOM

    val focusManager = LocalFocusManager.current
    val playerFocusRequester = remember { FocusRequester() }
    val channelListFocusRequester = remember { FocusRequester() }
    val searchFocusRequester = remember { FocusRequester() }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE || configuration.screenWidthDp >= 600

    val isSidebarVisible by viewModel.isSidebarVisible.collectAsState()
    var lastActivityTime by remember { mutableStateOf(System.currentTimeMillis()) }

    val context = LocalContext.current
    val activity = context.findActivity()

    androidx.activity.compose.BackHandler {
        if (isLandscape && isSidebarVisible) {
            viewModel.setSidebarVisible(false)
            lastActivityTime = java.lang.System.currentTimeMillis()
        } else {
            // Completely terminate the activity and exit process to prevent background audio playback
            activity?.finishAndRemoveTask()
            java.lang.System.exit(0)
        }
    }

    // Auto-hide the sidebar in landscape mode if there's no user activity for 5 seconds
    LaunchedEffect(isSidebarVisible, lastActivityTime, isLandscape) {
        if (isLandscape && isSidebarVisible) {
            kotlinx.coroutines.delay(5000)
            viewModel.setSidebarVisible(false)
        }
    }

    // Reset visibility when layout orientation changes
    LaunchedEffect(isLandscape) {
        viewModel.setSidebarVisible(true)
    }

    // Sync remote control focus based on sidebar visibility
    LaunchedEffect(isSidebarVisible) {
        if (isSidebarVisible) {
            try {
                channelListFocusRequester.requestFocus()
            } catch (e: Exception) {}
        } else {
            try {
                playerFocusRequester.requestFocus()
            } catch (e: Exception) {}
        }
    }

    // Create a movable content of VideoPlayer to preserve state across layout orientation switches
    val movablePlayer = remember {
        movableContentOf { params: PlayerParams ->
            VideoPlayer(
                streamUrl = params.url,
                bufferProfile = params.bufferProfile,
                videoQuality = params.videoQuality,
                resizeMode = params.resizeMode,
                onResizeModeChange = { newMode ->
                    resizeMode = newMode
                },
                onNextChannel = { 
                    viewModel.playNextChannel() 
                    lastActivityTime = System.currentTimeMillis()
                },
                onPreviousChannel = { 
                    viewModel.playPreviousChannel() 
                    lastActivityTime = System.currentTimeMillis()
                },
                modifier = Modifier.fillMaxSize(),
                focusRequester = playerFocusRequester,
                onDpadLeft = {
                    viewModel.setSidebarVisible(false)
                    lastActivityTime = System.currentTimeMillis()
                },
                onDpadRight = {
                    viewModel.setSidebarVisible(true)
                    lastActivityTime = System.currentTimeMillis()
                    try {
                        channelListFocusRequester.requestFocus()
                    } catch (e: Exception) {}
                },
                showFocusBorder = isSidebarVisible,
                onSwipeLeft = {
                    viewModel.setSidebarVisible(false)
                    lastActivityTime = System.currentTimeMillis()
                },
                onSwipeRight = {
                    viewModel.setSidebarVisible(true)
                    lastActivityTime = System.currentTimeMillis()
                    if (isLandscape) {
                        try {
                            channelListFocusRequester.requestFocus()
                        } catch (e: Exception) {}
                    }
                },
                onTap = {
                    viewModel.setSidebarVisible(!isSidebarVisible)
                    lastActivityTime = System.currentTimeMillis()
                }
            )
        }
    }

    Scaffold(
        topBar = {
            if (!isLandscape) {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Tv,
                                contentDescription = null,
                                tint = Color(0xFFFF9800),
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = buildAnnotatedString {
                                    append("Ulfa")
                                    withStyle(style = SpanStyle(color = Color(0xFFE91E63))) {
                                        append("TV")
                                    }
                                    append(" Live")
                                },
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                    },
                    actions = {
                        val activity = LocalContext.current.findActivity()
                        // Force device into Landscape TV Mode
                        IconButton(
                            onClick = { 
                                activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE 
                            },
                            modifier = Modifier.testTag("appbar_fullscreen_button")
                        ) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Default.Fullscreen,
                                contentDescription = "TV Mode",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Toggle Aspect Ratio (Zoom to Fill vs Fit to Screen)
                        IconButton(
                            onClick = { 
                                resizeMode = if (resizeMode == 0) 3 else 0
                            },
                            modifier = Modifier.testTag("appbar_aspect_ratio_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AspectRatio,
                                contentDescription = "Toggle Zoom",
                                tint = if (resizeMode == 3) Color(0xFFFF9800) else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Refresh Channel Database manually
                        IconButton(
                            onClick = { viewModel.syncChannels() },
                            modifier = Modifier.testTag("appbar_sync_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "Refresh Channels List",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Navigate to Settings
                        IconButton(
                            onClick = onNavigateToSettings,
                            modifier = Modifier.testTag("appbar_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (isLandscape) {
                // Wide TV Screens & Landscape Layout: Split view side-by-side with inactivity auto-hide
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        // Track any touch or key interaction on screen to reset inactivity timing
                        .pointerInput(isLandscape) {
                            awaitPointerEventScope {
                                while (true) {
                                    awaitPointerEvent()
                                    lastActivityTime = System.currentTimeMillis()
                                }
                            }
                        }
                        // Swipe left/right gestures
                        .pointerInput(isLandscape) {
                            detectHorizontalDragGestures(
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    if (dragAmount < -15f) {
                                        viewModel.setSidebarVisible(false)
                                    } else if (dragAmount > 15f) {
                                        viewModel.setSidebarVisible(true)
                                    }
                                    lastActivityTime = System.currentTimeMillis()
                                }
                            )
                        }
                ) {
                    // Left Column (Browse / Choose panel) inside animated visibility
                    AnimatedVisibility(
                        visible = isSidebarVisible,
                        enter = slideInHorizontally(
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                        ) { -it } + fadeIn(),
                        exit = slideOutHorizontally(
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                        ) { -it } + fadeOut()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(340.dp)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
                                .border(width = 1.dp, color = Color.White.copy(alpha = 0.08f))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            // Landscape Header with Title, Sync, and Settings for TV & Landscape viewing
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Tv,
                                        contentDescription = null,
                                        tint = Color(0xFFFF9800),
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = buildAnnotatedString {
                                            append("Ulfa")
                                            withStyle(style = SpanStyle(color = Color(0xFFE91E63))) {
                                                append("TV")
                                            }
                                            append(" Live")
                                        },
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Row {
                                    val activity = LocalContext.current.findActivity()
                                    IconButton(
                                        onClick = { 
                                            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED 
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = androidx.compose.material.icons.Icons.Default.FullscreenExit,
                                            contentDescription = "Exit Fullscreen",
                                            tint = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { 
                                            resizeMode = if (resizeMode == 0) 3 else 0
                                        },
                                        modifier = Modifier.size(36.dp).testTag("appbar_aspect_ratio_button_landscape")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AspectRatio,
                                            contentDescription = "Toggle Zoom",
                                            tint = if (resizeMode == 3) Color(0xFFFF9800) else MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.syncChannels() },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Sync,
                                            contentDescription = "Sync",
                                            tint = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = onNavigateToSettings,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Settings,
                                            contentDescription = "Settings",
                                            tint = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }

                            // Search box
                            SearchChannelsBox(
                                query = searchQuery,
                                onQueryChange = { 
                                    viewModel.updateSearchQuery(it)
                                    lastActivityTime = System.currentTimeMillis()
                                }
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Category Tab horizontal scroll
                            CategoriesScrollRow(
                                categories = categories,
                                selectedCategory = selectedCategory,
                                onSelect = { 
                                    viewModel.selectCategory(it)
                                    lastActivityTime = System.currentTimeMillis()
                                }
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Sync State loading top indicator
                            if (syncState is SyncState.Syncing) {
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    color = Color(0xFFFF9800)
                                )
                            }

                            // Channels listing LazyColumn
                            ChannelsVerticalListing(
                                channels = channels,
                                selectedChannel = selectedChannel,
                                playerFocusRequester = playerFocusRequester,
                                channelListFocusRequester = channelListFocusRequester,
                                onSelect = { 
                                    viewModel.selectChannel(it)
                                    lastActivityTime = System.currentTimeMillis()
                                },
                                onToggleFavorite = { 
                                    viewModel.toggleFavorite(it)
                                    lastActivityTime = System.currentTimeMillis()
                                },
                                onSyncTrigger = { 
                                    viewModel.syncChannels()
                                    lastActivityTime = System.currentTimeMillis()
                                },
                                onCollapseSidebar = {
                                    viewModel.setSidebarVisible(false)
                                    lastActivityTime = System.currentTimeMillis()
                                }
                            )
                        }
                    }

                    // Right Column (Player View pane) - remaining space
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(1f)
                            .background(Color.Black)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(isSidebarVisible) {
                                    detectTapGestures(
                                        onTap = {
                                            // Tapping on the active player toggles sidebar visibility on landscape screens
                                            viewModel.setSidebarVisible(!isSidebarVisible)
                                            lastActivityTime = System.currentTimeMillis()
                                        }
                                    )
                                }
                        ) {
                            // Live Player Block
                            Box(modifier = Modifier.weight(1f)) {
                                movablePlayer(PlayerParams(selectedChannel?.url, bufferProfile, videoQuality, resizeMode))
                            }

                            // Channel Description Banner Below Player (Visible only when sidebar is open to preserve full-bleed cinematic mode when hidden)
                            if (isSidebarVisible) {
                                selectedChannel?.let { channel ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(MaterialTheme.colorScheme.surface)
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(42.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (!channel.logoUrl.isNullOrEmpty()) {
                                                SubcomposeAsyncImage(
                                                    model = channel.logoUrl,
                                                    contentDescription = null,
                                                    contentScale = ContentScale.Fit,
                                                    modifier = Modifier.fillMaxSize(),
                                                    error = {
                                                        ChannelLetterPlaceholder(name = channel.name, modifier = Modifier.fillMaxSize())
                                                     }
                                                )
                                            } else {
                                                ChannelLetterPlaceholder(name = channel.name, modifier = Modifier.fillMaxSize())
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = "Playing: ${channel.name}",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "Category: ${channel.category} | Buffer optimization: ${bufferProfile.uppercase()}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Compact Mobile Screens: Top Player + Bottom List
                Column(modifier = Modifier.fillMaxSize()) {
                    
                    // Top Player pane (16:9 box)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .background(Color.Black)
                    ) {
                        movablePlayer(PlayerParams(selectedChannel?.url, bufferProfile, videoQuality, resizeMode))
                    }

                    // Bottom Browse pane
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        // Search box
                        SearchChannelsBox(
                            query = searchQuery,
                            onQueryChange = { viewModel.updateSearchQuery(it) }
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Category Tabs scroll
                        CategoriesScrollRow(
                            categories = categories,
                            selectedCategory = selectedCategory,
                            onSelect = { viewModel.selectCategory(it) }
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Sync State loading top indicator
                        if (syncState is SyncState.Syncing) {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                color = Color(0xFFFF9800)
                            )
                        }

                        // Channels listing LazyColumn
                        ChannelsVerticalListing(
                            channels = channels,
                            selectedChannel = selectedChannel,
                            playerFocusRequester = playerFocusRequester,
                            channelListFocusRequester = channelListFocusRequester,
                            onSelect = { viewModel.selectChannel(it) },
                            onToggleFavorite = { viewModel.toggleFavorite(it) },
                            onSyncTrigger = { viewModel.syncChannels() }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchChannelsBox(
    query: String,
    onQueryChange: (String) -> Unit
) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    androidx.compose.foundation.text.BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .testTag("search_channels_input"),
        singleLine = true,
        interactionSource = interactionSource,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { innerTextField ->
            OutlinedTextFieldDefaults.DecorationBox(
                value = query,
                innerTextField = innerTextField,
                enabled = true,
                singleLine = true,
                visualTransformation = androidx.compose.ui.text.input.VisualTransformation.None,
                interactionSource = interactionSource,
                placeholder = { 
                    Text(
                        "Search TV Channels...", 
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    ) 
                },
                leadingIcon = { 
                    Icon(
                        imageVector = Icons.Default.Search, 
                        contentDescription = "Search",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    ) 
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(
                            onClick = { onQueryChange("") },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear, 
                                contentDescription = "Clear",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                container = {
                    OutlinedTextFieldDefaults.ContainerBox(
                        enabled = true,
                        isError = false,
                        interactionSource = interactionSource,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        focusedBorderThickness = 1.dp,
                        unfocusedBorderThickness = 1.dp
                    )
                }
            )
        }
    )
}

@Composable
fun CategoriesScrollRow(
    categories: List<String>,
    selectedCategory: String,
    onSelect: (String) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("categories_row"),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(categories) { category ->
            CategoryTab(
                category = category,
                isSelected = category == selectedCategory,
                onSelect = { onSelect(category) }
            )
        }
    }
}

@Composable
fun ColumnScope.ChannelsVerticalListing(
    channels: List<LiveChannel>,
    selectedChannel: LiveChannel?,
    playerFocusRequester: FocusRequester,
    channelListFocusRequester: FocusRequester,
    onSelect: (LiveChannel) -> Unit,
    onToggleFavorite: (LiveChannel) -> Unit,
    onSyncTrigger: () -> Unit,
    onCollapseSidebar: () -> Unit = {}
) {
    if (channels.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.TvOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "No Channels Cached",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Sync channels database from repository playlist to watch live. Offline backup data works beautifully here.",
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(horizontal = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onSyncTrigger,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.testTag("empty_list_sync_button")
                ) {
                    Icon(imageVector = Icons.Default.Sync, contentDescription = "Sync")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Load/Sync Channels List")
                }
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag("channels_lazy_list"),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            items(channels, key = { it.url }) { channel ->
                val isCurrentSelected = selectedChannel?.url == channel.url || (selectedChannel == null && channels.firstOrNull()?.url == channel.url)
                val itemRequester = if (isCurrentSelected) channelListFocusRequester else remember { FocusRequester() }

                ChannelItem(
                    channel = channel,
                    isSelected = selectedChannel?.url == channel.url,
                    onSelect = { onSelect(channel) },
                    onToggleFavorite = { onToggleFavorite(channel) },
                    modifier = Modifier
                        .focusRequester(itemRequester)
                        .onKeyEvent { keyEvent ->
                            if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                when (keyEvent.nativeKeyEvent.keyCode) {
                                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                        try {
                                            playerFocusRequester.requestFocus()
                                        } catch (e: Exception) {}
                                        true
                                    }
                                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                                        onCollapseSidebar()
                                        try {
                                            playerFocusRequester.requestFocus()
                                        } catch (e: Exception) {}
                                        true
                                    }
                                    else -> false
                                }
                            } else {
                                false
                            }
                        }
                )
            }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var currentContext = this
    while (currentContext is ContextWrapper) {
        if (currentContext is Activity) {
            return currentContext
        }
        currentContext = currentContext.baseContext
    }
    return currentContext as? Activity
}
