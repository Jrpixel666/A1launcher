package com.a1launcher.assistant

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.a1launcher.A1LauncherApp
import com.a1launcher.model.ChatMessage
import com.a1launcher.model.LOCAL_MODELS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AssistantViewModel(application: Application) : AndroidViewModel(application) {

    private val app get() = getApplication<Application>() as A1LauncherApp
    private val settingsRepository = app.settingsRepository

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    /** Prefers the offline local model when one's installed and the user asked for it; else OpenRouter; else nothing configured. */
    private suspend fun backend(): AgentBackend {
        val preferLocal = settingsRepository.preferLocalAi.first()
        val localModelId = settingsRepository.localAiModelId.first()
        if (preferLocal && localModelId != null) {
            val model = LOCAL_MODELS.firstOrNull { it.id == localModelId }
            if (model != null) {
                withContext(Dispatchers.IO) { app.llamaServerRuntime.start(model) }
                return LocalLlamaBackend()
            }
        }
        val key = settingsRepository.getOpenRouterApiKey()
        return if (key != null) OpenRouterBackend(apiKey = key) else NotConfiguredBackend()
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        val updated = _messages.value + ChatMessage(ChatMessage.Role.USER, text)
        _messages.value = updated
        _isSending.value = true
        viewModelScope.launch {
            val reply = backend().sendMessage(updated)
            _messages.value = _messages.value + ChatMessage(ChatMessage.Role.ASSISTANT, reply)
            _isSending.value = false
        }
    }
}
