package com.vknxworks.talkctrl.update

import com.vknxworks.talkctrl.protocol.FirmwareVersion
import com.vknxworks.talkctrl.protocol.Models
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.io.IOException
import java.net.URI
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/** What fw_info.json says. */
data class FirmwareInfo(val version: FirmwareVersion?, val versionText: String, val url: String?, val enabledByOta: Boolean)

/**
 * The only network code in TalkCtrl: Cardo's public update server, and nothing else.
 * Each call is one anonymous HTTPS GET started by a tap. The URL carries only the model folder (and, for the
 * download, the firmware file path the server itself named). No account, token, serial or device id; a fixed
 * User-Agent; no cookies; redirects are not followed.
 */
object UpdateServer {
    const val HOST = "update.cardosystems.com"
    private const val BASE = "https://$HOST/firmware/"
    private const val MAX_JSON = 256 * 1024
    private const val MAX_FIRMWARE = 64L * 1024 * 1024

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    class RefusedException(message: String) : IOException(message)

    private fun folderUrl(folder: String, file: String): URL {
        if (!Models.isKnownFolder(folder) || !folder.matches(Regex("[A-Za-z0-9_]+"))) throw RefusedException("unknown model")
        return URL(BASE + folder + "/" + file)
    }

    /** "Check for update": fw_info.json for one model folder. */
    suspend fun check(folder: String): FirmwareInfo {
        val o = json.parseToJsonElement(getText(folderUrl(folder, "fw_info.json"))).jsonObject
        val v = o.str("fw_version").orEmpty()
        return FirmwareInfo(FirmwareVersion.parse(v), v, o.str("fw_url"), (o["isEnableByOTA"] as? JsonPrimitive)?.booleanOrNull ?: true)
    }

    /** "What's new": fw_release_notes.json, as lines per language key (en, de, es, fr, it, ja, ko, pt, ru, zh). */
    suspend fun releaseNotes(folder: String): Map<String, List<String>> {
        val o = json.parseToJsonElement(getText(folderUrl(folder, "fw_release_notes.json"))).jsonObject
        return o.mapNotNull { (k, v) ->
            (v as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }?.let { k to it }
        }.toMap()
    }

    /** The firmware file address, with the language filled in; null if it isn't on Cardo's update server. */
    fun firmwareUrl(info: FirmwareInfo, languageKey: String): URL? {
        val raw = info.url?.replace("{language}", languageKey)?.trim() ?: return null
        val https = if (raw.startsWith("http://")) "https://" + raw.removePrefix("http://") else raw
        return try {
            val uri = URI(https)
            if (uri.scheme != "https" || uri.host != HOST || uri.port != -1 || uri.userInfo != null) null else uri.toURL()
        } catch (_: Exception) {
            null
        }
    }

    suspend fun download(url: URL, dest: File, progress: (Long, Long) -> Unit) = withContext(Dispatchers.IO) {
        val c = open(url)
        try {
            val total = c.contentLengthLong
            if (total > MAX_FIRMWARE) throw RefusedException("too big")
            val tmp = File(dest.path + ".part")
            // A failed or cancelled download leaves nothing behind.
            var complete = false
            try {
                c.inputStream.use { input ->
                    tmp.outputStream().use { out ->
                        val buf = ByteArray(16 * 1024)
                        var done = 0L
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = input.read(buf)
                            if (n < 0) break
                            done += n
                            if (done > MAX_FIRMWARE) throw RefusedException("too big")
                            out.write(buf, 0, n)
                            progress(done, total)
                        }
                    }
                }
                if (!tmp.renameTo(dest)) throw IOException("rename")
                complete = true
            } finally {
                if (!complete) tmp.delete()
            }
        } finally {
            c.disconnect()
        }
    }

    private suspend fun getText(url: URL): String = withContext(Dispatchers.IO) {
        val c = open(url)
        try {
            val bytes = c.inputStream.use { it.readNBytes(MAX_JSON) }
            String(bytes, Charsets.UTF_8)
        } finally {
            c.disconnect()
        }
    }

    private fun open(url: URL): HttpsURLConnection {
        // Belt and braces: never talk to anything but Cardo's update host over HTTPS.
        if (url.protocol != "https" || url.host != HOST) throw RefusedException("host")
        val c = url.openConnection() as HttpsURLConnection
        c.instanceFollowRedirects = false
        c.useCaches = false
        c.connectTimeout = 15_000
        c.readTimeout = 30_000
        c.setRequestProperty("User-Agent", "TalkCtrl")
        c.setRequestProperty("Accept", "*/*")
        val code = c.responseCode
        if (code != 200) {
            c.disconnect()
            throw IOException("HTTP $code")
        }
        return c
    }

    private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull
}
