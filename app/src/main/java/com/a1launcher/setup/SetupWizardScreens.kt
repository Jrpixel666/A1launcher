package com.a1launcher.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.a1launcher.R
import com.a1launcher.model.AgentCli
import com.a1launcher.model.InstallProgress
import com.a1launcher.model.InstallStep

@Composable
fun WelcomeScreen(onNext: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResourceCompat(R.string.setup_welcome_title))
        Text(stringResourceCompat(R.string.setup_welcome_body), modifier = Modifier.padding(top = 12.dp))
        Button(onClick = onNext, modifier = Modifier.padding(top = 24.dp).fillMaxWidth()) {
            Text("Get started")
        }
    }
}

@Composable
fun LinuxDesktopOptInScreen(
    wantsLinuxDesktop: Boolean,
    onToggle: (Boolean) -> Unit,
    onNext: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text(stringResourceCompat(R.string.setup_step_bootstrap))
        Text("Arch Linux under proot (no root) with the Sway desktop. Downloads a Linux rootfs (several hundred MB) the first time.")
        Row(
            modifier = Modifier.padding(top = 16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Install Linux desktop")
            Switch(checked = wantsLinuxDesktop, onCheckedChange = onToggle)
        }
        Button(onClick = onNext, modifier = Modifier.padding(top = 24.dp).fillMaxWidth()) {
            Text("Next")
        }
    }
}

@Composable
fun AgentPickerScreen(
    selectedAgents: Set<AgentCli>,
    openRouterKey: String,
    onToggleAgent: (AgentCli) -> Unit,
    onOpenRouterKeyChange: (String) -> Unit,
    onNext: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text(stringResourceCompat(R.string.setup_step_agents))
        LazyColumn(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            items(AgentCli.localClis(), key = { it.id }) { agent ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = agent in selectedAgents,
                        onCheckedChange = { onToggleAgent(agent) },
                    )
                    Column {
                        Text(agent.displayName)
                        Text(agent.description, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = AgentCli.OPENROUTER in selectedAgents,
                        onCheckedChange = { onToggleAgent(AgentCli.OPENROUTER) },
                    )
                    Text("OpenRouter (hosted models)")
                }
                if (AgentCli.OPENROUTER in selectedAgents) {
                    OutlinedTextField(
                        value = openRouterKey,
                        onValueChange = onOpenRouterKeyChange,
                        label = { Text("OpenRouter API key") },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            }
        }
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
            Text("Review")
        }
    }
}

@Composable
fun ReviewScreen(
    wantsLinuxDesktop: Boolean,
    selectedAgents: Set<AgentCli>,
    onConfirm: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text(stringResourceCompat(R.string.setup_step_review))
        Text(if (wantsLinuxDesktop) "Linux desktop: will be installed" else "Linux desktop: skipped")
        Text("Agents: " + (selectedAgents.takeIf { it.isNotEmpty() }?.joinToString { it.displayName } ?: "none"))
        Button(onClick = onConfirm, modifier = Modifier.padding(top = 24.dp).fillMaxWidth()) {
            Text("Install")
        }
    }
}

@Composable
fun InstallingScreen(progress: InstallProgress) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text(stepLabel(progress.step))
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp))
        LazyColumn {
            items(progress.logTail) { line -> Text(line, style = androidx.compose.material3.MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
fun DoneScreen(progress: InstallProgress, onFinish: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        val failed = progress.step as? InstallStep.Failed
        Text(if (failed != null) "Setup finished with an error" else "All set")
        if (failed != null) Text(failed.message)
        Button(onClick = onFinish, modifier = Modifier.padding(top = 24.dp).fillMaxWidth()) {
            Text("Go to home screen")
        }
    }
}

private fun stepLabel(step: InstallStep): String = when (step) {
    is InstallStep.NotStarted -> "Preparing…"
    is InstallStep.DownloadingRootfs -> "Downloading Arch Linux rootfs…"
    is InstallStep.ExtractingRootfs -> "Extracting rootfs…"
    is InstallStep.ConfiguringPacman -> "Configuring package manager…"
    is InstallStep.InstallingCompositor -> "Installing Sway desktop…"
    is InstallStep.InstallingAgent -> "Installing ${step.agent.displayName}…"
    is InstallStep.Done -> "Done"
    is InstallStep.Failed -> "Failed: ${step.message}"
}

/** Small shim so these previews/screens don't require a Context-bound stringResource call chain. */
@Composable
private fun stringResourceCompat(id: Int): String = androidx.compose.ui.res.stringResource(id)
