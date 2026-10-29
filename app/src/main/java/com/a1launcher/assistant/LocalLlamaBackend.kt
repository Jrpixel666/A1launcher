package com.a1launcher.assistant

import com.a1launcher.linux.LlamaCpp
import com.a1launcher.model.ChatMessage

/**
 * Talks to the local `llama-server` running inside the proot guest
 * (started by LlamaServerRuntime) — fully offline once a model is
 * downloaded, since this never leaves the device's own loopback interface.
 */
class LocalLlamaBackend : AgentBackend {

    private val client = OpenAiCompatible.defaultClient()

    override suspend fun sendMessage(history: List<ChatMessage>): String =
        OpenAiCompatible.chatCompletion(
            client = client,
            url = "http://127.0.0.1:${LlamaCpp.PORT}/v1/chat/completions",
            model = "local",
            apiKey = null,
            history = history,
        )
}
