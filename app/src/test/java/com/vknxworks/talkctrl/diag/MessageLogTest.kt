package com.vknxworks.talkctrl.diag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageLogTest {
    @Test fun keepsProtocolBytes() {
        assertEquals("51 4b 00", MessageLog.describe(byteArrayOf(0x51, 0x4b, 0x00), outgoing = false))
        assertEquals("30 20 00 00", MessageLog.describe(byteArrayOf(0x30, 0x20, 0x00, 0x00), outgoing = true))
    }

    @Test fun removesPersonalDetails() {
        val name = byteArrayOf(0x42, 4) + "Axel".toByteArray()
        val d = MessageLog.describe(name, outgoing = false)
        assertFalse(d.contains("41 78"))
        assertTrue(d.startsWith("42 ["))
        val serial = byteArrayOf(0x43) + "PT1234567".toByteArray()
        assertTrue(MessageLog.describe(serial, outgoing = false).contains("removed"))
        val dial = byteArrayOf(0x11, 3) + "112".toByteArray()
        assertTrue(MessageLog.describe(dial, outgoing = true).contains("removed"))
        val setName = byteArrayOf(0x12, 2) + "Me".toByteArray()
        assertTrue(MessageLog.describe(setName, outgoing = true).contains("removed"))
    }

    @Test fun ringBufferAndTimes() {
        val log = MessageLog(capacity = 2)
        log.reset(1000)
        log.add(1500, true, byteArrayOf(1))
        log.add(2500, false, byteArrayOf(2))
        log.add(3001, false, byteArrayOf(3))
        assertEquals(listOf("+1.500 s < 02", "+2.001 s < 03"), log.snapshot())
    }
}
