package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull

class TvRepository(private val database: AppDatabase) {
    private val channelDao = database.tvChannelDao()
    private val playlistDao = database.playlistDao()
    private val watchHistoryDao = database.watchHistoryDao()

    val allChannels: Flow<List<Channel>> = channelDao.getAllChannels()
    val favoriteChannels: Flow<List<Channel>> = channelDao.getFavoriteChannels()
    val allPlaylists: Flow<List<PlaylistItem>> = playlistDao.getAllPlaylists()
    val watchHistory: Flow<List<WatchHistory>> = watchHistoryDao.getRecentHistory()

    suspend fun checkAndPrepopulate() {
        val existing = allChannels.first()
        if (existing.isEmpty()) {
            val defaultChannels = listOf(
                Channel(
                    name = "CBS News Live",
                    url = "https://cbsn-us.cbsnstream.cbsnews.com/main/manifest.m3u8",
                    category = "News",
                    logoUrl = "https://images.unsplash.com/photo-1585829365294-fa8c6332c023?w=100",
                    isFavorite = false,
                    isCustom = false
                ),
                Channel(
                    name = "Al Jazeera English",
                    url = "https://live-fta-gbr.live.aljazeera.net/aje/index.m3u8",
                    category = "News",
                    logoUrl = "https://images.unsplash.com/photo-1546422904-90eab23c3d7e?w=100",
                    isFavorite = true,
                    isCustom = false
                ),
                Channel(
                    name = "DW News Live",
                    url = "https://dwstream4-lh.akamaihd.net/i/dwstream4_live@131329/master.m3u8",
                    category = "News",
                    logoUrl = "https://images.unsplash.com/photo-1504711434969-e33886168f5c?w=100",
                    isFavorite = false,
                    isCustom = false
                ),
                Channel(
                    name = "Sintel Cinematic HD",
                    url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4",
                    category = "Movies",
                    logoUrl = "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?w=100",
                    isFavorite = true,
                    isCustom = false
                ),
                Channel(
                    name = "Tears of Steel Sci-Fi",
                    url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
                    category = "Movies",
                    logoUrl = "https://images.unsplash.com/photo-1536440136628-849c177e76a1?w=100",
                    isFavorite = false,
                    isCustom = false
                ),
                Channel(
                    name = "Big Buck Bunny (Kids)",
                    url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                    category = "Kids",
                    logoUrl = "https://images.unsplash.com/photo-1607604276583-eef5d076aa5f?w=100",
                    isFavorite = false,
                    isCustom = false
                ),
                Channel(
                    name = "Red Bull Action Sports",
                    url = "https://rbmn-live.akamaized.net/hls/live/590964/boxtv-sports/index.m3u8",
                    category = "Sports",
                    logoUrl = "https://images.unsplash.com/photo-1461896836934-ffe607ba8211?w=100",
                    isFavorite = false,
                    isCustom = false
                ),
                Channel(
                    name = "Elephant's Dream Classic",
                    url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
                    category = "Art & Design",
                    logoUrl = "https://images.unsplash.com/photo-1513364776144-60967b0f800f?w=100",
                    isFavorite = false,
                    isCustom = false
                )
            )
            channelDao.insertChannels(defaultChannels)
        }
    }

    suspend fun toggleFavorite(channel: Channel) {
        val updated = channel.copy(isFavorite = !channel.isFavorite)
        channelDao.updateChannel(updated)
    }

    suspend fun toggleFavoriteByUrl(url: String, isFav: Boolean) {
        channelDao.updateFavoriteStatus(url, isFav)
    }

    suspend fun addPlaylist(name: String, url: String?, channels: List<Channel>) {
        val playlistId = playlistDao.insertPlaylist(
            PlaylistItem(
                name = name,
                url = url,
                channelCount = channels.size
            )
        )
        val channelsWithPlaylist = channels.map {
            it.copy(
                isCustom = true,
                playlistName = name
            )
        }
        channelDao.insertChannels(channelsWithPlaylist)
    }

    suspend fun deletePlaylist(playlist: PlaylistItem) {
        playlistDao.deletePlaylist(playlist.id)
        playlist.name.let {
            channelDao.deleteChannelsByPlaylist(it)
        }
    }

    suspend fun insertWatchHistory(channel: Channel) {
        watchHistoryDao.insertWatchHistory(
            WatchHistory(
                channelUrl = channel.url,
                channelName = channel.name,
                category = channel.category,
                logoUrl = channel.logoUrl,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    suspend fun clearHistory() {
        watchHistoryDao.clearHistory()
    }

    suspend fun clearAllCustomPlaylists() {
        playlistDao.deleteAllPlaylists()
        channelDao.deleteCustomChannels()
    }
}
