package com.a1launcher.assistant

import com.a1launcher.model.ChatMessage

/** Talks to OpenRouter's OpenAI-compatible chat completions endpoint. */
class OpenRouterBackend(
    private val apiKey: String,
    private val model: String = "anthropic/claude-sonnet-4.5",
) : AgentBackend {

    private val client = OpenAiCompatible.defaultClient()

    override suspend fun sendMessage(history: List<ChatMessage>): String =
        OpenAiCompatible.chatCompletion(
            client = client,
            url = "https://openrouter.ai/api/v1/chat/completions",
            model = model,
            apiKey = apiKey,
            history = history,
        )
}
