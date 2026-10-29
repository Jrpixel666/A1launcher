package com.a1launcher.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.a1launcher.A1LauncherApp
import com.a1launcher.data.WidgetRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PinnedWidget(val widgetId: Int)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    val widgetHost = WidgetHostManager(application)
    private val widgetRepository = WidgetRepository(application)
    val settingsRepository get() = (getApplication<Application>() as A1LauncherApp).settingsRepository

    val pinnedWidgetIds: StateFlow<List<Int>> =
        widgetRepository.pinnedWidgetIds.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _isLinuxReady = MutableStateFlow(false)
    val isLinuxReady: StateFlow<Boolean> = _isLinuxReady.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.isLinuxEnvironmentReady.collect { _isLinuxReady.value = it }
        }
    }

    fun allocateWidgetId(): Int = widgetHost.allocateWidgetId()

    fun confirmWidgetPinned(widgetId: Int) {
        viewModelScope.launch { widgetRepository.addWidget(widgetId) }
    }

    fun removeWidget(widgetId: Int) {
        viewModelScope.launch {
            widgetRepository.removeWidget(widgetId)
            widgetHost.deleteWidgetId(widgetId)
        }
    }

    fun cancelAllocatedWidget(widgetId: Int) {
        widgetHost.deleteWidgetId(widgetId)
    }
}
