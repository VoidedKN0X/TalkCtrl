package com.vknxworks.talkctrl.protocol

import java.security.MessageDigest
import java.util.UUID

/**
 * Qualcomm GAIA v3 packets and the upgrade ("VM upgrade") messages, as used by the Cardo app (PROTOCOL.md section 9).
 * Pure Kotlin, unit tested.
 */
object Gaia {
    val SERVICE: UUID = UUID.fromString("00001100-d102-11e1-9b23-00025b00a5a5")
    val COMMAND: UUID = UUID.fromString("00001101-d102-11e1-9b23-00025b00a5a5")
    val RESPONSE: UUID = UUID.fromString("00001102-d102-11e1-9b23-00025b00a5a5")
    val DATA: UUID = UUID.fromString("00001103-d102-11e1-9b23-00025b00a5a5")

    const val VENDOR_V3 = 0x001D
    const val VENDOR_V1V2 = 0x000A

    const val FEATURE_BASIC = 0
    const val FEATURE_UPGRADE = 6

    const val TYPE_COMMAND = 0
    const val TYPE_NOTIFICATION = 1
    const val TYPE_RESPONSE = 2
    const val TYPE_ERROR = 3

    const val BASIC_GET_GAIA_VERSION = 0
    const val BASIC_REGISTER_NOTIFICATION = 7

    const val UPGRADE_CONNECT = 0
    const val UPGRADE_DISCONNECT = 1
    const val UPGRADE_CONTROL = 2

    const val NOTIFY_UPGRADE_DATA = 0
    const val NOTIFY_UPGRADE_STOP = 1
    const val NOTIFY_UPGRADE_START = 2

    fun command(feature: Int, type: Int, id: Int) = ((feature and 0x7F) shl 9) or ((type and 3) shl 7) or (id and 0x7F)

    data class Packet(val vendor: Int, val feature: Int, val type: Int, val id: Int, val payload: ByteArray) {
        fun bytes(): ByteArray {
            val c = command(feature, type, id)
            return byteArrayOf((vendor shr 8).toByte(), vendor.toByte(), (c shr 8).toByte(), c.toByte()) + payload
        }

        override fun equals(other: Any?) = other is Packet && other.vendor == vendor && other.feature == feature &&
            other.type == type && other.id == id && other.payload.contentEquals(payload)

        override fun hashCode() = ((vendor * 31 + feature) * 31 + type) * 31 + id
    }

    fun commandPacket(feature: Int, id: Int, payload: ByteArray = ByteArray(0)) =
        Packet(VENDOR_V3, feature, TYPE_COMMAND, id, payload).bytes()

    fun parse(b: ByteArray): Packet? {
        if (b.size < 4) return null
        val vendor = (b.u(0) shl 8) or b.u(1)
        val c = (b.u(2) shl 8) or b.u(3)
        return Packet(vendor, (c shr 9) and 0x7F, (c shr 7) and 3, c and 0x7F, b.copyOfRange(4, b.size))
    }

    /** RFCOMM frame: SOF 0xFF, version, flags, length (payload only), PDU, optional XOR checksum. */
    fun rfcommFrame(pdu: ByteArray, version: Int = 3, checksum: Boolean = false): ByteArray {
        val payloadLen = pdu.size - 4
        require(payloadLen >= 0)
        val ext = payloadLen > 255
        require(!ext || version >= 4) { "payload too long for this GAIA version" }
        val flags = (if (checksum) 1 else 0) or (if (ext) 2 else 0)
        val header = if (ext) byteArrayOf(0xFF.toByte(), version.toByte(), flags.toByte(), (payloadLen shr 8).toByte(), payloadLen.toByte())
        else byteArrayOf(0xFF.toByte(), version.toByte(), flags.toByte(), payloadLen.toByte())
        val frame = header + pdu
        return if (checksum) frame + byteArrayOf(frame.fold(0) { a, x -> a xor x.toInt() }.toByte()) else frame
    }

