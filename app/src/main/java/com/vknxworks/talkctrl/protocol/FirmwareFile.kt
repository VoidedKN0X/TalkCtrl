package com.vknxworks.talkctrl.protocol

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.GZIPInputStream

/**
 * Cardo's update server serves firmware as `<language>_ver.tar.gz`; the Cardo app takes the first file in the
 * archive and sends that (`SettingsUpdatingFWViewModel.extractTarGZ`). `.bin` and `.fot` files are sent as they are.
 */
object FirmwareFile {
    private const val MAX = 64 * 1024 * 1024

    fun isTarGz(name: String) = name.lowercase().endsWith(".tar.gz") || name.lowercase().endsWith(".tgz")

    /** The first regular file in a .tar.gz, or null if it isn't one. */
    fun firstTarEntry(gz: ByteArray): ByteArray? = try {
        val tar = GZIPInputStream(ByteArrayInputStream(gz)).use { readCapped(it) }
        firstEntry(tar)
    } catch (_: IOException) {
        null
    }

    private fun readCapped(input: java.io.InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            if (out.size() > MAX) throw IOException("too big")
        }
        return out.toByteArray()
    }

    /** POSIX/ustar: 512-byte headers, size as octal at offset 124 (12 bytes), type flag at 156. */
    internal fun firstEntry(tar: ByteArray): ByteArray? {
        var at = 0
        while (at + 512 <= tar.size) {
            val header = tar.copyOfRange(at, at + 512)
            if (header.all { it.toInt() == 0 }) return null
            val size = String(header, 124, 12, Charsets.US_ASCII).trim { it == ' ' || it == '\u0000' }.toLongOrNull(8) ?: return null
            val type = header[156].toInt().toChar()
            val dataStart = at + 512
            if (size < 0 || dataStart + size > tar.size) return null
            if (type == '0' || type == '\u0000') {
                return if (size > 0) tar.copyOfRange(dataStart, dataStart + size.toInt()) else null
            }
            at = dataStart + (((size + 511) / 512) * 512).toInt()
        }
        return null
    }
}
