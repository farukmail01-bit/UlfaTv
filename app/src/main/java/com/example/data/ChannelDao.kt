package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelDao {
    @Query("SELECT * FROM live_channels ORDER BY orderIndex ASC")
    fun getAllChannels(): Flow<List<LiveChannel>>

    @Query("SELECT * FROM live_channels WHERE isFavorite = 1 ORDER BY orderIndex ASC")
    fun getFavoriteChannels(): Flow<List<LiveChannel>>

    @Query("SELECT DISTINCT category FROM live_channels ORDER BY category ASC")
    fun getCategories(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannels(channels: List<LiveChannel>)

    @Query("UPDATE live_channels SET isFavorite = :isFavorite WHERE url = :url")
    suspend fun updateFavoriteStatus(url: String, isFavorite: Boolean)

    @Query("DELETE FROM live_channels")
    suspend fun clearAllChannels()

    @Query("SELECT COUNT(*) FROM live_channels")
    suspend fun getChannelCount(): Int
}
