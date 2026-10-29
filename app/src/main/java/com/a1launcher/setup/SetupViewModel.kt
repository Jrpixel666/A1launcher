package com.a1launcher.setup

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.a1launcher.A1LauncherApp
import com.a1launcher.linux.ProotInstallService
import com.a1launcher.model.AgentCli
import com.a1launcher.model.InstallProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class WizardStep { WELCOME, LINUX_OPTIN, AGENTS, REVIEW, INSTALLING, DONE }

class SetupViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = (application as A1LauncherApp).settingsRepository

    private val _step = MutableStateFlow(WizardStep.WELCOME)
    val step: StateFlow<WizardStep> = _step.asStateFlow()

    private val _wantsLinuxDesktop = MutableStateFlow(true)
    val wantsLinuxDesktop: StateFlow<Boolean> = _wantsLinuxDesktop.asStateFlow()

    private val _selectedAgents = MutableStateFlow<Set<AgentCli>>(setOf(AgentCli.CLAUDE))
    val selectedAgents: StateFlow<Set<AgentCli>> = _selectedAgents.asStateFlow()

    private val _openRouterKey = MutableStateFlow("")
    val openRouterKey: StateFlow<String> = _openRouterKey.asStateFlow()

    val installProgress: StateFlow<InstallProgress> =
        ProotInstallService.progress.stateIn(viewModelScope, SharingStarted.Eagerly, InstallProgress())

    fun setWantsLinuxDesktop(value: Boolean) {
        _wantsLinuxDesktop.value = value
    }

    fun toggleAgent(agent: AgentCli) {
        _selectedAgents.value = if (agent in _selectedAgents.value) {
            _selectedAgents.value - agent
        } else {
            _selectedAgents.value + agent
        }
    }

    fun setOpenRouterKey(key: String) {
        _openRouterKey.value = key
    }

    fun goTo(step: WizardStep) {
        _step.value = step
    }

    fun beginInstall() {
        _step.value = WizardStep.INSTALLING
        val agentIds = _selectedAgents.value.map { it.id }
        val key = _openRouterKey.value.ifBlank { null }
        if (key != null) {
            settingsRepository.saveOpenRouterApiKey(key)
        }
        if (!_wantsLinuxDesktop.value) {
            // No Linux desktop: still record setup as complete without starting the service.
            viewModelScope.launch {
                settingsRepository.markSetupComplete()
                _step.value = WizardStep.DONE
            }
            return
        }
        ProotInstallService.start(getApplication(), agentIds, key)
        viewModelScope.launch {
            installProgress.collect { progress ->
                if (progress.step is com.a1launcher.model.InstallStep.Done ||
                    progress.step is com.a1launcher.model.InstallStep.Failed
                ) {
                    settingsRepository.markSetupComplete()
                    _step.value = WizardStep.DONE
                }
            }
        }
    }
}
