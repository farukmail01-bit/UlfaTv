package com.example.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.data.Channel
import com.example.viewmodel.TvViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@UnstableApi
@Composable
fun VideoPlayer(
    channel: Channel,
    viewModel: TvViewModel,
    modifier: Modifier = Modifier,
    isFullscreen: Boolean = false,
    onToggleFullscreen: () -> Unit = {}
) {
    val baseContext = LocalContext.current
    val context = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
        baseContext.createAttributionContext("media")
    } else {
        baseContext
    }
    val coroutineScope = rememberCoroutineScope()

    // Collect playback configurations from viewmodel
    val isPlayingState by viewModel.isPlaying.collectAsState()
    val aspectRatioMode by viewModel.aspectRatioMode.collectAsState()

    // Player instances
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    var isBuffering by remember { mutableStateOf(true) }
    var playbackError by remember { mutableStateOf<String?>(null) }

    // On-screen overlay controls state
    var showControls by remember { mutableStateOf(false) }

    // Auto-hide controls helper
    LaunchedEffect(showControls) {
        if (showControls) {
            delay(4000)
            showControls = false
        }
    }

    // Initialize ExoPlayer
    DisposableEffect(channel.url) {
        val exoPlayer = ExoPlayer.Builder(context).build().apply {
            val mediaItem = if (channel.url.contains(".m3u8")) {
                MediaItem.Builder()
                    .setUri(channel.url)
                    .setMimeType(MimeTypes.APPLICATION_M3U8)
                    .build()
            } else {
                MediaItem.fromUri(channel.url)
            }
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = isPlayingState

            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    isBuffering = state == Player.STATE_BUFFERING
                    if (state == Player.STATE_READY) {
                        playbackError = null
                    }
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    isBuffering = false
                    playbackError = "Playback failed: ${error.localizedMessage ?: "Codec/Network error"}"
                }
            })
        }

        player = exoPlayer

        onDispose {
            exoPlayer.release()
            player = null
        }
    }

    // Sync play/pause with Viewmodel state
    LaunchedEffect(isPlayingState) {
        player?.playWhenReady = isPlayingState
    }

    // Handle Volume & Brightness HUD gesture adjustment
    BoxWithConstraints(
        modifier = modifier
            .testTag("video_player_container")
            .background(Color.Black)
            .pointerInput(Unit) {
                // Brightness & Volume Gestures
                var startVolume = 0f
                var startBrightness = 0.5f

                detectDragGestures(
                    onDragStart = { offset ->
                        showControls = false
                        // Set start parameters
                        startVolume = player?.volume ?: 1f

                        val activity = context.findActivity()
                        val lp = activity?.window?.attributes
                        startBrightness = if (lp != null && lp.screenBrightness >= 0f) {
                            lp.screenBrightness
                        } else {
                            0.5f // Default
                        }
                    },
                    onDragEnd = {
                        // Hide HUDs after short delay
                        coroutineScope.launch {
                            delay(1000)
                            viewModel.hideVolumeHud()
                            viewModel.hideBrightnessHud()
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val containerWidth = size.width
                        val containerHeight = size.height
                        val dragPercentY = -dragAmount.y / containerHeight

                        if (change.position.x < containerWidth / 2) {
                            // Left Side: Brightness Swipe
                            val newBrightness = (startBrightness + dragPercentY).coerceIn(0.01f, 1f)
                            startBrightness = newBrightness

                            val activity = context.findActivity()
                            activity?.runOnUiThread {
                                val lp = activity.window.attributes
                                lp.screenBrightness = newBrightness
                                activity.window.attributes = lp
                            }
                            viewModel.showBrightnessHud(newBrightness)
                        } else {
                            // Right Side: Volume Swipe
                            val newVolPercent = (startVolume + dragPercentY).coerceIn(0f, 1f)
                            startVolume = newVolPercent

                            player?.volume = newVolPercent
                            viewModel.showVolumeHud(newVolPercent)
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { showControls = !showControls },
                    onDoubleTap = { viewModel.nextAspectRatio() }
                )
            }
    ) {
        val width = constraints.maxWidth
        val height = constraints.maxHeight

        // AndroidView rendering Media3 PlayerView
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false // Use our elegant custom controls
                    keepScreenOn = true
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { playerView ->
                playerView.player = player
                // Scale Modes matching aspect ratio selection:
                // 0 = Fit, 1 = Fill, 2 = 16:9, 3 = Zoom
                playerView.resizeMode = when (aspectRatioMode) {
                    1 -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    2 -> AspectRatioFrameLayout.RESIZE_MODE_FIT // handled explicitly below if custom, or fit
                    3 -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Loading overlay
        if (isBuffering) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 4.dp
                )
            }
        }

        // Playback Error message
        playbackError?.let { err ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.8f))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Unable to Stream",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.Red
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.LightGray
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    IconButton(
                        onClick = {
                            player?.prepare()
                            player?.play()
                            playbackError = null
                        },
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp))
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Retry")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Retry", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }

        // Custom Overlay UI Controls (Play/Pause, Fullscreen, Aspect ratio HUD)
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
            ) {
                // Header (Channel Name & Info)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = channel.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                        Text(
                            text = channel.category,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.LightGray
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))

                    // Mode labels
                    val aspectLabel = when (aspectRatioMode) {
                        1 -> "FILL"
                        2 -> "16:9"
                        3 -> "ZOOM"
                        else -> "FIT"
                    }
                    Text(
                        text = aspectLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        modifier = Modifier
                            .background(Color.DarkGray.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                // Center Play / Pause Action
                IconButton(
                    onClick = { viewModel.togglePlayPause() },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(50))
                        .size(64.dp)
                ) {
                    Icon(
                        imageVector = if (isPlayingState) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Footer actions (Aspect Ratio and Fullscreen toggle)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { viewModel.nextAspectRatio() }
                    ) {
                        Icon(
                            imageVector = Icons.Default.AspectRatio,
                            contentDescription = "Aspect Ratio Mode",
                            tint = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    IconButton(
                        onClick = onToggleFullscreen
                    ) {
                        Icon(
                            imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                            contentDescription = "Toggle Fullscreen",
                            tint = Color.White
                        )
                    }
                }
            }
        }

        // Live Dynamic swipe HUD display (Volume)
        val hudVolVal by viewModel.hudVolume.collectAsState()
        hudVolVal?.let { level ->
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 24.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 12.dp, vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.VolumeUp, contentDescription = "Volume", tint = Color.White)
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(modifier = Modifier.height(80.dp).width(6.dp).background(Color.DarkGray, RoundedCornerShape(3.dp))) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(level)
                                .align(Alignment.BottomCenter)
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp))
                        )
                    }
                }
            }
        }

        // Live Dynamic swipe HUD display (Brightness)
        val hudBrightVal by viewModel.hudBrightness.collectAsState()
        hudBrightVal?.let { level ->
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 24.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 12.dp, vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.BrightnessLow, contentDescription = "Brightness", tint = Color.White)
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(modifier = Modifier.height(80.dp).width(6.dp).background(Color.DarkGray, RoundedCornerShape(3.dp))) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(level)
                                .align(Alignment.BottomCenter)
                                .background(Color.Yellow, RoundedCornerShape(3.dp))
                        )
                    }
                }
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
