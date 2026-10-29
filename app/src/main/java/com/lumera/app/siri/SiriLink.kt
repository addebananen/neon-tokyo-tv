package com.lumera.app.siri

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.Build
import android.os.Handler
import android.os.SystemClock
import android.util.Log
import java.util.UUID

/**
 * The Bluetooth side: finds the paired Siri Remote, keeps a GATT link to it, subscribes to its HID
 * reports and hands every report to [onReport].
 *
 * The remote's reports all share the HID Report characteristic (0x2A4D) and are told apart by
 * their Report Reference descriptor (0x2908): gen 2/3 sends buttons on 0xFB and touch on 0xFC;
 * gen 1 sends everything on one report and needs 0xAF written to its feature report first.
 */
@SuppressLint("MissingPermission")
class SiriLink(
    private val handler: Handler,
    private val listener: Listener
) {
    interface Listener {
        fun onReady(generation: Int, name: String, address: String)
        fun onReport(reportId: Int, data: ByteArray)
        fun onLost()
        fun onBattery(percent: Int)
        fun onProblem(text: String)
        fun onPaired(address: String)
    }

    private val adapter: BluetoothAdapter? by lazy {
        runCatching { ShellEnv.context.getSystemService(BluetoothManager::class.java)?.adapter }.getOrNull()
            ?: BluetoothAdapter.getDefaultAdapter()
    }

    private var gatt: BluetoothGatt? = null
    private var device: BluetoothDevice? = null
    private val reportIds = HashMap<Int, Int>() // characteristic instance id -> report id
    private val ops = ArrayDeque<Op>()
    private var busy = false
    private var ready = false
    private var closed = false
    private val notSiri = HashSet<String>()
    private var candidates = ArrayDeque<BluetoothDevice>()
    private var probing = false

    var state = "starting"
        private set

    private sealed class Op {
        class ReadDesc(val d: BluetoothGattDescriptor) : Op()
        class WriteDesc(val d: BluetoothGattDescriptor, val value: ByteArray) : Op()
        class ReadChar(val c: BluetoothGattCharacteristic) : Op()
        class WriteChar(val c: BluetoothGattCharacteristic, val value: ByteArray) : Op()
        class Run(val block: () -> Unit) : Op()
    }

    /** Connects to [address] if given, else looks for an Apple remote among the paired devices. */
    fun start(address: String) {
        val a = adapter
        if (a == null || !a.isEnabled) {
            state = "bluetooth-off"
            listener.onProblem("Bluetooth is off")
            handler.postDelayed({ start(address) }, 5_000)
            return
        }
        val saved = address.takeIf { it.isNotEmpty() }?.let { runCatching { a.getRemoteDevice(it) }.getOrNull() }
        if (saved != null && saved.bondState == BluetoothDevice.BOND_BONDED) {
            probing = false
            connect(saved)
            return
        }
        candidates = ArrayDeque(
            a.bondedDevices.orEmpty().filter {
                it.address !in notSiri &&
                    (it.type == BluetoothDevice.DEVICE_TYPE_LE || it.type == BluetoothDevice.DEVICE_TYPE_DUAL)
            }
        )
        probeNext()
    }

    private fun probeNext() {
        val next = candidates.removeFirstOrNull()
        if (next == null) {
            probing = false
            state = "no-remote"
            listener.onProblem("No Siri Remote paired yet")
            handler.postDelayed({ if (!ready && gatt == null) start("") }, 15_000)
            return
        }
        probing = true
        connect(next)
        // a device that never answers is not the remote
        val tried = next.address
        handler.postDelayed({
            if (probing && !ready && device?.address == tried) {
                notSiri += tried
                close()
                probeNext()
            }
        }, 10_000)
    }

    private fun connect(d: BluetoothDevice) {
        close()
        closed = false
        device = d
        state = "connecting"
        Log.i(TAG, "connecting to ${d.address}")
        gatt = if (Build.VERSION.SDK_INT >= 26) {
            d.connectGatt(ShellEnv.context, !probing, callback, BluetoothDevice.TRANSPORT_LE, BluetoothDevice.PHY_LE_1M_MASK, handler)
        } else {
            d.connectGatt(ShellEnv.context, !probing, callback, BluetoothDevice.TRANSPORT_LE)
        }
    }

    fun close() {
        closed = true
        ready = false
        ops.clear()
        busy = false
        reportIds.clear()
        gatt?.let { runCatching { it.disconnect(); it.close() } }
        gatt = null
    }

    // ------------------------------------------------------------------ pairing

    private var scanning = false

    /** Hold Back and Volume Up on the remote for 5 seconds, then this finds and pairs it. */
    fun pair() {
        val scanner = adapter?.bluetoothLeScanner ?: return listener.onProblem("Bluetooth is off")
        if (scanning) return
        scanning = true
        state = "pairing"
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        val cb = object : ScanCallback() {
            override fun onScanResult(type: Int, result: ScanResult) {
                val apple = result.scanRecord?.getManufacturerSpecificData(APPLE)
                // pairing mode: Apple data 07 0D 02 15 03 02 <identity address> ..., plus the HID service
                val pairingMode = apple != null && apple.size >= 3 && apple[0] == 0x07.toByte() &&
                    apple[1] == 0x0D.toByte() && apple[2] == 0x02.toByte()
                if (!pairingMode || result.rssi < -80 || !scanning) return
                scanning = false
                runCatching { scanner.stopScan(this) }
                bond(result.device)
            }
        }
        runCatching { scanner.startScan(null, settings, cb) }
            .onFailure { scanning = false; listener.onProblem("Scan failed: ${it.message}") }
        handler.postDelayed({
            if (scanning) {
                scanning = false
                runCatching { scanner.stopScan(cb) }
                state = "pair-timeout"
                listener.onProblem("No remote in pairing mode found")
            }
        }, 30_000)
    }

    private fun bond(d: BluetoothDevice) {
        Log.i(TAG, "bonding ${d.address}")
        val started = runCatching {
            BluetoothDevice::class.java.getMethod("createBond", Int::class.javaPrimitiveType)
                .invoke(d, BluetoothDevice.TRANSPORT_LE) as Boolean
        }.getOrElse { d.createBond() }
        if (!started) return listener.onProblem("Pairing did not start")
        val until = SystemClock.uptimeMillis() + 20_000
        val poll = object : Runnable {
            override fun run() {
                when (d.bondState) {
                    BluetoothDevice.BOND_BONDED -> {
                        notSiri -= d.address
                        listener.onPaired(d.address)
                        probing = false
                        connect(d)
                    }
                    BluetoothDevice.BOND_BONDING -> {
                        // Just Works: answer the consent ourselves, nobody has to press OK on the TV
                        runCatching { d.setPairingConfirmation(true) }
                        if (SystemClock.uptimeMillis() < until) handler.postDelayed(this, 150)
                    }
                    else -> if (SystemClock.uptimeMillis() < until) handler.postDelayed(this, 150) else {
                        state = "pair-failed"
                        listener.onProblem("Pairing failed; hold Back + Volume Up for 5 s and try again")
                    }
                }
            }
        }
        handler.post(poll)
    }

    // ------------------------------------------------------------------ GATT

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (g != gatt) return
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                state = "discovering"
                // the remote asks for 7.5-15 ms, latency 80, timeout 6 s; with host defaults it
                // drops the link whenever it dozes between presses
                g.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                runCatching {
                    BluetoothGatt::class.java.getMethod(
                        "requestLeConnectionUpdate",
                        Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType
                    ).invoke(g, 6, 12, 80, 600, 0, 0)
                }
                handler.postDelayed({ if (g == gatt) g.discoverServices() }, 150)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                val wasReady = ready
                ready = false
                ops.clear()
                busy = false
                if (wasReady) listener.onLost()
                if (closed) return
                if (probing) {
                    device?.address?.let { notSiri += it }
                    close()
                    probeNext()
                    return
                }
                state = "waiting"
                // reconnects by itself the moment a press wakes the remote
                val d = device ?: return
                g.close()
                gatt = null
                handler.postDelayed({ if (!closed && gatt == null) connect(d) }, 250)
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (g != gatt) return
            val pnp = g.getService(DEVICE_INFO)?.getCharacteristic(PNP_ID)
            val hid = g.getService(HID)
            if (hid == null) {
                if (probing) {
                    device?.address?.let { notSiri += it }
                    close(); probeNext()
                } else {
                    state = "hid-hidden"
                    listener.onProblem("Android hid the remote's buttons from the bridge (is it running as shell?)")
                }
                return
            }
            if (pnp != null) enqueue(Op.ReadChar(pnp)) else plan(g, hid)
        }

        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray, status: Int) =
            onRead(g, c, value, status)

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            if (Build.VERSION.SDK_INT < 33) @Suppress("DEPRECATION") onRead(g, c, c.value ?: ByteArray(0), status)
        }

        override fun onDescriptorRead(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int, value: ByteArray) =
            onDescRead(d, value, status)

        @Deprecated("Deprecated in Java")
        override fun onDescriptorRead(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            if (Build.VERSION.SDK_INT < 33) @Suppress("DEPRECATION") onDescRead(d, d.value ?: ByteArray(0), status)
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) Log.w(TAG, "CCCD write ${d.characteristic.uuid} failed: $status")
            next()
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) = next()

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) =
            changed(c, value)

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT < 33) @Suppress("DEPRECATION") changed(c, c.value ?: return)
        }
    }

    private fun onRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
        if (c.uuid == PNP_ID) {
            val vendor = if (value.size >= 3) (value[1].toInt() and 0xFF) or ((value[2].toInt() and 0xFF) shl 8) else -1
            if (vendor != APPLE) {
                Log.i(TAG, "${device?.address} is vendor ${"%04x".format(vendor)}, not Apple")
                device?.address?.let { notSiri += it }
                if (probing) { close(); probeNext(); return }
            }
            g.getService(HID)?.let { plan(g, it) }
        } else if (c.uuid == BATTERY_LEVEL && value.isNotEmpty()) {
            listener.onBattery(value[0].toInt() and 0xFF)
        }
        next()
    }

    private val refs = HashMap<BluetoothGattCharacteristic, IntArray>()

    private fun onDescRead(d: BluetoothGattDescriptor, value: ByteArray, status: Int) {
        if (d.uuid == REPORT_REF && value.size >= 2) {
            refs[d.characteristic] = intArrayOf(value[0].toInt() and 0xFF, value[1].toInt() and 0xFF)
        } else if (d.uuid == REPORT_REF) {
            Log.w(TAG, "report reference unreadable (status $status)")
        }
        next()
    }

    /** Reads every report's id, then subscribes. */
    private fun plan(g: BluetoothGatt, hid: android.bluetooth.BluetoothGattService) {
        refs.clear()
        reportIds.clear()
        val reports = hid.characteristics.filter { it.uuid == REPORT }
        reports.forEach { c -> c.getDescriptor(REPORT_REF)?.let { enqueue(Op.ReadDesc(it)) } }
        enqueue(Op.Run { subscribe(g, reports) })
    }

    private fun subscribe(g: BluetoothGatt, reports: List<BluetoothGattCharacteristic>) {
        if (refs.isEmpty() && reports.isNotEmpty()) {
            state = "hid-hidden"
            listener.onProblem("Android refused the remote's HID reports")
            return
        }
        val inputs = reports.filter { refs[it]?.get(1) == 1 }
        val gen = if (inputs.any { refs[it]?.get(0) == 0xFB || refs[it]?.get(0) == 0xFC }) 3 else 1
        inputs.forEach { c ->
            reportIds[c.instanceId] = refs[c]!![0]
            g.setCharacteristicNotification(c, true)
            c.getDescriptor(CCCD)?.let { enqueue(Op.WriteDesc(it, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)) }
        }
        // the unlock byte: gen 1 waits for it on its feature report, some gen 3 firmware on outputs
        reports.filter {
            val type = refs[it]?.get(1)
            (type == 2 || (gen == 1 && type == 3)) &&
                it.properties and (BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
        }.forEach { enqueue(Op.WriteChar(it, byteArrayOf(0xAF.toByte()))) }
        g.getService(BATTERY)?.getCharacteristic(BATTERY_LEVEL)?.let { b ->
            enqueue(Op.ReadChar(b))
            g.setCharacteristicNotification(b, true)
            b.getDescriptor(CCCD)?.let { enqueue(Op.WriteDesc(it, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)) }
        }
        enqueue(Op.Run {
            ready = true
            probing = false
            state = "connected"
            val d = device!!
            listener.onReady(gen, runCatching { d.name }.getOrNull() ?: "Siri Remote", d.address)
        })
    }

    private fun changed(c: BluetoothGattCharacteristic, value: ByteArray) {
        if (c.uuid == BATTERY_LEVEL) {
            if (value.isNotEmpty()) listener.onBattery(value[0].toInt() and 0xFF)
            return
        }
        val id = reportIds[c.instanceId] ?: return
        if (ready) listener.onReport(id, value)
    }

    private fun enqueue(op: Op) {
        ops.addLast(op)
        if (!busy) next()
    }

    private fun next() {
        val g = gatt ?: return
        val op = ops.removeFirstOrNull()
        if (op == null) { busy = false; return }
        busy = true
        val started = runCatching {
            when (op) {
                is Op.ReadDesc -> g.readDescriptor(op.d)
                is Op.ReadChar -> g.readCharacteristic(op.c)
                is Op.WriteDesc -> if (Build.VERSION.SDK_INT >= 33) {
                    g.writeDescriptor(op.d, op.value) == BluetoothGatt.GATT_SUCCESS
                } else {
                    @Suppress("DEPRECATION")
                    op.d.value = op.value
                    @Suppress("DEPRECATION")
                    g.writeDescriptor(op.d)
                }
                is Op.WriteChar -> {
                    val type = if (op.c.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) {
                        BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    } else {
                        BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                    }
                    if (Build.VERSION.SDK_INT >= 33) {
                        g.writeCharacteristic(op.c, op.value, type) == BluetoothGatt.GATT_SUCCESS
                    } else {
                        @Suppress("DEPRECATION")
                        op.c.writeType = type
                        @Suppress("DEPRECATION")
                        op.c.value = op.value
                        @Suppress("DEPRECATION")
                        g.writeCharacteristic(op.c)
                    }
                }
                is Op.Run -> { op.block(); false }
            }
        }.getOrElse { Log.w(TAG, "GATT op failed", it); false }
        // nothing to wait for: move on
        if (!started) handler.post { next() }
    }

    companion object {
        private const val TAG = "NeonSiri"
        const val APPLE = 0x004C
        private fun uuid16(v: Int) = UUID.fromString("%08x-0000-1000-8000-00805f9b34fb".format(v))
        val HID: UUID = uuid16(0x1812)
        val REPORT: UUID = uuid16(0x2A4D)
        val REPORT_REF: UUID = uuid16(0x2908)
        val CCCD: UUID = uuid16(0x2902)
        val DEVICE_INFO: UUID = uuid16(0x180A)
        val PNP_ID: UUID = uuid16(0x2A50)
        val BATTERY: UUID = uuid16(0x180F)
        val BATTERY_LEVEL: UUID = uuid16(0x2A19)
    }
}
