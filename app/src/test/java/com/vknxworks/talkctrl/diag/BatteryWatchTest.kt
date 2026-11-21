package com.vknxworks.talkctrl.diag

import org.junit.Assert.assertEquals
import org.junit.Test

class BatteryWatchTest {
    @Test fun warnsOncePerDrop() {
        val w = BatteryWatch()
        val seq = listOf(30, 25, 20, 19, 20, 21, 18, 24, 25, 22, 20).map { w.update(it, false, 20) }
        assertEquals(listOf(false, false, true, false, false, false, false, false, false, false, true), seq)
    }

    @Test fun chargingRearmsAndSilences() {
        val w = BatteryWatch()
        assertEquals(true, w.update(10, false, 15))
        assertEquals(false, w.update(10, true, 15))
        assertEquals(true, w.update(10, false, 15))
    }

    @Test fun offAndUnknown() {
        val w = BatteryWatch()
        assertEquals(false, w.update(5, false, 0))
        assertEquals(false, w.update(null, false, 20))
    }
}
