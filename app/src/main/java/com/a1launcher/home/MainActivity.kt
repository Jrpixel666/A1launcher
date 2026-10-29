package com.a1launcher.home

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.fillMaxSize
import com.a1launcher.ui.theme.A1Theme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.a1launcher.A1LauncherApp
import com.a1launcher.assistant.AssistantChatActivity
import com.a1launcher.setup.SetupWizardActivity
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: HomeViewModel by viewModels()
    private var pendingWidgetId: Int = -1

    private val configureWidgetLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && pendingWidgetId != -1) {
                viewModel.confirmWidgetPinned(pendingWidgetId)
            } else if (pendingWidgetId != -1) {
                viewModel.cancelAllocatedWidget(pendingWidgetId)
            }
            pendingWidgetId = -1
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            val setupComplete = (application as A1LauncherApp).settingsRepository.isSetupComplete
            setupComplete.collect { complete ->
                if (!complete) {
                    startActivity(Intent(this@MainActivity, SetupWizardActivity::class.java))
                }
            }
        }

        setContent {
            A1Theme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var drawerOpen by remember { mutableStateOf(false) }
                    val appListViewModel: AppListViewModel by viewModels()
                    val apps by appListViewModel.apps.collectAsStateWithLifecycle()
                    val pinnedIds by viewModel.pinnedWidgetIds.collectAsStateWithLifecycle()
                    val isLinuxReady by viewModel.isLinuxReady.collectAsStateWithLifecycle()

                    val widgetViews = remember(pinnedIds) {
                        pinnedIds.mapNotNull { id ->
                            val info = viewModel.widgetHost.getProviderInfo(id) ?: return@mapNotNull null
                            id to viewModel.widgetHost.createHostView(id, info)
                        }
                    }

                    HomeScreen(
                        appWidgetViews = widgetViews,
                        isLinuxReady = isLinuxReady,
                        onOpenDrawer = { drawerOpen = true },
                        onOpenAssistant = {
                            startActivity(Intent(this@MainActivity, AssistantChatActivity::class.java))
                        },
                        onOpenLinuxDesktop = {
                            // Opens a terminal; its toplevel creation is what starts
                            // LinuxWindowActivity as its own Recents card (LinuxRuntime.kt).
                            (application as A1LauncherApp).linuxRuntime.openTerminal()
                        },
                        onAddWidget = { launchWidgetPicker() },
                        onRemoveWidget = { viewModel.removeWidget(it) },
                        onOpenSettings = {
                            startActivity(Intent(this@MainActivity, com.a1launcher.settings.SettingsActivity::class.java))
                        },
                    )

                    AnimatedVisibility(visible = drawerOpen) {
                        AppDrawer(
                            apps = apps,
                            onAppClick = { app ->
                                drawerOpen = false
                                launchApp(app)
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.widgetHost.startListening()
    }

    override fun onPause() {
        viewModel.widgetHost.stopListening()
        super.onPause()
    }

    private fun launchApp(app: AppInfo) {
        val intent = Intent().apply {
            setClassName(app.packageName, app.activityName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    /**
     * Standard launcher widget-pick flow: allocate an ID, let the user choose a widget
     * (and, for restricted widgets, grant the bind permission), then create its host view.
     */
    private fun launchWidgetPicker() {
        val widgetId = viewModel.allocateWidgetId()
        pendingWidgetId = widgetId
        val pickIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        }
        configureWidgetLauncher.launch(pickIntent)
    }
}
