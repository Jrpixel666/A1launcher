package com.a1launcher.assistant

import com.a1launcher.model.ChatMessage

/** How the AI agent chat overlay talks to a model — OpenRouter today, a local agent CLI's HTTP bridge later. */
interface AgentBackend {
    suspend fun sendMessage(history: List<ChatMessage>): String
}

class NotConfiguredBackend : AgentBackend {
    override suspend fun sendMessage(history: List<ChatMessage>): String =
        "No model is configured yet. Add an OpenRouter API key in Settings, or finish installing " +
            "an agent CLI from the setup wizard."
}
