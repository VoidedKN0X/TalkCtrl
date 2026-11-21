package com.vknxworks.talkctrl.data.settings

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * TalkCtrl's profiles and/or settings as a JSON file you keep. Never in it: the remembered unit (its Bluetooth
 * address and name), the serial number, the app lock's PIN or keys. Every field is optional, so files from
 * older or newer versions restore what they can.
 */
@Serializable
data class SettingsFile(
    val format: String = FORMAT,
    val version: Int = VERSION,
    val appVersion: String? = null,
    val createdAt: String? = null,
    /** Saved profiles by slot name (SOLO, DUO, GROUP): the USER_CONFIG and VOLUMES records in hex, "aabbccdd:…". */
    val profiles: Map<String, String>? = null,
    val useRfcomm: Boolean? = null,
    val modelFolder: String? = null,
    val lockTimeout: String? = null,
    val hideScreenshots: Boolean? = null,
    val hideRecents: Boolean? = null,
    /** FM station names by frequency word (MHz × 100) as text. */
    val fmNames: Map<String, String>? = null,
) {
    companion object {
        const val FORMAT = "talkctrl-settings"
        const val VERSION = 1
        private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; explicitNulls = false }

        fun profilesOf(s: AppSettings, appVersion: String, createdAt: String) = SettingsFile(
            appVersion = appVersion, createdAt = createdAt,
            profiles = s.profiles.mapKeys { it.key.name },
        )

        fun settingsOf(s: AppSettings, appVersion: String, createdAt: String) = SettingsFile(
            appVersion = appVersion, createdAt = createdAt,
            useRfcomm = s.useRfcomm, modelFolder = s.modelFolder, lockTimeout = s.lockTimeout.name,
            hideScreenshots = s.hideScreenshots, hideRecents = s.hideRecents,
            fmNames = s.fmNames.mapKeys { it.key.toString() },
        )

        fun encode(f: SettingsFile): String = json.encodeToString(serializer(), f)

        /** The file, or null when it isn't a TalkCtrl settings file (or a newer format). */
        fun decode(text: String): SettingsFile? = runCatching { json.decodeFromString(serializer(), text) }.getOrNull()
            ?.takeIf { it.format == FORMAT && it.version in 1..VERSION }

        /** Whether [s] is a profile as TalkCtrl stores it: a 4-byte USER_CONFIG and a 7-byte VOLUMES record in hex. */
        fun validProfile(s: String): Boolean {
            val parts = s.split(':')
            if (parts.size != 2) return false
            val a = parts[0]
            val b = parts[1]
            val hex = Regex("^[0-9a-fA-F]+$")
            return a.length == 8 && b.length == 14 && hex.matches(a) && hex.matches(b) &&
                a.startsWith("00") && b.startsWith("02")
        }
    }

    /**
     * [current] with this file applied. Profiles that aren't valid records and unknown slots are skipped;
     * "Hide in screenshots" and "Hide in recent apps" can only be turned on, never off; the app lock itself and
     * the remembered unit are never touched.
     */
    fun applyTo(current: AppSettings): AppSettings {
        val newProfiles = profiles.orEmpty().mapNotNull { (k, v) ->
            val slot = ProfileSlot.entries.firstOrNull { it.name == k } ?: return@mapNotNull null
            if (validProfile(v)) slot to v.lowercase() else null
        }.toMap()
        val names = fmNames?.mapNotNull { (k, v) ->
            val f = k.toIntOrNull()?.takeIf { it in 7_000..11_000 } ?: return@mapNotNull null
            val n = v.replace('\n', ' ').trim().take(30)
            if (n.isEmpty()) null else f to n
        }?.toMap()
        return current.copy(
            profiles = current.profiles + newProfiles,
            useRfcomm = useRfcomm ?: current.useRfcomm,
            modelFolder = modelFolder?.takeIf { Regex("^[A-Za-z0-9_]{1,64}$").matches(it) } ?: current.modelFolder,
            lockTimeout = lockTimeout?.let { n -> LockTimeout.entries.firstOrNull { it.name == n } } ?: current.lockTimeout,
            hideScreenshots = current.hideScreenshots || hideScreenshots == true,
            hideRecents = current.hideRecents || hideRecents == true,
            fmNames = names ?: current.fmNames,
        )
    }
}
