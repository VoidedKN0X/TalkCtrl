package com.vknxworks.talkctrl.protocol

import java.util.UUID
import kotlin.math.roundToInt

/**
 * Cardo's CAIP64 control protocol, written from PROTOCOL.md. Pure Kotlin (no Android), so it can be unit tested.
 * All multi-byte values are big-endian.
 */
object Caip {
    val SERVICE: UUID = UUID.fromString("cd007f80-8b0b-11e6-ae22-56b6b6499611")
    val SERVICE_BLE_ONLY: UUID = UUID.fromString("cd007f83-8b0b-11e6-ae22-56b6b6499611")
    val WRITE: UUID = UUID.fromString("cd007f81-8b0b-11e6-ae22-56b6b6499611")
    val NOTIFY: UUID = UUID.fromString("cd007f82-8b0b-11e6-ae22-56b6b6499611")
    val RFCOMM: UUID = UUID.fromString("cd007f85-8b0b-11e6-ae22-56b6b6499611")
    val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    const val MTU = 64

    /** Address prefixes the Cardo app treats as Cardo units. */
    val CARDO_OUIS = listOf("00:0A:9B", "00:02:5B", "F8:23:87")
    fun isCardoAddress(address: String) = CARDO_OUIS.any { address.uppercase().startsWith(it) }

    // Opcodes
    const val OP_GET_PARAM = 0x00
    const val OP_GET_SPEED_DIAL = 0x01
    const val OP_GET_SN = 0x02
    const val OP_GET_VER = 0x03
    const val OP_GET_FRIENDLY_NAME = 0x04
    const val OP_SET_PARAM = 0x10
    const val OP_SET_SPEED_DIAL = 0x11
    const val OP_SET_FRIENDLY_NAME = 0x12
    const val OP_SERVICE_SET = 0x22
    const val OP_CONTROL = 0x30
    const val OP_UPDATE_PARAM = 0x40
    const val OP_UPDATE_SPEED_DIAL = 0x41
    const val OP_UPDATE_FRIENDLY_NAME = 0x42
    const val OP_UPDATE_SERIAL = 0x43
    const val OP_SRVC_STATE = 0x50
    const val OP_SRVC_BATTERY = 0x51
    const val OP_SRVC_MUSIC_SHARING = 0x69
    const val OP_OTA_START = 0xA2
    const val OP_OTA_READY = 0xA3

    /** CAIP version 0x40: "PRO mesh" units. */
    const val VERSION_CAIP64 = 0x40

    // Control events (sent as 0x30 hi lo len extra)
    object Ev {
        const val FACTORY_RESET = 0x0555
        const val FM_ON = 0x2000
        const val FM_OFF = 0x2001
        const val FM_SEEK_DOWN = 0x2002
        const val FM_SEEK_UP = 0x2003
        const val FM_SCAN = 0x2004
        const val FM_SCAN_BACK = 0x2005
        const val FM_AUTO_TUNE = 0x2006
        const val FM_PLAY_PRESET = 0x2007
        const val FM_SET_FREQ = 0x2008
        const val FM_STOP_SCAN = 0x2009
        const val GROUP_JOIN = 0x2100
        const val GROUP_CREATE = 0x2101
        const val GROUP_DELETE = 0x2102
        const val GROUP_CANCEL = 0x2103
        const val GROUP_ADD_RIDERS = 0x2104
        const val DMC_MUTE = 0x2201
        const val DMC_IC_BRIDGE = 0x2202
        const val IC_CALL_A = 0x2250
        const val IC_END_A = 0x2253
        const val IC_END_ALL = 0x2256
        const val IC_PAIR_A = 0x2257
        const val IC_PASSIVE_PAIR = 0x225A
        const val IC_STOP_PAIR = 0x225B
        const val CONF_START = 0x225C
        const val CONF_STOP = 0x225D
        const val IC_RESET_RIDERS = 0x225E
        const val MS_START = 0x2300
        const val MS_STOP = 0x2301
        const val SPEED_DIAL_CALL = 0x4005
    }

