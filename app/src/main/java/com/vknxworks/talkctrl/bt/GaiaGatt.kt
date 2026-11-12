package com.vknxworks.talkctrl.bt

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import com.vknxworks.talkctrl.protocol.Caip
import com.vknxworks.talkctrl.protocol.Gaia

/**
 * GAIA over BLE (Qualcomm's GAIA GATT service), the transport the Cardo app uses for firmware updates.
 * Commands go to the command characteristic, responses and notifications come from the response
 * characteristic. Callbacks arrive on Android's Bluetooth thread.
 */
@SuppressLint("MissingPermission") // Used only after BLUETOOTH_CONNECT was granted; SecurityException is caught.
class GaiaGatt(private val context: Context, private val listener: Listener) {

    interface Listener {
        /** Ready to send; [mtu] is the negotiated ATT MTU. */
        fun onReady(mtu: Int)
        fun onPacket(bytes: ByteArray)
        fun onDisconnected()
    }

    private var gatt: BluetoothGatt? = null
    private var command: BluetoothGattCharacteristic? = null
    private var mtu = 23
    @Volatile private var closed = false

    fun connect(address: String): Boolean {
        closed = false
        val device = try {
            context.getSystemService(BluetoothManager::class.java)?.adapter?.getRemoteDevice(address)
        } catch (_: IllegalArgumentException) {
            null
        } ?: return false
        return try {
            gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            gatt != null
        } catch (_: SecurityException) {
            false
        }
    }

    private val pending = ArrayDeque<ByteArray>()
    private var writing = false

    /** Queues one GAIA packet; Android allows one GATT write at a time. */
    fun send(packet: ByteArray): Boolean {
        if (gatt == null || command == null) return false
        synchronized(pending) { pending.addLast(packet) }
        pump()
        return true
    }

    private fun pump() {
        val g = gatt ?: return
        val c = command ?: return
        val next: ByteArray
        synchronized(pending) {
            if (writing) return
            next = pending.removeFirstOrNull() ?: return
            writing = true
        }
        val ok = try {
            g.writeCharacteristic(c, next, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothGatt.GATT_SUCCESS
        } catch (_: SecurityException) {
            false
        }
        if (!ok) {
            synchronized(pending) { writing = false }
        }
    }

    fun close() {
        closed = true
        try {
            gatt?.disconnect()
            gatt?.close()
        } catch (_: SecurityException) {
        }
        gatt = null
        command = null
        synchronized(pending) {
            pending.clear()
            writing = false
        }
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            try {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    g.requestMtu(MTU)
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    g.close()
                    if (gatt === g) {
                        gatt = null
                        command = null
                    }
                    if (!closed) listener.onDisconnected()
                }
            } catch (_: SecurityException) {
                listener.onDisconnected()
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) this@GaiaGatt.mtu = mtu
            try {
                g.discoverServices()
            } catch (_: SecurityException) {
                listener.onDisconnected()
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val s = g.getService(Gaia.SERVICE)
            val cmd = s?.getCharacteristic(Gaia.COMMAND)
            val rsp = s?.getCharacteristic(Gaia.RESPONSE)
            if (cmd == null || rsp == null) {
                close()
                listener.onDisconnected()
                return
            }
            command = cmd
            try {
                g.setCharacteristicNotification(rsp, true)
                val d = rsp.getDescriptor(Caip.CCCD)
                if (d == null || g.writeDescriptor(d, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) != BluetoothGatt.GATT_SUCCESS) {
                    listener.onReady(mtu)
                }
            } catch (_: SecurityException) {
                listener.onDisconnected()
            }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            listener.onReady(mtu)
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            synchronized(pending) { writing = false }
            pump()
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
            if (c.uuid == Gaia.RESPONSE) listener.onPacket(value.copyOf())
        }
    }

    companion object {
        const val MTU = 256
    }
}
