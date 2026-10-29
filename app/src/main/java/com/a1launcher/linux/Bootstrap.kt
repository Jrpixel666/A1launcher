package com.a1launcher.linux

import android.content.Context
import android.system.Os
import com.a1launcher.model.InstallStep
import kotlinx.coroutines.delay
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import kotlin.concurrent.thread

private const val ROOTFS_URL = "http://os.archlinuxarm.org/os/ArchLinuxARM-aarch64-latest.tar.gz"
private const val TARBALL = "ArchLinuxARM-aarch64-latest.tar.gz"
private const val NETWORK_TRIES = 3
private const val FORK_CHECK_SECONDS = 30L
private const val TARBALL_SPACE = 900_000_000L
private const val SYSTEM_SPACE = 2_100_000_000L

private class NoRetry(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * foot, fonts and the classic CLI tool set a Linux session is expected to
 * have, plus xkbcommon for keymap compiling. Adapted from Omalauncher's
 * package list (MIT) — their Omarchy-only picks (hyprland, waybar, ...)
 * dropped since A1 Launcher is its own compositor and UI, not a desktop
 * environment installed into the guest.
 */
val PACKAGES =
    listOf(
        "foot", "ttf-dejavu", "btop", "fastfetch", "neovim", "starship", "eza", "bat", "fd",
        "ripgrep", "fzf", "zoxide", "tmux", "jq", "man-db", "less", "git", "bash-completion",
        "libxkbcommon",
    )

private val MIRRORS =
    listOf(
        "de.mirror.archlinuxarm.org",
        "fl.us.mirror.archlinuxarm.org",
        "eu.mirror.archlinuxarm.org",
        "dk.mirror.archlinuxarm.org",
        "mirror.archlinuxarm.org",
    )

/** Firmware and kernel modules are useless under proot; pacman never unpacks them. */
private const val NO_EXTRACT = "NoExtract = usr/lib/firmware/* usr/lib/modules/*"

/**
 * Sets up the Arch Linux ARM rootfs under proot, no root required.
 *
 * Adapted from Omalauncher's `linux/Bootstrap.kt` (MIT): same download/
 * resume/space-check/extract pipeline and the same disable-the-kernel step,
 * which is the real, tested reason this blocks kernel packages rather than
 * just leaving pacman.conf's IgnorePkg to do it (the ALARM base tarball
 * ships a kernel and firmware pre-installed; IgnorePkg alone would leave
 * that ~1 GB in place). Trimmed of StepJournal/Provisioner's cross-update
 * tracking (step completion here is just "does the marker file exist") and
 * of Omarchy-specific steps (cliamp, its themes).
 */
class Bootstrap(
    private val context: Context,
    private val proot: Proot,
) {
    private var cancelled = false
    private var connection: HttpURLConnection? = null

    fun cancel() {
        cancelled = true
        connection?.disconnect()
    }

    private fun checkCancelled() {
        if (cancelled) throw java.util.concurrent.CancellationException()
    }

    val isInstalled get() = File(proot.rootfs, "usr/bin/pacman").exists()

    /**
     * Runs every step needed from the current state onward. Safe to call
     * again after a failure: each step either no-ops when its marker says
     * it's done, or (installRootfs) refuses to run once the system is up.
     */
    suspend fun run(onProgress: (InstallStep, Float?, List<String>) -> Unit) {
        val log = mutableListOf<String>()
        fun report(step: InstallStep, fraction: Float? = null, line: String? = null) {
            line?.let { log.add(it); if (log.size > 200) log.removeAt(0) }
            onProgress(step, fraction, log.toList())
        }

        if (!isInstalled) {
            attempt(network = true) { installRootfs { step, frac, line -> report(step, frac, line) } }
        }
        attempt(network = false) { configure() }
        attempt(network = false) { checkForks { report(InstallStep.CheckingDevice, null, it) } }
        attempt(network = false) {
            report(InstallStep.Configuring, null, "$ pacman-key --init && pacman-key --populate archlinuxarm")
            guest("pacman-key --init && pacman-key --populate archlinuxarm") { report(InstallStep.Configuring, null, it) }
        }
        attempt(network = false) {
            // The ALARM image ships a kernel and firmware (~1 GB) proot can never use.
            report(InstallStep.Configuring, null, "$ removing the shipped kernel/firmware")
            guest("pacman -Rdd --noconfirm linux-aarch64 \$(pacman -Qq | grep ^linux-firmware); true") { report(InstallStep.Configuring, null, it) }
        }
        if (!File(proot.rootfs, "usr/bin/foot").exists()) {
            attempt(network = true) {
                report(InstallStep.InstallingPackages, null)
                guest(pacmanInstall(PACKAGES, refresh = true)) { report(InstallStep.InstallingPackages, null, it) }
            }
        }
        if (!proot.keymap.exists()) {
            attempt(network = false) { compileKeymap(::report) }
        }
    }

    /** A network step gets a few tries, since phone connections drop; the download itself resumes. */
    private suspend fun attempt(network: Boolean, step: suspend () -> Unit) {
        repeat(if (network) NETWORK_TRIES - 1 else 0) { n ->
            try {
                return step()
            } catch (e: Exception) {
                if (e is NoRetry || e is java.util.concurrent.CancellationException) throw e
                delay(5_000L * (n + 1))
            }
        }
        step()
    }

    private fun installRootfs(report: (InstallStep, Float?, String?) -> Unit) {
        check(!isInstalled) { "refusing to replace a set-up system" }
        proot.rootfs.deleteRecursively()
        val tarball =
            try {
                obtainTarball(report)
            } catch (e: Exception) {
                throw if (e.outOfSpace()) outOfSpace() else e
            }
        try {
            extract(tarball, report)
        } catch (e: Exception) {
            if (e is NoRetry || e is java.util.concurrent.CancellationException || e.outOfSpace()) {
                runCatching { proot.rootfs.deleteRecursively() }
                throw if (e is NoRetry || e is java.util.concurrent.CancellationException) e else outOfSpace()
            }
            if (tarball.parentFile == context.cacheDir) tarball.delete()
            throw e
        }
        tarball.delete()
    }

    private fun requireSpace(bytes: Long) {
        val free = proot.filesDir.usableSpace
        if (free < bytes) throw NoRetry("Not enough space: need ${gigabytes(bytes)}, have ${gigabytes(free)}")
    }

    private fun outOfSpace() = NoRetry("Not enough space to install Linux (need about ${gigabytes(TARBALL_SPACE + SYSTEM_SPACE)})")

    private fun gigabytes(bytes: Long) = "%.1f GB".format(bytes / 1e9)

    private fun Throwable.outOfSpace(): Boolean =
        generateSequence(this) { it.cause }.any {
            (it is android.system.ErrnoException && it.errno == android.system.OsConstants.ENOSPC) ||
                it.message?.contains("ENOSPC") == true
        }

    /** Prefers a tarball pushed via adb to the app's external files dir; downloads otherwise. */
    private fun obtainTarball(report: (InstallStep, Float?, String?) -> Unit): File {
        val external = context.getExternalFilesDir(null)?.let { File(it, TARBALL) }
        if (external != null && external.exists()) return external
        val target = File(context.cacheDir, TARBALL)
        if (target.exists()) return target
        val partial = File(context.cacheDir, "$TARBALL.part")
        val validator = File(context.cacheDir, "$TARBALL.version")
        requireSpace(TARBALL_SPACE - (if (partial.exists()) partial.length() else 0L) + SYSTEM_SPACE)
        report(InstallStep.Downloading, 0f, "downloading Arch Linux ARM")
        val conn = openHttp(ROOTFS_URL)
        connection = conn
        try {
            checkCancelled()
            download(conn, partial, validator, report)
        } finally {
            connection = null
            conn.disconnect()
        }
        checkCancelled()
        check(partial.renameTo(target)) { "couldn't finish the download" }
        validator.delete()
        return target
    }

    private fun download(conn: HttpURLConnection, partial: File, validator: File, report: (InstallStep, Float?, String?) -> Unit) {
        val have = if (partial.exists() && validator.exists()) partial.length() else 0L
        if (have > 0) {
            conn.setRequestProperty("Range", "bytes=$have-")
            conn.setRequestProperty("If-Range", validator.readText())
        }
        val resumed = conn.responseCode == HttpURLConnection.HTTP_PARTIAL
        if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode} downloading the rootfs")
        val start = if (resumed) have else 0L
        val total = conn.contentLengthLong.let { if (it > 0) it + start else -1L }
        (conn.getHeaderField("ETag") ?: conn.getHeaderField("Last-Modified"))?.let(validator::writeText) ?: validator.delete()
        conn.inputStream.use { input ->
            java.io.FileOutputStream(partial, resumed).use { out ->
                val buf = ByteArray(1 shl 16)
                var done = start
                var lastReport = 0L
                while (true) {
                    checkCancelled()
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    done += n
                    if (done - lastReport > 4 shl 20) {
                        lastReport = done
                        report(InstallStep.Downloading, if (total > 0) done.toFloat() / total else null, "${done shr 20} MB")
                    }
                }
            }
        }
        check(total <= 0 || partial.length() == total) { "download ended early" }
    }

    private fun extract(tarball: File, report: (InstallStep, Float?, String?) -> Unit) {
        report(InstallStep.Extracting, 0f, "extracting")
        val root = proot.rootfs
        root.deleteRecursively()
        requireSpace(SYSTEM_SPACE)
        root.mkdirs()
        val total = tarball.length()
        val counter = CountingInputStream(tarball.inputStream())
        TarArchiveInputStream(GZIPInputStream(BufferedInputStream(counter, 1 shl 16), 1 shl 16)).use { tar ->
            var count = 0
            while (true) {
                checkCancelled()
                val entry = tar.nextEntry ?: break
                val name = entry.name.removePrefix("./").trimEnd('/')
                if (name.isEmpty() || skipEntry(name)) continue
                val file = File(root, name)
                file.parentFile?.mkdirs()
                when {
                    entry.isDirectory -> file.mkdirs()
                    entry.isSymbolicLink -> {
                        file.delete()
                        Os.symlink(entry.linkName, file.path)
                    }
                    entry.isLink -> {
                        val target = File(root, entry.linkName.removePrefix("./"))
                        if (target.exists()) target.copyTo(file, overwrite = true)
                    }
                    entry.isFile -> file.outputStream().use { tar.copyTo(it) }
                    else -> continue
                }
                if (!entry.isSymbolicLink) runCatching { Os.chmod(file.path, entry.mode and 0x1ff or 0x180) }
                if (++count % 1000 == 0) report(InstallStep.Extracting, counter.count.toFloat() / total, "$count files")
            }
        }
    }

    private fun skipEntry(name: String) =
        name == "boot" ||
            name.startsWith("boot/") ||
            name.startsWith("usr/lib/firmware/") ||
            name.startsWith("usr/lib/modules/") ||
            name.startsWith("dev/")

    private fun configure() {
        val root = proot.rootfs
        File(root, "etc/resolv.conf").apply {
            delete()
            writeText("nameserver 8.8.8.8\nnameserver 1.1.1.1\n")
        }
        File(root, "etc/hosts").writeText("127.0.0.1 localhost\n::1 localhost\n")
        File(root, "etc/locale.conf").writeText("LANG=C.UTF-8\n")
        File(root, "etc/pacman.d/mirrorlist").writeText(MIRRORS.joinToString("") { "Server = http://$it/\$arch/\$repo\n" })
        val conf = File(root, "etc/pacman.conf")
        conf.writeText(
            conf
                .readText()
                .replace(Regex("(?m)^(DownloadUser|CheckSpace|DisableSandbox|DisableDownloadTimeout|NoExtract)\\b.*\n"), "")
                .replaceFirst("[options]", "[options]\nDisableSandbox\nDisableDownloadTimeout\n$NO_EXTRACT"),
        )
    }

    private fun compileKeymap(report: (InstallStep, Float?, String?) -> Unit) {
        guest(
            "xkbcli compile-keymap --layout us > /tmp/keymap.xkb || " +
                "/usr/lib/xkbcommon/xkbcli-compile-keymap --layout us > /tmp/keymap.xkb",
        ) { report(InstallStep.CompilingKeymap, null, it) }
        writeAtomically(proot.keymap, File(proot.rootfs, "tmp/keymap.xkb").readBytes() + 0)
    }

    /**
     * Runs a pipeline, the smallest guest command that forks. Some custom
     * ROM kernels make every fork of a traced process restart forever; such
     * a phone gets a clear error here instead of hanging on the next step.
     */
    private fun checkForks(report: (String) -> Unit) {
        val process = proot.process("echo forks | cat").start()
        val output = StringBuffer()
        val reader = thread(isDaemon = true) { runCatching { process.inputStream.bufferedReader().forEachLine { output.appendLine(it) } } }
        if (process.waitFor(FORK_CHECK_SECONDS, TimeUnit.SECONDS)) {
            reader.join(1_000)
            output.lines().forEach(report)
            check(process.exitValue() == 0 && "forks" in output) { "this device's kernel can't run the guest (exit ${process.exitValue()})" }
            return
        }
        process.destroyForcibly()
        proot.killAll()
        error("this device's kernel can't run the guest (a command that forks never finished in ${FORK_CHECK_SECONDS}s)")
    }

    private fun guest(script: String, onLine: (String) -> Unit) {
        val code = proot.run(script, onLine)
        check(code == 0) { "command failed (exit $code): ${script.take(60)}" }
    }
}
