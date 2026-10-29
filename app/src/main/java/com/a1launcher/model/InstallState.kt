package com.a1launcher.model

import com.a1launcher.linux.Agent

/** Progress of the one-time Linux environment bootstrap, reported by ProotInstallService. */
sealed class InstallStep {
    data object NotStarted : InstallStep()
    data object Downloading : InstallStep()
    data object Extracting : InstallStep()
    data object Configuring : InstallStep()
    data object CheckingDevice : InstallStep()
    data object InstallingPackages : InstallStep()
    data object CompilingKeymap : InstallStep()
    data class InstallingAgent(val agent: Agent) : InstallStep()
    data object Done : InstallStep()
    data class Failed(val message: String) : InstallStep()
}

data class InstallProgress(
    val step: InstallStep = InstallStep.NotStarted,
    /** 0f-1f where known (a download, an extraction); null for a step with no meaningful fraction. */
    val fraction: Float? = null,
    val logTail: List<String> = emptyList(),
)
