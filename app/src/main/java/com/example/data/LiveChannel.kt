package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "live_channels")
data class LiveChannel(
    @PrimaryKey val url: String,
    val name: String,
    val logoUrl: String?,
    val category: String,
    val isFavorite: Boolean = false,
    val orderIndex: Int = 0
)
