package com.vknxworks.talkctrl.bt

/**
 * What to do after Android refused to start a GATT write (busy stack, brief link hiccup). Writing the same
 * packet again once after [DELAY_MS] usually goes through; if that is refused too, the link is treated as
 * lost, so the firmware transfer reconnects and resumes instead of waiting forever for an answer to a
 * packet that never went out.
 */
class WriteRetry(private val maxRetries: Int = 1) {
    enum class Action { RETRY, GIVE_UP }

    private var retries = 0

    /** A new packet is being written: it gets its own retry. */
    fun newPacket() {
        retries = 0
    }

    /** The write of the current packet was refused. */
    fun refused(): Action = if (retries < maxRetries) {
        retries++
        Action.RETRY
    } else {
        Action.GIVE_UP
    }

    companion object {
        const val DELAY_MS = 500L
    }
}
