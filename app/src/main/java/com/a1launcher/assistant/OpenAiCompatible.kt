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

/** Shared OpenAI-compatible `/chat/completions` client, used by both the hosted (OpenRouter) and local (llama.cpp) backends. */
object OpenAiCompatible {

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class ApiMessage(val role: String, val content: String)

    @Serializable
    private data class ChatRequest(val model: String, val messages: List<ApiMessage>)

    @Serializable
    private data class ChatChoice(val message: ApiMessage)

    @Serializable
    private data class ChatResponse(val choices: List<ChatChoice> = emptyList())

    suspend fun chatCompletion(
        client: OkHttpClient,
        url: String,
        model: String,
        apiKey: String?,
        history: List<ChatMessage>,
    ): String = withContext(Dispatchers.IO) {
        val requestBody = ChatRequest(
            model = model,
            messages = history.map { ApiMessage(role = it.role.toApiRole(), content = it.content) },
        )
        val body = json.encodeToString(requestBody).toRequestBody("application/json".toMediaType())
        val requestBuilder = Request.Builder().url(url).post(body)
        if (apiKey != null) requestBuilder.header("Authorization", "Bearer $apiKey")
        val request = requestBuilder.build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return@withContext "Request to $url failed (${response.code}): ${response.body?.string().orEmpty()}"
            }
            val parsed = json.decodeFromString<ChatResponse>(response.body?.string().orEmpty())
            parsed.choices.firstOrNull()?.message?.content ?: "(empty response)"
        }
    }

    fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private fun ChatMessage.Role.toApiRole(): String = when (this) {
        ChatMessage.Role.USER -> "user"
        ChatMessage.Role.ASSISTANT -> "assistant"
        ChatMessage.Role.SYSTEM -> "system"
    }
}
