package com.vknxworks.talkctrl.bt

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothSocket
import android.content.Context
import com.vknxworks.talkctrl.protocol.Caip
import com.vknxworks.talkctrl.protocol.Capabilities
import com.vknxworks.talkctrl.protocol.ConfigRecord
import com.vknxworks.talkctrl.protocol.FirmwareVersion
import com.vknxworks.talkctrl.protocol.UserConfig
import com.vknxworks.talkctrl.protocol.Volumes
import com.vknxworks.talkctrl.protocol.eqProfiles
import com.vknxworks.talkctrl.protocol.fmPresets
import com.vknxworks.talkctrl.protocol.splitRfcomm
import com.vknxworks.talkctrl.protocol.supportedLanguages
import com.vknxworks.talkctrl.protocol.u
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException

enum class LinkState { IDLE, CONNECTING, CONNECTED, FAILED }

/** What TalkCtrl knows about the connected unit. Null fields: not reported (yet). */
data class UnitState(
    val link: LinkState = LinkState.IDLE,
    val address: String? = null,
    val rfcomm: Boolean = false,
    val caipVersion: Int? = null,
    val name: String? = null,
    val serial: String? = null,
    val speedDial: String? = null,
    val battery: Int? = null,
    val charging: Boolean = false,
    val firmware: FirmwareVersion? = null,
    val internalVersion: String? = null,
    val user: UserConfig? = null,
    val volumes: Volumes? = null,
    val caps: Capabilities? = null,
    val languages: List<Int> = emptyList(),
    val eqs: List<Int> = emptyList(),
    val presets: List<Double>? = null,
    val fmState: Int = 0,
    val fmMhz: Double? = null,
    val fmPreset: Int = 0,
    val musicSharing: Int = 0,
    val unsupportedVersion: Boolean = false,
) {
    val connected get() = link == LinkState.CONNECTED
}

/**
 * The control link to the unit: BLE GATT (Cardo's CAIP service) or, if chosen in Settings, classic RFCOMM.
 * One link at a time. All writes go through one queue (Android allows one GATT operation at a time).
 */
