package com.vknxworks.talkctrl.bt

import org.junit.Assert.assertEquals
import org.junit.Test

class WriteRetryTest {
    @Test fun retriesOnceThenGivesUp() {
        val r = WriteRetry()
        r.newPacket()
        assertEquals(WriteRetry.Action.RETRY, r.refused())
        assertEquals(WriteRetry.Action.GIVE_UP, r.refused())
        assertEquals(WriteRetry.Action.GIVE_UP, r.refused())
    }

    @Test fun eachPacketGetsItsOwnRetry() {
        val r = WriteRetry()
        r.newPacket()
        assertEquals(WriteRetry.Action.RETRY, r.refused())
        r.newPacket()
        assertEquals(WriteRetry.Action.RETRY, r.refused())
        assertEquals(WriteRetry.Action.GIVE_UP, r.refused())
    }

    @Test fun noRetriesAllowed() {
        val r = WriteRetry(maxRetries = 0)
        r.newPacket()
        assertEquals(WriteRetry.Action.GIVE_UP, r.refused())
    }
}
