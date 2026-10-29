package com.a1launcher.linux

import com.a1launcher.model.LocalModel
import kotlin.concurrent.thread

/** Starts/stops the local `llama-server` process and tracks which model it's serving. */
class LlamaServerRuntime(private val proot: Proot) {

    @Volatile private var process: Process? = null
    @Volatile var runningModel: LocalModel? = null
        private set

    val isRunning get() = process?.isAlive == true

    @Synchronized
    fun start(model: LocalModel) {
        if (isRunning && runningModel?.id == model.id) return
        stop()
        proot.prepare()
        process = proot.process(LlamaCpp.serverCommand(model)).start()
        runningModel = model
        // Drain output on a daemon thread so the guest process never blocks on a full pipe.
        thread(name = "llama-server-log", isDaemon = true) {
            runCatching { process?.inputStream?.bufferedReader()?.forEachLine { } }
        }
    }

    @Synchronized
    fun stop() {
        process?.destroy()
        process = null
        runningModel = null
    }
}
