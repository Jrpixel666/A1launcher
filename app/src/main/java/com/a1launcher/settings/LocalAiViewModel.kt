package com.a1launcher.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.a1launcher.A1LauncherApp
import com.a1launcher.linux.LlamaCppInstallService
import com.a1launcher.model.LOCAL_MODELS
import com.a1launcher.model.LocalAiProgress
import com.a1launcher.model.LocalAiStep
import com.a1launcher.model.LocalModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LocalAiViewModel(application: Application) : AndroidViewModel(application) {

    private val app get() = getApplication<Application>() as A1LauncherApp
    private val settingsRepository = app.settingsRepository

    private val _selectedModel = MutableStateFlow(LOCAL_MODELS.first())
    val selectedModel: StateFlow<LocalModel> = _selectedModel.asStateFlow()

    val installedModelId: StateFlow<String?> =
        settingsRepository.localAiModelId.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val preferLocalAi: StateFlow<Boolean> =
        settingsRepository.preferLocalAi.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val progress: StateFlow<LocalAiProgress> =
        LlamaCppInstallService.progress.stateIn(viewModelScope, SharingStarted.Eagerly, LocalAiProgress())

    val isServerRunning: StateFlow<Boolean> get() = _isServerRunning
    private val _isServerRunning = MutableStateFlow(false)

    fun selectModel(model: LocalModel) {
        _selectedModel.value = model
    }

    fun install() {
        LlamaCppInstallService.start(app, _selectedModel.value.id)
    }

    fun setPreferLocalAi(prefer: Boolean) {
        viewModelScope.launch { settingsRepository.setPreferLocalAi(prefer) }
    }

    fun toggleServer() {
        val runtime = app.llamaServerRuntime
        if (runtime.isRunning) {
            runtime.stop()
        } else {
            val modelId = installedModelId.value ?: return
            val model = LOCAL_MODELS.firstOrNull { it.id == modelId } ?: return
            runtime.start(model)
        }
        _isServerRunning.value = runtime.isRunning
    }

    fun refreshServerState() {
        _isServerRunning.value = app.llamaServerRuntime.isRunning
    }
}