    // ---------- Building ----------

    fun control(event: Int, extra: ByteArray = ByteArray(0)): ByteArray =
        byteArrayOf(OP_CONTROL.toByte(), (event shr 8).toByte(), event.toByte(), extra.size.toByte()) + extra

    fun getVersion() = byteArrayOf(OP_GET_VER.toByte(), 0, 0)
    fun getSerial() = byteArrayOf(OP_GET_SN.toByte())
    fun getName() = byteArrayOf(OP_GET_FRIENDLY_NAME.toByte())
    fun getSpeedDial() = byteArrayOf(OP_GET_SPEED_DIAL.toByte())

    /** Names longer than this are refused by TalkCtrl (the unit's own limit isn't known). */
    const val NAME_MAX_BYTES = 30

    fun setName(name: String): ByteArray {
        val b = name.trim().toByteArray(Charsets.UTF_8)
        require(b.isNotEmpty() && b.size <= NAME_MAX_BYTES) { "name length" }
        return byteArrayOf(OP_SET_FRIENDLY_NAME.toByte(), b.size.toByte()) + b
    }

    fun setSpeedDial(number: String): ByteArray {
        require(number.isNotEmpty() && number.length <= 30 && number.all { it.isDigit() || it == '+' || it == '*' || it == '#' }) { "number" }
        val b = number.toByteArray(Charsets.US_ASCII)
        return byteArrayOf(OP_SET_SPEED_DIAL.toByte(), b.size.toByte()) + b
    }

    fun getConfig(types: List<Int>): ByteArray =
        byteArrayOf(OP_GET_PARAM.toByte(), types.size.toByte()) + ByteArray(types.size) { types[it].toByte() }

    fun setConfig(vararg records: ByteArray): ByteArray {
        val body = records.fold(ByteArray(0)) { acc, r -> acc + r }
        return byteArrayOf(OP_SET_PARAM.toByte(), body.size.toByte()) + body
    }

    /** Subscribe to every service the Cardo app subscribes to. */
    fun subscribeAll(): ByteArray {
        val agnostic = listOf(15, 14, 13, 12, 11, 10, 7, 6, 5, 4, 0).fold(0) { a, b -> a or (1 shl b) }
        val specific = (7..15).fold(0) { a, b -> a or (1 shl b) }
        return byteArrayOf(OP_SERVICE_SET.toByte(), agnostic.toByte(), (specific shr 8).toByte(), specific.toByte())
    }

    fun factoryReset() = control(Ev.FACTORY_RESET)
    fun fmSetFrequency(mhz: Double) = control(Ev.FM_SET_FREQ, word(fmWord(mhz)))
    fun fmPlayPreset(n: Int): ByteArray {
        require(n in 1..6)
        return control(Ev.FM_PLAY_PRESET, byteArrayOf(n.toByte()))
    }
    fun dmcMute(mute: Boolean) = control(Ev.DMC_MUTE, byteArrayOf(if (mute) 1 else 0))
    fun dmcBridge(on: Boolean) = control(Ev.DMC_IC_BRIDGE, byteArrayOf(if (on) 1 else 0))
    /** channel 0 = A, 1 = B, 2 = C */
    fun icPair(channel: Int) = control(Ev.IC_PAIR_A + channel.coerceIn(0, 2))
    fun icCall(channel: Int) = control(Ev.IC_CALL_A + channel.coerceIn(0, 2))
    fun icEnd(channel: Int) = control(Ev.IC_END_A + channel.coerceIn(0, 2))
    fun musicSharing(start: Boolean) = control(if (start) Ev.MS_START else Ev.MS_STOP)

    /** FM frequency word: MHz × 100. */
    fun fmWord(mhz: Double): Int = (mhz * 100.0).roundToInt()
    fun fmMhz(word: Int): Double = word / 100.0
    private fun word(v: Int) = byteArrayOf((v shr 8).toByte(), v.toByte())

    // ---------- Parsing ----------

