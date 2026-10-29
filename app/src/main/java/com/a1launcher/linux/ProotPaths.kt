package com.a1launcher.linux

import android.content.Context
import android.os.Build
import java.io.File

/**
 * Where everything proot-related lives, and why.
 *
 * Android 10+ (API 29) enforces W^X: a file an app wrote into its own
 * writable storage (filesDir, cacheDir, a downloaded tarball, ...) can never
 * be mmap'd executable, so it can never be exec()'d — this blocks the naive
 * "download a `proot` binary at runtime and run it" approach entirely.
 *
 * The standard workaround (how Termux itself ships `proot`/`bash`/`busybox`
 * post Android 10) is to package those binaries as *native libraries*:
 * drop prebuilt, statically-linked binaries under
 * `app/src/main/jniLibs/<abi>/libproot.so` (any name ending in .so works —
 * the content doesn't have to be an actual shared object) so Gradle copies
 * them into the APK the same way it would a JNI .so. The installed app's
 * `applicationInfo.nativeLibraryDir` is part of the app's verified code and
 * *is* exec-permitted under W^X, so that's where we read `proot` from.
 *
 * This repo does not vendor those binaries (see README's "Vendoring the
 * proot/busybox binaries" section for exactly how to build and place them) —
 * everything below is the real, correct runtime path for them once present.
 */
object ProotPaths {

    fun nativeLibDir(context: Context): File = File(context.applicationInfo.nativeLibraryDir)

    fun prootBinary(context: Context): File = File(nativeLibDir(context), "libproot.so")

    fun rootfsDir(context: Context): File = File(context.filesDir, "arch")

    fun scriptCacheDir(context: Context): File = File(context.filesDir, "scripts").apply { mkdirs() }

    fun downloadCacheDir(context: Context): File = File(context.cacheDir, "linux-bootstrap").apply { mkdirs() }

    /** Matches Arch Linux ARM's tarball naming; Android's primary ABI decides which rootfs we fetch. */
    fun archAbi(): String = when (Build.SUPPORTED_ABIS.firstOrNull()) {
        "arm64-v8a" -> "aarch64"
        "armeabi-v7a" -> "armv7"
        else -> "aarch64"
    }

    fun isProotAvailable(context: Context): Boolean = prootBinary(context).canExecute()
}
