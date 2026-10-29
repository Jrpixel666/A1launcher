package com.a1launcher.wayland

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs

/**
 * Window state for Linux toplevels. Every toplevel is shown as its own
 * Android task (LinuxWindowActivity), drawn with a [WindowRenderer]. Lives
 * on the main thread; protocol callbacks are posted over.
 *
 * Ported from Omalauncher's `ui/WindowManager.kt` (MIT): this is the actual
 * per-window-task mechanism (see [openAppWindow] and LinuxWindowActivity's
 * manifest entry, `documentLaunchMode="always"`) — one real Android Activity
 * class, launched with a fresh document task and a unique handle each time,
 * rather than one Activity per possible window.
 */
class WindowManager : WindowManagerHooks {
    lateinit var server: WaylandServer
    private val main = Handler(Looper.getMainLooper())

    var focused by mutableStateOf<XdgToplevel?>(null)
        private set

    val appWindows = mutableStateListOf<XdgToplevel>()
    private val sizes = HashMap<XdgToplevel, Pair<Int, Int>>()

    /** Starts the activity for an app window; set by LinuxRuntime. */
    var openAppWindow: (handle: String) -> Unit = {}

    var created: (XdgToplevel) -> Unit = {}
    var focusGained: (XdgToplevel) -> Unit = {}

    private val handles = HashMap<String, XdgToplevel>()
    private val session = System.currentTimeMillis().toString(36)
    private var nextHandle = 1

    fun windowByHandle(handle: String?) = handle?.let(handles::get)

    fun handleOf(window: XdgToplevel) = handles.entries.firstOrNull { it.value === window }?.key

    val scale get() = server.output.scale

    override fun toplevelCreated(toplevel: XdgToplevel) {
        main.post {
            created(toplevel)
            appWindows += toplevel
            val handle = "$session-${nextHandle++}"
            handles[handle] = toplevel
            openAppWindow(handle)
        }
    }

    override fun toplevelDestroyed(toplevel: XdgToplevel) {
        main.post {
            appWindows -= toplevel
            handles.values.remove(toplevel)
            sizes -= toplevel
            if (focused === toplevel) focus(null)
        }
    }

    fun applyAppLayout(window: XdgToplevel, width: Int, height: Int) {
        sizes[window] = width to height
        synchronized(server) { configure(window) }
    }

    fun close(window: XdgToplevel) = synchronized(server) { window.close() }

    fun focus(toplevel: XdgToplevel?) {
        val previous = focused
        focused = toplevel
        synchronized(server) {
            server.input.setKeyboardFocus(toplevel?.surface)
            listOfNotNull(previous, toplevel).distinct().forEach { configure(it) }
        }
        toplevel?.let(focusGained)
    }

    fun zoom(window: XdgToplevel, steps: Int) {
        if (window !== focused || steps == 0) return
        val code = if (steps > 0) Keys.KEY_EQUAL else Keys.KEY_MINUS
        server.postInput {
            key(Keys.KEY_LEFTCTRL, true)
            repeat(abs(steps)) {
                key(code, true)
                key(code, false)
            }
            key(Keys.KEY_LEFTCTRL, false)
        }
    }

    private fun configure(window: XdgToplevel) {
        val (w, h) = sizes[window] ?: return
        window.configure(w / scale, h / scale, window === focused)
    }

    fun pointerMove(hit: SurfaceHit?) = server.postInput { pointerMotion(hit?.surface, hit?.x ?: 0f, hit?.y ?: 0f) }

    fun pointerButton(button: Int, pressed: Boolean) = server.postInput { pointerButton(button, pressed) }

    fun click(hit: SurfaceHit, button: Int) =
        server.postInput {
            pointerMotion(hit.surface, hit.x, hit.y)
            pointerButton(button, true)
            pointerButton(button, false)
        }

    fun scroll(dx: Float, dy: Float, source: Int, steps: Int = 0) = server.postInput { pointerAxis(dx, dy, source, steps) }

    fun wantsTouch(surface: Surface) = synchronized(server) { server.input.wantsTouch(surface) }

    fun touchDown(hit: SurfaceHit, id: Int) = server.postInput { touchDown(hit.surface, id, hit.x, hit.y) }

    fun touchMotion(id: Int, x: Float, y: Float) = server.postInput { touchMotion(id, x, y) }

    fun touchUp(id: Int) = server.postInput { touchUp(id) }

    fun scrollStop() = server.postInput { pointerAxisStop() }

    private fun dismissPopups(window: XdgToplevel) =
        synchronized(server) {
            server.popups.filter { server.toplevelOf(it.surface) === window }.asReversed().forEach { it.dismiss() }
        }

    fun onPress(window: XdgToplevel, hit: SurfaceHit?): Boolean {
        if (window.renderer?.hasPopups == true && hit?.inPopup != true) {
            dismissPopups(window)
            return false
        }
        if (window !== focused) focus(window)
        return hit != null
    }
}
