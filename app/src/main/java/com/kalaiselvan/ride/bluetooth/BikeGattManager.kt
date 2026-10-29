package com.kalaiselvan.ride.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import com.kalaiselvan.ride.logs.LogStore
import com.kalaiselvan.ride.preferences.AppPreferences
import com.kalaiselvan.ride.telemetry.TelemetryEngine
import kotlinx.coroutines.*
import java.util.Calendar
import java.util.UUID

enum class ConnectionStatus {
    DISCONNECTED, CONNECTING, CONNECTED, FAILED
}

data class RawTelemetryPacket(
    val speed: Float? = null,
    val rpm: Float? = null,
    val gear: Int? = null,
    val throttle: Float? = null,
    val leanAngle: Float? = null,
    val leanDirection: String? = null,
    val maxLeanLeft: Float? = null,
    val maxLeanRight: Float? = null,
    val wheelieAngle: Float? = null,
    val wheelieFlag: Int? = null,
    val accelG: Float? = null,
    val frontBrakePressure: Float? = null,
    val cruiseState: String? = null,
    val cruiseSetSpeed: Int? = null,
    val ecuBattery: Float? = null,
    val fuelGaugeBars: Int? = null,
    val waterTemp: Int? = null,
    val inletAirTemp: Int? = null,
    val ridingMode: String? = null,
    val powerMode: String? = null,
    val kqsStatus: String? = null,
    val ktrcLevel: String? = null,
    val odometer: Int? = null,
    val tripA: Float? = null,
    val tripB: Float? = null,
    val avgSpeed: Int? = null,
    val avgFuelMileage: Float? = null,
    val instantFuelConsumption: Float? = null,
    val ecuErrorCodes: List<String>? = null,
    val bikeModel: String? = null,
    val vin: String? = null,
    val ktrcActive: Boolean? = null,
    val shiftLampRpm: Int? = null
)

