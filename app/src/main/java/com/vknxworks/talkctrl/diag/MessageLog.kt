package com.vknxworks.talkctrl.diag

/**
 * The last messages between the phone and the unit, kept in memory only (never on disk) for the diagnostics
 * export. Messages that carry personal details are reduced to their opcode and length before they are even
 * stored: the unit's name (0x12 set, 0x42 update), the speed-dial number (0x11 set, 0x41 update) and the serial
 * number (0x43 update). Everything else is kept as hex, which is what's needed to check the protocol.
 * Plain Kotlin, unit tested.
 */
class MessageLog(private val capacity: Int = 200) {
    private val lines = ArrayDeque<String>()
    private var start = 0L

    @Synchronized
    fun reset(nowMs: Long) {
        lines.clear()
        start = nowMs
    }

    @Synchronized
    fun add(nowMs: Long, outgoing: Boolean, msg: ByteArray) {
        val t = (nowMs - start).coerceAtLeast(0)
        lines.addLast("+%d.%03d s %s %s".format(t / 1000, t % 1000, if (outgoing) ">" else "<", describe(msg, outgoing)))
        while (lines.size > capacity) lines.removeFirst()
    }

    @Synchronized
    fun snapshot(): List<String> = lines.toList()

    companion object {
        /** Opcodes whose payload is personal: (outgoing) set speed dial, set name; (incoming) speed dial, name, serial. */
        private val PERSONAL_OUT = setOf(0x11, 0x12)
        private val PERSONAL_IN = setOf(0x41, 0x42, 0x43)

        fun describe(msg: ByteArray, outgoing: Boolean): String {
            if (msg.isEmpty()) return "(empty)"
            val op = msg[0].toInt() and 0xFF
            val personal = if (outgoing) op in PERSONAL_OUT else op in PERSONAL_IN
            return if (personal) "%02x [%d bytes, removed: personal]".format(op, msg.size - 1)
            else msg.joinToString(" ") { "%02x".format(it.toInt() and 0xFF) }
        }
    }
}