    sealed interface Message
    data class ConfigUpdate(val records: List<ConfigRecord>) : Message
    data class Battery(val percent: Int, val charging: Boolean) : Message
    data class State(val fmState: Int, val fmPreset: Int, val fmMhz: Double) : Message
    data class Serial(val serial: String) : Message
    data class Name(val name: String) : Message
    data class SpeedDial(val number: String) : Message
    data class MusicSharing(val state: Int) : Message
    data object OtaReady : Message
    data class Other(val opcode: Int) : Message

    fun parse(d: ByteArray): Message? {
        if (d.isEmpty()) return null
        val op = d.u(0)
        return when (op) {
            OP_UPDATE_PARAM -> ConfigUpdate(parseRecords(d, 2))
            OP_SRVC_BATTERY -> if (d.size >= 2) Battery(d.u(1) and 0x7F, d.u(1) and 0x80 != 0) else Other(op)
            OP_SRVC_STATE -> if (d.size > 6) State((d.u(3) and 0xF0) shr 4, d.u(3) and 0x0F, fmMhz((d.u(4) shl 8) or d.u(5))) else Other(op)
            OP_UPDATE_SERIAL -> lengthPrefixed(d)?.let { Serial(String(it, Charsets.UTF_8)) } ?: Other(op)
            OP_UPDATE_FRIENDLY_NAME -> if (d.size >= 2) Name(String(d.copyOfRange(2, d.size), Charsets.UTF_8).filter { !it.isISOControl() }) else Other(op)
            OP_UPDATE_SPEED_DIAL -> if (d.size >= 2) SpeedDial(String(d.copyOfRange(2, d.size), Charsets.UTF_8).filter { !it.isISOControl() }) else Other(op)
            OP_SRVC_MUSIC_SHARING -> if (d.size >= 2) MusicSharing(d.u(1) and 0x07) else Other(op)
            OP_OTA_READY -> OtaReady
            else -> Other(op)
        }
    }

    private fun lengthPrefixed(d: ByteArray): ByteArray? {
        if (d.size < 2) return null
        val n = d.u(1)
        if (2 + n > d.size) return null
        return d.copyOfRange(2, 2 + n)
    }

    /** Record sizes per type (type byte included). Variable-size types (custom keys) stop the parse. */
    val RECORD_SIZE = mapOf(
        0x00 to 4, 0x01 to 2, 0x02 to 7, 0x03 to 7, 0x04 to 9, 0x05 to 3, 0x06 to 4, 0x07 to 3,
        0x80 to 14, 0x81 to 5, 0x82 to 3, 0x83 to 2, 0x84 to 5, 0x85 to 2, 0x86 to 3, 0x87 to 4,
        0x8A to 2, 0x8C to 2, 0x8E to 2, 0x8F to 4,
    )

    /** The config types TalkCtrl asks for (the Cardo app's "all needed" list). */
    val REQUESTED_TYPES = listOf(0x00, 0x03, 0x01, 0x80, 0x02, 0x04, 0x05, 0x82, 0x81, 0x83, 0x84, 0x06, 0x07, 0x87, 0x86)

    fun parseRecords(d: ByteArray, from: Int): List<ConfigRecord> {
        val out = ArrayList<ConfigRecord>()
        var i = from
        while (i < d.size) {
            val type = d.u(i)
            val size = RECORD_SIZE[type] ?: break
            if (i + size > d.size) break
            out += ConfigRecord(type, d.copyOfRange(i, i + size))
            i += size
        }
        return out
    }
}

/** One config record as the unit sent it (type byte first). */
data class ConfigRecord(val type: Int, val raw: ByteArray) {
    override fun equals(other: Any?) = other is ConfigRecord && other.type == type && other.raw.contentEquals(raw)
    override fun hashCode() = type * 31 + raw.contentHashCode()
}

internal fun ByteArray.u(i: Int) = this[i].toInt() and 0xFF

