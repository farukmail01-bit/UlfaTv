package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TvChannelDao {
    @Query("SELECT * FROM channels ORDER BY orderIndex ASC")
    fun getAllChannels(): Flow<List<Channel>>

    @Query("SELECT * FROM channels WHERE isFavorite = 1 ORDER BY orderIndex ASC")
    fun getFavoriteChannels(): Flow<List<Channel>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannels(channels: List<Channel>)

    @Update
    suspend fun updateChannel(channel: Channel)

    @Query("UPDATE channels SET isFavorite = :isFavorite WHERE url = :url")
    suspend fun updateFavoriteStatus(url: String, isFavorite: Boolean)

    @Query("DELETE FROM channels WHERE playlistName = :playlistName")
    suspend fun deleteChannelsByPlaylist(playlistName: String)

    @Query("DELETE FROM channels WHERE isCustom = 1")
    suspend fun deleteCustomChannels()
}

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY timestamp DESC")
    fun getAllPlaylists(): Flow<List<PlaylistItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistItem): Long

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: Int)

    @Query("DELETE FROM playlists")
    suspend fun deleteAllPlaylists()
}

@Dao
interface WatchHistoryDao {
    @Query("SELECT * FROM watch_history ORDER BY timestamp DESC")
    fun getRecentHistory(): Flow<List<WatchHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWatchHistory(history: WatchHistory)

    @Query("DELETE FROM watch_history")
    suspend fun clearHistory()
}
