package com.a1launcher.model

/** Progress of the one-time Linux environment bootstrap, reported by ProotInstallService. */
sealed class InstallStep {
    data object NotStarted : InstallStep()
    data object DownloadingRootfs : InstallStep()
    data object ExtractingRootfs : InstallStep()
    data object ConfiguringPacman : InstallStep()
    data object InstallingCompositor : InstallStep()
    data class InstallingAgent(val agent: AgentCli) : InstallStep()
    data object Done : InstallStep()
    data class Failed(val message: String) : InstallStep()
}

data class InstallProgress(
    val step: InstallStep = InstallStep.NotStarted,
    val percent: Int = 0,
    val logTail: List<String> = emptyList(),
)
