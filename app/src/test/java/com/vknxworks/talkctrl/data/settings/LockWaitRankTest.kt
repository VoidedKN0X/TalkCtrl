package com.vknxworks.talkctrl.data.settings

import org.junit.Assert.assertTrue
import org.junit.Test

class LockWaitRankTest {
    @Test fun shorterIsStricter() {
        assertTrue(lockWaitRank(0L) < lockWaitRank(60_000L))
        assertTrue(lockWaitRank(60_000L) < lockWaitRank(3_600_000L))
    }

    @Test fun restartIsTheLeastStrict() {
        assertTrue(lockWaitRank(3_600_000L) < lockWaitRank(-1L))
        assertTrue(lockWaitRank(0L) < lockWaitRank(-1L))
    }
}
