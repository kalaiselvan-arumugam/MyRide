package com.kalaiselvan.ride.notifications

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Icon
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.kalaiselvan.ride.bluetooth.BikeGattManager
import com.kalaiselvan.ride.logs.LogStore
import com.kalaiselvan.ride.preferences.AppPreferences
import com.kalaiselvan.ride.preferences.NavSource
import com.kalaiselvan.ride.preferences.SpeedUnit
import com.kalaiselvan.ride.telemetry.TelemetryEngine
import kotlinx.coroutines.*
import java.util.Locale

open class BikeNotificationListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "MyRideNotify"

        const val TYPE_CALL = 1
        const val TYPE_MAIL = 6

        const val STATUS_ADDED = 0
        const val STATUS_REMOVED = 2

        val CALL_PACKAGES = listOf(
            "com.google.android.dialer",
            "com.samsung.android.dialer",
            "com.sonymobile.android.dialer",
            "android.dialer",
            "com.android.server.telecom",
            "com.android.phone",
            "com.oneplus.dialer",
            "com.oppo.dialer",
            "com.vivo.dialer",
            "com.xiaomi.dialer",
            "com.android.incallui",
            "com.truecaller"
        )

        val MAIL_PACKAGES = listOf(
            "com.google.android.gm",
            "com.google.android.apps.messaging",
            "com.samsung.android.messaging",
            "com.android.mms",
            "com.whatsapp",
            "com.facebook.orca",
            "com.instagram.android",
            "jp.naver.line.android",
            "com.microsoft.office.outlook",
            "jp.co.yahoo.android.ymail",
            "org.telegram.messenger",
            "org.thoughtcrime.securesms",
            "com.truecaller"
        )

        const val MAPS_PACKAGE = "com.google.android.apps.maps"

        fun isMapsPackage(pkg: String?): Boolean {
            if (pkg == null) return false
            val p = pkg.lowercase()
            return p == MAPS_PACKAGE || p == "com.google.android.apps.mapslite" || (p.contains("google") && p.contains("maps"))
        }

        private val activeKeys = java.util.concurrent.ConcurrentHashMap<Int, MutableSet<String>>()
        private var activeNavNotifId: Int = -1
        private var lastNavKey: String = ""
        private var lastValidDist: Int = -1
        private var lastValidUnit: Int = 1
        private var lastValidIcon: Int = -1

        data class CachedNav(val mode: Int, val icon: Int, val distance: Int, val unit: Int)
        @Volatile var cachedNav: CachedNav? = null
            private set

        private var pendingClearJob: kotlinx.coroutines.Job? = null
        private val serviceScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default + kotlinx.coroutines.SupervisorJob())

        private fun scheduleDelayedClearNavigation(service: BikeNotificationListenerService, reason: String) {
            pendingClearJob?.cancel()
            pendingClearJob = serviceScope.launch {
                kotlinx.coroutines.delay(4000) // 4-second grace period
                val hasNav = try {
                    service.activeNotifications?.any {
                        isMapsPackage(it.packageName) && service.isNavigationNotification(it)
                    } == true
                } catch (e: Exception) {
                    false
                }
                val callActive = activeKeys[TYPE_CALL]?.isNotEmpty() == true || isCallInProgress(service)
                val isOlaNavigating = com.kalaiselvan.ride.navigation.OlaNavEngine.isNavigating.value
                if (!hasNav && !callActive && !isOlaNavigating) {
                    service.clearNavigation(reason)
                } else {
                    LogStore.logEvent("Delayed nav clear cancelled: activeNav=$hasNav, callActive=$callActive, olaNav=$isOlaNavigating")
                }
            }
        }

        private fun isCallInProgress(context: Context): Boolean {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
            return am?.mode in listOf(
                android.media.AudioManager.MODE_IN_CALL,
                android.media.AudioManager.MODE_IN_COMMUNICATION,
                android.media.AudioManager.MODE_RINGTONE
            )
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        LogStore.logEvent("BikeNotificationListenerService CONNECTED and ACTIVE")
        try {
            // Re-sync any current active call or message notifications
            val activeNotifs = activeNotifications ?: emptyArray()
            for (sbn in activeNotifs) {
                onNotificationPosted(sbn)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking active notifications on connect: ${e.message}")
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        LogStore.logEvent("BikeNotificationListenerService DISCONNECTED. Rebinding...")
        try {
            requestRebind(ComponentName(this, BikeNotificationListenerService::class.java))
        } catch (e: Exception) {
            Log.e(TAG, "Rebind failed: ${e.message}")
        }
    }

    private fun categorizeNotification(sbn: StatusBarNotification): Int {
        val pkg = sbn.packageName?.lowercase() ?: return 0
        val notif = sbn.notification ?: return 0
        val cat = notif.category

        val isCallCategory = Notification.CATEGORY_CALL == cat || Notification.CATEGORY_MISSED_CALL == cat
        val isDialer = CALL_PACKAGES.any { pkg.contains(it) } ||
                pkg.contains("dialer") ||
                pkg.contains("telecom") ||
                pkg.contains("incall") ||
                (pkg.contains("phone") && !pkg.contains("google.android.apps.photos"))

        if (isCallCategory || isDialer) {
            return TYPE_CALL
        }

        if (pkg.contains("whatsapp") && (isCallCategory || (notif.flags and Notification.FLAG_ONGOING_EVENT) != 0)) {
            return TYPE_CALL
        }

        val isMsgCategory = Notification.CATEGORY_MESSAGE == cat ||
                Notification.CATEGORY_EMAIL == cat ||
                Notification.CATEGORY_SOCIAL == cat
        val isMailPkg = MAIL_PACKAGES.any { pkg.contains(it) } ||
                pkg.contains("mms") ||
                pkg.contains("messaging")

        if (isMailPkg || isMsgCategory) {
            return TYPE_MAIL
        }

        return 0
    }

    private fun clearNavigation(reason: String) {
        if (activeNavNotifId == -1 && lastNavKey.isEmpty() && lastValidDist == -1) {
            return
        }
        // NEVER clear bike navigation if in-app Ola navigation is active!
        if (com.kalaiselvan.ride.navigation.OlaNavEngine.isNavigating.value) {
            LogStore.logEvent("Clear Navigation suppressed: OlaNavEngine is actively navigating")
            return
        }
        LogStore.logEvent("TFT Navigation Cleared ($reason)")
        activeNavNotifId = -1
        lastNavKey = ""
        lastValidDist = -1
        lastValidUnit = 1
        lastValidIcon = -1
        cachedNav = null
        TelemetryEngine.getInstance(this).updateNavigationState(false, "", 0, 0)
        BikeGattManager.instance?.sendNavigationData(3, 0xFF, 0, 0x0F)
    }

    private fun isNavigationNotification(sbn: StatusBarNotification): Boolean {
        if (!isMapsPackage(sbn.packageName)) return false
        val notif = sbn.notification ?: return false

        // Any ongoing notification from Maps or marked with CATEGORY_NAVIGATION, transport, or foreground service
        val isNavCategory = Notification.CATEGORY_NAVIGATION == notif.category || "transport" == notif.category
        val isOngoing = (notif.flags and Notification.FLAG_ONGOING_EVENT) != 0 || sbn.isOngoing
        val isForeground = (notif.flags and Notification.FLAG_FOREGROUND_SERVICE) != 0

        if (isNavCategory || isOngoing || isForeground) return true

        // Fallback: check if the notification extras contain navigation keywords or distance
        val extras = notif.extras ?: return false
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.lowercase() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.lowercase() ?: ""
        val hasNavKeywords = title.contains("turn") || title.contains("exit") || title.contains("head") ||
                title.contains("continue") || title.contains("onto") || title.contains("toward") ||
                text.contains("min") || text.contains("km") || text.contains(" m") || text.contains("mi")
        return hasNavKeywords || notif.getLargeIcon() != null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val sbnNotNull = sbn ?: return
        val pkg = sbnNotNull.packageName ?: return
        val prefs = AppPreferences.getInstance(this).userPreferences.value
        val notifKey = sbnNotNull.key ?: "${pkg}|${sbnNotNull.id}"

        val categoryType = categorizeNotification(sbnNotNull)

        if (categoryType == TYPE_CALL && prefs.callNotifications) {
            val set = activeKeys.computeIfAbsent(TYPE_CALL) { java.util.concurrent.ConcurrentHashMap.newKeySet() }
            set.add(notifKey)
            LogStore.logEvent("TFT Incoming Call alert posted ($pkg)")
            BikeGattManager.instance?.sendNotification(TYPE_CALL, STATUS_ADDED)
        } else if (categoryType == TYPE_MAIL && prefs.smsNotifications) {
            val set = activeKeys.computeIfAbsent(TYPE_MAIL) { java.util.concurrent.ConcurrentHashMap.newKeySet() }
            set.add(notifKey)
            LogStore.logEvent("TFT New Message/SMS alert posted ($pkg)")
            BikeGattManager.instance?.sendNotification(TYPE_MAIL, STATUS_ADDED)
        }

        if (isMapsPackage(pkg)) {
            LogStore.logEvent("Maps notification received (id=${sbnNotNull.id}, category=${sbnNotNull.notification?.category}, flags=0x${Integer.toHexString(sbnNotNull.notification?.flags ?: 0)}, ongoing=${sbnNotNull.isOngoing})")
            if (!prefs.turnByTurnNav) {
                LogStore.logEvent("Maps notification ignored: turnByTurnNav disabled in Settings")
                return
            }
            if (prefs.navSource != NavSource.DEFAULT_MAP) {
                LogStore.logEvent("Maps notification ignored: NavSource is ${prefs.navSource}")
                return
            }

            if (!isNavigationNotification(sbnNotNull)) {
                LogStore.logEvent("Maps notif skipped: not navigation category=${sbnNotNull.notification?.category}")
                return
            }

            handleMapsNotification(sbnNotNull)
        }
    }

    override fun onCreate() {
        super.onCreate()
    }

    private fun handleMapsNotification(sbn: StatusBarNotification) {
        pendingClearJob?.cancel()
        pendingClearJob = null
        try {
            val notification = sbn.notification ?: return
            val extras = notification.extras ?: return

            // 1. Collect all available text fields from Maps notification
            val rawTexts = listOfNotNull(
                extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
                extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
                extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
                extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString(),
                extras.getCharSequence(Notification.EXTRA_INFO_TEXT)?.toString(),
                extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString(),
                notification.tickerText?.toString()
            )

            val texts = mutableListOf<String>()
            for (raw in rawTexts) {
                val clean = raw.replace('\u00A0', ' ').replace('\u202F', ' ').trim()
                if (clean.isNotBlank() && clean !in texts) {
                    texts.add(clean)
                }
            }

            activeNavNotifId = sbn.id

            // 2. Extract custom RemoteViews text if available and texts list is empty
            val rv = notification.bigContentView ?: notification.contentView
            if (rv != null && texts.isEmpty()) {
                try {
                    val mapsContext = try {
                        createPackageContext(MAPS_PACKAGE, Context.CONTEXT_RESTRICTED)
                    } catch (_: Exception) {
                        this
                    }
                    val root = rv.apply(mapsContext, FrameLayout(mapsContext))
                    fun walk(v: View) {
                        if (v is TextView) {
                            val s = v.text?.toString()?.replace('\u00A0', ' ')?.replace('\u202F', ' ')?.trim()
                            if (!s.isNullOrBlank() && s !in texts) texts.add(s)
                        } else if (v is ViewGroup) {
                            for (i in 0 until v.childCount) walk(v.getChildAt(i))
                        }
                    }
                    walk(root)
                } catch (e: Throwable) {
                    LogStore.logEvent("Maps remote view notice: ${e.message}")
                }
            }

            if (texts.isEmpty()) {
                LogStore.logEvent("Maps notif empty: no text fields")
                return
            }

            // 3. Parse countdown distance
            val titleText = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
                ?.replace('\u00A0', ' ')?.replace('\u202F', ' ')?.trim() ?: ""
            var extractedDist = 0

            // Step 3a: Check title first (e.g., "500 m" or "In 500 m, turn right")
            if (titleText.isNotBlank()) {
                val parsed = NavNotificationParser.parseDistance(titleText)
                if (parsed.first > 0) {
                    extractedDist = parsed.first
                }
            }

            // Step 3b: If not found in title, scan other texts excluding ETA/summary lines
            if (extractedDist <= 0) {
                for (str in texts) {
                    val lower = str.lowercase(Locale.ROOT)
                    if (lower.contains("min") || lower.contains("hr") || lower.contains("eta") ||
                        (lower.contains(":") && (lower.contains("am") || lower.contains("pm")))) {
                        continue
                    }
                    val parsed = NavNotificationParser.parseDistance(str)
                    if (parsed.first > 0) {
                        extractedDist = parsed.first
                        break
                    }
                }
            }

            // 4. Parse maneuver instruction & turn ID (0..55)
            val instruction = if (titleText.isNotBlank()) titleText else texts.firstOrNull() ?: ""
            var parsedTurn = NavNotificationParser.parseManeuver(instruction)

            // If title text gave no turn, check other texts
            if (parsedTurn == 0) {
                for (t in texts) {
                    val pt = NavNotificationParser.parseManeuver(t)
                    if (pt != 0) {
                        parsedTurn = pt
                        break
                    }
                }
            }

            var icon = parsedTurn

            // Motorcycle TFT cluster has no LCD segment for Departure (42). Map Departure to Straight (0).
            if (icon == 42) {
                icon = 0
            }

            val hasExplicitArrivalText = instruction.lowercase(Locale.ROOT).let {
                it.contains("you have arrived") || it.contains("you've arrived") ||
                it.contains("arrived at") || it.contains("destination reached") ||
                it.contains("at your destination") || it.contains("has arrived")
            }
            val isDestinationEvent = (icon == 43) || hasExplicitArrivalText ||
                instruction.lowercase(Locale.ROOT).contains("destination")

            // Destination arrival (0m) occurs strictly when Google Maps reports true arrival
            val isTrueArrival = hasExplicitArrivalText || (isDestinationEvent && extractedDist <= 0 && lastValidDist <= 0)

            val isExplicitStraight = instruction.lowercase(Locale.ROOT).let {
                it.contains("straight") || it.contains("continue") || it.contains("keep straight")
            }
            if (icon == 0 && !isExplicitStraight && !isDestinationEvent && lastValidIcon > 0) {
                // Retain active maneuver if intermediate notification has no turn keyword and is not explicitly straight
                icon = lastValidIcon
            } else if (icon > 0) {
                lastValidIcon = icon
            }

            // 5. Retain previous distance if intermediate notification lacks distance string
            val finalMeters = when {
                isTrueArrival -> {
                    lastValidDist = 0
                    0
                }
                extractedDist > 0 -> {
                    lastValidDist = extractedDist
                    extractedDist
                }
                lastValidDist > 0 -> {
                    LogStore.logEvent("Maps distance missing in text - retaining last known distance: ${lastValidDist}m")
                    lastValidDist
                }
                else -> 0
            }

            val gatt = BikeGattManager.instance
            val userPrefs = AppPreferences.getInstance(this).userPreferences.value
            val bikeMac = userPrefs.lastBikeMac
            if (gatt != null && !gatt.isConnected && bikeMac.isNotBlank()) {
                LogStore.logEvent("Maps Nav Active: Bike not connected, initiating direct connect to $bikeMac")
                gatt.connect(bikeMac, isDirect = true)
            }

            val maneuverIcon = if (isTrueArrival) 43 else icon

            // 6. Deduplicate identical updates to prevent flooding the BLE queue
            val joinedTexts = texts.joinToString(" | ")
            val dedupeKey = "$joinedTexts#$finalMeters#$maneuverIcon"
            if (dedupeKey == lastNavKey && gatt?.isConnected == true) {
                return
            }
            if (gatt?.isConnected == true) {
                lastNavKey = dedupeKey
            }

            // 7. Unit conversion (Metric vs Imperial)
            val (finalDist, unit) = if (userPrefs.speedUnit == SpeedUnit.MPH) {
                val feet = Math.round(finalMeters / 0.3048).toInt()
                Pair(feet.coerceAtLeast(0), 3) // 3 = ft
            } else {
                Pair(finalMeters.coerceAtLeast(0), 1) // 1 = m
            }
            lastValidUnit = unit

            // Mode 0: navigating on cluster (Z900 TFT uses mode 0 with icon 43 for arrived destination)
            val mode = 0
            val distToSend = if (isTrueArrival) 0 else finalDist
            LogStore.logEvent("TFT Nav Dispatch: mode=$mode, icon=$maneuverIcon, dist=$distToSend (unit=$unit), isTrueArrival=$isTrueArrival, text='$instruction'")

            cachedNav = CachedNav(mode, maneuverIcon, distToSend, unit)
            TelemetryEngine.getInstance(this).updateNavigationState(
                isActive = true,
                instruction = if (isTrueArrival && instruction.isBlank()) "Destination Arrived" else instruction,
                distanceMeters = if (isTrueArrival) 0 else finalMeters,
                iconId = maneuverIcon
            )

            if (gatt == null) {
                LogStore.logEvent("Nav Update cached for connect: BikeGattManager is null (bike not connected yet)")
            } else {
                gatt.sendNavigationData(mode, maneuverIcon, distToSend, unit, instruction = instruction)
            }
        } catch (e: Throwable) {
            LogStore.logEvent("handleMapsNotification error: ${e.javaClass.simpleName}: ${e.message}")
            Log.e(TAG, "Maps processing error", e)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        val sbnNotNull = sbn ?: return
        val pkg = sbnNotNull.packageName ?: return
        val notifKey = sbnNotNull.key ?: "${pkg}|${sbnNotNull.id}"

        for (cat in listOf(TYPE_CALL, TYPE_MAIL)) {
            val set = activeKeys[cat]
            if (set != null && set.remove(notifKey)) {
                if (set.isEmpty()) {
                    val label = if (cat == TYPE_CALL) "Call" else "Message/SMS"
                    LogStore.logEvent("TFT $label alert cleared ($pkg)")
                    BikeGattManager.instance?.sendNotification(cat, STATUS_REMOVED)

                    // If a phone call just dropped/ended, BikeGattManager automatically restores navigation.
                    // For external maps, also trigger a rescan of active notifications to get the latest turn packet.
                    if (cat == TYPE_CALL && !com.kalaiselvan.ride.navigation.OlaNavEngine.isNavigating.value) {
                        try {
                            activeNotifications?.firstOrNull { isMapsPackage(it.packageName) && isNavigationNotification(it) }?.let {
                                handleMapsNotification(it)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Post-call activeNotifications rescan failed: ${e.message}")
                        }
                    }
                }
            }
        }

        if (isMapsPackage(pkg)) {
            val isCallActive = activeKeys[TYPE_CALL]?.isNotEmpty() == true || isCallInProgress(this)
            if (isCallActive) {
                LogStore.logEvent("Maps notification removed while phone call is active - retaining cluster navigation")
                return
            }
            if (com.kalaiselvan.ride.navigation.OlaNavEngine.isNavigating.value) {
                LogStore.logEvent("Maps notification removed while Ola navigation is active - retaining cluster navigation")
                return
            }

            val hasOtherNav = try {
                activeNotifications?.any {
                    isMapsPackage(it.packageName) && isNavigationNotification(it) && it.id != sbnNotNull.id
                } == true
            } catch (e: Exception) {
                false
            }
            if (hasOtherNav) {
                LogStore.logEvent("Maps notification removed but another navigation notification exists - retaining")
                return
            }

            scheduleDelayedClearNavigation(this, "Navigation notification (id=${sbnNotNull.id}) removed")
        }
    }
}