@SuppressLint("MissingPermission") // Every entry point is called only after BLUETOOTH_CONNECT was granted; SecurityException is caught.
class UnitLink(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
    private val adapter: BluetoothAdapter? = context.getSystemService(BluetoothManager::class.java)?.adapter

    private val _state = MutableStateFlow(UnitState())
    val state: StateFlow<UnitState> = _state.asStateFlow()

    /** Every message from the unit, parsed. */
    val messages = MutableSharedFlow<Caip.Message>(extraBufferCapacity = 64)
    private val raw = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)

    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private var socket: BluetoothSocket? = null
    private val pending = ArrayDeque<ByteArray>()
    private var writing = false
    private var job: Job? = null
    private var collector: Job? = null

    fun connect(address: String, rfcomm: Boolean) {
        disconnect()
        val device = try {
            adapter?.getRemoteDevice(address)
        } catch (_: IllegalArgumentException) {
            null
        } ?: run { _state.value = UnitState(LinkState.FAILED, address); return }
        _state.value = UnitState(LinkState.CONNECTING, address, rfcomm)
        job = scope.launch {
            try {
                if (rfcomm) runRfcomm(device) else device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE).also { gatt = it }
                handshake()
            } catch (_: SecurityException) {
                fail()
            }
        }
    }

    fun disconnect() {
        collector?.cancel()
        collector = null
        job?.cancel()
        job = null
        try {
            gatt?.disconnect()
            gatt?.close()
        } catch (_: SecurityException) {
        }
        gatt = null
        writeChar = null
        try {
            socket?.close()
        } catch (_: IOException) {
        }
        socket = null
        synchronized(pending) {
            pending.clear()
            writing = false
        }
        _state.value = UnitState()
    }

    private fun fail() {
        val s = _state.value
        disconnect()
        _state.value = UnitState(LinkState.FAILED, s.address, s.rfcomm)
    }

    // ---------- Sending ----------

    /** Queues one CAIP message. False when not connected. */
    fun send(msg: ByteArray): Boolean {
        if (gatt == null && socket == null) return false
        val s = socket
        if (s != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    s.outputStream.write(msg)
                } catch (_: IOException) {
                    scope.launch { fail() }
                }
            }
            return true
        }
        synchronized(pending) { pending.addLast(msg) }
        pumpWrites()
        return true
    }

    private fun pumpWrites() {
        val g = gatt ?: return
        val c = writeChar ?: return
        val next: ByteArray
        synchronized(pending) {
            if (writing) return
            next = pending.removeFirstOrNull() ?: return
            writing = true
        }
        val r = try {
            g.writeCharacteristic(c, next, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
        } catch (_: SecurityException) {
            -1
        }
        if (r != BluetoothGatt.GATT_SUCCESS) {
            // One retry after a second, as the Cardo app does; then drop it.
            scope.launch {
                delay(1000)
                val again = try {
                    g.writeCharacteristic(c, next, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
                } catch (_: SecurityException) {
                    -1
                }
                if (again != BluetoothGatt.GATT_SUCCESS) {
                    synchronized(pending) { writing = false }
                    pumpWrites()
                }
            }
        }
    }

    // ---------- BLE ----------

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            scope.launch {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    try {
                        g.discoverServices()
                    } catch (_: SecurityException) {
                        fail()
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    if (gatt === g) fail()
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            scope.launch {
                val w = g.services.firstNotNullOfOrNull { it.getCharacteristic(Caip.WRITE) }
                val n = g.services.firstNotNullOfOrNull { it.getCharacteristic(Caip.NOTIFY) }
                if (w == null || n == null) return@launch fail()
                writeChar = w
                try {
                    g.requestMtu(Caip.MTU)
                    g.setCharacteristicNotification(n, true)
                    val d = n.getDescriptor(Caip.CCCD)
                    if (d != null) g.writeDescriptor(d, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                    else ready.tryEmit(Unit)
                } catch (_: SecurityException) {
                    fail()
                }
            }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            ready.tryEmit(Unit)
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            scope.launch {
                synchronized(pending) { writing = false }
                pumpWrites()
            }
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
            if (c.uuid == Caip.NOTIFY) raw.tryEmit(value.copyOf())
        }
    }

    private val ready = MutableSharedFlow<Unit>(replay = 1, extraBufferCapacity = 1)

    // ---------- RFCOMM ----------

    private suspend fun runRfcomm(device: BluetoothDevice) {
        val s = device.createInsecureRfcommSocketToServiceRecord(Caip.RFCOMM)
        socket = s
        val ok = kotlinx.coroutines.withContext(Dispatchers.IO) {
            try {
                adapter?.cancelDiscovery()
                s.connect()
                true
            } catch (_: IOException) {
                false
            }
        }
        if (!ok) return fail()
        scope.launch(Dispatchers.IO) {
            val buf = ByteArray(1024)
            try {
                while (true) {
                    val n = s.inputStream.read(buf)
                    if (n < 0) break
                    splitRfcomm(buf.copyOf(n)).forEach { raw.tryEmit(it) }
                }
            } catch (_: IOException) {
            }
            scope.launch { if (socket === s) fail() }
        }
        ready.tryEmit(Unit)
    }

    // ---------- Handshake and incoming ----------

    private suspend fun handshake() {
        ready.resetReplayCache()
        if (withTimeoutOrNull(20_000) { ready.first() } == null) return fail()
        delay(300)
        // 1. Version: the next message after GET_VER is the answer, whatever its opcode.
        val ver = sendAndAwait(Caip.getVersion(), 4000) { raw.first() }
        val v = ver?.firstOrNull()?.toInt()?.and(0xFF)
        _state.update { it.copy(link = LinkState.CONNECTED, caipVersion = v, unsupportedVersion = v != null && v != Caip.VERSION_CAIP64) }
        collector = scope.launch(start = CoroutineStart.UNDISPATCHED) { raw.collect { onRaw(it) } }
        // 2. Serial, name, speed dial, settings, then the live services.
        suspend fun ask(msg: ByteArray, wait: suspend () -> Unit) {
            sendAndAwait(msg, 2500) { wait() }
        }
        ask(Caip.getSerial()) { messages.first { it is Caip.Serial } }
        ask(Caip.getName()) { messages.first { it is Caip.Name } }
        ask(Caip.getSpeedDial()) { messages.first { it is Caip.SpeedDial } }
        ask(Caip.getConfig(Caip.REQUESTED_TYPES)) { messages.first { it is Caip.ConfigUpdate } }
        send(Caip.subscribeAll())
    }

    /** Subscribes first, then sends, so a fast answer isn't missed. */
    private suspend fun <T> sendAndAwait(msg: ByteArray, timeoutMs: Long, wait: suspend () -> T): T? = coroutineScope {
        val answer = async(start = CoroutineStart.UNDISPATCHED) { withTimeoutOrNull(timeoutMs) { wait() } }
        send(msg)
        answer.await()
    }

    private fun onRaw(d: ByteArray) {
        val m = Caip.parse(d) ?: return
        _state.update { s ->
            when (m) {
                is Caip.ConfigUpdate -> m.records.fold(s) { acc, r -> acc.apply(r) }
                is Caip.Battery -> s.copy(battery = m.percent, charging = m.charging)
                is Caip.State -> s.copy(fmState = m.fmState, fmPreset = m.fmPreset, fmMhz = m.fmMhz.takeIf { it > 0 })
                is Caip.Serial -> s.copy(serial = m.serial)
                is Caip.Name -> s.copy(name = m.name)
                is Caip.SpeedDial -> s.copy(speedDial = m.number)
                is Caip.MusicSharing -> s.copy(musicSharing = m.state)
                Caip.OtaReady, is Caip.Other -> s
            }
        }
        messages.tryEmit(m)
    }

    private fun UnitState.apply(r: ConfigRecord): UnitState = when (r.type) {
        0x00 -> copy(user = UserConfig.from(r))
        0x02 -> copy(volumes = Volumes.from(r))
        0x03 -> copy(firmware = FirmwareVersion.from(r))
        0x04 -> copy(internalVersion = "${(r.raw.u(3) shl 8) or r.raw.u(4)}.${(r.raw.u(5) shl 8) or r.raw.u(6)}.${(r.raw.u(7) shl 8) or r.raw.u(8)}")
        0x05 -> copy(languages = supportedLanguages(r))
        0x80 -> copy(presets = fmPresets(r))
        0x82 -> copy(caps = Capabilities.from(r))
        0x84 -> copy(eqs = eqProfiles(r))
        else -> this
    }

    // ---------- Changing settings ----------

    /** Changes USER_CONFIG: read-modify-write of the unit's own bytes, shown at once. */
    fun setUser(change: (UserConfig) -> UserConfig): Boolean {
        val cur = _state.value.user ?: return false
        val next = change(cur)
        if (!send(Caip.setConfig(next.record()))) return false
        _state.update { it.copy(user = next) }
        return true
    }

    fun setVolumes(change: (Volumes) -> Volumes): Boolean {
        val cur = _state.value.volumes ?: return false
        val next = change(cur)
        if (!send(Caip.setConfig(next.record()))) return false
        _state.update { it.copy(volumes = next) }
        return true
    }

    /** Applies a saved profile (USER_CONFIG + VOLUMES records) in one message. */
    fun applyRecords(user: UserConfig, volumes: Volumes): Boolean {
        if (!send(Caip.setConfig(user.record(), volumes.record()))) return false
        _state.update { it.copy(user = user, volumes = volumes) }
        return true
    }

    fun setPresets(presets: List<Double>): Boolean {
        if (!send(Caip.setConfig(com.vknxworks.talkctrl.protocol.fmPresetsRecord(presets)))) return false
        _state.update { it.copy(presets = presets) }
        return true
    }

    fun rename(name: String): Boolean {
        if (!send(Caip.setName(name))) return false
        _state.update { it.copy(name = name.trim()) }
        return true
    }

    fun setSpeedDial(number: String): Boolean {
        if (!send(Caip.setSpeedDial(number))) return false
        _state.update { it.copy(speedDial = number) }
        return true
    }

    /** Asks the unit to get ready for an update and waits up to [timeoutMs] for its answer. */
    suspend fun requestOtaReady(timeoutMs: Long): Boolean {
        if (gatt == null && socket == null) return false
        return sendAndAwait(byteArrayOf(Caip.OP_OTA_START.toByte()), timeoutMs) { messages.first { it == Caip.OtaReady } } != null
    }
}
