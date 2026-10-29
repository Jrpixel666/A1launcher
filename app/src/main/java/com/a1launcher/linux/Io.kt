package com.a1launcher.linux

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** A connection to [url] with timeouts, not yet connected. The caller disconnects it. */
fun openHttp(
    url: String,
    connectTimeout: Int = 15_000,
    readTimeout: Int = 30_000,
): HttpURLConnection =
    (URL(url).openConnection() as HttpURLConnection).also {
        it.connectTimeout = connectTimeout
        it.readTimeout = readTimeout
    }

/** Lowercase hex, as sha256sum prints it. */
fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).hex()

fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }

/** Replaces [file] in one rename, so a process reading or exec'ing it (a running guest) never sees it half written. */
fun writeAtomically(
    file: File,
    bytes: ByteArray,
    executable: Boolean = false,
) {
    file.parentFile?.mkdirs()
    val tmp = File(file.parentFile, ".${file.name}.tmp")
    tmp.outputStream().use {
        it.write(bytes)
        it.fd.sync()
    }
    if (executable) tmp.setExecutable(true, false)
    if (!tmp.renameTo(file)) {
        tmp.delete()
        throw IOException("can't replace $file")
    }
}

/** Reads to the end, or [limit] bytes. */
fun InputStream.readUpTo(limit: Int): ByteArray {
    val out = ByteArrayOutputStream()
    val chunk = ByteArray(8192)
    while (out.size() < limit) {
        val n = read(chunk, 0, minOf(chunk.size, limit - out.size()))
        if (n < 0) break
        out.write(chunk, 0, n)
    }
    return out.toByteArray()
}

/** Counts the bytes read through it; [onRead] gets the running total after each read. */
class CountingInputStream(
    input: InputStream,
    private val onRead: (Long) -> Unit = {},
) : FilterInputStream(input) {
    var count = 0L
        private set

    override fun read(): Int = super.read().also { if (it >= 0) onRead(++count) }

    override fun read(
        b: ByteArray,
        off: Int,
        len: Int,
    ): Int =
        super.read(b, off, len).also {
            if (it > 0) {
                count += it
                onRead(count)
            }
        }
}