    /** Payload room for a BLE ATT MTU (the Cardo library uses MTU - 7 for the GAIA payload). */
    fun blePayloadSize(mtu: Int) = mtu - 7

    /** Upgrade data per DATA message: GAIA payload - 3 (upgrade header) - 1 (last flag). */
    fun chunkSize(gaiaPayload: Int) = gaiaPayload - 4
}

/**
 * How upgrade messages are wrapped in GAIA. The Cardo app uses GAIA v3 (vendor 0x001D) only for Flow Lite
 * (`DeviceTypeFamilyCardo.isSupportOTAFrameworkV3`) and the older GAIA v1/v2 framing (vendor 0x000A,
 * commands 0x0640-0x0642, upgrade data in event 0x12 notifications) for every other unit.
 */
sealed interface GaiaDialect {
    fun registerNotification(): ByteArray
    fun connect(): ByteArray
    fun disconnect(): ByteArray
    fun control(message: ByteArray): ByteArray
    fun decode(bytes: ByteArray): Incoming?

    enum class Cmd { REGISTER, CONNECT, DISCONNECT, CONTROL, OTHER }

    sealed interface Incoming {
        /** The unit's answer to one of our commands. */
        data class Ack(val cmd: Cmd, val ok: Boolean, val status: Int) : Incoming
        /** An upgrade message from the unit; [reply] (if any) must be sent back first. */
        class Data(val message: ByteArray, val reply: ByteArray?) : Incoming
        data object Start : Incoming
        data object Stop : Incoming
    }

    data object V3 : GaiaDialect {
        override fun registerNotification() = Gaia.commandPacket(Gaia.FEATURE_BASIC, Gaia.BASIC_REGISTER_NOTIFICATION, byteArrayOf(Gaia.FEATURE_UPGRADE.toByte()))
        override fun connect() = Gaia.commandPacket(Gaia.FEATURE_UPGRADE, Gaia.UPGRADE_CONNECT)
        override fun disconnect() = Gaia.commandPacket(Gaia.FEATURE_UPGRADE, Gaia.UPGRADE_DISCONNECT)
        override fun control(message: ByteArray) = Gaia.commandPacket(Gaia.FEATURE_UPGRADE, Gaia.UPGRADE_CONTROL, message)

        override fun decode(bytes: ByteArray): Incoming? {
            val p = Gaia.parse(bytes) ?: return null
            if (p.vendor != Gaia.VENDOR_V3) return null
            val cmd = when {
                p.feature == Gaia.FEATURE_BASIC && p.id == Gaia.BASIC_REGISTER_NOTIFICATION -> Cmd.REGISTER
                p.feature != Gaia.FEATURE_UPGRADE -> Cmd.OTHER
                p.id == Gaia.UPGRADE_CONNECT -> Cmd.CONNECT
                p.id == Gaia.UPGRADE_DISCONNECT -> Cmd.DISCONNECT
                p.id == Gaia.UPGRADE_CONTROL -> Cmd.CONTROL
                else -> Cmd.OTHER
            }
            return when (p.type) {
                Gaia.TYPE_RESPONSE -> Incoming.Ack(cmd, true, 0)
                Gaia.TYPE_ERROR -> Incoming.Ack(cmd, false, p.payload.firstOrNull()?.toInt()?.and(0xFF) ?: 0)
                Gaia.TYPE_NOTIFICATION -> if (p.feature != Gaia.FEATURE_UPGRADE) null else when (p.id) {
                    Gaia.NOTIFY_UPGRADE_DATA -> Incoming.Data(p.payload, null)
                    Gaia.NOTIFY_UPGRADE_START -> Incoming.Start
                    Gaia.NOTIFY_UPGRADE_STOP -> Incoming.Stop
                    else -> null
                }
                else -> null
            }
        }
    }

