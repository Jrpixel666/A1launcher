package com.a1launcher.linuxdesktop

import android.app.ActivityManager.TaskDescription
import android.os.Bundle
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.a1launcher.A1LauncherApp
import com.a1launcher.wayland.BTN_LEFT
import com.a1launcher.wayland.SurfaceHit
import com.a1launcher.wayland.WindowRenderer
import com.a1launcher.wayland.XdgToplevel
import kotlinx.coroutines.flow.first

/**
 * Shows a single Linux window as its own Android task: this (with its
 * `documentLaunchMode="always"` manifest entry and LinuxRuntime's use of
 * `FLAG_ACTIVITY_NEW_DOCUMENT`) is literally the "Linux GUI apps open as
 * native Android tasks in Recents" mechanism, adapted from Omalauncher's
 * `LinuxWindowActivity.kt` (MIT).
 *
 * Trimmed of their image-paste/mic-dictation/extra-keys input chrome and of
 * clipboard sync — touch (tap, drag) and the window's own rendering work;
 * physical/soft keyboard forwarding into the Wayland seat is not wired up
 * yet (Input.kt's `key()` is ready for it; this activity doesn't call it).
 */
class LinuxWindowActivity : ComponentActivity() {

    private val runtime get() = (application as A1LauncherApp).linuxRuntime
    private var window: XdgToplevel? = null
    private var renderer: WindowRenderer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val handle = intent.data?.lastPathSegment
        val window = runtime.wm.windowByHandle(handle)
        if (window == null) {
            finishAndRemoveTask()
            return
        }
        this.window = window
        val renderer = WindowRenderer(runtime.server, window)
        this.renderer = renderer
        window.renderer = renderer

        setTaskDescription(TaskDescription.Builder().setLabel(window.appId.ifBlank { "Linux window" }).build())

        setContent {
            AndroidView(
                factory = { context ->
                    SurfaceView(context).apply {
                        holder.addCallback(object : SurfaceHolder.Callback {
                            override fun surfaceCreated(holder: SurfaceHolder) = Unit
                            override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
                                renderer.attach(holder.surface, w, h)
                                runtime.wm.applyAppLayout(window, w, h)
                            }
                            override fun surfaceDestroyed(holder: SurfaceHolder) = renderer.detach()
                        })
                        setOnTouchListener { _, event -> handleTouch(window, renderer, event) }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            LaunchedEffect(window) {
                val stillOpen = derivedStateOf { window in runtime.wm.appWindows }
                snapshotFlow { stillOpen.value }.first { !it }
                finishAndRemoveTask()
            }
        }
    }

    private fun handleTouch(window: XdgToplevel, renderer: WindowRenderer, event: MotionEvent): Boolean {
        val hit: SurfaceHit? = renderer.hitTest(event.x, event.y)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!runtime.wm.onPress(window, hit)) return true
                runtime.wm.pointerMove(hit)
                runtime.wm.pointerButton(BTN_LEFT, true)
            }
            MotionEvent.ACTION_MOVE -> runtime.wm.pointerMove(hit)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                runtime.wm.pointerButton(BTN_LEFT, false)
            }
        }
        return true
    }

    override fun onDestroy() {
        renderer?.release()
        window?.renderer = null
        super.onDestroy()
    }
}
