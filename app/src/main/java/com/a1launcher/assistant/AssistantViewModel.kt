package com.a1launcher.assistant

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.a1launcher.A1LauncherApp
import com.a1launcher.model.ChatMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AssistantViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = (application as A1LauncherApp).settingsRepository

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    private fun backend(): AgentBackend {
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
