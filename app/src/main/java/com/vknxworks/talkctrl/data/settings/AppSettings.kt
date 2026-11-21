package com.vknxworks.talkctrl.data.settings

import android.content.Context
import androidx.annotation.StringRes
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vknxworks.talkctrl.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.io.IOException

val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = "talkctrl")

/** How long TalkCtrl can be in the background before the app lock asks again (-1: only after a restart). */
enum class LockTimeout(@StringRes val label: Int, val millis: Long) {
    IMMEDIATELY(R.string.lock_again_immediately, 0L),
    MINUTE_1(R.string.lock_again_1m, 60_000L),
    MINUTES_5(R.string.lock_again_5m, 5 * 60_000L),
    MINUTES_15(R.string.lock_again_15m, 15 * 60_000L),
    HOUR_1(R.string.lock_again_1h, 60 * 60_000L),
    RESTART(R.string.lock_again_restart, -1L),
}

/** The three one-tap profiles. */
enum class ProfileSlot(@StringRes val label: Int) {
    SOLO(R.string.profile_solo),
    DUO(R.string.profile_duo),
    GROUP(R.string.profile_group),
}

data class AppSettings(
    /** The remembered unit (Bluetooth address and the name it had). */
    val unitAddress: String? = null,
    val unitName: String? = null,
    /** Connect over classic Bluetooth (RFCOMM) instead of BLE. */
    val useRfcomm: Boolean = false,
    /** Model folder on Cardo's update server, as picked on the update screen. */
    val modelFolder: String? = null,
    /** Saved profiles: hex of the USER_CONFIG record + the VOLUMES record, per slot. */
    val profiles: Map<ProfileSlot, String> = emptyMap(),
    val lockTimeout: LockTimeout = LockTimeout.IMMEDIATELY,
    val hideScreenshots: Boolean = false,
    val hideRecents: Boolean = false,
    /** Your own names for FM stations, by frequency word (MHz × 100), kept on the phone only. */
    val fmNames: Map<Int, String> = emptyMap(),
    /** Warn when the unit's battery reaches this % (0: off). */
    val lowBattery: Int = 0,
) {
    /** The name you gave [mhz], if any. */
    fun fmName(mhz: Double?): String? = mhz?.let { fmNames[Math.round(it * 100).toInt()] }
}

/** [AppSettings.fmNames] as stored: one "frequency=name" per line (names can't hold a line break). */
internal fun encodeFmNames(m: Map<Int, String>): String = m.entries.sortedBy { it.key }.joinToString("\n") { "${it.key}=${it.value}" }

internal fun decodeFmNames(s: String?): Map<Int, String> = s.orEmpty().lines().mapNotNull { line ->
    val i = line.indexOf('=')
    if (i <= 0) return@mapNotNull null
    val f = line.substring(0, i).toIntOrNull() ?: return@mapNotNull null
    val n = line.substring(i + 1).trim()
    if (n.isEmpty()) null else f to n
}.toMap()


class SettingsRepository(private val context: Context, scope: CoroutineScope) {
    private object K {
        val unitAddress = stringPreferencesKey("unit_address")
        val unitName = stringPreferencesKey("unit_name")
        val useRfcomm = booleanPreferencesKey("use_rfcomm")
        val modelFolder = stringPreferencesKey("model_folder")
        val profiles = ProfileSlot.entries.associateWith { stringPreferencesKey("profile_${it.name.lowercase()}") }
        val lockTimeout = stringPreferencesKey("lock_timeout")
        val hideScreenshots = booleanPreferencesKey("hide_screenshots")
        val hideRecents = booleanPreferencesKey("hide_recents")
        val fmNames = stringPreferencesKey("fm_names")
        val lowBattery = androidx.datastore.preferences.core.intPreferencesKey("low_battery")
    }

    private val prefs: Flow<Preferences> = context.appDataStore.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    val settings: StateFlow<AppSettings> = prefs
        .map { it.toSettings() }
        .stateIn(scope, SharingStarted.Eagerly, AppSettings())

    private fun Preferences.toSettings() = AppSettings(
        unitAddress = this[K.unitAddress],
        unitName = this[K.unitName],
        useRfcomm = this[K.useRfcomm] ?: false,
        modelFolder = this[K.modelFolder],
        profiles = K.profiles.mapNotNull { (slot, key) -> this[key]?.let { slot to it } }.toMap(),
        lockTimeout = enumOr(this[K.lockTimeout], LockTimeout.IMMEDIATELY),
        hideScreenshots = this[K.hideScreenshots] ?: false,
        hideRecents = this[K.hideRecents] ?: false,
        fmNames = decodeFmNames(this[K.fmNames]),
        lowBattery = (this[K.lowBattery] ?: 0).coerceIn(0, 50),
    )

    /** Saves a change. If the phone's storage fails, the change is dropped instead of closing the app. */
    suspend fun update(transform: (AppSettings) -> AppSettings) {
        try {
            context.appDataStore.edit { p ->
                val s = transform(p.toSettings())
                p.putOrRemove(K.unitAddress, s.unitAddress)
                p.putOrRemove(K.unitName, s.unitName)
                p[K.useRfcomm] = s.useRfcomm
                p.putOrRemove(K.modelFolder, s.modelFolder)
                K.profiles.forEach { (slot, key) -> p.putOrRemove(key, s.profiles[slot]) }
                p[K.lockTimeout] = s.lockTimeout.name
                p[K.hideScreenshots] = s.hideScreenshots
                p[K.hideRecents] = s.hideRecents
                p.putOrRemove(K.fmNames, encodeFmNames(s.fmNames).ifEmpty { null })
                p[K.lowBattery] = s.lowBattery
            }
        } catch (e: IOException) {
            // Nothing to do: the settings stay as they were.
        }
    }
}

private fun androidx.datastore.preferences.core.MutablePreferences.putOrRemove(key: Preferences.Key<String>, v: String?) {
    if (v != null) this[key] = v else remove(key)
}

private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
    name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default
