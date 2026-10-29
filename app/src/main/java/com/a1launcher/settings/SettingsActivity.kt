package com.a1launcher.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a1launcher.assistant.AssistantOverlayService
import com.a1launcher.model.LOCAL_MODELS
import com.a1launcher.model.LocalAiStep
import com.a1launcher.ui.theme.A1Theme

class SettingsActivity : ComponentActivity() {

    private val localAiViewModel: LocalAiViewModel by viewModels()

    private val overlayPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            A1Theme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var bubbleEnabled by remember { mutableStateOf(false) }

                    LazyColumn(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                        item { Text("Settings", style = MaterialTheme.typography.headlineSmall) }
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("Floating AI agent bubble")
                                Switch(
                                    checked = bubbleEnabled,
                                    onCheckedChange = { enabled ->
                                        if (enabled && !canDrawOverlays()) {
                                            requestOverlayPermission()
                                            return@Switch
                                        }
                                        bubbleEnabled = enabled
                                        if (enabled) {
                                            startForegroundService(Intent(this@SettingsActivity, AssistantOverlayService::class.java))
                                        } else {
                                            stopService(Intent(this@SettingsActivity, AssistantOverlayService::class.java))
                                        }
                                    },
                                )
                            }
                        }
                        item { HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp)) }
                        item { OfflineAiSection(viewModel = localAiViewModel) }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        localAiViewModel.refreshServerState()
    }

    private fun canDrawOverlays(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName"),
        )
        overlayPermissionLauncher.launch(intent)
    }
}

@androidx.compose.runtime.Composable
private fun OfflineAiSection(viewModel: LocalAiViewModel) {
    val selectedModel by viewModel.selectedModel.collectAsStateWithLifecycle()
    val installedModelId by viewModel.installedModelId.collectAsStateWithLifecycle()
    val preferLocal by viewModel.preferLocalAi.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val serverRunning by viewModel.isServerRunning.collectAsStateWithLifecycle()
    val installing = progress.step !is LocalAiStep.NotStarted && progress.step !is LocalAiStep.Done && progress.step !is LocalAiStep.Failed

    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Offline AI", style = MaterialTheme.typography.titleMedium)
        Text(
            "Runs llama.cpp fully on-device under the Linux environment — no network needed " +
                "once a model is downloaded. Adapted from AbuZar-Ansarii/Android-GPU (see THIRD_PARTY.md).",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )

        if (installedModelId == null) {
            Text("Pick a model to install:", style = MaterialTheme.typography.labelLarge)
            LOCAL_MODELS.forEach { model ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = model.id == selectedModel.id, onClick = { viewModel.selectModel(model) })
                    Text("${model.label} (${model.approxSize})")
                }
            }
            Button(
                onClick = { viewModel.install() },
                enabled = !installing,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) {
                Text(if (installing) "Installing…" else "Install offline AI")
            }
            if (installing || progress.step is LocalAiStep.Failed) {
                Text(stepLabel(progress.step), modifier = Modifier.padding(top = 8.dp))
                if (installing) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
            }
        } else {
            val model = LOCAL_MODELS.firstOrNull { it.id == installedModelId }
            Text("Installed: ${model?.label ?: installedModelId}")
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Use for assistant chat")
                Switch(checked = preferLocal, onCheckedChange = { viewModel.setPreferLocalAi(it) })
            }
            Button(onClick = { viewModel.toggleServer() }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Text(if (serverRunning) "Stop local server" else "Start local server")
            }
        }
    }
}

private fun stepLabel(step: LocalAiStep): String = when (step) {
    is LocalAiStep.NotStarted -> ""
    is LocalAiStep.InstallingBuildTools -> "Installing build tools…"
    is LocalAiStep.CloningLlamaCpp -> "Fetching llama.cpp source…"
    is LocalAiStep.Building -> "Building llama.cpp (this can take a while on a phone)…"
    is LocalAiStep.DownloadingModel -> "Downloading ${step.modelName}…"
    is LocalAiStep.Done -> "Done"
    is LocalAiStep.Failed -> "Failed: ${step.message}"
}
