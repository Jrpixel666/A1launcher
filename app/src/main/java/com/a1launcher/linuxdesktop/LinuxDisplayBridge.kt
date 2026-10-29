package com.a1launcher.linuxdesktop

import android.view.Surface

/**
 * The one piece this scaffold deliberately does not implement: compositing
 * Sway's Wayland output onto an Android [Surface] and forwarding touch/key
 * input back into the Wayland seat.
 *
 * Why it's a stub, not fudged: that requires a custom wlroots backend
 * (`wlr_output`/`wlr_input_device` implementations) built as native code
 * against the NDK, which takes the running Sway process's rendered frames
 * and blits them via `ANativeWindow_fromSurface()` — real JNI/C work with a
 * native build step this repo doesn't have wired up yet. Everything around
 * this interface (activity lifecycle, task affinity so the session gets its
 * own Recents card, starting/stopping the proot `sway` process, surface
 * resize-on-rotation handling) is real and wired up in LinuxDesktopActivity;
 * only the native compositor bridge itself is missing.
 *
 * To finish this: implement a JNI module (e.g. `waybridge`) exposing these
 * three calls, built as `app/src/main/cpp/waybridge.cpp` with a wlroots
 * dependency, and swap [NoOpLinuxDisplayBridge] for the real binding in
 * LinuxDesktopActivity.
 */
interface LinuxDisplayBridge {
    /** Hands the bridge the Android Surface to render Wayland frames into. */
    fun attachSurface(surface: Surface, widthPx: Int, heightPx: Int)

    /** Surface resized (rotation, split-screen, freeform resize, ...). */
    fun resizeSurface(widthPx: Int, heightPx: Int)

    /** Forward a touch/pointer event into the Wayland seat's virtual input device. */
    fun dispatchPointerEvent(xPx: Float, yPx: Float, action: Int)

    fun detachSurface()
}

class NoOpLinuxDisplayBridge : LinuxDisplayBridge {
    override fun attachSurface(surface: Surface, widthPx: Int, heightPx: Int) = Unit
    override fun resizeSurface(widthPx: Int, heightPx: Int) = Unit
    override fun dispatchPointerEvent(xPx: Float, yPx: Float, action: Int) = Unit
    override fun detachSurface() = Unit
}
