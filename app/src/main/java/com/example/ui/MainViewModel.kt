package com.example.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed interface SyncState {
    object Idle : SyncState
    data class Syncing(val message: String = "Parsing Playlist...") : SyncState
    object Success : SyncState
    data class Error(val message: String) : SyncState
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val channelDao = database.channelDao()
    private val preferenceDao = database.preferenceDao()
    val repository = ChannelRepository(channelDao, preferenceDao)

    private val defaultPlaylistUrl = "https://raw.githubusercontent.com/farukmail24/UlfaTV/main/LiveTV.m3u"

    private val _playlistUrlState = MutableStateFlow(defaultPlaylistUrl)
    val playlistUrlState: StateFlow<String> = _playlistUrlState.asStateFlow()

    private val _m3uFilePathState = MutableStateFlow("")
    val m3uFilePathState: StateFlow<String> = _m3uFilePathState.asStateFlow()

    // UI States
    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _selectedChannel = MutableStateFlow<LiveChannel?>(null)
    val selectedChannel: StateFlow<LiveChannel?> = _selectedChannel.asStateFlow()

    // Preferences States
    private val _autoPlayEnabled = MutableStateFlow(true)
    val autoPlayEnabled: StateFlow<Boolean> = _autoPlayEnabled.asStateFlow()

    private val _dpadSidebarControlEnabled = MutableStateFlow(false)
    val dpadSidebarControlEnabled: StateFlow<Boolean> = _dpadSidebarControlEnabled.asStateFlow()

    private val _bufferProfile = MutableStateFlow("stable") // stable, low_latency, low_internet
    val bufferProfile: StateFlow<String> = _bufferProfile.asStateFlow()

    private val _videoQuality = MutableStateFlow("auto") // auto, low, medium, high
    val videoQuality: StateFlow<String> = _videoQuality.asStateFlow()

    private val _themeMode = MutableStateFlow("dark") // dark, light
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _lastWatchedUrl = MutableStateFlow<String?>(null)
    val lastWatchedUrl: StateFlow<String?> = _lastWatchedUrl.asStateFlow()

    private val _lastSyncTime = MutableStateFlow(0L)
    val lastSyncTime: StateFlow<Long> = _lastSyncTime.asStateFlow()

    private val _isSidebarVisible = MutableStateFlow(true)
    val isSidebarVisible: StateFlow<Boolean> = _isSidebarVisible.asStateFlow()

    fun setSidebarVisible(visible: Boolean) {
        _isSidebarVisible.value = visible
    }

    private val _currentScreen = MutableStateFlow("player")
    val currentScreen: StateFlow<String> = _currentScreen.asStateFlow()

    fun navigateTo(screen: String) {
        _currentScreen.value = screen
    }

    // Reactive channels derived from database, search query, and category
    val categories: StateFlow<List<String>> = repository.categories
        .map { list -> listOf("All", "Favorites") + list }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf("All", "Favorites"))

    val uiChannels: StateFlow<List<LiveChannel>> = combine(
        repository.allChannels,
        _searchQuery,
        _selectedCategory
    ) { channels, query, category ->
        var result = channels

        // Filter by category
        if (category == "Favorites") {
            result = result.filter { it.isFavorite }
        } else if (category != "All") {
            result = result.filter { it.category.equals(category, ignoreCase = true) }
        }

        // Filter by search query
        if (query.isNotBlank()) {
            result = result.filter {
                it.name.contains(query, ignoreCase = true) ||
                it.category.contains(query, ignoreCase = true)
            }
        }

        result
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalChannelsCount: StateFlow<Int> = repository.allChannels
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    init {
        loadPreferences()
        
        // Auto synchronization check: Sync if database is completely empty
        viewModelScope.launch {
            val count = repository.getChannelCount()
            if (count == 0) {
                syncChannels(verifyStreams = false)
            } else {
                // If we have cached channels, read auto play preference and play the last channel or first channel
                if (autoPlayEnabled.value) {
                    val lastUrl = lastWatchedUrl.value
                    val all = repository.allChannels.first()
                    if (all.isNotEmpty()) {
                        val matchedChannel = all.find { it.url == lastUrl }
                        _selectedChannel.value = matchedChannel ?: all.first()
                    }
                }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectCategory(category: String) {
        _selectedCategory.value = category
    }

    fun selectChannel(channel: LiveChannel?) {
        _selectedChannel.value = channel
        if (channel != null) {
            // Update last watched setting
            savePreference("last_watched_url", channel.url)
            _lastWatchedUrl.value = channel.url
        }
    }

    fun playNextChannel() {
        val currentChannels = uiChannels.value
        val currentChannel = _selectedChannel.value ?: return
        if (currentChannels.isEmpty()) return

        val currentIndex = currentChannels.indexOfFirst { it.url == currentChannel.url }
        if (currentIndex != -1 && currentIndex < currentChannels.size - 1) {
            selectChannel(currentChannels[currentIndex + 1])
        } else {
            // Loop back to start
            selectChannel(currentChannels.first())
        }
    }

    fun playPreviousChannel() {
        val currentChannels = uiChannels.value
        val currentChannel = _selectedChannel.value ?: return
        if (currentChannels.isEmpty()) return

        val currentIndex = currentChannels.indexOfFirst { it.url == currentChannel.url }
        if (currentIndex != -1 && currentIndex > 0) {
            selectChannel(currentChannels[currentIndex - 1])
        } else {
            // Loop to end
            selectChannel(currentChannels.last())
        }
    }

    fun toggleFavorite(channel: LiveChannel) {
        viewModelScope.launch {
            repository.toggleFavorite(channel.url, !channel.isFavorite)
            // If the selected channel is favorites, update its state
            if (_selectedChannel.value?.url == channel.url) {
                _selectedChannel.value = channel.copy(isFavorite = !channel.isFavorite)
            }
        }
    }

    fun syncChannels(verifyStreams: Boolean = true) {
        viewModelScope.launch {
            _syncState.value = SyncState.Syncing("Downloading Playlist...")
            val result = repository.syncChannels(_playlistUrlState.value, verifyStreams) { progressMsg ->
                _syncState.value = SyncState.Syncing(progressMsg)
            }
            result.onSuccess {
                _syncState.value = SyncState.Success
                loadPreferences() // reload sync timestamp
                
                val all = repository.allChannels.first()
                if (all.isNotEmpty()) {
                    val current = _selectedChannel.value
                    if (current != null && !all.any { it.url == current.url }) {
                        _selectedChannel.value = all.first()
                    } else if (current == null && autoPlayEnabled.value) {
                        _selectedChannel.value = all.first()
                    }
                } else {
                    _selectedChannel.value = null
                }
            }.onFailure { exception ->
                _syncState.value = SyncState.Error(exception.localizedMessage ?: "Sync configuration error")
            }
        }
    }

    fun syncChannelsFromFile(filePath: String, verifyStreams: Boolean = true) {
        viewModelScope.launch {
            _syncState.value = SyncState.Syncing("Reading File...")
            saveM3uFilePath(filePath)
            val result = repository.syncChannelsFromFile(filePath, verifyStreams) { progressMsg ->
                _syncState.value = SyncState.Syncing(progressMsg)
            }
            result.onSuccess {
                _syncState.value = SyncState.Success
                loadPreferences() // reload sync timestamp and file path
                
                val all = repository.allChannels.first()
                if (all.isNotEmpty()) {
                    val current = _selectedChannel.value
                    if (current != null && !all.any { it.url == current.url }) {
                        _selectedChannel.value = all.first()
                    } else if (current == null && autoPlayEnabled.value) {
                        _selectedChannel.value = all.first()
                    }
                } else {
                    _selectedChannel.value = null
                }
            }.onFailure { exception ->
                _syncState.value = SyncState.Error(exception.localizedMessage ?: "File sync error")
            }
        }
    }

    fun saveM3uUrl(url: String) {
        _playlistUrlState.value = url
        savePreference("playlist_url", url)
    }

    fun saveM3uFilePath(path: String) {
        _m3uFilePathState.value = path
        savePreference("m3u_file_path", path)
    }

    // Reset sync state to idle
    fun clearSyncState() {
        _syncState.value = SyncState.Idle
    }

    // Load/Save Settings Preferences
    private fun loadPreferences() {
        viewModelScope.launch {
            _autoPlayEnabled.value = (preferenceDao.getPreferenceValue("auto_play") ?: "true").toBoolean()
            _dpadSidebarControlEnabled.value = (preferenceDao.getPreferenceValue("dpad_sidebar_control") ?: "false").toBoolean()
            _bufferProfile.value = preferenceDao.getPreferenceValue("buffer_profile") ?: "stable"
            _videoQuality.value = preferenceDao.getPreferenceValue("video_quality") ?: "auto"
            _themeMode.value = preferenceDao.getPreferenceValue("theme_mode") ?: "dark"
            _lastWatchedUrl.value = preferenceDao.getPreferenceValue("last_watched_url")
            _lastSyncTime.value = (preferenceDao.getPreferenceValue("last_sync_time") ?: "0").toLong()
            _playlistUrlState.value = preferenceDao.getPreferenceValue("playlist_url") ?: defaultPlaylistUrl
            _m3uFilePathState.value = preferenceDao.getPreferenceValue("m3u_file_path") ?: ""
        }
    }

    fun setAutoPlay(enabled: Boolean) {
        _autoPlayEnabled.value = enabled
        savePreference("auto_play", enabled.toString())
    }

    fun setDpadSidebarControlEnabled(enabled: Boolean) {
        _dpadSidebarControlEnabled.value = enabled
        savePreference("dpad_sidebar_control", enabled.toString())
    }

    fun setBufferProfile(profile: String) {
        _bufferProfile.value = profile
        savePreference("buffer_profile", profile)
    }

    fun setVideoQuality(quality: String) {
        _videoQuality.value = quality
        savePreference("video_quality", quality)
    }

    fun setThemeMode(mode: String) {
        _themeMode.value = mode
        savePreference("theme_mode", mode)
    }

    private fun savePreference(key: String, value: String) {
        viewModelScope.launch {
            preferenceDao.insertPreference(AppPreference(key, value))
        }
    }
}
