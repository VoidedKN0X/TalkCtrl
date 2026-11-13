package com.vknxworks.talkctrl

import android.Manifest
import android.content.Context
import com.vknxworks.talkctrl.bt.Scanner
import com.vknxworks.talkctrl.bt.UnitLink
import com.vknxworks.talkctrl.data.lock.LockVault
import com.vknxworks.talkctrl.data.settings.AppSettings
import com.vknxworks.talkctrl.data.settings.SettingsRepository
import com.vknxworks.talkctrl.protocol.FirmwareFile
import com.vknxworks.talkctrl.ui.common.hasPermission
import com.vknxworks.talkctrl.ui.lock.AppLock
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Manual dependency container; one per process. */
class AppContainer(val context: Context) {
    /** Background work that outlives a screen. A failure there must never close the app. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main + CoroutineExceptionHandler { _, _ -> })

    val settings = SettingsRepository(context, appScope)

    /** The control link to the unit. */
    val link = UnitLink(context)
    val scanner = Scanner(context)

    /** A request to open the firmware update screen (from its notification). */
    val openUpdate = MutableStateFlow(false)

    /** The app lock's PIN and fingerprint key (the shared Ctrl series lock). */
    val vault = LockVault(context, "talkctrl")

    init {
        AppLock.onLocked = { onLocked() }
        // Firmware copies left in the cache by an earlier run (no transfer survives the app's process).
        appScope.launch(Dispatchers.IO) { clearFirmwareCopies() }
    }

    /** Deletes TalkCtrl's temporary firmware copies. Only while no transfer is running. */
    fun clearFirmwareCopies() {
        FirmwareFile.TEMP_NAMES.forEach { runCatching { java.io.File(context.cacheDir, it).delete() } }
    }

    val canUseBluetooth get() = context.hasPermission(Manifest.permission.BLUETOOTH_CONNECT)

    /** Connects to [address] and remembers it as your unit. */
    fun connect(address: String, name: String?) {
        if (!canUseBluetooth) return
        val rfcomm = settings.settings.value.useRfcomm
        link.connect(address, rfcomm)
        appScope.launch { settings.update { it.copy(unitAddress = address, unitName = name ?: if (it.unitAddress == address) it.unitName else null) } }
    }

    /** Reconnects to the remembered unit, if there is one. */
    fun reconnect() {
        val s = settings.settings.value
        if (s.unitAddress != null && canUseBluetooth) link.connect(s.unitAddress, s.useRfcomm)
    }

    fun forgetUnit() {
        link.disconnect()
        appScope.launch { settings.update { it.copy(unitAddress = null, unitName = null, modelFolder = null) } }
    }

    suspend fun enableLock(pin: String) {
        AppLock.markUnlocked()
        withContext(Dispatchers.Default) { vault.setUp(pin) }
    }

    fun disableLock() {
        vault.disable()
    }

    /**
     * "Forgot PIN?": the PIN can't be recovered, so TalkCtrl's settings (the remembered unit and saved
     * profiles) are reset and the lock turned off. Nothing on the unit is changed.
     */
    suspend fun forgetEverything() {
        link.disconnect()
        settings.update { AppSettings() }
        vault.disable()
        AppLock.markUnlocked()
    }

    fun onLocked() {
        vault.close()
    }

    fun onUnlocked() {
        AppLock.markUnlocked()
    }
}

val Context.container: AppContainer
    get() = (applicationContext as TalkCtrlApp).container