@SuppressLint("MissingPermission")
class BikeGattManager(
    private val context: Context,
    private val onConnectionChanged: (ConnectionStatus) -> Unit,
    private val onTelemetryReceived: (RawTelemetryPacket) -> Unit
) {
    private var bluetoothGatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    var lastMac: String? = null
        private set
    private var retryCount = 0
    private val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
    private val adapter get() = bluetoothManager?.adapter

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    val isConnected: Boolean
        get() = writeChar != null

    private var seqCounter: Int = 0
    private var heartbeatJob: Job? = null
    private var watchdogJob: Job? = null

    data class NavState(val mode: Int, val icon: Int, val distance: Int, val unit: Int)
    @Volatile private var activeNavState: NavState? = null

    @Volatile
    private var lastDataTimestamp: Long = System.currentTimeMillis()

    // ── Serialized Two-Lane Queue State ──
    private data class QueuedFrame(val data: ByteArray, val label: String)

    private sealed class GattOp {
        data class WriteFrame(val frame: QueuedFrame, val urgent: Boolean) : GattOp()
        data class WriteDesc(val desc: BluetoothGattDescriptor, val value: ByteArray, val label: String) : GattOp()
        data class Action(val name: String, val block: () -> Unit) : GattOp()
    }

    private val urgentQueue = ArrayDeque<QueuedFrame>()
    private val normalQueue = ArrayDeque<GattOp>()
    @Volatile private var busy: Boolean = false
    @Volatile private var waiting: Int = WAIT_NONE
    private var busyRetries: Int = 0
    private var opTimeoutJob: Job? = null
    private var directConnectTimeoutJob: Job? = null

    private var clockSynced: Boolean = false
    private var lastShiftLampStatus: Int = 0
    private var lastShiftLampLevel: Int = 0
    private var cachedInfoConfig: ByteArray? = null

    init {
        instance = this
    }

    companion object {
        const val TAG = "BikeGatt"

        val SERVICE_UUID: UUID = UUID.fromString("92faec07-c075-4b7c-a6c2-bbd1d1a150f5")
        val CONTROL_UUID: UUID = UUID.fromString("acf1b15c-10f9-4942-a32d-f9e019b95402")

        val NOTIFY_UUIDS = listOf(
            UUID.fromString("3aabbb34-eac0-40f5-9d50-3a1ee6787136"),
            UUID.fromString("02fad1bd-358e-441c-b296-fe874af38a7e"),
            UUID.fromString("5e119eba-35a7-4463-a7af-7fa40a302350")
        )

        private const val WAIT_NONE = 0
        private const val WAIT_WRITE = 1
        private const val WAIT_DESCRIPTOR = 2
        private const val WRITE_BUSY = 201 // BluetoothStatusCodes.ERROR_GATT_WRITE_REQUEST_BUSY
        private const val OP_TIMEOUT_MS = 3000L

        var instance: BikeGattManager? = null
            private set
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    directConnectTimeoutJob?.cancel()
                    directConnectTimeoutJob = null
                    LogStore.logEvent("GATT Connected! Negotiating MTU 300...")
                    onConnectionChanged(ConnectionStatus.CONNECTING)
                    retryCount = 0
                    val mtuRequested = gatt.requestMtu(300)
                    if (!mtuRequested) {
                        LogStore.logEvent("requestMtu(300) returned false, discovering services immediately...")
                        gatt.discoverServices()
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    LogStore.logEvent("GATT Disconnected.")
                    cleanUpConnection()
                    onConnectionChanged(ConnectionStatus.DISCONNECTED)
                    try {
                        gatt.close()
                    } catch (_: Exception) {}
                    bluetoothGatt = null

                    // Auto-Standby: Arm background autoConnect mode so next ignition cycle connects immediately
                    val mac = lastMac ?: AppPreferences.getInstance(context).userPreferences.value.lastBikeMac
                    if (mac.isNotBlank() && BluetoothAdapter.checkBluetoothAddress(mac)) {
                        LogStore.logEvent("Auto-Standby: Arming background GATT auto-connect for next ignition on $mac...")
                        scope.launch {
                            delay(2000)
                            connect(mac, isAutoConnect = true)
                        }
                    }
                }
            } else {
                LogStore.logEvent("GATT Status Error: $status")
                cleanUpConnection()
                try {
                    gatt.close()
                } catch (_: Exception) {}
                bluetoothGatt = null
                onConnectionChanged(ConnectionStatus.FAILED)

                val mac = lastMac ?: AppPreferences.getInstance(context).userPreferences.value.lastBikeMac
                if (mac.isNotBlank() && BluetoothAdapter.checkBluetoothAddress(mac)) {
                    val delayMs = if (status == 133) 2000L else 3000L
                    LogStore.logEvent("Auto-Standby: Re-engaging auto-connect in ${delayMs / 1000}s for $mac...")
                    scope.launch {
                        delay(delayMs)
                        connect(mac, isAutoConnect = true)
                    }
                } else {
                    LogStore.logEvent("No saved MAC. Arming background hardware BLE scanner.")
                    BackgroundBleScanner.arm(context)
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            LogStore.logEvent("MTU set to $mtu. Discovering bike services...")
            gatt.discoverServices()
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(SERVICE_UUID)
                if (service != null) {
                    writeChar = service.getCharacteristic(CONTROL_UUID)
                    LogStore.logEvent("Bike Service located. Subscribing to telemetry notifications via queue...")

                    NOTIFY_UUIDS.forEach { uuid ->
                        val char = service.getCharacteristic(uuid)
                        if (char != null) {
                            gatt.setCharacteristicNotification(char, true)
                            val desc = char.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                            if (desc != null) {
                                enqueueDesc(desc, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE, "CCCD ${uuid.toString().substring(0, 8)}")
                            }
                        }
                    }

                    // Any additional notify characteristics
                    service.characteristics.filter {
                        it.uuid !in NOTIFY_UUIDS && (it.properties and (BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_INDICATE)) != 0
                    }.forEach { char ->
                        gatt.setCharacteristicNotification(char, true)
                        val desc = char.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                        if (desc != null) {
                            enqueueDesc(desc, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE, "CCCD extra ${char.uuid.toString().substring(0, 8)}")
                        }
                    }

                    // Enqueue Handshake execution once all descriptors have been successfully written!
                    enqueueAction("StartHandshake") {
                        LogStore.logEvent("Subscribed to all telemetry streams. Executing bike handshake...")
                        onConnectionChanged(ConnectionStatus.CONNECTED)
                        runHandshake(gatt)
                    }
                } else {
                    LogStore.logEvent("Bike Service UUID not found on device.")
                }
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (gatt != bluetoothGatt || waiting != WAIT_DESCRIPTOR) return
            if (status != BluetoothGatt.GATT_SUCCESS) {
                LogStore.logEvent("Descriptor write failed, status $status")
            }
            opDone()
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (gatt != bluetoothGatt || waiting != WAIT_WRITE) return
            if (status != BluetoothGatt.GATT_SUCCESS) {
                LogStore.logEvent("Characteristic write failed, status $status")
            }
            opDone()
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            lastDataTimestamp = System.currentTimeMillis()
            val data = characteristic.value
            if (data != null && data.isNotEmpty()) {
                parseFrame(data)
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            lastDataTimestamp = System.currentTimeMillis()
            if (value.isNotEmpty()) {
                parseFrame(value)
            }
        }
    }

    // ── Queue Management ──

    @Synchronized
    private fun enqueueWrite(data: ByteArray, label: String, urgent: Boolean) {
        if (urgent) {
            val type = data[0].toInt() and 0xFF
            // Deduplicate: a newer frame of the same type replaces one still waiting in urgent lane
            val it = urgentQueue.iterator()
            while (it.hasNext()) {
                val existing = it.next().data
                val existingType = existing[0].toInt() and 0xFF
                if (existingType == type) {
                    if (type == 0x12) {
                        // Frame 0x12 category is at byte 9 (1=Call, 6=Mail): only replace matching category
                        val existingCat = if (existing.size > 9) existing[9].toInt() and 0xFF else 0
                        val newCat = if (data.size > 9) data[9].toInt() and 0xFF else 0
                        if (existingCat == newCat) {
                            it.remove()
                        }
                    } else {
                        it.remove()
                    }
                }
            }
            urgentQueue.add(QueuedFrame(data, label))
        } else {
            normalQueue.add(GattOp.WriteFrame(QueuedFrame(data, label), urgent = false))
        }
        if (!busy) next()
    }

    @Synchronized
    private fun enqueueDesc(desc: BluetoothGattDescriptor, value: ByteArray, label: String) {
        normalQueue.add(GattOp.WriteDesc(desc, value, label))
        if (!busy) next()
    }

    @Synchronized
    private fun enqueueAction(name: String, block: () -> Unit) {
        normalQueue.add(GattOp.Action(name, block))
        if (!busy) next()
    }

    @Synchronized
    private fun next() {
        val gatt = bluetoothGatt
        if (gatt == null) {
            busy = false
            waiting = WAIT_NONE
            return
        }

        // Serve urgent lane first (dash arrows, status, notifications)
        val urgentFrame = urgentQueue.removeFirstOrNull()
        if (urgentFrame != null) {
            busy = true
            executeWrite(urgentFrame, urgent = true)
            return
        }

        val normalOp = normalQueue.removeFirstOrNull()
        if (normalOp != null) {
            busy = true
            when (normalOp) {
                is GattOp.WriteFrame -> executeWrite(normalOp.frame, urgent = false)
                is GattOp.WriteDesc -> executeWriteDesc(normalOp)
                is GattOp.Action -> {
                    normalOp.block()
                    opDone()
                }
            }
            return
        }

        busy = false
        waiting = WAIT_NONE
    }

    private fun executeWrite(frame: QueuedFrame, urgent: Boolean) {
        val gatt = bluetoothGatt
        val control = writeChar ?: gatt?.getService(SERVICE_UUID)?.getCharacteristic(CONTROL_UUID)
        if (gatt == null || control == null) {
            LogStore.logEvent("Write dropped, not connected: ${frame.label}")
            opDone()
            return
        }

        waiting = WAIT_WRITE
        opTimeoutJob?.cancel()
        opTimeoutJob = scope.launch {
            delay(OP_TIMEOUT_MS)
            LogStore.logEvent("GATT write timed out (${frame.label}), continuing...")
            opDone()
        }

        val rc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(control, frame.data, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
        } else {
            @Suppress("DEPRECATION")
            control.value = frame.data
            @Suppress("DEPRECATION")
            if (gatt.writeCharacteristic(control)) BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_FAILURE
        }

        if (rc == WRITE_BUSY) {
            // Android still busy with an earlier write: keep this frame first in lane and retry shortly
            waiting = WAIT_NONE
            opTimeoutJob?.cancel()
            if (urgent) {
                urgentQueue.addFirst(frame)
            } else {
                normalQueue.addFirst(GattOp.WriteFrame(frame, false))
            }
            if (++busyRetries < 30) {
                scope.launch {
                    delay(100)
                    busy = false
                    next()
                }
            } else {
                LogStore.logEvent("Bluetooth write stayed busy too long: reconnecting")
                busyRetries = 0
                reconnect()
            }
            return
        }

        busyRetries = 0
        if (rc != BluetoothGatt.GATT_SUCCESS) {
            LogStore.logEvent("TX ${frame.label} failed with rc=$rc")
            opDone()
        } else {
            LogStore.logFrame("TX", frame.data[0].toInt() and 0xFF, frame.data.joinToString("") { "%02X".format(it) })
        }
    }

    private fun executeWriteDesc(op: GattOp.WriteDesc) {
        val gatt = bluetoothGatt
        if (gatt == null) {
            opDone()
            return
        }
        waiting = WAIT_DESCRIPTOR
        opTimeoutJob?.cancel()
        opTimeoutJob = scope.launch {
            delay(OP_TIMEOUT_MS)
            LogStore.logEvent("Descriptor write timed out (${op.label}), continuing...")
            opDone()
        }

        val rc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(op.desc, op.value)
        } else {
            @Suppress("DEPRECATION")
            op.desc.value = op.value
            @Suppress("DEPRECATION")
            if (gatt.writeDescriptor(op.desc)) BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_FAILURE
        }

        if (rc != BluetoothGatt.GATT_SUCCESS) {
            LogStore.logEvent("Write descriptor failed (${op.label}): rc=$rc")
            opDone()
        }
    }

    private fun opDone() {
        opTimeoutJob?.cancel()
        opTimeoutJob = null
        waiting = WAIT_NONE
        scope.launch {
            delay(50) // 50 ms pacing delay between BLE GATT packets for meter MCU processing
            synchronized(this@BikeGattManager) {
                busy = false
                next()
            }
        }
    }

    private fun cleanUpConnection() {
        directConnectTimeoutJob?.cancel()
        directConnectTimeoutJob = null
        heartbeatJob?.cancel()
        heartbeatJob = null
        watchdogJob?.cancel()
        watchdogJob = null
        opTimeoutJob?.cancel()
        opTimeoutJob = null
        urgentQueue.clear()
        normalQueue.clear()
        busy = false
        waiting = WAIT_NONE
        busyRetries = 0
        writeChar = null
        clockSynced = false
        cachedInfoConfig = null
    }

    private fun runHandshake(gatt: BluetoothGatt) {
        fun sendSimpleFrame(frameId: Byte, len: Byte, label: String) {
            val frame = ByteArray(15) { 0xFF.toByte() }
            frame[0] = frameId
            frame[1] = len
            frame[2] = (seqCounter++ and 0xFF).toByte()
            enqueueWrite(frame, label, urgent = false)
        }

        sendSimpleFrame(0x03.toByte(), 0x0C.toByte(), "0x03 DTCs")
        sendSimpleFrame(0x40.toByte(), 0x0C.toByte(), "0x40 InfoConfig")
        sendSimpleFrame(0x1A.toByte(), 0x0C.toByte(), "0x1A FuelUnits")
        sendSimpleFrame(0x1D.toByte(), 0x0C.toByte(), "0x1D ModelYear")
        sendSimpleFrame(0x47.toByte(), 0x0C.toByte(), "0x47 MainUnit")

        // Send Device Info frame (0x0B, Phone Model Name 35 bytes per PROTOCOL.md)
        val phoneName = android.os.Build.MODEL ?: "Android"
        enqueueWrite(buildPhoneNameFrame(phoneName), "0x0B PhoneName($phoneName)", urgent = false)

        sendSimpleFrame(0x41.toByte(), 0x0C.toByte(), "0x41 Battery/Fuel")

        sendInitial1B()
        sendInitial48()
        sendInitial1E()

        // Start Live Ride Log stream
        startRideLog()

        sendInitial08()
        sendSimpleFrame(0x45.toByte(), 0x0C.toByte(), "0x45 TripSummary")

        // Send 0x42
        val f42 = ByteArray(15) { 0xFF.toByte() }
        f42[0] = 0x42.toByte()
        f42[1] = 0x0C.toByte()
        f42[2] = (seqCounter++ and 0xFF).toByte()
        f42[3] = 0x01.toByte()
        enqueueWrite(f42, "0x42 LiveStreamAck", urgent = false)

        LogStore.logEvent("Handshake sequence queued! Live telemetry unlocked.")

        // Immediately send phone status so cluster battery icon illuminates promptly on connect
        sendPhoneStatus()

        // Immediate check: if there is an active navigation turn already waiting, send it right away
        if (com.kalaiselvan.ride.navigation.OlaNavEngine.isNavigating.value) {
            com.kalaiselvan.ride.navigation.OlaNavEngine.forceRefreshTft()
        } else {
            val initialNav = activeNavState ?: com.kalaiselvan.ride.notifications.BikeNotificationListenerService.cachedNav?.let {
                NavState(it.mode, it.icon, it.distance, it.unit).also { s -> activeNavState = s }
            }
            if (initialNav != null && initialNav.mode != 3) {
                val frame = buildNavigationPacket(initialNav.mode, initialNav.icon, initialNav.distance, initialNav.unit)
                enqueueWrite(frame, "0x14 InitialNav", urgent = true)
            }
        }

        startHeartbeat()
        startWatchdog()
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            var tick = 0
            while (isActive) {
                delay(1000)
                tick++

                // Continuous 1-second navigation refresh (1 Hz) required by motorcycle TFT to keep maneuver arrow on screen
                // Pause keep-alive while an incoming phone call is actively ringing/in-call on the dash
                if (!isCallActive) {
                    val nav = activeNavState ?: com.kalaiselvan.ride.notifications.BikeNotificationListenerService.cachedNav?.let {
                        NavState(it.mode, it.icon, it.distance, it.unit).also { s -> activeNavState = s }
                    }
                    if (nav != null && nav.mode != 3) {
                        val frame = buildNavigationPacket(nav.mode, nav.icon, nav.distance, nav.unit)
                        enqueueWrite(frame, "0x14 NavKeepAlive", urgent = true)
                    }
                }

                // 3-second periodic pulse for Phone Status (battery/charging) -> prevents cluster battery icon from timing out
                if (tick % 3 == 0) {
                    sendPhoneStatus()
                }
            }
        }
    }

    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = scope.launch {
            while (isActive) {
                delay(5000)
                val elapsed = System.currentTimeMillis() - lastDataTimestamp
                if (elapsed > 20000) {
                    LogStore.logEvent("Watchdog: No data received for ${elapsed / 1000}s. Triggering auto-recovery...")
                    watchdogJob?.cancel()
                    reconnect()
                    break
                }
            }
        }
    }

    private fun reconnect() {
        val mac = lastMac
        disconnect()
        if (mac != null) {
            scope.launch {
                delay(2000)
                connect(mac)
            }
        }
    }

    fun requestShiftLampRead() {
        if (bluetoothGatt != null && writeChar != null) {
            sendInitial1B()
            LogStore.logEvent("Manual request: reading Shift-Up Lamp from dash")
        }
    }

    private fun getInfoConfigMode(index: Int, mask: Int): Int {
        val cfg = cachedInfoConfig ?: return -1
        if (cfg.size <= index) return -1
        val b = cfg[index].toInt() and 0xFF
        var m = mask
        var shift = 0
        while (m and 1 == 0 && shift < 8) {
            m = m shr 1
            shift++
        }
        val v = (b and mask) shr shift
        return if (v in 0..1) v else -1
    }

    private fun readImuAxis(v: ByteArray, a: Int, b: Int, hi: Int, lo: Int): Float? {
        if (v.size <= maxOf(hi, lo)) return null
        if ((v[a].toInt() and 0xFF) == 0xFF && (v[b].toInt() and 0xFF) == 0xFF) return null
        val x = ((v[hi].toInt() and 0xFF) shl 8) or (v[lo].toInt() and 0xFF)
        return if (x == 0) null else (x * 4.1768f / 32768.0f) - 4.1768f
    }

    private fun parseFrame(data: ByteArray) {
        val frameId = data[0].toInt() and 0xFF
        val hex = data.joinToString("") { "%02X".format(it) }
        LogStore.logFrame("RX", frameId, hex)
        // ── Frame 0x20: ACK Response from Meter ──
        if (frameId == 0x20 && data.size >= 4) {
            val reqId = data[3].toInt() and 0xFF
            val resultCode = if (data.size >= 5) data[4].toInt() and 0xFF else 0
            val reqName = when (reqId) {
                0x14 -> "0x14 Turn-by-Turn Nav"
                0x13 -> "0x13 Meter Indication / Status"
                0x12 -> "0x12 Notification (Call/SMS)"
                0x42 -> "0x42 Live Stream Ack"
                0x08 -> "0x08 Meter Init"
                0x45 -> "0x45 Trip Summary"
                else -> "0x%02X".format(reqId)
            }
            val statusStr = if (resultCode == 0) "SUCCESS (Accepted by bike)" else "REJECTED (code 0x%02X)".format(resultCode)
            LogStore.logEvent("RX ACK 0x20 for $reqName -> $statusStr")
            return
        }

        // ── Frame 0x40: InfoConfig (Vehicle Capabilities & Scaling Config) ──
        if (frameId == 0x40 && data.size >= 15) {
            cachedInfoConfig = data.copyOf()
            LogStore.logEvent("RX 0x40 InfoConfig cached (${data.size} bytes)")
        }

        // ── Frame 0x13: Phone Status / Meter Indication Echo ──
        if (frameId == 0x13 && data.size >= 5) {
            sendPhoneStatus() // Reply to keep TFT alive for notifications
        }

        // ── Frame 0x1B: General Settings / Meter Clock Response (Bidirectional Clock Sync & Shift Lamp) ──
        if (frameId == 0x1B && data.size >= 15) {
            val isGeneralSettings = (data[5].toInt() and 0xFF) == 0x05 &&
                    ((data[6].toInt() and 0xFF) == 0x0A || (data[6].toInt() and 0xFF) == 0x4A)
            if (isGeneralSettings) {
                val shiftLampByte = data[8].toInt() and 0xFF
                lastShiftLampStatus = (shiftLampByte shr 6) and 0x03
                lastShiftLampLevel = shiftLampByte and 0x3F
                if (lastShiftLampLevel in 1..61) {
                    val shiftRpm = (lastShiftLampLevel - 1) * 250 + 3000
                    LogStore.logEvent("🏍️ Dash Shift-Up Lamp read from bike: $shiftRpm RPM (Level: $lastShiftLampLevel, Mode: $lastShiftLampStatus)")
                    com.kalaiselvan.ride.preferences.AppPreferences.getInstance(context).setShiftLampRpm(shiftRpm)
                    onTelemetryReceived(RawTelemetryPacket(shiftLampRpm = shiftRpm))
                }

                if (!clockSynced) {
                    clockSynced = true
                    val allowClockSync = com.kalaiselvan.ride.preferences.AppPreferences.getInstance(context).userPreferences.value.dashClockSync
                    if (allowClockSync) {
                        syncDashClock(lastShiftLampStatus, lastShiftLampLevel)
                    } else {
                        LogStore.logEvent("Dash clock sync skipped (disabled in Settings)")
                    }
                }
            }
        }

        // ── Frame 0x4A: High Frequency Telemetry (Speed, RPM, Gear, Throttle, Lean, G-force) ──
        if (frameId == 0x4A) {
            var speedOffset = 9
            var rpmOffset = 11
            var gearOffset = 13
            var throttleOffset = 14
            var leanOffset = 19
            var accelOffset = 17
            var wheelieOffset = 21
            var brakeOffset = 35

            if (data.size >= 90 && data[1] == 0x5C.toByte()) {
                speedOffset = 9
                rpmOffset = 11
                gearOffset = 13
                throttleOffset = 14
                accelOffset = 17
                leanOffset = 19
            } else if (data.size < 15) {
                return
            }

            // Wheel Speed with InfoConfig (0x40) scaling
            val speedMode = getInfoConfigMode(9, 0x0C)
            val raw9 = (((data[speedOffset].toInt() and 0x01) shl 8) or (data[speedOffset + 1].toInt() and 0xFF)).toFloat()
            val speedRaw16 = (((data[speedOffset].toInt() and 0xFF) shl 8) or (data[speedOffset + 1].toInt() and 0xFF)).toFloat()
            val speedScaled = speedRaw16 * 0.01220703125f
            val speed = when {
                speedMode == 1 -> raw9
                speedMode == 0 -> speedScaled
                data.size >= 90 && data[1] == 0x5C.toByte() -> speedRaw16
                speedScaled in 0.1f..320f -> speedScaled
                raw9 in 0.1f..320f -> raw9
                else -> 0f
            }

            // RPM with InfoConfig (0x40) scaling
            val rpmMode = getInfoConfigMode(9, 0x03)
            val raw15 = (((data[rpmOffset].toInt() and 0x7F) shl 8) or (data[rpmOffset + 1].toInt() and 0xFF)).toFloat()
            val rpmScaledQuarter = ((((data[rpmOffset].toInt() and 0xFF) shl 8) or (data[rpmOffset + 1].toInt() and 0xFF)) / 4).toFloat()
            val rpm = when {
                rpmMode == 1 -> if (rpmScaledQuarter <= 16000f) rpmScaledQuarter else 0f
                rpmMode == 0 -> if (raw15 <= 16000f) raw15 else 0f
                raw15 > 20000 && rpmScaledQuarter <= 15000 -> rpmScaledQuarter
                raw15 == 0f && rpmScaledQuarter > 0 -> rpmScaledQuarter
                raw15 <= 16000 -> raw15
                else -> 0f
            }

            // Gear (0=N, 1..6)
            val gearNibble = data[gearOffset].toInt() and 0x0F
            val gear = if (gearNibble == 0x0F) 0 else gearNibble

            // Throttle (0..100%) with InfoConfig (0x40) scaling
            val thrMode = getInfoConfigMode(10, 0x0C)
            val throttleRaw = data[throttleOffset].toInt() and 0xFF
            val throttle = when {
                thrMode == 1 -> (throttleRaw * 0.3921568627f).coerceIn(0f, 100f)
                thrMode == 0 && throttleRaw <= 128 -> (throttleRaw * 0.78125f).coerceIn(0f, 100f)
                else -> (throttleRaw * 0.78740157f).coerceIn(0f, 100f)
            }

            // Lean Angle & Direction with InfoConfig (0x40) scaling
            val leanMode = getInfoConfigMode(11, 0xC0)
            var lean = 0.0f
            if (data.size > leanOffset + 1) {
                val b19 = data[leanOffset].toInt() and 0xFF
                val b20 = data[leanOffset + 1].toInt() and 0xFF
                if (b19 != 0xFF || b20 != 0xFF) {
                    lean = when (leanMode) {
                        1 -> (((b19 and 0x0F) shl 8) or b20) * 0.1f
                        0 -> (((b19 and 0x07) shl 8) or b20) * 0.1953125f - 100.0f
                        else -> {
                            val leanA = (((b19 and 0x07) shl 8) or b20) * 0.1953125f - 100.0f
                            val leanB = (((b19 and 0x0F) shl 8) or b20) * 0.1f
                            if (leanA in -90.0f..90.0f) leanA else leanB
                        }
                    }
                }
            }

            // Wheelie / Stoppie Angle with InfoConfig (0x40) scaling
            val whMode = getInfoConfigMode(11, 0x0C)
            var wheelieAngle = 0.0f
            var wheelieFlag = 0
            if (data.size > wheelieOffset + 2) {
                wheelieFlag = data[wheelieOffset].toInt() and 0x03
                val b22 = data[wheelieOffset + 1].toInt() and 0xFF
                val b23 = data[wheelieOffset + 2].toInt() and 0xFF
                if (b22 != 0xFF || b23 != 0xFF) {
                    wheelieAngle = when (whMode) {
                        1 -> (((b22 and 0x0F) shl 8) or b23) * 0.1f
                        0 -> (((b22 and 0x07) shl 8) or b23) * 0.09765625f
                        else -> {
                            val angA = (((b22 and 0x07) shl 8) or b23) * 0.09765625f
                            val angB = (((b22 and 0x0F) shl 8) or b23) * 0.1f
                            if (angA in 0.0f..60.0f) angA else if (angB in 0.0f..60.0f) angB else 0.0f
                        }
                    }
                }
            }

            // Front Brake Pressure
            var brakePressure = 0.0f
            if (data.size > brakeOffset + 4) {
                val b35 = data[brakeOffset].toInt() and 0xFF
                val b36 = data[brakeOffset + 1].toInt() and 0xFF
                val b39 = data[brakeOffset + 4].toInt() and 0xFF
                if (b35 != 0xFF && b36 != 0xFF && b39 != 0xFF) {
                    brakePressure = b39 * 0.1953125f
                }
            }

            // Cruise Control (Frame 0x4A Block 85, byte 87 bits 1 & 0)
            var cruiseState: String? = null
            var cruiseSetSpeed: Int? = null
            if (data.size > 87) {
                val b85 = data[85].toInt() and 0xFF
                val b86 = data[86].toInt() and 0xFF
                // Block 85 header present if markers are not 0xFF
                if (b85 != 0xFF && b86 != 0xFF) {
                    val cByte = data[87].toInt() and 0xFF
                    if (cByte != 0xFF) {
                        val cruiseWorking = (cByte and 0x02) != 0
                        val cruiseOn = (cByte and 0x01) != 0
                        cruiseState = when {
                            cruiseWorking -> "ACTIVE"
                            cruiseOn -> "READY"
                            else -> "OFF"
                        }
                        if (cruiseWorking) {
                            cruiseSetSpeed = if (speed > 0f) speed.toInt() else null
                        }
                    }
                }
            }

            val absLean = kotlin.math.abs(lean)
            val leanDir = if (lean < -1.0f) "LEFT" else if (lean > 1.0f) "RIGHT" else "CENTER"

            onTelemetryReceived(
                RawTelemetryPacket(
                    speed = speed,
                    rpm = rpm,
                    gear = gear,
                    throttle = throttle,
                    leanAngle = absLean,
                    leanDirection = leanDir,
                    maxLeanLeft = if (lean < -1.0f) absLean else null,
                    maxLeanRight = if (lean > 1.0f) lean else null,
                    wheelieAngle = wheelieAngle,
                    wheelieFlag = wheelieFlag,
                    frontBrakePressure = brakePressure,
                    cruiseState = cruiseState,
                    cruiseSetSpeed = cruiseSetSpeed
                )
            )
            return
        }

        // ── Frame 0x41: 12V ECU Battery & Fuel ──
        if (frameId == 0x41 && data.size > 14) {
            val battRaw = data[14].toInt() and 0xFF
            val ecuBattery = if (battRaw != 0xFF && battRaw > 0) (battRaw / 12.23f) else null
            val fuelGauge = if (data.size > 30) (data[30].toInt() and 0x0F) else null

            onTelemetryReceived(
                RawTelemetryPacket(
                    ecuBattery = ecuBattery,
                    fuelGaugeBars = fuelGauge
                )
            )
        }

        // ── Frame 0x03: Model & VIN Information (ASCII chunks 02 71 / 02 72 / 02 73) ──
        if (frameId == 0x03) {
            val parts = arrayOfNulls<String>(3)
            var i = 5
            while (i + 1 < data.size) {
                val marker = data[i].toInt() and 0xFF
                val tag = data[i + 1].toInt() and 0xFF
                if (marker == 0x02 && tag in 0x71..0x73) {
                    val sb = StringBuilder()
                    var k = i + 2
                    while (k < i + 10 && k < data.size) {
                        val c = data[k].toInt() and 0xFF
                        if (c == 0xFF || c == 0) break
                        if (c in 0x20..0x7E) sb.append(c.toChar())
                        k++
                    }
                    parts[tag - 0x71] = sb.toString().trim()
                }
                i += 10
            }
            val rawCode = if (!parts[0].isNullOrBlank()) parts[0] else null
            val vin = if (rawCode != null && !parts[1].isNullOrBlank()) {
                (parts[0] ?: "") + (parts[1] ?: "") + (parts[2] ?: "")
            } else null
            val model = if (!rawCode.isNullOrBlank() || !vin.isNullOrBlank()) {
                BikeModelDecoder.decode(rawCode, vin).ifBlank { rawCode }
            } else null

            if (model != null) {
                LogStore.logEvent("🏍️ Bike Model Identified: $model (Raw: ${rawCode ?: "N/A"}, VIN: ${vin ?: "N/A"})")
            }
            onTelemetryReceived(RawTelemetryPacket(bikeModel = model, vin = vin))
        }

        // ── Frame 0x48: Rider Settings (Riding Mode, KTRC, KQS, Power Mode) ──
        if (frameId == 0x48) {
            val bModeKtrc: Int? = when {
                data.size >= 20 && (data[18].toInt() and 0xFF) != 0xFF -> data[18].toInt() and 0xFF
                data.size in 10..19 && (data[8].toInt() and 0xFF) != 0xFF -> data[8].toInt() and 0xFF
                else -> null
            }
            val bKqsPower: Int? = when {
                data.size >= 20 && (data[19].toInt() and 0xFF) != 0xFF -> data[19].toInt() and 0xFF
                data.size in 10..19 && (data[9].toInt() and 0xFF) != 0xFF -> data[9].toInt() and 0xFF
                else -> null
            }

            var modeStr: String? = null
            var ktrcStr: String? = null
            var powerStr: String? = null
            var kqsStr: String? = null

            if (bModeKtrc != null) {
                val rawMode = bModeKtrc and 0x07
                modeStr = when (rawMode) {
                    1 -> "SPORT"
                    2 -> "ROAD"
                    3 -> "RAIN"
                    4 -> "RIDER"
                    5 -> "RIDER 2"
                    6 -> "RIDER 3"
                    7 -> "RIDER 4"
                    else -> null
                }

                val rawKtrc = (bModeKtrc and 0x78) shr 3
                ktrcStr = when (rawKtrc) {
                    0, 10 -> "OFF"
                    in 1..5 -> rawKtrc.toString()
                    else -> null
                }
            }

            if (bKqsPower != null) {
                val rawPower = bKqsPower and 0x03
                powerStr = when (rawPower) {
                    1 -> "FULL"
                    2 -> "MIDDLE"
                    3 -> "LOW"
                    else -> null
                }

                val rawKqs = (bKqsPower and 0x60) shr 5
                kqsStr = when (rawKqs) {
                    1 -> "OFF"
                    2 -> "ON"
                    else -> null
                }
            }

            LogStore.logEvent("RX 0x48 VehicleSettings: mode=$modeStr, ktrc=$ktrcStr, power=$powerStr, kqs=$kqsStr (bModeKtrc=0x${bModeKtrc?.let { "%02X".format(it) } ?: "null"})")
            if (modeStr != null || ktrcStr != null || powerStr != null || kqsStr != null) {
                onTelemetryReceived(
                    RawTelemetryPacket(
                        ktrcLevel = ktrcStr,
                        ridingMode = modeStr,
                        kqsStatus = kqsStr,
                        powerMode = powerStr
                    )
                )
            }
        }

        // ── Frame 0x4B: Suspension & IMU (45° Physical Tilt Corrected) ──
        if (frameId == 0x4B && data.size >= 43) {
            val ayG = readImuAxis(data, 15, 16, 22, 21) // Y axis
            val axG = readImuAxis(data, 25, 26, 32, 31) // X axis
            val azG = readImuAxis(data, 35, 36, 42, 41) // Z axis (lateral)

            var longitudinalG: Float? = null

            if (axG != null && ayG != null) {
                val sin45 = 0.70710678f
                // Bike 6-axis IMU is mounted at 45° about lateral axis
                val rawLongG = (axG - ayG) * sin45
                // Deadband to ensure stationary reading is strictly 0.00 G
                longitudinalG = if (kotlin.math.abs(rawLongG) < 0.05f) 0.0f else rawLongG
            }

            // Wheel speed fallback from block 45
            var frontKmh: Float? = null
            if (data.size > 48) {
                val blk5Ok = !(data[45].toInt() == -1 && data[46].toInt() == -1)
                if (blk5Ok) {
                    val frontRaw = ((data[47].toInt() and 0xFF) shl 8) or (data[48].toInt() and 0xFF)
                    if (frontRaw != 0xFFFF) {
                        val spd = (frontRaw * 400.0f) / 32768.0f
                        if (spd in 0.1f..320f) {
                            frontKmh = spd
                        }
                    }
                }
            }

            // Frame 0x4B byte 51 carries TCS intervention levels (tcs_level_hb & lb)
            var tcsActiveFromImu: Boolean? = null
            if (data.size >= 52) {
                val tcsByte = data[51].toInt() and 0xFF
                val tcsHb = (tcsByte shr 5) and 0x07
                val tcsLb = (tcsByte shr 2) and 0x07
                tcsActiveFromImu = (tcsHb > 0 || tcsLb > 0)
            }

            if (longitudinalG != null || frontKmh != null || tcsActiveFromImu != null) {
                onTelemetryReceived(
                    RawTelemetryPacket(
                        speed = frontKmh,
                        accelG = longitudinalG, // 0.00 G stationary! Positive = accelerating, Negative = braking
                        ktrcActive = tcsActiveFromImu
                    )
                )
            }
        }

        // ── Frame 0x45: Riding Log Extension (Coolant Temp, Intake Air Temp, Instant Fuel) ──
        if (frameId == 0x45 && data.size >= 21) {
            val b17 = data[17].toInt() and 0xFF
            val b20 = data[20].toInt() and 0xFF
            // Temperatures per motorcycle cluster spec: water b17 - 40, intake b20 - 40
            // 0xFF and 0x00 indicate disconnected / absent sensors (-40°C is bottom of scale)
            val waterTemp = if (b17 != 0xFF && b17 != 0x00 && b17 in 41..190) b17 - 40 else null
            val airTemp = if (b20 != 0xFF && b20 != 0x00 && b20 in 41..120) b20 - 40 else null

            var instFuel: Float? = null
            if (data.size >= 29) {
                val b27 = data[27].toInt() and 0xFF
                val b28 = data[28].toInt() and 0xFF
                if (b27 != 0xFF && b28 != 0xFF) {
                    val rawFuel = ((b27 and 0x03) shl 8) or b28
                    if (rawFuel in 1..999) {
                        instFuel = rawFuel * 0.1f
                    }
                }
            }

            var odoLong: Int? = null
            var fuelBarsLong: Int? = null
            var avgMilLong: Float? = null
            var battLong: Float? = null

            if (data.size > 59) {
                val b57 = data[57].toInt() and 0xFF
                val b58 = data[58].toInt() and 0xFF
                val b59 = data[59].toInt() and 0xFF
                if (b57 != 0xFF && b58 != 0xFF && b59 != 0xFF) {
                    odoLong = ((b57 and 0x0F) shl 16) or (b58 shl 8) or b59
                }
            }
            if (data.size > 60) {
                val b60 = data[60].toInt() and 0x0F
                if (b60 in 0..6) fuelBarsLong = b60
            }
            if (data.size > 63) {
                val b62 = data[62].toInt() and 0xFF
                val b63 = data[63].toInt() and 0xFF
                if (b62 != 0xFF && b63 != 0xFF) {
                    avgMilLong = (((b62 and 0x03) shl 8) or b63) * 0.1f
                }
            }
            if (data.size > 64) {
                val b64 = data[64].toInt() and 0xFF
                if (b64 != 0xFF && b64 > 0) battLong = b64 * 0.1f
            }

            if (waterTemp != null || airTemp != null || instFuel != null || odoLong != null || fuelBarsLong != null || avgMilLong != null || battLong != null) {
                onTelemetryReceived(
                    RawTelemetryPacket(
                        waterTemp = waterTemp,
                        inletAirTemp = airTemp,
                        instantFuelConsumption = instFuel,
                        odometer = odoLong,
                        fuelGaugeBars = fuelBarsLong,
                        avgFuelMileage = avgMilLong,
                        ecuBattery = battLong
                    )
                )
            }
        }

        // ── TLV Scanner (0x20, 0x40, 0x41, 0x45, 0x47) ──
        if (frameId == 0x20 || frameId == 0x40 || frameId == 0x41 || frameId == 0x45 || frameId == 0x47) {
            var i = 5
            while (i < data.size - 2) {
                if (data[i].toInt() == 0x05) {
                    val tag = data[i + 1].toInt() and 0xFF
                    val tagPos = i

                    when (tag) {
                        0x11 -> { // RPM, Speed, Gear, Fuel
                            if (tagPos + 5 < data.size) {
                                val b2 = data[tagPos + 2].toInt() and 0xFF
                                val b3 = data[tagPos + 3].toInt() and 0xFF
                                val b4 = data[tagPos + 4].toInt() and 0xFF
                                val b5 = data[tagPos + 5].toInt() and 0xFF

                                val rpmRaw = ((b2 shl 8) or b3) and 0x7FFF
                                val rpm = if (b2 < 0xF0 && rpmRaw > 0 && rpmRaw < 0x7FFF) rpmRaw.toFloat() else 0f
                                val speed = if (b4 !in setOf(0x00, 0xC4, 0xFE, 0xFF) && b4 <= 299) b4.toFloat() else 0f
                                val gearNibble = b5 and 0x0F
                                val gear = if (gearNibble == 0x0F) 0 else gearNibble
                                val fuelNibble = (b5 and 0xF0) shr 4
                                val fuel = if (fuelNibble > 6) 0 else fuelNibble

                                onTelemetryReceived(
                                    RawTelemetryPacket(
                                        rpm = rpm,
                                        speed = speed,
                                        gear = gear,
                                        fuelGaugeBars = fuel
                                    )
                                )
                            }
                            i += 6
                        }

                        0x17 -> { // Coolant Water Temp / Inlet Air Temp
                            if (tagPos + 5 < data.size) {
                                val wRaw = data[tagPos + 2].toInt() and 0xFF
                                val iRaw = data[tagPos + 5].toInt() and 0xFF
                                val wTemp = if (wRaw != 0xFF && wRaw != 0x00) wRaw - 39 else null
                                val iTemp = if (iRaw != 0xFF && iRaw != 0x00) iRaw - 50 else null

                                onTelemetryReceived(
                                    RawTelemetryPacket(
                                        waterTemp = wTemp,
                                        inletAirTemp = iTemp
                                    )
                                )
                            }
                            i += 6
                        }

                        0x32 -> { // Peak Lean Angles
                            if (tagPos + 3 < data.size) {
                                val leftRaw = data[tagPos + 2].toInt() and 0xFF
                                val rightRaw = data[tagPos + 3].toInt() and 0xFF
                                val left = if (leftRaw in setOf(0x3F, 0xFF, 0xD3)) 0f else leftRaw.toFloat()
                                val right = if (rightRaw in setOf(0x3F, 0xFF, 0xD3)) 0f else rightRaw.toFloat()

                                onTelemetryReceived(
                                    RawTelemetryPacket(
                                        maxLeanLeft = if (left > 0) left else null,
                                        maxLeanRight = if (right > 0) right else null
                                    )
                                )
                            }
                            i += 4
                        }

                        0x34 -> { // Odometer + Avg Fuel Mileage
                            if (tagPos + 9 < data.size) {
                                val o1 = data[tagPos + 3].toInt() and 0xFF
                                val o2 = data[tagPos + 4].toInt() and 0xFF
                                val odo = (o1 shl 8) or o2

                                val avgRaw = data[tagPos + 8].toInt() and 0xFF
                                val instRaw = data[tagPos + 9].toInt() and 0xFF
                                val avgFuel = if (avgRaw != 0xFF && avgRaw != 0) avgRaw * 0.1f else 0.0f
                                val instFuel = if (instRaw != 0xFF && instRaw != 0) instRaw * 0.1f else 0.0f

                                onTelemetryReceived(
                                    RawTelemetryPacket(
                                        odometer = odo,
                                        avgFuelMileage = avgFuel,
                                        instantFuelConsumption = instFuel
                                    )
                                )
                            }
                            i += 10
                        }

                        0x35 -> { // Trip A, Trip B, Avg Speed
                            if (tagPos + 9 < data.size) {
                                val ta1 = data[tagPos + 3].toInt() and 0xFF
                                val ta2 = data[tagPos + 4].toInt() and 0xFF
                                val tripA = ((ta1 shl 8) or ta2) * 0.1f

                                val tb1 = data[tagPos + 6].toInt() and 0xFF
                                val tb2 = data[tagPos + 7].toInt() and 0xFF
                                val tripB = ((tb1 shl 8) or tb2) * 0.1f

                                val avgSpeedRaw = data[tagPos + 8].toInt() and 0xFF
                                val avgSpeed = if (avgSpeedRaw != 0xFF) avgSpeedRaw else 0

                                onTelemetryReceived(
                                    RawTelemetryPacket(
                                        tripA = tripA,
                                        tripB = tripB,
                                        avgSpeed = avgSpeed
                                    )
                                )
                            }
                            i += 10
                        }

                        else -> i += 2
                    }
                } else {
                    i++
                }
            }
        }
    }

    private fun sendInitial1B() {
        val frame = ByteArray(15) { 0xFF.toByte() }
        frame[0] = 0x1B.toByte()
        frame[1] = 0x0C.toByte()
        frame[2] = 0x00.toByte() // Byte 2 is always 0x00 for 1B General Settings read
        frame[5] = 0x05.toByte()
        frame[6] = 0x0A.toByte()
        frame[7] = 0x00.toByte()
        enqueueWrite(frame, "0x1B GeneralSettingsRead", urgent = false)
    }

    private fun sendInitial48() {
        val frame = ByteArray(45) { 0xFF.toByte() }
        frame[0] = 0x48.toByte()
        frame[1] = 0x2A.toByte()
        frame[2] = 0x00.toByte() // Byte 2 is ALWAYS 0x00 for 0x48 Vehicle Settings read per bike protocol
        frame[5] = 0x05.toByte()
        frame[6] = 0x09.toByte()
        frame[7] = 0x00.toByte()
        enqueueWrite(frame, "0x48 VehicleSettingsRead", urgent = false)
    }

    private fun sendInitial1E() {
        val frame = ByteArray(45) { 0xFF.toByte() }
        frame[0] = 0x1E.toByte()
        frame[1] = 0x2A.toByte()
        frame[2] = 0x01.toByte() // Byte 2 is 0x01 for 0x1E Service Indicator read per bike protocol
        frame[5] = 0x05.toByte()
        frame[6] = 0x0C.toByte()
        frame[7] = 0x00.toByte()
        frame[15] = 0x05.toByte()
        frame[16] = 0x0D.toByte()
        frame[17] = 0x00.toByte()
        frame[25] = 0x05.toByte()
        frame[26] = 0x0E.toByte()
        frame[27] = 0x00.toByte()
        enqueueWrite(frame, "0x1E MaintenanceRead", urgent = false)
    }

    private fun sendInitial08() {
        // 0x08 meter indication init per bike protocol
        val frame = byteArrayOf(
            0x08.toByte(), 0x0C.toByte(), 0x00.toByte(), 0xFF.toByte(), 0xFF.toByte(),
            0x0A.toByte(), 0x08.toByte(), 0x01.toByte(), 0x78.toByte(), 0x03.toByte(),
            0xE8.toByte(), 0x00.toByte(), 0xC8.toByte(), 0x00.toByte(), 0x64.toByte()
        )
        enqueueWrite(frame, "0x08 MeterIndicationInit", urgent = false)
    }

    private fun buildPhoneNameFrame(name: String): ByteArray {
        val utf8 = name.toByteArray(Charsets.UTF_8).let { if (it.size > 24) it.copyOf(24) else it }
        val b = ByteArray(35)
        b[0] = 0x0B.toByte() // 11
        b[1] = 0x20.toByte() // 32
        b[2] = 0x00.toByte()
        b[3] = 0xFF.toByte()
        b[4] = 0xFF.toByte()
        for (part in 0 until 3) {
            val offset = part * 8
            if (offset < utf8.size) {
                b[5 + part * 10] = 0x05.toByte()
                b[6 + part * 10] = (part + 1).toByte()
                val len = Math.min(8, utf8.size - offset)
                System.arraycopy(utf8, offset, b, 7 + part * 10, len)
            }
        }
        return b
    }

    fun startRideLog() {
        val calendar = Calendar.getInstance()
        val day = calendar.get(Calendar.DAY_OF_MONTH).toByte()
        val month = (calendar.get(Calendar.MONTH) + 1).toByte()
        val year = (calendar.get(Calendar.YEAR) % 100).toByte()
        val hour = calendar.get(Calendar.HOUR_OF_DAY).toByte()
        val minute = calendar.get(Calendar.MINUTE).toByte()
        val second = calendar.get(Calendar.SECOND).toByte()

        val frame = ByteArray(15)
        frame[0] = 0x1B.toByte()
        frame[1] = 0x0C.toByte()
        frame[2] = (seqCounter++ and 0xFF).toByte()
        frame[3] = 0xFF.toByte()
        frame[4] = 0xFF.toByte()
        frame[5] = 0x05.toByte()
        frame[6] = 0x4A.toByte()
        frame[7] = 0x01.toByte() // Start Live Stream
        frame[8] = 0x45.toByte()
        frame[9] = day
        frame[10] = month
        frame[11] = year
        frame[12] = hour
        frame[13] = minute
        frame[14] = second

        enqueueWrite(frame, "0x1B StartRideLog", urgent = false)
        LogStore.logEvent("TFT Start Ride Log Queued: %02d/%02d/%02d %02d:%02d:%02d".format(day, month, year, hour, minute, second))
    }

    fun stopRideLog() {
        val calendar = Calendar.getInstance()
        val day = calendar.get(Calendar.DAY_OF_MONTH).toByte()
        val month = (calendar.get(Calendar.MONTH) + 1).toByte()
        val year = (calendar.get(Calendar.YEAR) % 100).toByte()
        val hour = calendar.get(Calendar.HOUR_OF_DAY).toByte()
        val minute = calendar.get(Calendar.MINUTE).toByte()
        val second = calendar.get(Calendar.SECOND).toByte()

        val frame = ByteArray(15)
        frame[0] = 0x1B.toByte()
        frame[1] = 0x0C.toByte()
        frame[2] = (seqCounter++ and 0xFF).toByte()
        frame[3] = 0xFF.toByte()
        frame[4] = 0xFF.toByte()
        frame[5] = 0x05.toByte()
        frame[6] = 0x4A.toByte()
        frame[7] = 0x00.toByte() // Stop
        frame[8] = 0x45.toByte()
        frame[9] = day
        frame[10] = month
        frame[11] = year
        frame[12] = hour
        frame[13] = minute
        frame[14] = second

        enqueueWrite(frame, "0x1B StopRideLog", urgent = false)
        LogStore.logEvent("TFT Stop Ride Log Queued")
    }

    /**
     * Bidirectional Dash Clock Sync (Frame 0x1B)
     */
    fun syncDashClock(
        shiftLampStatus: Int = lastShiftLampStatus,
        shiftLampLevel: Int = lastShiftLampLevel,
        force: Boolean = false
    ) {
        val allowSync = com.kalaiselvan.ride.preferences.AppPreferences.getInstance(context).userPreferences.value.dashClockSync
        if (!force && !allowSync) {
            LogStore.logEvent("Dash Clock Sync skipped (disabled in Settings)")
            return
        }

        val c = Calendar.getInstance()
        val day = c.get(Calendar.DAY_OF_MONTH)
        val month = c.get(Calendar.MONTH) + 1
        val year = c.get(Calendar.YEAR)
        val hour = c.get(Calendar.HOUR_OF_DAY)
        val minute = c.get(Calendar.MINUTE)
        val second = c.get(Calendar.SECOND)

        val frame = ByteArray(15) { 0xFF.toByte() }
        frame[0] = 0x1B.toByte()
        frame[1] = 0x0C.toByte()
        frame[2] = 0x00.toByte()
        frame[5] = 0x05.toByte()
        frame[6] = 0x4A.toByte()
        frame[7] = 0x01.toByte()
        frame[8] = (((shiftLampStatus and 3) shl 6) or (shiftLampLevel and 0x3F)).toByte()
        frame[9] = (day and 0x3F).toByte()
        frame[10] = (month and 0x0F).toByte()
        frame[11] = ((year - 2000) and 0xFF).toByte()
        frame[12] = (hour and 0x1F).toByte()
        frame[13] = (minute and 0x3F).toByte()
        frame[14] = (second and 0x3F).toByte()

        enqueueWrite(frame, "0x1B DashClockSync", urgent = false)
        LogStore.logEvent("Dash Clock Sync sent: %02d/%02d/%04d %02d:%02d:%02d".format(day, month, year, hour, minute, second))
    }

    fun sendPhoneStatus(forcedBatteryPct: Int? = null, forcedCharging: Boolean? = null) {
        val prefs = com.kalaiselvan.ride.preferences.AppPreferences.getInstance(context).userPreferences.value
        if (!prefs.phoneBatteryMirroring && forcedBatteryPct == null) {
            return
        }
        val batteryStatus: Intent? = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level: Int = forcedBatteryPct ?: batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: 100
        val scale: Int = if (forcedBatteryPct != null) 100 else (batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: 100)
        val batteryPct = if (level != -1 && scale != -1) (level * 100 / scale.toFloat()).toInt() else 100
        val isCharging = forcedCharging ?: (batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) == BatteryManager.BATTERY_STATUS_CHARGING)

        var batteryLevel = when {
            batteryPct <= 10 -> 0
            batteryPct <= 30 -> 1
            batteryPct <= 70 -> 2
            else -> 3
        }
        if (isCharging) batteryLevel = 7 + batteryLevel // Charging states 7..10 per PROTOCOL.md

        // Frame 0x13: Phone Status / Meter Indication (15 bytes, block 0)
        val frame = ByteArray(15) { 0xFF.toByte() }
        frame[0] = 0x13.toByte()
        frame[1] = 0x0C.toByte()
        frame[2] = 0x00.toByte() // Byte 2 is ALWAYS 0x00 for Frame 0x13 per bike protocol
        frame[5] = 0x05.toByte()
        frame[6] = 0x00.toByte()
        frame[7] = ((batteryLevel and 0x0F) shl 4).toByte()
        frame[8] = 0x00.toByte()

        enqueueWrite(frame, "0x13 PhoneStatus(batt=$batteryPct%)", urgent = true)
    }

    /**
     * Send Call or SMS notification mirror packet (Frame 0x12)
     * @param type 1 for Call, 6 for SMS / Mail
     * @param status 0 for Added/Active, 2 for Removed/Clear
     */
    var isCallActive: Boolean = false
        private set

    fun sendNotification(type: Int, status: Int) {
        if (type == 1) { // 1 = Call
            isCallActive = (status == 0) // 0 = added/active, 2 = removed/clear
            if (status == 2) {
                // Call ended: restore navigation to TFT immediately
                scope.launch {
                    delay(300) // Brief delay to let cluster finish clearing call popup
                    if (com.kalaiselvan.ride.navigation.OlaNavEngine.isNavigating.value) {
                        com.kalaiselvan.ride.navigation.OlaNavEngine.forceRefreshTft()
                    } else {
                        val nav = activeNavState ?: com.kalaiselvan.ride.notifications.BikeNotificationListenerService.cachedNav?.let {
                            NavState(it.mode, it.icon, it.distance, it.unit).also { s -> activeNavState = s }
                        }
                        if (nav != null && nav.mode != 3) {
                            sendNavigationData(nav.mode, nav.icon, nav.distance, nav.unit)
                        }
                    }
                }
            }
        }

        // Frame 0x12 (15 bytes), per bike protocol / DashAlerts:
        // 12 08 00 FF FF 0A 12 <status: 0=add, 2=remove> FF <category: 1=call, 6=mail> <state: 1=on, 0=off> 00 00 00 00
        val frame = ByteArray(15)
        frame[0] = 0x12.toByte()
        frame[1] = 0x08.toByte()
        frame[2] = 0x00.toByte()
        frame[3] = 0xFF.toByte()
        frame[4] = 0xFF.toByte()
        frame[5] = 0x0A.toByte()
        frame[6] = 0x12.toByte()
        frame[7] = status.toByte()
        frame[8] = 0xFF.toByte()
        frame[9] = type.toByte()
        frame[10] = (if (status == 0) 1 else 0).toByte()

        enqueueWrite(frame, "0x12 Notification(type=$type, status=$status)", urgent = true)
        LogStore.logEvent("TFT Notification Queued: type=$type, status=$status")
    }

    /**
     * Send Turn-by-Turn Navigation data to the TFT (Frame 0x14, 35 bytes)
     * @param mode 0: Under Navigation, 1: Under Routing, 2: Completed, 3: Clear / No Navigation
     * @param icon Icon ID from bike protocol (0-55)
     * @param distance Distance value in meters (or feet if imperial)
     * @param unit 0: km, 1: m, 2: mile, 3: ft, 4: yd, 15: none
     * @param instruction Optional turn-by-turn guidance instruction text
     */
    fun sendNavigationData(mode: Int, icon: Int, distance: Int, unit: Int = 1, instruction: String = "") {
        if (mode == 3) {
            activeNavState = null
            // Remove any pending 0x14 navigation frames so clear is immediate
            val itUrgent = urgentQueue.iterator()
            while (itUrgent.hasNext()) {
                if ((itUrgent.next().data[0].toInt() and 0xFF) == 0x14) {
                    itUrgent.remove()
                }
            }
            val clearFrame = buildNavigationPacket(3, 0xFF, 0, 0x0F)
            enqueueWrite(clearFrame, "0x14 NavClear", urgent = true)
            LogStore.logEvent("TFT Navigation Cleared (Frame 0x14 Mode 3)")
            TelemetryEngine.getInstance(context).updateNavigationState(isActive = false, instruction = "", distanceMeters = 0, iconId = 0)
            return
        }

        activeNavState = NavState(mode, icon, distance, unit)
        val frame = buildNavigationPacket(mode, icon, distance, unit)
        enqueueWrite(frame, "0x14 Nav(mode=$mode, icon=$icon, dist=$distance, unit=$unit)", urgent = true)
        LogStore.logEvent("TFT Navigation Queued: mode=$mode, icon=$icon, dist=$distance, unit=$unit")
        val effectiveInstruction = instruction.ifBlank {
            if (icon == 43) "Destination Arrived" else "Turn in ${distance}m"
        }
        TelemetryEngine.getInstance(context).updateNavigationState(
            isActive = true,
            instruction = effectiveInstruction,
            distanceMeters = distance,
            iconId = icon
        )
    }

    private fun buildNavigationPacket(mode: Int, icon: Int, distance: Int, unit: Int): ByteArray {
        val roundedDist = if (distance >= 10) (distance / 10) * 10 else distance
        val d = roundedDist.coerceIn(0, 0xFFFFFF)
        val frame = ByteArray(35) { 0xFF.toByte() }
        frame[0] = 0x14.toByte() // 20: NAVI_CONTROL_POINT
        frame[1] = 0x08.toByte()
        frame[2] = 0x00.toByte() // Byte 2 is ALWAYS 0x00 for Frame 0x14 per bike protocol
        frame[5] = 0x05.toByte()
        frame[6] = 0x70.toByte()
        frame[7] = ((mode and 0x0F) shl 4).toByte()
        frame[13] = ((unit and 0x0F) shl 4).toByte()
        frame[15] = 0x05.toByte()
        frame[16] = 0x71.toByte()
        frame[17] = (if (mode == 3) 0xFF else icon).toByte()
        frame[18] = ((d ushr 16) and 0xFF).toByte()
        frame[19] = ((d ushr 8) and 0xFF).toByte()
        frame[20] = (d and 0xFF).toByte()
        return frame
    }

    fun connect(macAddress: String, isAutoConnect: Boolean = true, isDirect: Boolean = false) {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            scope.launch(Dispatchers.Main) {
                connect(macAddress, isAutoConnect, isDirect)
            }
            return
        }
        if (lastMac != macAddress) {
            retryCount = 0
            lastMac = macAddress
        }
        val device = adapter?.getRemoteDevice(macAddress)
        if (device == null) {
            LogStore.logEvent("Device not found for MAC: $macAddress")
            onConnectionChanged(ConnectionStatus.FAILED)
            return
        }
        directConnectTimeoutJob?.cancel()
        directConnectTimeoutJob = null
        try {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
        } catch (_: Exception) {}
        bluetoothGatt = null

        val effectiveAutoConnect = if (isDirect) false else isAutoConnect
        LogStore.logEvent("Connecting GATT to $macAddress (autoConnect=$effectiveAutoConnect, isDirect=$isDirect)...")
        onConnectionChanged(ConnectionStatus.CONNECTING)
        bluetoothGatt = device.connectGatt(context, effectiveAutoConnect, gattCallback, BluetoothDevice.TRANSPORT_LE)

        // If direct connect was requested, enforce a 12-second timeout window
        if (!effectiveAutoConnect || isDirect) {
            directConnectTimeoutJob = scope.launch {
                delay(12000)
                if (bluetoothGatt != null && !isConnected) {
                    LogStore.logEvent("Direct connect timed out after 12s (bike may be OFF). Falling back to background standby autoConnect...")
                    try {
                        bluetoothGatt?.disconnect()
                        bluetoothGatt?.close()
                    } catch (_: Exception) {}
                    bluetoothGatt = null
                    onConnectionChanged(ConnectionStatus.DISCONNECTED)
                    connect(macAddress, isAutoConnect = true, isDirect = false)
                    BackgroundBleScanner.arm(context)
                }
            }
        }
    }

    fun onBluetoothDisabled() {
        LogStore.logEvent("BikeGattManager: Bluetooth disabled. Tearing down GATT link.")
        directConnectTimeoutJob?.cancel()
        directConnectTimeoutJob = null
        cleanUpConnection()
        try {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
        } catch (_: Exception) {}
        bluetoothGatt = null
        onConnectionChanged(ConnectionStatus.DISCONNECTED)
    }

    fun testNavigationFrame(icon: Int = 2, distance: Int = 250) {
        val testInstruction = if (icon == 43) "Destination Arrived" else "In $distance m, turn right"
        LogStore.logEvent("🧪 Manual TFT Navigation Test: dispatching Frame 0x14 (icon=$icon, dist=${distance}m, text='$testInstruction')")
        sendNavigationData(mode = 0, icon = icon, distance = distance, unit = 1, instruction = testInstruction)
    }

    fun clearNavigationFrame() {
        LogStore.logEvent("🧪 Manual TFT Navigation Clear: dispatching Frame 0x14 Mode 3")
        sendNavigationData(mode = 3, icon = 0xFF, distance = 0, unit = 0x0F)
    }

    fun disconnect() {
        directConnectTimeoutJob?.cancel()
        directConnectTimeoutJob = null
        lastMac = null
        retryCount = 0
        heartbeatJob?.cancel()
        heartbeatJob = null
        watchdogJob?.cancel()
        watchdogJob = null
        activeNavState = null
        try {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
        } catch (_: Exception) {}
        bluetoothGatt = null
        onConnectionChanged(ConnectionStatus.DISCONNECTED)
        LogStore.logEvent("Disconnected cleanly.")
    }
}
