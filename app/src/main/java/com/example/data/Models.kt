package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "channels")
data class Channel(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val url: String,
    val category: String,
    val logoUrl: String? = null,
    val isFavorite: Boolean = false,
    val isCustom: Boolean = false, // True if added by user via M3U
    val playlistName: String? = null,
    val orderIndex: Int = 0
)

@Entity(tableName = "playlists")
data class PlaylistItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val url: String?, // Can be null if loaded from raw text
    val channelCount: Int,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "watch_history")
data class WatchHistory(
    @PrimaryKey val channelUrl: String,
    val channelName: String,
    val category: String,
    val logoUrl: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: Sender,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val recommendedChannelUrl: String? = null // Clickable link to play this channel!
) {
    enum class Sender {
        USER,
        AI
    }
}
