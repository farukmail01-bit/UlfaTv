package com.example.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.FileNotFoundException

class ChannelRepository(
    private val channelDao: ChannelDao,
    private val preferenceDao: PreferenceDao
) {
    val allChannels: Flow<List<LiveChannel>> = channelDao.getAllChannels()
    val favoriteChannels: Flow<List<LiveChannel>> = channelDao.getFavoriteChannels()
    val categories: Flow<List<String>> = channelDao.getCategories()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val verificationHttpClient = okHttpClient.newBuilder()
        .connectTimeout(1500, java.util.concurrent.TimeUnit.MILLISECONDS)
        .readTimeout(1500, java.util.concurrent.TimeUnit.MILLISECONDS)
        .build()

    suspend fun toggleFavorite(url: String, isFavorite: Boolean) {
        channelDao.updateFavoriteStatus(url, isFavorite)
    }

    suspend fun getChannelCount(): Int {
        return channelDao.getChannelCount()
    }

    private suspend fun isChannelActive(url: String): Boolean = withContext(Dispatchers.IO) {
        try {
            // First try with lightweight HEAD request
            val request = Request.Builder()
                .url(url)
                .head()
                .build()
            
            verificationHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    true
                } else {
                    // Fallback to GET requesting only the first byte (Range header) to preserve bandwidth
                    val getRequest = Request.Builder()
                        .url(url)
                        .header("Range", "bytes=0-0")
                        .build()
                    verificationHttpClient.newCall(getRequest).execute().use { getResponse ->
                        getResponse.isSuccessful
                    }
                }
            }
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun filterActiveChannels(
        parsedChannels: List<LiveChannel>,
        onProgress: (String) -> Unit
    ): List<LiveChannel> = withContext(Dispatchers.IO) {
        val total = parsedChannels.size
        if (total == 0) return@withContext emptyList()

        onProgress("Verifying streams: 0/$total checked (0 active)...")

        val activeChannels = java.util.Collections.synchronizedList(mutableListOf<LiveChannel>())
        val verifiedCount = java.util.concurrent.atomic.AtomicInteger(0)
        val activeCount = java.util.concurrent.atomic.AtomicInteger(0)

        // Chunking prevents OOM and native thread creation errors (Unable to create new native thread)
        val chunks = parsedChannels.chunked(15)
        for (chunk in chunks) {
            coroutineScope {
                chunk.map { channel ->
                    async {
                        val active = isChannelActive(channel.url)
                        val currentVerified = verifiedCount.incrementAndGet()
                        if (active) {
                            activeCount.incrementAndGet()
                            activeChannels.add(channel)
                        }
                        if (currentVerified % 5 == 0 || currentVerified == total) {
                            onProgress("Verifying streams: $currentVerified/$total checked (${activeCount.get()} active)...")
                        }
                    }
                }.awaitAll()
            }
        }

        if (activeChannels.isEmpty()) {
            Log.w("ChannelRepository", "No active channels found. Saving all parsed channels as fallback.")
            parsedChannels
        } else {
            activeChannels.sortedBy { it.orderIndex }
        }
    }

    suspend fun syncChannelsFromFile(filePath: String, verifyStreams: Boolean = true, onProgress: (String) -> Unit): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d("ChannelRepository", "Syncing channels from local file: $filePath")
            onProgress("Loading local file...")
            val file = java.io.File(filePath)
            if (!file.exists()) {
                return@withContext Result.failure(FileNotFoundException("File does not exist at: $filePath"))
            }
            if (!file.isFile) {
                return@withContext Result.failure(IOException("Path is not a regular file: $filePath"))
            }
            val bodyText = file.readText()
            if (bodyText.isEmpty()) {
                return@withContext Result.failure(Exception("File content is empty"))
            }

            // 1. Get current favorites to preserve them
            val currentFavorites = channelDao.getFavoriteChannels().first().map { it.url }.toSet()

            // 2. Parse M3U
            onProgress("Parsing local M3U file...")
            val newChannels = parseM3U(bodyText, currentFavorites)
            if (newChannels.isEmpty()) {
                return@withContext Result.failure(Exception("No valid channel entries parsed from this file"))
            }

            // 3. Filter only active channels IN MEMORY if verifyStreams is true
            val channelsToSave = if (verifyStreams) {
                onProgress("Verifying stream URLs...")
                filterActiveChannels(newChannels, onProgress)
            } else {
                newChannels
            }

            // 4. Update Room Database in one single atomic operation
            onProgress("Caching verified channels...")
            channelDao.replaceChannels(channelsToSave)

            // Save last sync time
            preferenceDao.insertPreference(
                AppPreference("last_sync_time", System.currentTimeMillis().toString())
            )
            preferenceDao.insertPreference(
                AppPreference("m3u_file_path", filePath)
            )

            Log.d("ChannelRepository", "Synchronized ${channelsToSave.size} channels successfully from file")
            return@withContext Result.success(Unit)
        } catch (e: Exception) {
            Log.e("ChannelRepository", "Error syncing channels from file", e)
            return@withContext Result.failure(e)
        }
    }

    suspend fun syncChannels(playlistUrl: String, verifyStreams: Boolean = true, onProgress: (String) -> Unit): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d("ChannelRepository", "Syncing channels from: $playlistUrl")
            onProgress("Downloading remote playlist...")
            val request = Request.Builder()
                .url(playlistUrl)
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("Failed to download playlist: $response"))
                }

                val bodyText = response.body?.string() ?: ""
                if (bodyText.isEmpty()) {
                    return@withContext Result.failure(Exception("Playlist content is empty"))
                }

                // 1. Get current favorites to preserve them
                val currentFavorites = channelDao.getFavoriteChannels().first().map { it.url }.toSet()

                // 2. Parse M3U
                onProgress("Parsing remote playlist...")
                val newChannels = parseM3U(bodyText, currentFavorites)
                if (newChannels.isEmpty()) {
                    return@withContext Result.failure(Exception("No channels found in the parsed playlist"))
                }

                // 3. Filter only active channels IN MEMORY if verifyStreams is true
                val channelsToSave = if (verifyStreams) {
                    onProgress("Verifying stream URLs...")
                    filterActiveChannels(newChannels, onProgress)
                } else {
                    newChannels
                }

                // 4. Update Room Database in one single atomic operation
                onProgress("Caching verified channels...")
                channelDao.replaceChannels(channelsToSave)

                // Save last sync time
                preferenceDao.insertPreference(
                    AppPreference("last_sync_time", System.currentTimeMillis().toString())
                )

                Log.d("ChannelRepository", "Synchronized ${channelsToSave.size} channels successfully")
                return@withContext Result.success(Unit)
            }
        } catch (e: Exception) {
            Log.e("ChannelRepository", "Error syncing channels", e)
            return@withContext Result.failure(e)
        }
    }

    private fun parseM3U(m3uText: String, favorites: Set<String>): List<LiveChannel> {
        val channels = mutableListOf<LiveChannel>()
        val lines = m3uText.lines()
        var currentName: String? = null
        var currentLogo: String? = null
        var currentGroup = "General"
        var foundExtInf = false
        var orderIndex = 0

        for (line in lines) {
            val trimmedLine = line.trim()
            if (trimmedLine.isEmpty()) continue

            if (trimmedLine.startsWith("#EXTINF:")) {
                foundExtInf = true
                currentLogo = parseAttribute(trimmedLine, "tvg-logo") ?: parseAttribute(trimmedLine, "logo")
                currentGroup = parseAttribute(trimmedLine, "group-title") ?: parseAttribute(trimmedLine, "group") ?: "General"
                
                // Extract group cleanups
                if (currentGroup.isBlank()) {
                    currentGroup = "General"
                }

                val lastCommaIndex = trimmedLine.lastIndexOf(',')
                currentName = if (lastCommaIndex != -1) {
                    trimmedLine.substring(lastCommaIndex + 1).trim()
                } else {
                    parseAttribute(trimmedLine, "tvg-name") ?: "Channel ${orderIndex + 1}"
                }
            } else if (!trimmedLine.startsWith("#")) {
                if (foundExtInf && currentName != null) {
                    val streamUrl = trimmedLine
                    val isFav = favorites.contains(streamUrl)
                    channels.add(
                        LiveChannel(
                            url = streamUrl,
                            name = currentName,
                            logoUrl = currentLogo?.takeIf { it.isNotBlank() },
                            category = currentGroup,
                            isFavorite = isFav,
                            orderIndex = orderIndex++
                        )
                    )
                }
                currentName = null
                currentLogo = null
                currentGroup = "General"
                foundExtInf = false
            }
        }
        return channels
    }

    private fun parseAttribute(line: String, key: String): String? {
        val keyIndex = line.indexOf("$key=\"")
        if (keyIndex == -1) return null
        val startIndex = keyIndex + key.length + 2
        val endIndex = line.indexOf('"', startIndex)
        if (endIndex == -1) return null
        return line.substring(startIndex, endIndex)
    }
}
