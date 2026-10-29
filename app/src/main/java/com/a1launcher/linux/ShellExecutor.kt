package com.a1launcher.linux

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * Copies a shell-script asset out to app-private storage and runs it with
 * the bundled `proot` as the interpreter's host process.
 *
 * Note this is the inverse of the W^X concern in ProotPaths: the *script*
 * itself is never exec()'d directly by Android — it's data, read by `sh`
 * (which lives inside the Arch rootfs, or is the bundled busybox applet),
 * exactly like any shell script is data to its interpreter. Only the actual
 * machine-code binaries (`proot`, `busybox`) must live in nativeLibraryDir.
 */
class ShellExecutor(private val context: Context) {

    fun extractScriptAsset(assetPath: String): File {
        val dest = File(ProotPaths.scriptCacheDir(context), assetPath.substringAfterLast('/'))
        context.assets.open("scripts/$assetPath").use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        return dest
    }

    /** Extracts every file under assets/scripts/ once, preserving the agents/ subfolder. */
    fun extractAllScripts(): File {
        val root = ProotPaths.scriptCacheDir(context)
        fun copyDir(assetDir: String, destDir: File) {
            destDir.mkdirs()
            val entries = context.assets.list(assetDir) ?: return
            for (entry in entries) {
                val assetSub = if (assetDir.isEmpty()) entry else "$assetDir/$entry"
                val children = context.assets.list(assetSub)
                if (children != null && children.isNotEmpty()) {
                    copyDir(assetSub, File(destDir, entry))
                } else {
                    context.assets.open("scripts/$assetSub").use { input ->
                        File(destDir, entry).outputStream().use { output -> input.copyTo(output) }
                    }
                }
            }
        }
        copyDir("", root)
        return root
    }

    /**
     * Runs [scriptFile] via the bundled proot's own `sh` (busybox ash, also
     * vendored in nativeLibraryDir as libbusybox.so), with [env] merged into
     * the child process environment. Emits stdout/stderr line by line.
     */
    fun runScript(scriptFile: File, env: Map<String, String>): Flow<String> = flow {
        val busybox = File(ProotPaths.nativeLibDir(context), "libbusybox.so")
        val interpreter = if (busybox.canExecute()) busybox.absolutePath else "/system/bin/sh"
        val args = if (busybox.canExecute()) {
            listOf(interpreter, "sh", scriptFile.absolutePath)
        } else {
            listOf(interpreter, scriptFile.absolutePath)
        }

        val builder = ProcessBuilder(args)
            .redirectErrorStream(true)
            .directory(context.filesDir)
        builder.environment().putAll(env)
        builder.environment()["PROOT_TMP_DIR"] = context.cacheDir.absolutePath

        val process = builder.start()
        BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
            reader.lineSequence().forEach { emit(it) }
        }
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            throw IllegalStateException("${scriptFile.name} exited with code $exitCode")
        }
    }.flowOn(Dispatchers.IO)
}
