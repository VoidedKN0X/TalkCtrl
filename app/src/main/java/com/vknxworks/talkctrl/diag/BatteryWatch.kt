package com.vknxworks.talkctrl.diag

/**
 * Decides when to warn about the unit's battery: once when it reaches [threshold] % or less while not charging,
 * and again only after it has been charging or has come back to at least [threshold] + 5 %, so a level that
 * wobbles around the line doesn't warn over and over. Plain Kotlin, unit tested.
 */
class BatteryWatch {
    private var armed = true

    /** True when a warning should be shown now. [threshold] 0 means off. */
    @Synchronized
    fun update(level: Int?, charging: Boolean, threshold: Int): Boolean {
        if (threshold <= 0 || level == null) return false
        if (charging || level >= threshold + 5) {
            armed = true
            return false
        }
        if (armed && level <= threshold) {
            armed = false
            return true
        }
        return false
    }

    /** A new connection starts afresh. */
    @Synchronized
    fun reset() {
        armed = true
    }
}
