package com.example.ui.player

import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import okhttp3.HttpUrl.Companion.toHttpUrl
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(
    streamUrl: String?,
    bufferProfile: String,
    videoQuality: String,
    resizeMode: Int,
    onResizeModeChange: (Int) -> Unit,
    onNextChannel: () -> Unit,
    onPreviousChannel: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = remember { FocusRequester() },
    onDpadLeft: () -> Unit = {},
    onDpadRight: () -> Unit = {},
    showFocusBorder: Boolean = true,
    onSwipeLeft: (() -> Unit)? = null,
    onSwipeRight: (() -> Unit)? = null,
    onTap: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var isBuffering by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    var isFocused by remember { mutableStateOf(false) }

    var zoomFeedbackMessage by remember { mutableStateOf<String?>(null) }
    var showZoomFeedback by remember { mutableStateOf(false) }

    LaunchedEffect(showZoomFeedback) {
        if (showZoomFeedback) {
            delay(2000)
            showZoomFeedback = false
        }
    }

    val currentOnNextChannel by rememberUpdatedState(onNextChannel)

    // Auto-skip unplayable/offline channels gracefully after 3 seconds
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            delay(3000)
            errorMessage = null
            currentOnNextChannel()
        }
    }

    // Recreate or configure ExoPlayer when bufferProfile or videoQuality parameters change
    DisposableEffect(bufferProfile, videoQuality) {
        // 1. Build Custom LoadControl matching user buffer optimization setting
        val loadControlBuilder = DefaultLoadControl.Builder()
        val loadControl = when (bufferProfile) {
            "low_latency" -> {
                loadControlBuilder.setBufferDurationsMs(
                    8000,    // Min buffer
                    15000,   // Max buffer
                    1000,    // Buffer for playback start
                    2000     // Buffer after rebuffer
                ).build()
            }
            "low_internet" -> {
                // Large buffering configuration (safeguard for low speed lines)
                loadControlBuilder.setBufferDurationsMs(
                    60000,   // Min buffer 60 seconds
                    120000,  // Max buffer 120 seconds
                    6000,    // Starts after 6 seconds
                    12000    // Starts after 12 seconds rebuffering
                ).build()
            }
            else -> { // "stable" (Default)
                loadControlBuilder.setBufferDurationsMs(
                    25000,   // Min buffer
                    50000,   // Max buffer
                    2000,    // Starts after 2 seconds
                    4000     // Starts after 4 seconds rebuffering
                ).build()
            }
        }

        // 2. Configure video bounds matching user quality settings
        val baseTrackParams = TrackSelectionParameters.Builder(context)
        val trackParams = when (videoQuality) {
            "low" -> baseTrackParams.setMaxVideoSize(426, 240).build()
            "medium" -> baseTrackParams.setMaxVideoSize(854, 480).build()
            "high" -> baseTrackParams.setMaxVideoSize(1280, 720).build()
            else -> baseTrackParams.build() // auto
        }

        val audioAttributes = androidx.media3.common.AudioAttributes.Builder()
            .setUsage(androidx.media3.common.C.USAGE_MEDIA)
            .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        val player = ExoPlayer.Builder(context.applicationContext)
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, true)
            .build()
            .apply {
                trackSelectionParameters = trackParams
                repeatMode = Player.REPEAT_MODE_OFF
                playWhenReady = true
            }

        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    errorMessage = null
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                android.util.Log.e("VideoPlayer", "ExoPlayer playback error: ${error.message}", error)
                val errorCodeName = error.errorCodeName
                val isNetworkError = errorCodeName.contains("NETWORK") || errorCodeName.contains("DNS") || errorCodeName.contains("TIMEOUT")
                
                val baseMsg = if (isNetworkError) {
                    "This channel is Geo-Blocked or restricted to Local ISP Networks (BDIX).\n\nThe Web Preview Emulator cannot play it because it runs on Global Cloud Servers."
                } else {
                    "Stream Error: ${error.message ?: "Unknown Error"}\nCode: ${errorCodeName}"
                }
                
                errorMessage = "$baseMsg\n\n⚠️ Channel is offline / unplayable.\nSkipping to next channel automatically in 3 seconds...\n\n⚠️ চ্যানেলটি অফলাইন বা প্লে হচ্ছে না।\n৩ সেকেন্ডে পরবর্তী চ্যানেলে স্কিপ করা হচ্ছে..."
                isBuffering = false
            }
        }
        player.addListener(listener)

        exoPlayer = player

        onDispose {
            player.removeListener(listener)
            player.release()
            exoPlayer = null
        }
    }

    // Separately handle stream url changes on the existing ExoPlayer instance to avoid slow recreate freezes
    LaunchedEffect(streamUrl, exoPlayer) {
        val player = exoPlayer ?: return@LaunchedEffect
        if (streamUrl.isNullOrEmpty()) {
            player.stop()
            player.clearMediaItems()
            return@LaunchedEffect
        }

        player.stop()
        errorMessage = null
        isBuffering = true

        try {
            val userAgent = "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36"
            val okHttpClient = okhttp3.OkHttpClient.Builder()
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .dns(object : okhttp3.Dns {
                    override fun lookup(hostname: String): List<java.net.InetAddress> {
                        return try {
                            okhttp3.Dns.SYSTEM.lookup(hostname)
                        } catch (e: Exception) {
                            try {
                                val fallbackResolver = okhttp3.dnsoverhttps.DnsOverHttps.Builder()
                                    .client(okhttp3.OkHttpClient())
                                    .url("https://cloudflare-dns.com/dns-query".toHttpUrl()) // Use Cloudflare DoH
                                    .build()
                                fallbackResolver.lookup(hostname)
                            } catch (e2: Exception) {
                                throw java.net.UnknownHostException("Failed to resolve $hostname (Geo-Blocked/BDIX)")
                            }
                        }
                    }
                })
                .build()
            val httpDataSourceFactory = androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(okHttpClient)
                .setUserAgent(userAgent)

            val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
            val mediaSourceFactory = DefaultMediaSourceFactory(context)
                .setDataSourceFactory(dataSourceFactory)

            val mediaItemBuilder = MediaItem.Builder().setUri(streamUrl)
            val lowerUrl = streamUrl.lowercase()
            if (lowerUrl.contains("m3u8") || lowerUrl.contains(".m3u8")) {
                mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
            } else if (lowerUrl.contains(".mpd") || lowerUrl.contains("mpd")) {
                mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_MPD)
            }
            val mediaItem = mediaItemBuilder.build()
            
            val mediaSource = if (lowerUrl.contains("m3u8")) {
                androidx.media3.exoplayer.hls.HlsMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(mediaItem)
            } else {
                mediaSourceFactory.createMediaSource(mediaItem)
            }
            
            player.setMediaSource(mediaSource)
            player.prepare()
            player.playWhenReady = true
        } catch (e: Exception) {
            android.util.Log.e("VideoPlayer", "Error loading stream: ${e.message}", e)
            errorMessage = "Failed to load channel stream: ${e.message}"
            isBuffering = false
        }
    }

    // Monitor Activity Lifecycle to pause/resume playback on background/foreground transitions
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> {
                    exoPlayer?.pause()
                }
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                    exoPlayer?.play()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Box(
        modifier = modifier
            .background(Color.Black)
            .focusRequester(focusRequester)
            .onFocusChanged { state -> isFocused = state.isFocused }
            .focusable()
            .border(
                width = if (isFocused && showFocusBorder) 3.dp else 0.dp,
                color = if (isFocused && showFocusBorder) Color(0xFFFF9800) else Color.Transparent,
                shape = RoundedCornerShape(4.dp)
            )
             // Handle TV Box physical Remote hardware buttons or Mobile Keyboard inputs
            .onKeyEvent { event ->
                if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    when (event.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_PAGE_UP -> {
                            onNextChannel()
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_PAGE_DOWN -> {
                            onPreviousChannel()
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            onDpadLeft()
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            onDpadRight()
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                            exoPlayer?.let {
                                if (it.isPlaying) it.pause() else it.play()
                            }
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
    ) {
        if (streamUrl.isNullOrEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Select a channel to play", color = Color.White)
            }
        } else {
            // Android Media3 PlayerView inside Compose container
            AndroidView(
                factory = { _ ->
                    PlayerView(context).apply {
                        useController = false
                        keepScreenOn = true
                        isFocusable = false
                        isFocusableInTouchMode = false
                        descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = { view ->
                    view.player = exoPlayer
                    view.resizeMode = resizeMode
                },
                modifier = Modifier.fillMaxSize()
            )

            // Transparent Gesture Overlay Box covering the entire player area to handle swipe up/down/left/right and tap/double tap
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // 1. Single tap to toggle sidebar / channel list, double tap to toggle zoom
                    .pointerInput(resizeMode) {
                        detectTapGestures(
                            onTap = {
                                onTap?.invoke()
                            },
                            onDoubleTap = {
                                val newMode = if (resizeMode == 0) 3 else 0
                                onResizeModeChange(newMode)
                                zoomFeedbackMessage = if (newMode == 3) "Zoom to Fill (জুম টু ফিল)" else "Fit to Screen (ফিট টু স্ক্রীন)"
                                showZoomFeedback = true
                            }
                        )
                    }
                    // 2. Drag gestures for Swipes (Horizontal left/right and Vertical up/down)
                    .pointerInput(Unit) {
                        var totalDragX = 0f
                        var totalDragY = 0f
                        detectDragGestures(
                            onDragStart = {
                                totalDragX = 0f
                                totalDragY = 0f
                            },
                            onDragEnd = {
                                val absX = kotlin.math.abs(totalDragX)
                                val absY = kotlin.math.abs(totalDragY)
                                if (absX > absY) {
                                    if (absX > 80f) {
                                        if (totalDragX < 0) {
                                            onSwipeLeft?.invoke()
                                        } else {
                                            onSwipeRight?.invoke()
                                        }
                                    }
                                } else {
                                    if (absY > 80f) {
                                        if (totalDragY < 0) {
                                            onNextChannel()
                                        } else {
                                            onPreviousChannel()
                                        }
                                    }
                                }
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                totalDragX += dragAmount.x
                                totalDragY += dragAmount.y
                            }
                        )
                    }
                    // 3. YouTube-like pinch to zoom
                    .pointerInput(resizeMode) {
                        detectTransformGestures { _, _, zoom, _ ->
                            if (zoom > 1.05f) {
                                if (resizeMode != 3) {
                                    onResizeModeChange(3)
                                    zoomFeedbackMessage = "Zoom to Fill (জুম টু ফিল)"
                                    showZoomFeedback = true
                                }
                            } else if (zoom < 0.95f) {
                                if (resizeMode != 0) {
                                    onResizeModeChange(0)
                                    zoomFeedbackMessage = "Fit to Screen (ফিট টু স্ক্রীন)"
                                    showZoomFeedback = true
                                }
                            }
                        }
                    }
            )

            // Zoom/Aspect Ratio feedback message overlay
            AnimatedVisibility(
                visible = showZoomFeedback,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(20.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Default.AspectRatio,
                            contentDescription = null,
                            tint = Color(0xFFFF9800),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = zoomFeedbackMessage ?: "",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }



            // Buffering Indicator overlay
            if (isBuffering) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = Color(0xFFFF9800))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Buffering...", color = Color.White.copy(alpha = 0.8f))
                    }
                }
            }

            // Error Overlay Dialog
            errorMessage?.let { errorMsg ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.8f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text("⚠️ Stream Unreachable", color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            errorMsg,
                            color = Color.White.copy(alpha = 0.9f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        }
    }
}
