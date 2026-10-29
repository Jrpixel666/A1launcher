package com.a1launcher.linux

import android.content.Context
import com.a1launcher.model.AgentCli
import kotlinx.coroutines.flow.Flow
import java.io.File

/**
 * Orchestrates the shell scripts under assets/scripts/ with the right env
 * vars. Each returned Flow emits raw stdout/stderr lines; a "STEP:<name>"
 * line is the script announcing a phase change (parsed by ProotInstallService
 * into the InstallStep sealed class the UI observes) — everything else is
 * just log output to show in a scrollback view.
 */
class ProotManager(private val context: Context) {

    private val shell = ShellExecutor(context)

    private fun baseEnv(): Map<String, String> = mapOf(
        "PROOT_BIN" to ProotPaths.prootBinary(context).absolutePath,
        "ROOTFS_DIR" to ProotPaths.rootfsDir(context).absolutePath,
        "CACHE_DIR" to ProotPaths.downloadCacheDir(context).absolutePath,
        "ARCH_ABI" to ProotPaths.archAbi(),
        "HOME" to context.filesDir.absolutePath,
        "PATH" to "${ProotPaths.nativeLibDir(context).absolutePath}:/system/bin",
    )

    fun isReady(): Boolean = File(ProotPaths.rootfsDir(context), "usr/bin/pacman").exists()

    fun bootstrapArch(): Flow<String> {
        val scriptsRoot = shell.extractAllScripts()
        return shell.runScript(File(scriptsRoot, "bootstrap-arch.sh"), baseEnv())
    }

    fun installSway(): Flow<String> {
        val scriptsRoot = ProotPaths.scriptCacheDir(context)
        return shell.runScript(File(scriptsRoot, "install-sway.sh"), baseEnv())
    }

    fun installAgent(agent: AgentCli, apiKey: String? = null): Flow<String> {
        val scriptsRoot = ProotPaths.scriptCacheDir(context)
        val scriptFile = File(scriptsRoot, agent.scriptAsset)
        val env = baseEnv().toMutableMap()
        if (apiKey != null) env["API_KEY"] = apiKey
        return shell.runScript(scriptFile, env)
    }

    /**
     * Launches the already-installed Sway session headless-to-Wayland-socket
     * inside the guest; LinuxDesktopActivity is the display client for that
     * socket (see its class doc for the exact handoff and its current limits).
     */
    fun launchSwaySessionCommand(): List<String> = listOf(
        ProotPaths.prootBinary(context).absolutePath,
        "-0", "-r", ProotPaths.rootfsDir(context).absolutePath,
        "-b", "/dev", "-b", "/proc", "-b", "/sys",
        "-w", "/root",
        "/usr/bin/sway",
    )
}
