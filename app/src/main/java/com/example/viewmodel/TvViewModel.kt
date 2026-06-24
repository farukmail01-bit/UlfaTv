package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.api.GeminiClient
import com.example.data.AppDatabase
import com.example.data.Channel
import com.example.data.ChatMessage
import com.example.data.PlaylistItem
import com.example.data.TvRepository
import com.example.data.WatchHistory
import com.example.parser.M3uParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class TvViewModel(private val repository: TvRepository) : ViewModel() {

    // Active screen state (Tabs)
    private val _currentTab = MutableStateFlow(0) // 0 = Channels, 1 = Playlists, 2 = AI Guide, 3 = History
    val currentTab: StateFlow<Int> = _currentTab.asStateFlow()

    // Search query and category filter
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    // Current playing stream
    private val _currentChannel = MutableStateFlow<Channel?>(null)
    val currentChannel: StateFlow<Channel?> = _currentChannel.asStateFlow()

    // Playback state
    private val _isPlaying = MutableStateFlow(true)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _aspectRatioMode = MutableStateFlow(0) // 0 = Fit, 1 = Fill, 2 = 16:9, 3 = Zoom
    val aspectRatioMode: StateFlow<Int> = _aspectRatioMode.asStateFlow()

    // Playlist loading state
    private val _playlistLoadingState = MutableStateFlow<PlaylistLoadState>(PlaylistLoadState.Idle)
    val playlistLoadingState: StateFlow<PlaylistLoadState> = _playlistLoadingState.asStateFlow()

    // AI Chat state
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage(
                sender = ChatMessage.Sender.AI,
                text = "Welcome to **UlfaTV AI Guide**! 📺✨\n\nI can recommend channels, tell you what's available, or guide you on importing custom IPTV playlists. What are you in the mood to watch today?"
            )
        )
    )
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _isAiTyping = MutableStateFlow(false)
    val isAiTyping: StateFlow<Boolean> = _isAiTyping.asStateFlow()

    // HUD overlays
    private val _hudVolume = MutableStateFlow<Float?>(null) // null = hidden, otherwise 0f..1f
    val hudVolume: StateFlow<Float?> = _hudVolume.asStateFlow()

    private val _hudBrightness = MutableStateFlow<Float?>(null) // null = hidden, otherwise 0f..1f
    val hudBrightness: StateFlow<Float?> = _hudBrightness.asStateFlow()

    // Combine flows for advanced channel filtering
    val allChannels: StateFlow<List<Channel>> = repository.allChannels
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteChannels: StateFlow<List<Channel>> = repository.favoriteChannels
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlists: StateFlow<List<PlaylistItem>> = repository.allPlaylists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val watchHistory: StateFlow<List<WatchHistory>> = repository.watchHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Filtered channels listing
    val filteredChannels: StateFlow<List<Channel>> = combine(
        allChannels,
        _searchQuery,
        _selectedCategory
    ) { channels, query, category ->
        channels.filter { channel ->
            val matchesSearch = channel.name.contains(query, ignoreCase = true) ||
                    channel.category.contains(query, ignoreCase = true)
            val matchesCategory = category == "All" || channel.category == category
            matchesSearch && matchesCategory
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Unique Categories list derived from current channels
    val categories: StateFlow<List<String>> = allChannels.combine(_selectedCategory) { channels, _ ->
        val base = channels.map { it.category }.distinct().sorted()
        listOf("All") + base
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf("All"))

    init {
        viewModelScope.launch {
            repository.checkAndPrepopulate()
            // Set first channel as default playing channel on startup
            val list = repository.allChannels.first()
            if (list.isNotEmpty() && _currentChannel.value == null) {
                _currentChannel.value = list.first()
            }
        }
    }

    fun setTab(tabIndex: Int) {
        _currentTab.value = tabIndex
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setCategory(category: String) {
        _selectedCategory.value = category
    }

    fun selectChannel(channel: Channel) {
        _currentChannel.value = channel
        _isPlaying.value = true
        viewModelScope.launch {
            repository.insertWatchHistory(channel)
        }
    }

    fun togglePlayPause() {
        _isPlaying.value = !_isPlaying.value
    }

    fun nextAspectRatio() {
        _aspectRatioMode.value = (_aspectRatioMode.value + 1) % 4
    }

    fun toggleFavorite(channel: Channel) {
        viewModelScope.launch {
            repository.toggleFavorite(channel)
        }
    }

    // Playback control HUDs
    fun showVolumeHud(level: Float) {
        _hudVolume.value = level.coerceIn(0f, 1f)
    }

    fun hideVolumeHud() {
        _hudVolume.value = null
    }

    fun showBrightnessHud(level: Float) {
        _hudBrightness.value = level.coerceIn(0f, 1f)
    }

    fun hideBrightnessHud() {
        _hudBrightness.value = null
    }

    // Load IPTV Playlist from URL
    fun loadPlaylistFromUrl(name: String, url: String) {
        viewModelScope.launch {
            _playlistLoadingState.value = PlaylistLoadState.Loading
            try {
                val m3uContent = withContext(Dispatchers.IO) {
                    val client = OkHttpClient()
                    val request = Request.Builder().url(url).build()
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) throw Exception("Failed to fetch: HTTP ${response.code}")
                        response.body?.string() ?: throw Exception("Empty response body")
                    }
                }

                val parsedChannels = withContext(Dispatchers.Default) {
                    M3uParser.parse(m3uContent, name)
                }

                if (parsedChannels.isEmpty()) {
                    _playlistLoadingState.value = PlaylistLoadState.Error("No valid TV streams found in M3U file.")
                } else {
                    repository.addPlaylist(name, url, parsedChannels)
                    _playlistLoadingState.value = PlaylistLoadState.Success(parsedChannels.size)
                }
            } catch (e: Exception) {
                _playlistLoadingState.value = PlaylistLoadState.Error(e.localizedMessage ?: "Network or parsing error.")
            }
        }
    }

    // Load IPTV Playlist from pasted text
    fun loadPlaylistFromText(name: String, text: String) {
        viewModelScope.launch {
            _playlistLoadingState.value = PlaylistLoadState.Loading
            try {
                val parsedChannels = withContext(Dispatchers.Default) {
                    M3uParser.parse(text, name)
                }

                if (parsedChannels.isEmpty()) {
                    _playlistLoadingState.value = PlaylistLoadState.Error("No valid TV streams found in M3U text.")
                } else {
                    repository.addPlaylist(name, null, parsedChannels)
                    _playlistLoadingState.value = PlaylistLoadState.Success(parsedChannels.size)
                }
            } catch (e: Exception) {
                _playlistLoadingState.value = PlaylistLoadState.Error(e.localizedMessage ?: "Parsing error.")
            }
        }
    }

    fun clearPlaylistState() {
        _playlistLoadingState.value = PlaylistLoadState.Idle
    }

    fun deletePlaylist(playlist: PlaylistItem) {
        viewModelScope.launch {
            repository.deletePlaylist(playlist)
            // If playing a channel that belonged to deleted playlist, fallback to first channel
            val current = _currentChannel.value
            if (current != null && current.playlistName == playlist.name) {
                val remaining = allChannels.first()
                if (remaining.isNotEmpty()) {
                    selectChannel(remaining.first())
                } else {
                    _currentChannel.value = null
                }
            }
        }
    }

    fun clearAllCustomData() {
        viewModelScope.launch {
            repository.clearAllCustomPlaylists()
            val remaining = allChannels.first()
            if (remaining.isNotEmpty()) {
                selectChannel(remaining.first())
            } else {
                _currentChannel.value = null
            }
        }
    }

    fun clearWatchHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    // Gemini AI Chat integration
    fun sendChatMessage(text: String) {
        if (text.trim().isEmpty()) return

        val userMsg = ChatMessage(sender = ChatMessage.Sender.USER, text = text)
        _chatMessages.value = _chatMessages.value + userMsg
        _isAiTyping.value = true

        viewModelScope.launch {
            // Prep info about our channels to inject as context
            val currentList = allChannels.value
            val channelsInfo = currentList.joinToString("\n") {
                "- ${it.name} [Category: ${it.category}] - Stream URL: ${it.url}"
            }

            val replyText = GeminiClient.generateResponse(text, _chatMessages.value, channelsInfo)
            
            // Look for any stream URL from our channels mentioned in the response to make it playable
            var recUrl: String? = null
            for (ch in currentList) {
                if (replyText.contains(ch.url, ignoreCase = true) || replyText.contains(ch.name, ignoreCase = true)) {
                    recUrl = ch.url
                    break
                }
            }

            // Fallback: search for any url in the string if no direct catalog match
            if (recUrl == null) {
                val urlRegex = "(https?://[^\\s)]+)".toRegex()
                val match = urlRegex.find(replyText)
                if (match != null) {
                    recUrl = match.value
                }
            }

            val aiMsg = ChatMessage(
                sender = ChatMessage.Sender.AI,
                text = replyText,
                recommendedChannelUrl = recUrl
            )

            _chatMessages.value = _chatMessages.value + aiMsg
            _isAiTyping.value = false
        }
    }

    fun playStreamUrlDirectly(url: String, label: String) {
        val existing = allChannels.value.find { it.url == url }
        if (existing != null) {
            selectChannel(existing)
        } else {
            // Temporary dynamic channel
            val tempChannel = Channel(
                name = label,
                url = url,
                category = "AI Stream Recommendation",
                isCustom = true
            )
            _currentChannel.value = tempChannel
            _isPlaying.value = true
        }
    }
}

sealed interface PlaylistLoadState {
    object Idle : PlaylistLoadState
    object Loading : PlaylistLoadState
    data class Success(val count: Int) : PlaylistLoadState
    data class Error(val message: String) : PlaylistLoadState
}

class TvViewModelFactory(private val repository: TvRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TvViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TvViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
