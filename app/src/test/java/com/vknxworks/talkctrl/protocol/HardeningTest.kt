package com.vknxworks.talkctrl.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What a unit sends is untrusted: odd values must not crash the transfer or send the wrong bytes. */
class HardeningTest {
    private class Recorder : UpgradeEngine.Io {
        val sent = ArrayList<ByteArray>()
        var end: UpgradeEngine.End? = null
        override fun send(gaiaPacket: ByteArray) { sent += gaiaPacket }
        override fun schedule(delayMs: Long, block: () -> Unit) {}
        override fun onPhase(phase: UpgradeEngine.Phase) {}
        override fun onProgress(percent: Double) {}
        override fun onEnd(end: UpgradeEngine.End) { this.end = end }
    }

    private fun notify(m: UpgradeMessage) = Gaia.Packet(Gaia.VENDOR_V3, Gaia.FEATURE_UPGRADE, Gaia.TYPE_NOTIFICATION, Gaia.NOTIFY_UPGRADE_DATA, m.bytes()).bytes()
    private val ack = Gaia.Packet(Gaia.VENDOR_V3, Gaia.FEATURE_UPGRADE, Gaia.TYPE_RESPONSE, Gaia.UPGRADE_CONTROL, ByteArray(0)).bytes()
    private fun int(v: Int) = byteArrayOf((v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte())
    private fun bytesReq(wanted: Int, skip: Int) = notify(UpgradeMessage(UpgradeMessage.DATA_BYTES_REQ, int(wanted) + int(skip)))

    /** The data bytes of the last DATA message sent. */
    private fun lastData(io: Recorder): ByteArray {
        val p = Gaia.parse(io.sent.last())!!
        val m = UpgradeMessage.parse(p.payload)!!
        assertEquals(UpgradeMessage.DATA, m.opcode)
        return m.content.copyOfRange(1, m.content.size)
    }

    @Test fun hugeSkipIsIgnoredInsteadOfWrappingTheOffset() {
        val file = ByteArray(100) { it.toByte() }
        val io = Recorder()
        val e = UpgradeEngine(file, io, 10)
        e.onPacket(bytesReq(10, 0))
        assertArrayEquals(file.copyOfRange(0, 10), lastData(io))
        e.onPacket(bytesReq(10, Int.MAX_VALUE - 5))
        e.onPacket(ack)
        assertArrayEquals(file.copyOfRange(10, 20), lastData(io))
        assertNull(io.end)
    }

    @Test fun negativeOrOversizedRequestsStayInsideTheFile() {
        val file = ByteArray(30) { it.toByte() }
        val io = Recorder()
        val e = UpgradeEngine(file, io, 50)
        e.onPacket(bytesReq(-1, -7))
        assertArrayEquals(file, lastData(io))
        val io2 = Recorder()
        val e2 = UpgradeEngine(file, io2, 50)
        e2.onPacket(notify(UpgradeMessage(UpgradeMessage.DATA_BYTES_REQ, ByteArray(3))))
        assertTrue(io2.end is UpgradeEngine.End.Failed)
    }

    @Test fun tarSizesPastTheEndAreRefused() {
        val h = ByteArray(512)
        "fw.bin".toByteArray().copyInto(h)
        "77777777777".toByteArray().copyInto(h, 124)
        h[156] = '0'.code.toByte()
        assertNull(FirmwareFile.firstEntry(h + ByteArray(512)))
        assertNull(FirmwareFile.firstTarEntry(byteArrayOf(1, 2, 3)))
    }
}
