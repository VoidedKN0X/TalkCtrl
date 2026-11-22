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
    /** Low-battery warning level in % (0 off). Only turned on by an import when notifications are allowed. */
    val lowBattery: Int? = null,
) {
    companion object {
        const val FORMAT = "talkctrl-settings"
        const val VERSION = 1
        /** The levels offered in Settings (0 = off). */
        val LOW_BATTERY_CHOICES = listOf(0, 10, 15, 20, 30)
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
            lowBattery = s.lowBattery,
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
     * "Hide in screenshots" and "Hide in recent apps" can only be turned on, never off, and the lock timeout can
     * only get shorter; the app lock itself and
     * the remembered unit are never touched.
     */
    fun applyTo(current: AppSettings, canNotify: Boolean = true): AppSettings {
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
            // Like the two Hide switches, a file can only make the lock stricter: its timeout is used only
            // when it asks again sooner than the one set here.
            lockTimeout = lockTimeout?.let { n -> LockTimeout.entries.firstOrNull { it.name == n } }
                ?.takeIf { lockWaitRank(it.millis) < lockWaitRank(current.lockTimeout.millis) } ?: current.lockTimeout,
            hideScreenshots = current.hideScreenshots || hideScreenshots == true,
            hideRecents = current.hideRecents || hideRecents == true,
            fmNames = names ?: current.fmNames,
            lowBattery = lowBattery?.takeIf { it in LOW_BATTERY_CHOICES && (it == 0 || canNotify) } ?: current.lowBattery,
        )
    }
}

/**
 * How long the app lock waits before asking again, for comparing timeouts: the smaller, the stricter.
 * A negative value ("only when TalkCtrl restarts") waits the longest.
 */
internal fun lockWaitRank(millis: Long): Long = if (millis < 0) Long.MAX_VALUE else millis
