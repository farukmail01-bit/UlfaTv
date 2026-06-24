package com.example.parser

import com.example.data.Channel
import java.io.BufferedReader
import java.io.StringReader

object M3uParser {

    fun parse(content: String, playlistName: String): List<Channel> {
        val channels = mutableListOf<Channel>()
        val reader = BufferedReader(StringReader(content))
        var line: String?
        var currentChannelName = ""
        var currentCategory = "Custom IPTV"
        var currentLogoUrl: String? = null
        var hasExtInf = false

        val groupTitleRegex = """group-title="([^"]*)"""".toRegex()
        val logoRegex = """tvg-logo="([^"]*)"""".toRegex()

        try {
            while (reader.readLine().also { line = it } != null) {
                val trimmedLine = line!!.trim()
                if (trimmedLine.isEmpty()) continue

                if (trimmedLine.startsWith("#EXTINF:")) {
                    hasExtInf = true
                    
                    // Parse category (group-title)
                    val groupMatch = groupTitleRegex.find(trimmedLine)
                    currentCategory = groupMatch?.groupValues?.getOrNull(1) ?: "Custom IPTV"

                    // Parse logo url (tvg-logo)
                    val logoMatch = logoRegex.find(trimmedLine)
                    currentLogoUrl = logoMatch?.groupValues?.getOrNull(1)

                    // Parse channel name (anything after the last comma)
                    val commaIndex = trimmedLine.lastIndexOf(',')
                    currentChannelName = if (commaIndex != -1 && commaIndex < trimmedLine.length - 1) {
                        trimmedLine.substring(commaIndex + 1).trim()
                    } else {
                        "IPTV Channel ${channels.size + 1}"
                    }
                    if (currentChannelName.isEmpty()) {
                        currentChannelName = "IPTV Channel ${channels.size + 1}"
                    }
                } else if (!trimmedLine.startsWith("#") && hasExtInf) {
                    // This line is the URL!
                    val streamUrl = trimmedLine
                    if (streamUrl.startsWith("http://") || streamUrl.startsWith("https://") || streamUrl.endsWith(".m3u8") || streamUrl.endsWith(".mp4")) {
                        channels.add(
                            Channel(
                                name = currentChannelName,
                                url = streamUrl,
                                category = currentCategory,
                                logoUrl = currentLogoUrl,
                                isFavorite = false,
                                isCustom = true,
                                playlistName = playlistName,
                                orderIndex = channels.size
                            )
                        )
                    }
                    // Reset trackers
                    currentChannelName = ""
                    currentCategory = "Custom IPTV"
                    currentLogoUrl = null
                    hasExtInf = false
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            reader.close()
        }

        return channels
    }
}