    /** GAIA v1/v2 over BLE: [vendor 0x000A][command][payload]; acknowledgements set bit 15 and start with a status byte. */
    data object V2 : GaiaDialect {
        const val CMD_REGISTER_NOTIFICATION = 0x4001
        const val CMD_EVENT_NOTIFICATION = 0x4003
        const val CMD_UPGRADE_CONNECT = 0x0640
        const val CMD_UPGRADE_DISCONNECT = 0x0641
        const val CMD_UPGRADE_CONTROL = 0x0642
        const val EVENT_VMU_PACKET = 0x12
        const val ACK = 0x8000

        private fun packet(cmd: Int, payload: ByteArray = ByteArray(0)) =
            byteArrayOf(0x00, Gaia.VENDOR_V1V2.toByte(), (cmd shr 8).toByte(), cmd.toByte()) + payload

        override fun registerNotification() = packet(CMD_REGISTER_NOTIFICATION, byteArrayOf(EVENT_VMU_PACKET.toByte()))
        override fun connect() = packet(CMD_UPGRADE_CONNECT)
        override fun disconnect() = packet(CMD_UPGRADE_DISCONNECT)
        override fun control(message: ByteArray) = packet(CMD_UPGRADE_CONTROL, message)

        override fun decode(bytes: ByteArray): Incoming? {
            if (bytes.size < 4) return null
            val vendor = (bytes.u(0) shl 8) or bytes.u(1)
            if (vendor != Gaia.VENDOR_V1V2) return null
            val word = (bytes.u(2) shl 8) or bytes.u(3)
            val cmd = word and 0x7FFF
            val payload = bytes.copyOfRange(4, bytes.size)
            if (word and ACK != 0) {
                val status = payload.firstOrNull()?.toInt()?.and(0xFF) ?: 0
                val which = when (cmd) {
                    CMD_REGISTER_NOTIFICATION -> Cmd.REGISTER
                    CMD_UPGRADE_CONNECT -> Cmd.CONNECT
                    CMD_UPGRADE_DISCONNECT -> Cmd.DISCONNECT
                    CMD_UPGRADE_CONTROL -> Cmd.CONTROL
                    else -> Cmd.OTHER
                }
                return Incoming.Ack(which, status == 0, status)
            }
            if (cmd == CMD_EVENT_NOTIFICATION && payload.isNotEmpty() && payload.u(0) == EVENT_VMU_PACKET) {
                // Acknowledge the notification (status 0), as the Cardo app's UpgradeGaiaManager does.
                return Incoming.Data(payload.copyOfRange(1, payload.size), packet(CMD_EVENT_NOTIFICATION or ACK, byteArrayOf(0)))
            }
            return null
        }
    }

    companion object {
        /** The Cardo app picks GAIA v3 only for Flow Lite. */
        fun forModel(folder: String?): GaiaDialect = if (folder == "Flow_Lite") V3 else V2
    }
}

/** An upgrade-library message: opcode, 2-byte length, content. */
data class UpgradeMessage(val opcode: Int, val content: ByteArray = ByteArray(0)) {
    fun bytes() = byteArrayOf(opcode.toByte(), (content.size shr 8).toByte(), content.size.toByte()) + content

    override fun equals(other: Any?) = other is UpgradeMessage && other.opcode == opcode && other.content.contentEquals(content)
    override fun hashCode() = opcode * 31 + content.contentHashCode()

