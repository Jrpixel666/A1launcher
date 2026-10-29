package com.a1launcher.linux

import android.content.Context
import java.io.File
import java.util.TimeZone

/**
 * Paths and process launching for the proot'd Arch rootfs.
 *
 * Adapted from Omalauncher's `linux/Proot.kt` (MIT): same binds, same proot
 * flags, same PROOT_LOADER/LD_LIBRARY_PATH env contract (easy to get wrong
 * and the actual reason naive proot invocations silently fail on real
 * devices). Trimmed of Omalauncher's own data binds (Omarchy themes/widgets
 * live outside the rootfs in their build; A1 Launcher doesn't have an
 * equivalent yet) and of GuestFs/hostFile/guestFile (their Files-app path
 * translation, not needed without a Files app).
 */
class Proot(
    private val context: Context,
) {
    private val nativeDir = File(context.applicationInfo.nativeLibraryDir)
    val filesDir: File = context.filesDir
    val rootfs = File(filesDir, "arch")
    val tmpDir = File(filesDir, "tmp")

    /** Bound to XDG_RUNTIME_DIR inside the guest; holds the Wayland socket. */
    val runtimeDir = File(filesDir, "xdg")
    val waylandSocket = File(runtimeDir, "wayland-0")
    val keymap = File(filesDir, "keymap.xkb")

    val home = File(rootfs, "root")
    val applications = File(home, ".local/share/applications")
    val launcherConfig = File(home, ".config/a1launcher")

    /** Stand-ins for /proc files Android hides from apps. */
    val procShim = ProcShim(File(filesDir, "proc"))

    /** How guest apps render OpenGL and Vulkan; software-only until a GPU bridge is ported. */
    @Volatile var graphicsEnv: List<String> = emptyList()

    /**
     * Android's Chinese, Japanese and Korean font, which the guest has none
     * of (Arch's is ~300 MB). Absent on some devices; the guest then goes
     * without, as before.
     */
    private val cjkFont = File("/system/fonts/NotoSansCJK-Regular.ttc")

    private val binds = listOf("/tmp" to tmpDir, "/dev/shm" to tmpDir, GUEST_RUNTIME_DIR to runtimeDir)

    /**
     * Resets what only lives as long as our process (guest processes die with
     * it): /tmp, the runtime dir, a stale pacman lock. Call once, before any
     * guest command runs.
     */
    fun prepare() {
        for (dir in listOf(tmpDir, runtimeDir)) {
            dir.deleteRecursively()
            dir.mkdirs()
        }
        File(rootfs, "var/lib/pacman/db.lck").delete()
        applications.mkdirs()
    }

    private fun command(script: String): List<String> =
        listOf(
            File(nativeDir, "libproot.so").path,
            "--kill-on-exit",
            "--link2symlink",
            "-0",
            "-r",
            rootfs.path,
        ) + procShim.binds.flatMap { (guest, host) -> listOf("-b", "${host.path}:$guest") } +
            listOf(
                "-b", "/dev",
                "-b", "/proc",
                "-b", "/sys/devices/system/cpu",
            ) + (if (cjkFont.isFile) listOf("-b", "${cjkFont.path}:$GUEST_CJK_FONT") else emptyList()) +
            binds.flatMap { (guest, host) -> listOf("-b", "${host.path}:$guest") } +
            listOf(
                "-w", GUEST_HOME,
                "/usr/bin/env", "-i",
                "HOME=$GUEST_HOME",
                "USER=root",
                "LANG=C.UTF-8",
                "TZ=${TimeZone.getDefault().id}",
                "TERM=xterm-256color",
                "PATH=/usr/local/sbin:/usr/local/bin:/usr/bin:/usr/sbin:/root/.local/bin:/root/.local/share/mise/shims",
                "TAR_OPTIONS=--no-same-permissions",
                "MISE_NPM_PACKAGE_MANAGER=npm",
                "DISABLE_AUTOUPDATER=1",
                "OPENCODE_DISABLE_AUTOUPDATE=1",
                "GROK_DISABLE_AUTOUPDATER=1",
                "XDG_RUNTIME_DIR=$GUEST_RUNTIME_DIR",
                "WAYLAND_DISPLAY=${waylandSocket.name}",
                "XDG_SESSION_TYPE=wayland",
                "GDK_BACKEND=wayland",
                "QT_QPA_PLATFORM=wayland",
                "MOZ_ENABLE_WAYLAND=1",
                "SDL_VIDEODRIVER=wayland",
            ) + graphicsEnv + listOf("/bin/sh", "-c", script)

    /**
     * Kills every proot we started and every guest process: before the
     * rootfs is deleted. A SIGKILLed proot doesn't take its tracees along
     * (--kill-on-exit needs a clean exit); they run on untraced and spin, so
     * they go too, found by their executable, proot's loader.
     */
    fun killAll() {
        val proot = File(nativeDir, "libproot.so").path
        val loader = File(nativeDir, "libproot-loader.so").path
        for (dir in File("/proc").listFiles().orEmpty()) {
            val pid = dir.name.toIntOrNull() ?: continue
            val argv0 = runCatching { File(dir, "cmdline").readBytes().takeWhile { it != 0.toByte() }.toByteArray().decodeToString() }.getOrNull()
            val exe = runCatching { android.system.Os.readlink(File(dir, "exe").path) }.getOrNull()
            if (argv0 == proot || exe == loader) runCatching { android.system.Os.kill(pid, android.system.OsConstants.SIGKILL) }
        }
    }

    /** A guest `sh -c [script]`, stderr merged into stdout. Needs [prepare] to have run. */
    fun process(script: String): ProcessBuilder =
        ProcessBuilder(command(script)).apply {
            environment().apply {
                clear()
                put("PROOT_LOADER", File(nativeDir, "libproot-loader.so").path)
                put("PROOT_TMP_DIR", tmpDir.path)
                put("LD_LIBRARY_PATH", nativeDir.path)
            }
            redirectErrorStream(true)
        }

    companion object {
        const val GUEST_HOME = "/root"
        const val GUEST_RUNTIME_DIR = "/run/user/0"
        private const val GUEST_CJK_FONT = "/usr/share/fonts/android/NotoSansCJK-Regular.ttc"
    }
}
