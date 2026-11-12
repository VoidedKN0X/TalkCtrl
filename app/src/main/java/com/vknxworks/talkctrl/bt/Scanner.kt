package com.vknxworks.talkctrl.bt

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import com.vknxworks.talkctrl.protocol.Caip
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class FoundUnit(val address: String, val name: String?, val paired: Boolean, val cardo: Boolean, val rssi: Int?)

/**
 * Finds units: phones paired with a Cardo unit already know it (the address prefix tells it's Cardo), and a
 * BLE scan (BLUETOOTH_SCAN with neverForLocation, no location) adds units nearby.
 */
@SuppressLint("MissingPermission") // Called only after BLUETOOTH_SCAN / BLUETOOTH_CONNECT were granted; SecurityException is caught.
class Scanner(context: Context) {
    private val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
    private val _found = MutableStateFlow<List<FoundUnit>>(emptyList())
    val found: StateFlow<List<FoundUnit>> = _found.asStateFlow()
    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    val bluetoothOn get() = adapter?.isEnabled == true

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) = add(result)
        override fun onBatchScanResults(results: MutableList<ScanResult>) = results.forEach { add(it) }
        override fun onScanFailed(errorCode: Int) {
            _scanning.value = false
        }
    }

    private fun add(r: ScanResult) {
        val address = r.device.address ?: return
        val name = try {
            r.scanRecord?.deviceName ?: r.device.name
        } catch (_: SecurityException) {
            null
        }
        val uuids = r.scanRecord?.serviceUuids.orEmpty().map { it.uuid }
        val cardo = Caip.isCardoAddress(address) || Caip.SERVICE in uuids || Caip.SERVICE_BLE_ONLY in uuids
        if (name == null && !cardo) return
        _found.update { list ->
            val old = list.firstOrNull { it.address == address }
            val item = FoundUnit(address, name ?: old?.name, old?.paired ?: false, cardo || old?.cardo == true, r.rssi)
            (list.filterNot { it.address == address } + item).sorted()
        }
    }

    private fun List<FoundUnit>.sorted() = sortedWith(compareByDescending<FoundUnit> { it.cardo }.thenByDescending { it.paired }.thenBy { it.name ?: "~" })

    fun start() {
        val paired = try {
            adapter?.bondedDevices.orEmpty().map {
                FoundUnit(it.address, it.name, paired = true, cardo = Caip.isCardoAddress(it.address), rssi = null)
            }
        } catch (_: SecurityException) {
            emptyList()
        }
        _found.value = paired.sorted()
        val scanner = adapter?.bluetoothLeScanner ?: return
        try {
            scanner.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), callback)
            _scanning.value = true
        } catch (_: SecurityException) {
        } catch (_: IllegalStateException) {
        }
    }

    fun stop() {
        try {
            adapter?.bluetoothLeScanner?.stopScan(callback)
        } catch (_: SecurityException) {
        } catch (_: IllegalStateException) {
        }
        _scanning.value = false
    }
}
