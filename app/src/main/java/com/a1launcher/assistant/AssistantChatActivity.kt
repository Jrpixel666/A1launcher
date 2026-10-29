package com.a1launcher.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class AssistantChatActivity : ComponentActivity() {

    private val viewModel: AssistantViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val messages by viewModel.messages.collectAsStateWithLifecycle()
                    val isSending by viewModel.isSending.collectAsStateWithLifecycle()
                    AssistantChatScreen(
                        messages = messages,
                        isSending = isSending,
                        onSend = viewModel::sendMessage,
                    )
                }
            }
        }
    }
}
