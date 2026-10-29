package com.a1launcher.setup

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.a1launcher.ui.theme.A1Theme
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class SetupWizardActivity : ComponentActivity() {

    private val viewModel: SetupViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            A1Theme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val step by viewModel.step.collectAsStateWithLifecycle()
                    val wantsLinux by viewModel.wantsLinuxDesktop.collectAsStateWithLifecycle()
                    val selectedAgents by viewModel.selectedAgents.collectAsStateWithLifecycle()
                    val useOpenRouter by viewModel.useOpenRouter.collectAsStateWithLifecycle()
                    val openRouterKey by viewModel.openRouterKey.collectAsStateWithLifecycle()
                    val progress by viewModel.installProgress.collectAsStateWithLifecycle()

                    when (step) {
                        WizardStep.WELCOME -> WelcomeScreen(
                            onNext = { viewModel.goTo(WizardStep.LINUX_OPTIN) },
                        )
                        WizardStep.LINUX_OPTIN -> LinuxDesktopOptInScreen(
                            wantsLinuxDesktop = wantsLinux,
                            onToggle = viewModel::setWantsLinuxDesktop,
                            onNext = { viewModel.goTo(WizardStep.AGENTS) },
                        )
                        WizardStep.AGENTS -> AgentPickerScreen(
                            selectedAgents = selectedAgents,
                            useOpenRouter = useOpenRouter,
                            openRouterKey = openRouterKey,
                            onToggleAgent = viewModel::toggleAgent,
                            onToggleOpenRouter = viewModel::setUseOpenRouter,
                            onOpenRouterKeyChange = viewModel::setOpenRouterKey,
                            onNext = { viewModel.goTo(WizardStep.REVIEW) },
                        )
                        WizardStep.REVIEW -> ReviewScreen(
                            wantsLinuxDesktop = wantsLinux,
                            selectedAgents = selectedAgents,
                            onConfirm = { viewModel.beginInstall() },
                        )
                        WizardStep.INSTALLING -> InstallingScreen(progress = progress)
                        WizardStep.DONE -> DoneScreen(progress = progress, onFinish = { finish() })
                    }
                }
            }
        }
    }
}