    companion object {
        const val START_REQ = 0x01
        const val START_CFM = 0x02
        const val DATA_BYTES_REQ = 0x03
        const val DATA = 0x04
        const val ABORT_REQ = 0x07
        const val ABORT_CFM = 0x08
        const val TRANSFER_COMPLETE_IND = 0x0B
        const val TRANSFER_COMPLETE_RES = 0x0C
        const val PROCEED_TO_COMMIT = 0x0E
        const val COMMIT_REQ = 0x0F
        const val COMMIT_CFM = 0x10
        const val ERROR_IND = 0x11
        const val COMPLETE_IND = 0x12
        const val SYNC_REQ = 0x13
        const val SYNC_CFM = 0x14
        const val START_DATA_REQ = 0x15
        const val IS_VALIDATION_DONE_REQ = 0x16
        const val IS_VALIDATION_DONE_CFM = 0x17
        const val ERROR_RES = 0x1F
        const val SILENT_COMMIT_SUPPORTED_REQ = 0x20
        const val SILENT_COMMIT_SUPPORTED_CFM = 0x21
        const val SILENT_COMMIT_CFM = 0x22
        const val COMPLETE_IND_WITH_STATUS = 0x25

        fun parse(b: ByteArray): UpgradeMessage? {
            if (b.size < 3) return null
            return UpgradeMessage(b.u(0), b.copyOfRange(3, b.size))
        }

        /** SYNC_REQ id: the last 4 bytes of the file's MD5. */
        fun syncId(file: ByteArray): ByteArray = MessageDigest.getInstance("MD5").digest(file).copyOfRange(12, 16)
    }
}

/**
 * The standard Qualcomm upgrade state machine over GAIA v3, in acknowledged mode (each UPGRADE_CONTROL
 * waits for its GAIA response; no RWCP). Feed it every GAIA packet from the unit; it calls [Io.send] with the
 * GAIA packets to write. Not thread safe: call it from one thread.
 */
class UpgradeEngine(private val file: ByteArray, private val io: Io, chunk: Int, private val dialect: GaiaDialect = GaiaDialect.V3) {

    interface Io {
        fun send(gaiaPacket: ByteArray)
        fun schedule(delayMs: Long, block: () -> Unit)
        fun onPhase(phase: Phase)
        fun onProgress(percent: Double)
        fun onEnd(end: End)
    }

    enum class Phase { CONNECTING, SYNCING, TRANSFERRING, VALIDATING, REBOOTING, COMMITTING, DONE }

    sealed interface End {
        data object Complete : End
        data object Aborted : End
        data class Failed(val reason: Reason, val code: Int = 0) : End
    }

    enum class Reason { BATTERY_LOW, UNIT_ERROR, PROTOCOL, NOT_READY, REFUSED }

    /** Resume points (SYNC_CFM). */
    object Resume { const val START = 0; const val PRE_VALIDATE = 1; const val PRE_REBOOT = 2; const val POST_REBOOT = 3; const val COMMIT = 4; const val POST_COMMIT = 5 }

    private val syncId = UpgradeMessage.syncId(file)
    private var chunkSize = chunk.coerceAtLeast(1)
    private val queue = ArrayDeque<ByteArray>()
    private var waiting = false
    private var offset = 0
    private var remaining = 0
    private var resume = Resume.START
    private var protocol = 0
    private var startAttempts = 0
    private var aborting = false
    private var restartAfterAbort = false
    private var ended = false
    var phase = Phase.CONNECTING
        private set

    fun setChunkSize(n: Int) {
        chunkSize = n.coerceAtLeast(1)
    }

    /** Call when the GAIA link is up (first start, and again after the unit rebooted). */
    fun start() {
        ended = false
        queue.clear()
        waiting = false
        setPhase(if (resume >= Resume.PRE_REBOOT) phase else Phase.CONNECTING)
        enqueueRaw(dialect.registerNotification())
        enqueueRaw(dialect.connect())
    }

    /** The link dropped. True when that's expected (the unit reboots to install the update). */
    fun onDisconnected(): Boolean {
        queue.clear()
        waiting = false
        offset = 0
        remaining = 0
        return phase == Phase.REBOOTING
    }

    fun abort() {
        if (ended) return
        aborting = true
        restartAfterAbort = false
        queue.clear()
        remaining = 0
        sendUpgrade(UpgradeMessage(UpgradeMessage.ABORT_REQ))
    }

