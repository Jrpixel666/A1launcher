package com.a1launcher.setup

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.a1launcher.A1LauncherApp
import com.a1launcher.linux.AGENTS
import com.a1launcher.linux.Agent
import com.a1launcher.linux.ProotInstallService
import com.a1launcher.model.InstallProgress
import com.a1launcher.model.InstallStep
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

    private val _selectedAgents = MutableStateFlow<Set<Agent>>(setOf(AGENTS.first { it.bin == "claude" }))
    val selectedAgents: StateFlow<Set<Agent>> = _selectedAgents.asStateFlow()

    /** Not one of AGENTS: a hosted-API key for A1's own assistant overlay, not a guest CLI install. */
    private val _useOpenRouter = MutableStateFlow(false)
    val useOpenRouter: StateFlow<Boolean> = _useOpenRouter.asStateFlow()

    private val _openRouterKey = MutableStateFlow("")
    val openRouterKey: StateFlow<String> = _openRouterKey.asStateFlow()

    val installProgress: StateFlow<InstallProgress> =
        ProotInstallService.progress.stateIn(viewModelScope, SharingStarted.Eagerly, InstallProgress())

    fun setWantsLinuxDesktop(value: Boolean) {
        _wantsLinuxDesktop.value = value
    }

    fun toggleAgent(agent: Agent) {
        _selectedAgents.value = if (agent in _selectedAgents.value) {
            _selectedAgents.value - agent
        } else {
            _selectedAgents.value + agent
        }
    }

    fun setUseOpenRouter(value: Boolean) {
        _useOpenRouter.value = value
    }

    fun setOpenRouterKey(key: String) {
        _openRouterKey.value = key
    }

    fun goTo(step: WizardStep) {
        _step.value = step
    }

    fun beginInstall() {
        _step.value = WizardStep.INSTALLING
        val agentBins = _selectedAgents.value.map { it.bin }
        val key = _openRouterKey.value.takeIf { _useOpenRouter.value }?.ifBlank { null }

        if (!_wantsLinuxDesktop.value) {
            // No Linux environment: still record setup as complete, and save the OpenRouter
            // key if given (the in-app assistant works even without the Linux side).
            viewModelScope.launch {
                key?.let { settingsRepository.saveOpenRouterApiKey(it) }
                settingsRepository.markSetupComplete()
                _step.value = WizardStep.DONE
            }
            return
        }

        ProotInstallService.start(getApplication(), agentBins, key)
        viewModelScope.launch {
            installProgress.collect { progress ->
                if (progress.step is InstallStep.Done || progress.step is InstallStep.Failed) {
                    settingsRepository.markSetupComplete()
                    _step.value = WizardStep.DONE
                }
            }
        }
    }
}
