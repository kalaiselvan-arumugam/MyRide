package com.kalaiselvan.ride.bluetooth

import android.annotation.SuppressLint
import android.app.Activity
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothLeDeviceFilter
import android.companion.CompanionDeviceManager
import android.companion.CompanionDeviceService
import android.companion.DevicePresenceEvent
import android.companion.ObservingDevicePresenceRequest
import android.content.Context
import android.content.IntentSender
import android.os.Build
import androidx.annotation.RequiresApi
import com.kalaiselvan.ride.logs.LogStore
import com.kalaiselvan.ride.telemetry.TelemetryEngine
import java.util.regex.Pattern

/**
 * CompanionDeviceService for automatic zero-battery background connection.
 * When the motorcycle ignition is turned on, the bike starts advertising its BLE service.
 * Android OS CompanionDeviceManager detects this advertisement in hardware and wakes up
 * this service even when the phone is locked in the rider's pocket.
 */
@RequiresApi(Build.VERSION_CODES.S)
@SuppressLint("MissingPermission")
class BikePresenceService : CompanionDeviceService() {

    override fun onDevicePresenceEvent(event: DevicePresenceEvent) {
        val eventType = event.event
        LogStore.logEvent("CompanionDeviceService: Device presence event $eventType")
        if (eventType == DevicePresenceEvent.EVENT_BLE_APPEARED ||
            eventType == DevicePresenceEvent.EVENT_BT_CONNECTED
        ) {
            onBikeAppeared("presence event $eventType")
        } else if (eventType == DevicePresenceEvent.EVENT_BLE_DISAPPEARED ||
            eventType == DevicePresenceEvent.EVENT_BT_DISCONNECTED
        ) {
            onBikeDisappeared("presence event $eventType")
        }
    }

    @Deprecated("Deprecated in Java", ReplaceWith("onDevicePresenceEvent(event)"))
    override fun onDeviceAppeared(associationInfo: AssociationInfo) {
        LogStore.logEvent("CompanionDeviceService: onDeviceAppeared (${associationInfo.deviceMacAddress})")
        onBikeAppeared("onDeviceAppeared")
    }

    @Deprecated("Deprecated in Java", ReplaceWith("onDevicePresenceEvent(event)"))
    override fun onDeviceDisappeared(associationInfo: AssociationInfo) {
        LogStore.logEvent("CompanionDeviceService: onDeviceDisappeared (${associationInfo.deviceMacAddress})")
        onBikeDisappeared("onDeviceDisappeared")
    }

    private fun onBikeAppeared(reason: String) {
        LogStore.logEvent("Autostart: Bike in range ($reason). Launching telemetry bridge...")
        val engine = TelemetryEngine.getInstance(this)
        if (!engine.state.value.isBtConnected && !engine.state.value.isBtConnecting) {
            BikeForegroundService.start(this)
            engine.connect()
        }
        BackgroundBleScanner.launchApp(this)
    }

    private fun onBikeDisappeared(reason: String) {
        LogStore.logEvent("Autostart: Bike out of range ($reason).")
    }

    companion object {
        const val REQ_COMPANION_ASSOCIATE = 1002

        fun cdm(context: Context): CompanionDeviceManager? {
            return context.getSystemService(CompanionDeviceManager::class.java)
        }

        fun getAssociation(context: Context): AssociationInfo? {
            return try {
                cdm(context)?.myAssociations?.firstOrNull()
            } catch (_: Exception) {
                null
            }
        }

        fun isAssociated(context: Context): Boolean {
            return getAssociation(context) != null
        }

        fun associate(activity: Activity, onAssociated: ((AssociationInfo) -> Unit)? = null) {
            val manager = cdm(activity) ?: return
            val bonded = BackgroundBleScanner.getBondedBikeDevice(activity)
            val savedMac = com.kalaiselvan.ride.preferences.AppPreferences.getInstance(activity).userPreferences.value.lastBikeMac
            val targetMac = bonded?.address ?: savedMac.takeIf { it.isNotBlank() }

            val reqBuilder = AssociationRequest.Builder().setSingleDevice(false)
            if (!targetMac.isNullOrBlank() && android.bluetooth.BluetoothAdapter.checkBluetoothAddress(targetMac)) {
                reqBuilder.addDeviceFilter(
                    BluetoothLeDeviceFilter.Builder()
                        .setScanFilter(android.bluetooth.le.ScanFilter.Builder().setDeviceAddress(targetMac).build())
                        .build()
                )
            }
            reqBuilder.addDeviceFilter(
                BluetoothLeDeviceFilter.Builder()
                    .setNamePattern(Pattern.compile("(?i).*(bike).*"))
                    .build()
            )

            manager.associate(reqBuilder.build(), activity.mainExecutor, object : CompanionDeviceManager.Callback() {
                override fun onAssociationPending(intentSender: IntentSender) {
                    try {
                        activity.startIntentSenderForResult(intentSender, REQ_COMPANION_ASSOCIATE, null, 0, 0, 0)
                    } catch (e: Exception) {
                        LogStore.logEvent("Companion association pending error: ${e.message}")
                    }
                }

                override fun onAssociationCreated(associationInfo: AssociationInfo) {
                    LogStore.logEvent("Companion association created: ${associationInfo.displayName} (${associationInfo.deviceMacAddress})")
                    observe(activity, associationInfo)
                    onAssociated?.invoke(associationInfo)
                }

                override fun onFailure(errorMessage: CharSequence?) {
                    LogStore.logEvent("Companion association failed: $errorMessage")
                }
            })
        }

        fun observe(context: Context, info: AssociationInfo) {
            try {
                val manager = cdm(context) ?: return
                val mac = info.deviceMacAddress?.toString()?.uppercase()
                if (Build.VERSION.SDK_INT >= 34) {
                    try {
                        manager.startObservingDevicePresence(
                            ObservingDevicePresenceRequest.Builder()
                                .setAssociationId(info.id)
                                .build()
                        )
                    } catch (_: Throwable) {
                        if (!mac.isNullOrBlank()) {
                            @Suppress("DEPRECATION")
                            manager.startObservingDevicePresence(mac)
                        }
                    }
                } else if (!mac.isNullOrBlank()) {
                    @Suppress("DEPRECATION")
                    manager.startObservingDevicePresence(mac)
                }
                LogStore.logEvent("Companion presence observing started for $mac (id ${info.id})")
            } catch (e: Exception) {
                LogStore.logEvent("Failed to observe companion device: ${e.message}")
            }
        }

        fun disassociate(context: Context) {
            val info = getAssociation(context) ?: return
            try {
                cdm(context)?.disassociate(info.id)
                LogStore.logEvent("Companion disassociated")
            } catch (e: Exception) {
                LogStore.logEvent("Failed to disassociate companion: ${e.message}")
            }
        }
    }
}
