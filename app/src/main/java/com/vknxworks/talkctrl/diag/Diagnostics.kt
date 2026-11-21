package com.vknxworks.talkctrl.diag

import android.os.Build
import com.vknxworks.talkctrl.BuildConfig
import com.vknxworks.talkctrl.bt.UnitState
import com.vknxworks.talkctrl.data.settings.AppSettings
import com.vknxworks.talkctrl.protocol.Models
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/**
 * A plain-text report of what TalkCtrl sees of the unit, to check on a real unit that the protocol works.
 * Personal details are left out on purpose: no unit name, serial number, Bluetooth address, speed-dial number
 * or FM station names, and the message log has those messages reduced to opcode and length ([MessageLog]).
 * Written only to the file you choose; never sent anywhere.
 */
object Diagnostics {
    fun build(unit: UnitState, settings: AppSettings, log: List<String>): String = buildString {
        fun line(k: String, v: Any?) = append(k).append(": ").append(v ?: "–").append('\n')
        fun hex(b: ByteArray) = b.joinToString(" ") { "%02x".format(it.toInt() and 0xFF) }
        append("TalkCtrl diagnostics\n")
        append("Personal details removed: unit name, serial number, Bluetooth address, speed-dial number, station names.\n\n")
        line("Created (UTC)", OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS))
        line("TalkCtrl", BuildConfig.VERSION_NAME)
        line("Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        line("Phone", "${Build.MANUFACTURER} ${Build.MODEL}")
        append('\n')
        line("Link", unit.link)
        line("Transport", if (unit.rfcomm) "classic Bluetooth (RFCOMM)" else "Bluetooth LE (GATT)")
        line("RFCOMM fallback setting", settings.useRfcomm)
        line("CAIP version", unit.caipVersion)
        line("Unsupported CAIP version", unit.unsupportedVersion)
        line("Firmware", unit.firmware)
        line("Firmware (internal)", unit.internalVersion)
        line("Model folder (picked)", settings.modelFolder)
        // Only the model the serial's two-letter prefix points to, never the serial itself.
        line("Model folder (from serial prefix)", Models.guess(unit.serial)?.folder)
        line("Name reported", unit.name != null)
        line("Serial reported", unit.serial != null)
        line("Battery", unit.battery?.let { "$it %" })
        line("Charging", unit.charging)
        append('\n')
        line("USER_CONFIG", unit.user?.let { hex(it.record()) })
        line("VOLUMES", unit.volumes?.let { hex(it.record()) })
        line("Capabilities", unit.caps?.let { "%02x %02x".format(it.b1, it.b2) })
        line("Languages", unit.languages.joinToString(",").ifEmpty { null })
        line("EQ profiles", unit.eqs.joinToString(",").ifEmpty { null })
        line("FM presets (MHz)", unit.presets?.joinToString(", "))
        line("FM state / preset / MHz", "${unit.fmState} / ${unit.fmPreset} / ${unit.fmMhz ?: "–"}")
        line("Music sharing", unit.musicSharing)
        line("Ready for update", unit.otaReady)
        append("\nMessages (> phone to unit, < unit to phone, time since connecting), last ${log.size}:\n")
        if (log.isEmpty()) append("(none since TalkCtrl started)\n")
        log.forEach { append(it).append('\n') }
    }
}