/** USER_CONFIG (type 0x00), kept as raw bytes; setters flip only their own bits. */
data class UserConfig(val b1: Int, val b2: Int, val b3: Int) {
    val language get() = (b1 shr 4) and 0x0F
    val voiceCommands get() = b1 and 0x08 != 0
    val voicePrompts get() = b1 and 0x04 != 0
    /** 0 worldwide, 1 Japan */
    val fmRegion get() = (b1 shr 1) and 1
    val rds get() = b1 and 1 != 0
    val phoneMixing get() = b2 and 0x80 != 0
    /** 0 medium, 1 low, 2 high */
    val micSensitivity get() = (b2 shr 5) and 3
    /** 0 off, 1 low, 2 medium, 3 high */
    val autoVolume get() = (b2 shr 1) and 0x0F
    val noiseGate get() = b2 and 1 != 0
    val ecoMode get() = b3 and 0x80 != 0
    val eqProfile get() = b3 and 0x3F

    fun withLanguage(v: Int) = copy(b1 = (b1 and 0x0F) or ((v and 0x0F) shl 4))
    fun withVoiceCommands(on: Boolean) = copy(b1 = bit(b1, 3, on))
    fun withVoicePrompts(on: Boolean) = copy(b1 = bit(b1, 2, on))
    fun withFmRegion(v: Int) = copy(b1 = bit(b1, 1, v == 1))
    fun withRds(on: Boolean) = copy(b1 = bit(b1, 0, on))
    fun withPhoneMixing(on: Boolean) = copy(b2 = bit(b2, 7, on))
    fun withMicSensitivity(v: Int) = copy(b2 = (b2 and 0x9F) or ((v and 3) shl 5))
    fun withAutoVolume(v: Int) = copy(b2 = (b2 and 0xE1) or ((v and 0x0F) shl 1))
    fun withNoiseGate(on: Boolean) = copy(b2 = bit(b2, 0, on))
    fun withEq(v: Int) = copy(b3 = (b3 and 0xC0) or (v and 0x3F))

    fun record() = byteArrayOf(0x00, b1.toByte(), b2.toByte(), b3.toByte())

    companion object {
        fun from(r: ConfigRecord) = UserConfig(r.raw.u(1), r.raw.u(2), r.raw.u(3))
    }
}

/** VOLUMES (type 0x02): 4-bit levels. */
data class Volumes(
    val announcements: Int,
    val intercom: Int,
    val phone: Int,
    val phone2: Int,
    val fm: Int,
    val music: Int,
    val music2: Int,
    /** Music level while on intercom, raw code (9 = 10 %, 1..8 = 20..90 %, 0 = 100 %). */
    val musicUnderIntercom: Int,
    /** Intercom mix code (0 none, 1 full, 2 75 %, 3 50 %, 4 25 %). */
    val mix: Int,
) {
    /** Writes the whole record (volume id 0), as the Cardo app does. */
    fun record() = byteArrayOf(
        0x02, 0x00,
        ((announcements shl 4) or (intercom and 0xF)).toByte(),
        ((phone shl 4) or (phone2 and 0xF)).toByte(),
        ((fm shl 4) or (music and 0xF)).toByte(),
        ((music2 shl 4) or (musicUnderIntercom and 0xF)).toByte(),
        mix.toByte(),
    )

    companion object {
        const val MAX = 15
        fun from(r: ConfigRecord): Volumes {
            val d = r.raw
            return Volumes(
                announcements = d.u(2) shr 4, intercom = d.u(2) and 0xF,
                phone = d.u(3) shr 4, phone2 = d.u(3) and 0xF,
                fm = d.u(4) shr 4, music = d.u(4) and 0xF,
                music2 = d.u(5) shr 4, musicUnderIntercom = d.u(5) and 0xF,
                mix = d.u(6),
            )
        }

        /** Music-under-intercom percentage for a raw code. */
        fun reductionPercent(code: Int) = when (code) { 0 -> 100; 9 -> 10; in 1..8 -> (code + 1) * 10; else -> 10 }
        fun reductionCode(percent: Int) = when (percent) { 100 -> 0; 10 -> 9; else -> (percent / 10 - 1).coerceIn(1, 8) }
    }
}

