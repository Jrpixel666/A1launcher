package com.a1launcher.linux

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.a1launcher.linuxdesktop.LinuxWindowActivity
import com.a1launcher.wayland.WaylandServer
import com.a1launcher.wayland.WindowManager
import kotlin.concurrent.thread

/**
 * Process-wide Linux state: the rootfs, the Wayland server, and the window
 * manager that turns each Linux toplevel into its own Android task.
 *
 * This is the real mechanism the task's "Linux GUI apps open as native
 * Android tasks in Recents" requirement asked for, adapted from
 * Omalauncher's `Runtime.kt` (MIT) down to just that mechanism: [wm]'s
 * [WindowManager.openAppWindow] starts [LinuxWindowActivity] with a fresh
 * document task per window handle (`documentLaunchMode="always"` in the
 * manifest + `FLAG_ACTIVITY_NEW_DOCUMENT`), so each one gets its own Recents
 * card. A1 Launcher's own Compose UI and widgets don't go through here.
 */
class LinuxRuntime(private val context: Context) {
    val proot = Proot(context)
    val wm = WindowManager()
    val server: WaylandServer = WaylandServer(proot.waylandSocket.path, wm).also { wm.server = it }
    val agentStore = AgentStore(proot)
    val bootstrap = Bootstrap(context, proot)

    @Volatile private var started = false

    /** Starts the compositor and loads installed agents; no-ops if Linux isn't installed yet or already running. */
    @Synchronized
    fun start() {
        if (started || !bootstrap.isInstalled) return
        started = true
        proot.prepare()
        proot.procShim.start()
        server.start()
        agentStore.load()
        wm.openAppWindow = ::openWindowActivity
    }

    val isRunning get() = started

    private fun openWindowActivity(handle: String) {
        val intent = Intent(context, LinuxWindowActivity::class.java)
            .setData(Uri.parse("a1launcher://window/$handle"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** Opens a terminal; its toplevel creation is what actually triggers [openWindowActivity]. */
    fun openTerminal() {
        start()
        thread(name = "open-terminal") { runCatching { proot.run("foot") } }
    }

    fun agentInvocationCommand(agent: Agent, prompt: String): String = agentInvocation(agent.bin, prompt)

    /** Opens [agent] in a terminal with [prompt] already typed in, e.g. from the assistant bar. */
    fun openAgent(agent: Agent, prompt: String) {
        start()
        thread(name = "open-agent-${agent.bin}") { runCatching { proot.run(agentInvocationCommand(agent, prompt)) } }
    }
}
