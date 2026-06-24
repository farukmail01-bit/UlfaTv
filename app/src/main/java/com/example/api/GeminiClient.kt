package com.example.api

import com.example.BuildConfig
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class Part(
    @Json(name = "text") val text: String? = null
)

@JsonClass(generateAdapter = true)
data class Content(
    @Json(name = "parts") val parts: List<Part>
)

@JsonClass(generateAdapter = true)
data class GenerateContentRequest(
    @Json(name = "contents") val contents: List<Content>,
    @Json(name = "systemInstruction") val systemInstruction: Content? = null
)

@JsonClass(generateAdapter = true)
data class Candidate(
    @Json(name = "content") val content: Content? = null
)

@JsonClass(generateAdapter = true)
data class GenerateContentResponse(
    @Json(name = "candidates") val candidates: List<Candidate>? = null
)

interface GeminiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

object GeminiClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val service: GeminiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
        retrofit.create(GeminiService::class.java)
    }

    suspend fun generateResponse(
        prompt: String,
        chatHistory: List<com.example.data.ChatMessage>,
        channelsInfo: String
    ): String {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return "Hi there! I would love to help you, but the Gemini API Key is currently missing. Please configure it in your AI Studio Secrets Panel to chat with me!"
        }

        val systemInstructionText = """
            You are "UlfaTV AI Assistant", a smart and friendly television program and live streaming guide.
            The user is using the "UlfaTV" Android app, which is a premium live IPTV streaming and media playback platform.
            
            Here is the current list of available TV channels in the user's catalog:
            $channelsInfo
            
            When answering:
            1. Suggest channels from the available catalog that match the user's mood, query, or category request.
            2. ALWAYS provide the channel EXACT stream URL in your recommendations if you mention a channel. The app will detect the stream URL and make it directly playable!
            3. If the user asks for a channel that is not in the list, politely inform them they can add custom M3U playlists in the "Playlists" tab to load any channel!
            4. Keep your answers engaging, short, helpful, and formatted beautifully in Markdown.
            5. Since this is UlfaTV, speak with warmth, enthusiasm, and a helpful, executive tone.
        """.trimIndent()

        val contents = mutableListOf<Content>()

        // Map chat history (max last 10 messages to save context window)
        val historyToMap = chatHistory.takeLast(10)
        for (msg in historyToMap) {
            val role = if (msg.sender == com.example.data.ChatMessage.Sender.USER) "user" else "model"
            // Wait, the standard REST API structure for chat uses Content structures.
            // Let's add them as sequential contents.
            contents.add(
                Content(parts = listOf(Part(text = msg.text)))
            )
        }

        // Add the current prompt
        contents.add(Content(parts = listOf(Part(text = prompt))))

        val request = GenerateContentRequest(
            contents = contents,
            systemInstruction = Content(parts = listOf(Part(text = systemInstructionText)))
        )

        return try {
            val response = service.generateContent(apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: "I couldn't generate a recommendation right now. Please try again!"
        } catch (e: Exception) {
            "Sorry, I encountered an error communicating with the AI server: ${e.localizedMessage}. Please ensure your internet is connected and your API key is correct."
        }
    }
}