    fun onPacket(bytes: ByteArray) {
        when (val p = dialect.decode(bytes) ?: return) {
            is GaiaDialect.Incoming.Ack -> {
                waiting = false
                when {
                    p.ok && p.cmd == GaiaDialect.Cmd.CONNECT -> {
                        setPhase(if (resume >= Resume.PRE_REBOOT) Phase.COMMITTING else Phase.SYNCING)
                        sendSync()
                    }
                    p.ok || p.cmd == GaiaDialect.Cmd.REGISTER || p.cmd == GaiaDialect.Cmd.OTHER || p.cmd == GaiaDialect.Cmd.DISCONNECT ->
                        // A refused notification registration means it's already registered on some firmware: carry on.
                        pump()
                    else -> fail(Reason.REFUSED, p.status)
                }
            }
            is GaiaDialect.Incoming.Data -> {
                p.reply?.let { io.send(it) }
                UpgradeMessage.parse(p.message)?.let { handle(it) }
            }
            GaiaDialect.Incoming.Start -> sendSync()
            GaiaDialect.Incoming.Stop -> Unit // the unit pauses; it sends Start again to continue
        }
    }

    // ---------- Upgrade messages ----------

    private fun handle(m: UpgradeMessage) {
        if (ended && m.opcode != UpgradeMessage.ABORT_CFM) return
        val c = m.content
        when (m.opcode) {
            UpgradeMessage.SYNC_CFM -> {
                if (c.size >= 6) {
                    resume = c.u(0)
                    protocol = c.u(5)
                    if (protocol > 6) return fail(Reason.PROTOCOL)
                } else resume = Resume.START
                sendUpgrade(UpgradeMessage(UpgradeMessage.START_REQ))
            }
            UpgradeMessage.START_CFM -> {
                if (c.size < 3) return fail(Reason.PROTOCOL)
                val status = c.u(0)
                if (status != 0) {
                    if (status == 9 && startAttempts < 5) {
                        startAttempts++
                        io.schedule(2000) { if (!ended) sendUpgrade(UpgradeMessage(UpgradeMessage.START_REQ)) }
                        return
                    }
                    return fail(if (status == 9) Reason.NOT_READY else Reason.UNIT_ERROR, status)
                }
                startAttempts = 0
                when (resume) {
                    Resume.COMMIT -> commit()
                    Resume.PRE_REBOOT -> transferComplete()
                    Resume.POST_REBOOT -> {
                        setPhase(Phase.COMMITTING)
                        sendUpgrade(UpgradeMessage(UpgradeMessage.PROCEED_TO_COMMIT, byteArrayOf(0)))
                    }
                    Resume.PRE_VALIDATE -> validate()
                    Resume.POST_COMMIT -> setPhase(Phase.COMMITTING)
                    else -> {
                        setPhase(Phase.TRANSFERRING)
                        offset = 0
                        remaining = 0
                        sendUpgrade(UpgradeMessage(UpgradeMessage.START_DATA_REQ))
                    }
                }
            }
            UpgradeMessage.DATA_BYTES_REQ -> {
                if (c.size != 8) return fail(Reason.PROTOCOL)
                val wanted = c.int32(0)
                val skip = c.int32(4)
                if (skip > 0 && offset + skip < file.size) offset += skip
                val left = file.size - offset
                remaining = if (wanted in 1 until left) wanted else left
                setPhase(Phase.TRANSFERRING)
                pump()
            }
            UpgradeMessage.IS_VALIDATION_DONE_CFM -> {
                val wait = if (c.size >= 2) ((c.u(0) shl 8) or c.u(1)).toLong() else 0L
                io.schedule(wait) { if (!ended) sendUpgrade(UpgradeMessage(UpgradeMessage.IS_VALIDATION_DONE_REQ)) }
            }
            UpgradeMessage.TRANSFER_COMPLETE_IND -> {
                resume = Resume.PRE_REBOOT
                transferComplete()
            }
            UpgradeMessage.SILENT_COMMIT_SUPPORTED_CFM -> confirmTransferComplete()
            UpgradeMessage.COMMIT_REQ -> {
                resume = Resume.COMMIT
                commit()
            }
            UpgradeMessage.COMPLETE_IND, UpgradeMessage.COMPLETE_IND_WITH_STATUS, UpgradeMessage.SILENT_COMMIT_CFM -> {
                setPhase(Phase.DONE)
                end(End.Complete)
                enqueueRaw(dialect.disconnect())
            }
            UpgradeMessage.ERROR_IND -> {
                sendUpgrade(UpgradeMessage(UpgradeMessage.ERROR_RES, c.copyOf(2.coerceAtMost(c.size))))
                val code = if (c.size >= 2) (c.u(0) shl 8) or c.u(1) else -1
                when (code) {
                    0x21 -> fail(Reason.BATTERY_LOW, code)
                    0x81 -> {
                        // The unit holds part of a different file: abort it and start this one from the beginning.
                        aborting = true
                        restartAfterAbort = true
                        sendUpgrade(UpgradeMessage(UpgradeMessage.ABORT_REQ))
                    }
                    else -> fail(Reason.UNIT_ERROR, code)
                }
            }
            UpgradeMessage.ABORT_CFM -> {
                aborting = false
                if (restartAfterAbort) {
                    restartAfterAbort = false
                    resume = Resume.START
                    offset = 0
                    remaining = 0
                    sendSync()
                } else {
                    end(End.Aborted)
                    enqueueRaw(dialect.disconnect())
                }
            }
        }
    }