/** Unit capabilities (type 0x82). */
data class Capabilities(val b1: Int, val b2: Int) {
    val autoVolume get() = b1 and 0x80 != 0
    val musicSharing get() = b1 and 0x40 != 0
    val bluetoothIntercom get() = b1 and 0x20 != 0
    val phoneMixing get() = b1 and 0x10 != 0
    val voiceCommands get() = b1 and 0x08 != 0
    val dmc get() = b1 and 0x04 != 0
    val icChannels get() = b1 and 0x03
    val fm get() = b2 and 0x40 != 0
    val ota get() = b2 and 0x20 != 0
    val privateChat get() = b2 and 0x10 != 0
    val dmcIcBridge get() = b2 and 0x01 != 0

    companion object {
        fun from(r: ConfigRecord) = Capabilities(r.raw.u(1), r.raw.u(2))
    }
}

data class FirmwareVersion(val major: Int, val minor: Int) : Comparable<FirmwareVersion> {
    override fun compareTo(other: FirmwareVersion) = compareValuesBy(this, other, { it.major }, { it.minor })
    override fun toString() = "$major.$minor"

    companion object {
        /** From HS_TYPE (type 0x03). */
        fun from(r: ConfigRecord) = FirmwareVersion((r.raw.u(3) shl 8) or r.raw.u(4), (r.raw.u(5) shl 8) or r.raw.u(6))

        /** "2.5", "2.5.1" (extra parts ignored) or null. */
        fun parse(s: String?): FirmwareVersion? {
            val m = Regex("""(\d{1,3})[._](\d{1,3})""").find(s ?: return null) ?: return null
            return FirmwareVersion(m.groupValues[1].toInt(), m.groupValues[2].toInt())
        }
    }
}

object Bits {
    fun mask16(r: ConfigRecord, at: Int) = (r.raw.u(at) shl 8) or r.raw.u(at + 1)
    fun setBits(mask: Int, width: Int = 16) = (0 until width).filter { mask and (1 shl it) != 0 }
}

/** Supported languages (type 0x05): language codes. */
fun supportedLanguages(r: ConfigRecord): List<Int> = Bits.setBits(Bits.mask16(r, 1)).filter { it <= 12 }

/** Available EQ profiles (type 0x84): profile codes. */
fun eqProfiles(r: ConfigRecord): List<Int> {
    val a = Bits.mask16(r, 3) // first 16 profiles (the Cardo app reads bytes 3-4 first)
    val b = Bits.mask16(r, 1)
    return Bits.setBits(a) + Bits.setBits(b).map { it + 16 }
}

/** FM presets (type 0x80): 6 frequencies in MHz (0 = empty). */
fun fmPresets(r: ConfigRecord): List<Double> = (0 until 6).map { i -> Caip.fmMhz((r.raw.u(2 + i * 2) shl 8) or r.raw.u(3 + i * 2)) }

fun fmPresetsRecord(presets: List<Double>): ByteArray {
    require(presets.size == 6)
    val out = ByteArray(14)
    out[0] = 0x80.toByte()
    out[1] = 0
    presets.forEachIndexed { i, mhz ->
        val w = if (mhz <= 0.0) 0 else Caip.fmWord(mhz)
        out[2 + i * 2] = (w shr 8).toByte()
        out[3 + i * 2] = w.toByte()
    }
    return out
}

private fun bit(v: Int, n: Int, on: Boolean) = if (on) v or (1 shl n) else v and (1 shl n).inv() and 0xFF

/** RFCOMM: Cardo's splitting rule (each message is data[i+1] + 3 bytes). Inferred meaning; see PROTOCOL.md. */
fun splitRfcomm(data: ByteArray): List<ByteArray> {
    val out = ArrayList<ByteArray>()
    var i = 0
    var len = -1
    while (i < data.size - 1 && len != 0) {
        len = data.u(i + 1)
        val end = minOf(data.size, i + len + 3)
        out += data.copyOfRange(i, end)
        i = end
    }
    if (out.isEmpty() && data.isNotEmpty()) out += data
    return out
}
