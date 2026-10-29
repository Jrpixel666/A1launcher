package com.a1launcher.linuxdesktop

import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import com.a1launcher.linux.ProotManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The Linux desktop session as its own native Android task (see the
 * `taskAffinity`/`singleTask` manifest entry) — this is the "single Linux
 * Desktop task" design: one Recents card hosting the whole Sway session,
 * which does its own tiling/window management inside it, rather than one
 * Recents card per Linux window (see README: Linux GUI Integration for why).
 *
 * Pixel compositing is currently a documented stub — see LinuxDisplayBridge.
 */
class LinuxDesktopActivity : ComponentActivity() {

    private val bridge: LinuxDisplayBridge = NoOpLinuxDisplayBridge()
    private var swayProcess: Process? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    LinuxDesktopSurface(bridge = bridge)
                }
            }
        }
        startSwaySession()
    }

    private fun startSwaySession() {
        val manager = ProotManager(this)
        if (!manager.isReady()) return
        lifecycleScope.launch(Dispatchers.IO) {
            val command = manager.launchSwaySessionCommand()
            val builder = ProcessBuilder(command)
                .redirectErrorStream(true)
                .directory(filesDir)
            swayProcess = builder.start()
        }
    }

    override fun onDestroy() {
        swayProcess?.destroy()
        super.onDestroy()
    }
}

@Composable
private fun LinuxDesktopSurface(bridge: LinuxDisplayBridge) {
    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { context ->
                SurfaceView(context).apply {
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            bridge.attachSurface(holder.surface, width, height)
                        }

                        override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
                            bridge.resizeSurface(w, h)
                        }

                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            bridge.detachSurface()
                        }
                    })
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        if (bridge is NoOpLinuxDisplayBridge) {
            Text(
                text = "Sway is running under proot, but the display bridge\n" +
                    "(Wayland output → this surface) isn't wired up yet.\n" +
                    "See LinuxDisplayBridge.kt.",
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
            )
        }
    }
}
