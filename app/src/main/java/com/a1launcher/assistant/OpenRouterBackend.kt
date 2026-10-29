package com.a1launcher.assistant

import com.a1launcher.model.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** Talks to OpenRouter's OpenAI-compatible chat completions endpoint. */
class OpenRouterBackend(
    private val apiKey: String,
    private val model: String = "anthropic/claude-sonnet-4.5",
) : AgentBackend {

    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    @Serializable
    private data class ApiMessage(val role: String, val content: String)

    @Serializable
    private data class ChatRequest(val model: String, val messages: List<ApiMessage>)

    @Serializable
    private data class ChatChoice(val message: ApiMessage)

    @Serializable
    private data class ChatResponse(val choices: List<ChatChoice> = emptyList())

    override suspend fun sendMessage(history: List<ChatMessage>): String = withContext(Dispatchers.IO) {
        val requestBody = ChatRequest(
            model = model,
            messages = history.map { ApiMessage(role = it.role.toApiRole(), content = it.content) },
        )
        val body = json.encodeToString(requestBody).toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("https://openrouter.ai/api/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("HTTP-Referer", "https://a1launcher.app")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return@withContext "OpenRouter request failed (${response.code}): ${response.body?.string().orEmpty()}"
            }
            val parsed = json.decodeFromString<ChatResponse>(response.body?.string().orEmpty())
            parsed.choices.firstOrNull()?.message?.content ?: "(empty response)"
        }
    }

    private fun ChatMessage.Role.toApiRole(): String = when (this) {
        ChatMessage.Role.USER -> "user"
        ChatMessage.Role.ASSISTANT -> "assistant"
        ChatMessage.Role.SYSTEM -> "system"
    }
}
