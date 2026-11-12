package com.vknxworks.talkctrl.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

private fun b(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

class GaiaTest {

    @Test fun commandValues() {
        assertEquals(0x0C00, Gaia.command(6, Gaia.TYPE_COMMAND, 0))
        assertEquals(0x0C02, Gaia.command(6, Gaia.TYPE_COMMAND, 2))
        assertEquals(0x0C80, Gaia.command(6, Gaia.TYPE_NOTIFICATION, 0))
        assertEquals(0x0D02, Gaia.command(6, Gaia.TYPE_RESPONSE, 2))
        assertEquals(0x0D80, Gaia.command(6, Gaia.TYPE_ERROR, 0))
        assertEquals(0x0007, Gaia.command(0, Gaia.TYPE_COMMAND, 7))
    }

    @Test fun packetBuildAndParse() {
        assertArrayEquals(b(0x00, 0x1D, 0x00, 0x07, 0x06), Gaia.commandPacket(Gaia.FEATURE_BASIC, Gaia.BASIC_REGISTER_NOTIFICATION, b(6)))
        assertArrayEquals(b(0x00, 0x1D, 0x0C, 0x00), Gaia.commandPacket(Gaia.FEATURE_UPGRADE, Gaia.UPGRADE_CONNECT))
        val p = Gaia.parse(b(0x00, 0x1D, 0x0C, 0x80, 0x14, 0x00, 0x00))!!
        assertEquals(Gaia.VENDOR_V3, p.vendor)
        assertEquals(6, p.feature)
        assertEquals(Gaia.TYPE_NOTIFICATION, p.type)
        assertEquals(0, p.id)
        assertArrayEquals(b(0x14, 0, 0), p.payload)
        assertNull(Gaia.parse(b(0, 0x1D, 0x0C)))
    }

    @Test fun rfcommFrames() {
        val pdu = b(0x00, 0x1D, 0x0C, 0x02, 0x13, 0x00, 0x04, 1, 2, 3, 4)
        assertArrayEquals(b(0xFF, 3, 0, 7) + pdu, Gaia.rfcommFrame(pdu))
        val withSum = Gaia.rfcommFrame(pdu, checksum = true)
        assertEquals(1, withSum[2].toInt())
        assertEquals(0, withSum.fold(0) { a, x -> a xor x.toInt() } and 0xFF)
        val big = b(0, 0x1D, 0x0C, 0x02) + ByteArray(300)
        val f = Gaia.rfcommFrame(big, version = 4)
        assertEquals(2, f[2].toInt())
        assertEquals(300, ((f[3].toInt() and 0xFF) shl 8) or (f[4].toInt() and 0xFF))
    }

    @Test fun upgradeMessages() {
        assertArrayEquals(b(0x13, 0x00, 0x04, 1, 2, 3, 4), UpgradeMessage(UpgradeMessage.SYNC_REQ, b(1, 2, 3, 4)).bytes())
        assertArrayEquals(b(0x01, 0x00, 0x00), UpgradeMessage(UpgradeMessage.START_REQ).bytes())
        val m = UpgradeMessage.parse(b(0x03, 0x00, 0x08, 0, 0, 0, 16, 0, 0, 0, 0))!!
        assertEquals(UpgradeMessage.DATA_BYTES_REQ, m.opcode)
        assertEquals(8, m.content.size)
    }

    @Test fun syncIdIsLastFourMd5Bytes() {
        val file = ByteArray(100) { it.toByte() }
        val md5 = MessageDigest.getInstance("MD5").digest(file)
        assertArrayEquals(md5.copyOfRange(12, 16), UpgradeMessage.syncId(file))
    }

    @Test fun chunkSizeFromMtu() {
        assertEquals(245, Gaia.chunkSize(Gaia.blePayloadSize(256)))
    }

    /** A pretend unit that runs the standard upgrade flow, including a reboot, against the engine. */
    private class FakeUnit(val file: ByteArray, val dialect: GaiaDialect = GaiaDialect.V3) : UpgradeEngine.Io {
        lateinit var engine: UpgradeEngine
        val received = java.io.ByteArrayOutputStream()
        val sentOpcodes = ArrayList<Int>()
        val phases = ArrayList<UpgradeEngine.Phase>()
        var end: UpgradeEngine.End? = null
        var rebooted = false
        var resume = 0
        val tasks = ArrayList<() -> Unit>()

        val notificationAcks = ArrayList<ByteArray>()

        private fun notify(m: UpgradeMessage) = engine.onPacket(
            if (dialect == GaiaDialect.V3) Gaia.Packet(Gaia.VENDOR_V3, 6, Gaia.TYPE_NOTIFICATION, 0, m.bytes()).bytes()
            else b(0x00, 0x0A, 0x40, 0x03, 0x12) + m.bytes(),
        )

        /** The unit's acknowledgement of [packet]. */
        private fun ack(packet: ByteArray) = engine.onPacket(
            if (dialect == GaiaDialect.V3) Gaia.parse(packet)!!.let { Gaia.Packet(it.vendor, it.feature, Gaia.TYPE_RESPONSE, it.id, ByteArray(0)).bytes() }
            else byteArrayOf(packet[0], packet[1], (packet[2].toInt() or 0x80).toByte(), packet[3], 0),
        )

        /** The upgrade message inside an UPGRADE_CONTROL command, or null for other commands. */
        private fun controlPayload(packet: ByteArray): ByteArray? = if (dialect == GaiaDialect.V3) {
            Gaia.parse(packet)!!.takeIf { it.feature == 6 && it.id == Gaia.UPGRADE_CONTROL }?.payload
        } else {
            packet.copyOfRange(4, packet.size).takeIf { packet[2].toInt() == 0x06 && packet[3].toInt() == 0x42 }
        }

        // The unit answers later, like a real Bluetooth link (no re-entrant calls into the engine).
        override fun send(gaiaPacket: ByteArray) {
            tasks += { handle(gaiaPacket) }
        }

        private fun handle(gaiaPacket: ByteArray) {
            // V2: the host acknowledges our notifications; those get no answer.
            if (dialect == GaiaDialect.V2 && (gaiaPacket[2].toInt() and 0x80) != 0) {
                notificationAcks += gaiaPacket
                return
            }
            val payload = controlPayload(gaiaPacket)
            val data = payload?.let { UpgradeMessage.parse(it) }?.takeIf { it.opcode == UpgradeMessage.DATA }
            // Store data before acknowledging it: the ack makes the engine send the next chunk at once.
            data?.let { received.write(it.content, 1, it.content.size - 1) }
            ack(gaiaPacket)
            if (payload == null) return
            val m = UpgradeMessage.parse(payload)!!
            sentOpcodes += m.opcode
            when (m.opcode) {
                UpgradeMessage.SYNC_REQ -> notify(UpgradeMessage(UpgradeMessage.SYNC_CFM, b(resume) + m.content + b(3)))
                UpgradeMessage.START_REQ -> notify(UpgradeMessage(UpgradeMessage.START_CFM, b(0, 0x0F, 0xA0)))
                UpgradeMessage.START_DATA_REQ -> notify(UpgradeMessage(UpgradeMessage.DATA_BYTES_REQ, int(file.size) + int(0)))
                UpgradeMessage.IS_VALIDATION_DONE_REQ -> notify(UpgradeMessage(UpgradeMessage.TRANSFER_COMPLETE_IND))
                UpgradeMessage.TRANSFER_COMPLETE_RES -> {
                    // Reboot into the new image.
                    rebooted = true
                    resume = 3
                    tasks += {
                        assertTrue(engine.onDisconnected())
                        engine.start()
                    }
                }
                UpgradeMessage.PROCEED_TO_COMMIT -> notify(UpgradeMessage(UpgradeMessage.COMMIT_REQ))
                UpgradeMessage.COMMIT_CFM -> notify(UpgradeMessage(UpgradeMessage.COMPLETE_IND))
            }
        }

        private fun int(v: Int) = b(v ushr 24, v ushr 16, v ushr 8, v)
        override fun schedule(delayMs: Long, block: () -> Unit) {
            tasks += block
        }
        override fun onPhase(phase: UpgradeEngine.Phase) {
            phases += phase
        }
        override fun onProgress(percent: Double) {}
        override fun onEnd(end: UpgradeEngine.End) {
            this.end = end
        }
    }

    @Test fun fullUpgradeWithRebootV3() = fullUpgrade(GaiaDialect.V3)

    @Test fun fullUpgradeWithRebootV2() = fullUpgrade(GaiaDialect.V2)

    @Test fun v2Packets() {
        assertArrayEquals(b(0x00, 0x0A, 0x40, 0x01, 0x12), GaiaDialect.V2.registerNotification())
        assertArrayEquals(b(0x00, 0x0A, 0x06, 0x40), GaiaDialect.V2.connect())
        assertArrayEquals(b(0x00, 0x0A, 0x06, 0x42, 0x01, 0x00, 0x00), GaiaDialect.V2.control(UpgradeMessage(UpgradeMessage.START_REQ).bytes()))
        assertEquals(GaiaDialect.Incoming.Ack(GaiaDialect.Cmd.CONNECT, true, 0), GaiaDialect.V2.decode(b(0x00, 0x0A, 0x86, 0x40, 0x00)))
        assertEquals(GaiaDialect.Incoming.Ack(GaiaDialect.Cmd.CONTROL, false, 5), GaiaDialect.V2.decode(b(0x00, 0x0A, 0x86, 0x42, 0x05)))
        val d = GaiaDialect.V2.decode(b(0x00, 0x0A, 0x40, 0x03, 0x12, 0x14, 0x00, 0x00)) as GaiaDialect.Incoming.Data
        assertArrayEquals(b(0x14, 0x00, 0x00), d.message)
        assertArrayEquals(b(0x00, 0x0A, 0xC0, 0x03, 0x00), d.reply)
        assertNull(GaiaDialect.V2.decode(b(0x00, 0x1D, 0x0C, 0x80)))
        assertEquals(GaiaDialect.V3, GaiaDialect.forModel("Flow_Lite"))
        assertEquals(GaiaDialect.V2, GaiaDialect.forModel("Packtalk_Edge"))
    }

    @Test fun tarGzFirstEntry() {
        fun header(name: String, size: Int, type: Char): ByteArray {
            val h = ByteArray(512)
            name.toByteArray().copyInto(h)
            String.format("%011o", size).toByteArray().copyInto(h, 124)
            h[156] = type.code.toByte()
            return h
        }
        val content = ByteArray(700) { (it % 251).toByte() }
        val tar = header("dir/", 0, '5') + header("dir/ver.bin", content.size, '0') + content + ByteArray(1024 - 700) + ByteArray(1024)
        val gz = java.io.ByteArrayOutputStream().also { o -> java.util.zip.GZIPOutputStream(o).use { it.write(tar) } }.toByteArray()
        assertArrayEquals(content, FirmwareFile.firstTarEntry(gz))
        assertNull(FirmwareFile.firstTarEntry(b(1, 2, 3)))
        assertTrue(FirmwareFile.isTarGz("en_uk_ver.tar.gz"))
    }

    private fun fullUpgrade(dialect: GaiaDialect) {
        val file = ByteArray(10_000) { (it * 7).toByte() }
        val unit = FakeUnit(file, dialect)
        val engine = UpgradeEngine(file, unit, 245, dialect)
        unit.engine = engine
        engine.start()
        while (unit.tasks.isNotEmpty()) unit.tasks.removeAt(0)()
        assertArrayEquals(file, unit.received.toByteArray())
        assertTrue(unit.rebooted)
        assertEquals(UpgradeEngine.End.Complete, unit.end)
        assertEquals(
            listOf(
                UpgradeMessage.SYNC_REQ, UpgradeMessage.START_REQ, UpgradeMessage.START_DATA_REQ,
            ) + List((file.size + 244) / 245) { UpgradeMessage.DATA } + listOf(
                UpgradeMessage.IS_VALIDATION_DONE_REQ, UpgradeMessage.TRANSFER_COMPLETE_RES,
                UpgradeMessage.SYNC_REQ, UpgradeMessage.START_REQ, UpgradeMessage.PROCEED_TO_COMMIT, UpgradeMessage.COMMIT_CFM,
            ),
            unit.sentOpcodes,
        )
        assertTrue(UpgradeEngine.Phase.REBOOTING in unit.phases)
        assertEquals(UpgradeEngine.Phase.DONE, unit.phases.last())
    }

    @Test fun lowBatteryErrorFails() {
        val file = ByteArray(10)
        var ended: UpgradeEngine.End? = null
        val sent = ArrayList<ByteArray>()
        lateinit var engine: UpgradeEngine
        engine = UpgradeEngine(file, object : UpgradeEngine.Io {
            override fun send(gaiaPacket: ByteArray) {
                sent += gaiaPacket
            }
            override fun schedule(delayMs: Long, block: () -> Unit) {}
            override fun onPhase(phase: UpgradeEngine.Phase) {}
            override fun onProgress(percent: Double) {}
            override fun onEnd(end: UpgradeEngine.End) {
                ended = end
            }
        }, 100)
        engine.start()
        val err = UpgradeMessage(UpgradeMessage.ERROR_IND, b(0x00, 0x21)).bytes()
        engine.onPacket(Gaia.Packet(Gaia.VENDOR_V3, 6, Gaia.TYPE_NOTIFICATION, 0, err).bytes())
        assertEquals(UpgradeEngine.End.Failed(UpgradeEngine.Reason.BATTERY_LOW, 0x21), ended)
    }

    @Test fun abortSendsAbortReq() {
        val file = ByteArray(10)
        val sent = ArrayList<ByteArray>()
        val engine = UpgradeEngine(file, object : UpgradeEngine.Io {
            override fun send(gaiaPacket: ByteArray) {
                sent += gaiaPacket
            }
            override fun schedule(delayMs: Long, block: () -> Unit) {}
            override fun onPhase(phase: UpgradeEngine.Phase) {}
            override fun onProgress(percent: Double) {}
            override fun onEnd(end: UpgradeEngine.End) {}
        }, 100)
        engine.abort()
        val p = Gaia.parse(sent.single())!!
        assertEquals(UpgradeMessage.ABORT_REQ, UpgradeMessage.parse(p.payload)!!.opcode)
    }
}