    private fun sendSync() = sendUpgrade(UpgradeMessage(UpgradeMessage.SYNC_REQ, syncId))

    private fun transferComplete() {
        if (protocol < 4) confirmTransferComplete() else sendUpgrade(UpgradeMessage(UpgradeMessage.SILENT_COMMIT_SUPPORTED_REQ))
    }

    /** Continue with an interactive commit: the unit reboots into the new image and asks to commit. */
    private fun confirmTransferComplete() {
        setPhase(Phase.REBOOTING)
        sendUpgrade(UpgradeMessage(UpgradeMessage.TRANSFER_COMPLETE_RES, byteArrayOf(0)))
    }

    private fun commit() {
        setPhase(Phase.COMMITTING)
        sendUpgrade(UpgradeMessage(UpgradeMessage.COMMIT_CFM, byteArrayOf(0)))
        resume = Resume.POST_COMMIT
    }

    private fun validate() {
        resume = Resume.PRE_VALIDATE
        setPhase(Phase.VALIDATING)
        sendUpgrade(UpgradeMessage(UpgradeMessage.IS_VALIDATION_DONE_REQ))
    }

    // ---------- Sending (acknowledged) ----------

    private fun sendUpgrade(m: UpgradeMessage) = enqueueRaw(dialect.control(m.bytes()))

    private fun enqueueRaw(p: ByteArray) {
        queue.addLast(p)
        pump()
    }

    private fun pump() {
        if (waiting) return
        val next = queue.removeFirstOrNull()
        if (next != null) {
            waiting = true
            io.send(next)
            return
        }
        if (remaining > 0 && !aborting && !ended) {
            val n = minOf(chunkSize, remaining)
            val data = file.copyOfRange(offset, offset + n)
            offset += n
            remaining -= n
            val last = offset >= file.size
            waiting = true
            io.send(dialect.control(UpgradeMessage(UpgradeMessage.DATA, byteArrayOf(if (last) 1 else 0) + data).bytes()))
            io.onProgress(offset * 100.0 / file.size)
            if (last) {
                remaining = 0
                validate()
            }
        }
    }

    private fun setPhase(p: Phase) {
        if (phase != p) {
            phase = p
            io.onPhase(p)
        }
    }

    private fun fail(r: Reason, code: Int = 0) = end(End.Failed(r, code))

    private fun end(e: End) {
        if (ended) return
        ended = true
        remaining = 0
        io.onEnd(e)
    }
}

private fun ByteArray.int32(at: Int) = (u(at) shl 24) or (u(at + 1) shl 16) or (u(at + 2) shl 8) or u(at + 3)
