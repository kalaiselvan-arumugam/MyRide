package com.kalaiselvan.ride.preferences

import android.content.Context
import android.content.SharedPreferences
import com.kalaiselvan.ride.utils.PreferenceEncryptor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ThemeMode(val displayName: String) {
    DARK("Dark"),
    LIGHT("Light");

    companion object {
        fun fromName(name: String): ThemeMode =
            try { valueOf(name) } catch (_: Exception) { DARK }
    }
}

enum class SpeedUnit {
    KMH, MPH
}

enum class TemperatureUnit {
    CELSIUS,
    FAHRENHEIT
}

enum class NavSource {
    DEFAULT_MAP,
    OLA_MAPS
}

enum class OlaMapStyle(val styleId: String, val displayName: String) {
    OLA_DARK("default-dark-standard", "Dark"),
    OLA_LIGHT("default-light-standard", "Light");

    companion object {
        fun fromStyleId(id: String): OlaMapStyle =
            when (id) {
                "default-light-standard", "light" -> OLA_LIGHT
                else -> OLA_DARK
            }
    }
}

enum class BackupSchedule(val displayName: String, val description: String) {
    NEVER("Never", "Automated backup disabled"),
    DAILY("Daily", "Every day at 12:00 AM"),
    WEEKLY("Weekly", "Every Sunday at 12:00 AM");

    companion object {
        fun fromName(name: String): BackupSchedule =
            when (name) {
                "DAILY" -> DAILY
                "WEEKLY" -> WEEKLY
                else -> NEVER
            }
    }
}

enum class CrashSensitivity(val displayName: String) {
    LOW("Low"),
    MEDIUM("Medium"),
    HIGH("High");

    companion object {
        fun fromName(name: String): CrashSensitivity =
            try { valueOf(name) } catch (_: Exception) { MEDIUM }
    }
}

data class EmergencyContact(
    val name: String,
    val number: String
)

/**
 * Persisted destination record kept for 5 days.
 */
data class RecentDestination(
    val placeId: String,
    val mainText: String,
    val secondaryText: String,
    val lat: Double,
    val lng: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val routeSummary: String = "",
    val routeDistance: String = ""
)

data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val speedUnit: SpeedUnit = SpeedUnit.KMH,
    val temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
    val callNotifications: Boolean = true,
    val smsNotifications: Boolean = true,
    val turnByTurnNav: Boolean = true,
    val navSource: NavSource = NavSource.DEFAULT_MAP,
    val olaMapsApiKey: String = "",
    val olaMapStyle: OlaMapStyle = OlaMapStyle.OLA_DARK,
    val phoneBatteryMirroring: Boolean = true,
    val dashClockSync: Boolean = true,
    val isDevUnlocked: Boolean = false,
    // Achievement records with current user achievements in 24-hour format
    val topSpeedKmh: Float = 0f,
    val topSpeedDate: String = "",
    val zeroToHundredSec: Float = 0f,
    val zeroToHundredDate: String = "",
    val maxGForce: Float = 0f,
    val maxGForceDate: String = "",
    val maxLeanLeft: Float = 0f,
    val maxLeanLeftDate: String = "",
    val maxLeanRight: Float = 0f,
    val maxLeanRightDate: String = "",
    val maxWheelie: Float = 0f,
    val maxWheelieDate: String = "",
    val maxStoppie: Float = 0f,
    val maxStoppieDate: String = "",
    val longestDistKm: Double = 0.0,
    val longestDistDate: String = "",
    val longestDurationSec: Long = 0L,
    val longestDurationDate: String = "",
    // Persisted last connected session telemetry values
    val lastOdo: Double = 0.0,
    val lastTripA: Float = 0.0f,
    val lastTripB: Float = 0.0f,
    val lastSyncDateTime: String = "",
    // Persisted last completed session summary metrics (retained while disconnected)
    val lastSessionTimeMs: Long = 0L,
    val lastSessionDistanceKm: Double = 0.0,
    val lastSessionMaxLeanL: Float = 0f,
    val lastSessionMaxLeanR: Float = 0f,
    val lastSessionMaxWheelie: Float = 0f,
    val lastSessionMaxStoppie: Float = 0f,
    val lastSessionMaxG: Float = 0f,
    // Parked bike location memory ("Walk to the bike")
    val parkedLat: Double = 0.0,
    val parkedLon: Double = 0.0,
    val parkedAccuracy: Float = 0f,
    val parkedTimeMs: Long = 0L,
    val parkedTimeFormatted: String = "",
    // Bike hardware identification from BLE Frame 0x03 / advertisement
    val bikeModelName: String = "",
    val bikeVin: String = "",
    val ktrcLevel: String = "1",
    val lastBikeMac: String = "",
    val hasShownFirstLaunchPrompt: Boolean = false,
    // Parked location expiry timeout (1h, 3h, 6h, 12h, 24h)
    val parkedExpiryHours: Int = 12,
    // Shift-Up Indicator Lamp threshold (read from TFT Frame 0x1B, 0 = not read yet from bike)
    val shiftLampRpm: Int = 0,
    // Legal & Safety Disclaimer accepted flag
    val hasAcceptedDisclaimer: Boolean = false,
    // Custom Brand Name displayed in dashboard header (Default: "MyRide", max 15 chars, letters only)
    val customBrandName: String = "MyRide",
    // Preferred currency symbol for service & maintenance logging (defaults to device system locale)
    val preferredCurrency: String = AppPreferences.getDefaultCurrencySymbol(),
    // Keep display active and prevent screen timeout while viewing dashboard
    val keepDashboardScreenOn: Boolean = true,
    // Enable auto-rotation to landscape on dashboard (when disabled, locks to portrait even if system auto-rotate is on)
    val autoRotateDashboard: Boolean = false,
    // Auto-choose fastest route in navigation or allow picking alternatives on map
    val autoChooseFastestRoute: Boolean = true,
    // Auto-backup configuration
    val autoBackupEnabled: Boolean = false,
    val autoBackupSchedule: BackupSchedule = BackupSchedule.NEVER,
    val autoBackupFolderUri: String = "",
    val autoBackupFolderName: String = "",
    val autoBackupEncrypt: Boolean = true,
    val autoBackupPasswordEncrypted: String = "",
    val lastBackupTimestampMs: Long = 0L,
    // Rider profile
    val riderName: String = "",
    // Crash Detection (Experimental)
    val crashDetectionEnabled: Boolean = false,
    val crashSensitivity: CrashSensitivity = CrashSensitivity.MEDIUM,
    val crashCountdownSeconds: Int = 30,
    val crashSmsSubscriptionId: Int = -1,
    val crashEmergencyContacts: String = "[]",
    val crashNotifiedContacts: String = "[]",
    val simPhoneOverrides: String = "{}",
    // Audio Cockpit & Helmet Alerts
    val audioAlertsEnabled: Boolean = false,
    val audioRevLimitAlert: Boolean = true,
    val audioSpeedThresholdAlert: Boolean = true,
    val audioLeanAngleAlert: Boolean = true,
    val audioLeanThresholdDeg: Int = 40
)

class AppPreferences private constructor(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _userPreferences = MutableStateFlow(loadPreferences())
    val userPreferences: StateFlow<UserPreferences> = _userPreferences.asStateFlow()

    private val _recentDestinations = MutableStateFlow<List<RecentDestination>>(emptyList())
    val recentDestinations: StateFlow<List<RecentDestination>> = _recentDestinations.asStateFlow()

    init {
        _recentDestinations.value = loadRecentDestinations()
    }

    companion object {
        private const val PREFS_NAME = "myride_preferences"

        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_SPEED_UNIT = "speed_unit"
        private const val KEY_TEMP_UNIT = "temperature_unit"
        private const val KEY_CALL_NOTIF = "call_notifications"
        private const val KEY_SMS_NOTIF = "sms_notifications"
        private const val KEY_TBT_NAV = "turn_by_turn_nav"
        private const val KEY_NAV_SOURCE = "nav_source"
        private const val KEY_OLA_MAPS_API_KEY = "ola_maps_api_key"
        private const val KEY_OLA_MAP_STYLE = "ola_map_style"
        private const val KEY_RECENT_DESTINATIONS = "recent_destinations_json"
        private const val KEY_BATTERY_MIRROR = "battery_mirroring"
        private const val KEY_DASH_CLOCK_SYNC = "dash_clock_sync"
        private const val KEY_DEV_UNLOCKED = "is_dev_unlocked"

        private const val KEY_PARKED_LAT = "parked_lat"
        private const val KEY_PARKED_LON = "parked_lon"
        private const val KEY_PARKED_ACCURACY = "parked_accuracy"
        private const val KEY_PARKED_TIME = "parked_time"
        private const val KEY_PARKED_TIME_FORMATTED = "parked_time_formatted"

        private const val KEY_TOP_SPEED = "top_speed"
        private const val KEY_TOP_SPEED_DATE = "top_speed_date"
        private const val KEY_ZERO_HUNDRED = "zero_hundred"
        private const val KEY_ZERO_HUNDRED_DATE = "zero_hundred_date"
        private const val KEY_MAX_G = "max_g"
        private const val KEY_MAX_G_DATE = "max_g_date"
        private const val KEY_MAX_LEAN_L = "max_lean_l"
        private const val KEY_MAX_LEAN_L_DATE = "max_lean_l_date"
        private const val KEY_MAX_LEAN_R = "max_lean_r"
        private const val KEY_MAX_LEAN_R_DATE = "max_lean_r_date"
        private const val KEY_MAX_WHEELIE = "max_wheelie"
        private const val KEY_MAX_WHEELIE_DATE = "max_wheelie_date"
        private const val KEY_MAX_STOPPIE = "max_stoppie"
        private const val KEY_MAX_STOPPIE_DATE = "max_stoppie_date"
        private const val KEY_LONGEST_DIST = "longest_dist"
        private const val KEY_LONGEST_DIST_DATE = "longest_dist_date"
        private const val KEY_LONGEST_TIME = "longest_time"
        private const val KEY_LONGEST_TIME_DATE = "longest_time_date"

        private const val KEY_LAST_ODO = "last_odo"
        private const val KEY_LAST_TRIP_A = "last_trip_a"
        private const val KEY_LAST_TRIP_B = "last_trip_b"
        private const val KEY_LAST_SYNC_DATE = "last_sync_date"

        private const val KEY_LAST_SESSION_TIME_MS = "last_session_time_ms"
        private const val KEY_LAST_SESSION_DIST_KM = "last_session_dist_km"
        private const val KEY_LAST_SESSION_MAX_LEAN_L = "last_session_max_lean_l"
        private const val KEY_LAST_SESSION_MAX_LEAN_R = "last_session_max_lean_r"
        private const val KEY_LAST_SESSION_MAX_WHEELIE = "last_session_max_wheelie"
        private const val KEY_LAST_SESSION_MAX_STOPPIE = "last_session_max_stoppie"
        private const val KEY_LAST_SESSION_MAX_G = "last_session_max_g"
        private const val KEY_BIKE_MODEL_NAME = "bike_model_name"
        private const val KEY_BIKE_VIN = "bike_vin"
        private const val KEY_KTRC_LEVEL = "ktrc_level"
        private const val KEY_LAST_BIKE_MAC = "last_bike_mac"
        private const val KEY_FIRST_LAUNCH_PROMPT = "has_shown_first_launch_prompt"
        private const val KEY_PARKED_EXPIRY_HOURS = "parked_expiry_hours"
        private const val KEY_SHIFT_LAMP_RPM = "shift_lamp_rpm"
        private const val KEY_DISCLAIMER_ACCEPTED = "has_accepted_disclaimer"
        private const val KEY_CUSTOM_BRAND_NAME = "custom_brand_name"
        private const val KEY_PREFERRED_CURRENCY = "preferred_currency"
        private const val KEY_KEEP_DASHBOARD_SCREEN_ON = "keep_dashboard_screen_on"
        private const val KEY_AUTO_ROTATE_DASHBOARD = "auto_rotate_dashboard"
        private const val KEY_AUTO_CHOOSE_FASTEST_ROUTE = "key_auto_choose_fastest_route"
        private const val KEY_AUTO_BACKUP_ENABLED = "auto_backup_enabled"
        private const val KEY_AUTO_BACKUP_SCHEDULE = "auto_backup_schedule"
        private const val KEY_AUTO_BACKUP_FOLDER_URI = "auto_backup_folder_uri"
        private const val KEY_AUTO_BACKUP_FOLDER_NAME = "auto_backup_folder_name"
        private const val KEY_AUTO_BACKUP_ENCRYPT = "auto_backup_encrypt"
        private const val KEY_AUTO_BACKUP_PASSWORD_ENC = "auto_backup_password_enc"
        private const val KEY_LAST_BACKUP_TIMESTAMP = "last_backup_timestamp_ms"
        private const val KEY_RIDER_NAME = "rider_name"
        private const val KEY_CRASH_ENABLED = "crash_detection_enabled"
        private const val KEY_CRASH_SENSITIVITY = "crash_sensitivity"
        private const val KEY_CRASH_COUNTDOWN = "crash_countdown_seconds"
        private const val KEY_CRASH_SIM_ID = "crash_sms_subscription_id"
        private const val KEY_CRASH_CONTACTS = "crash_emergency_contacts"
        private const val KEY_CRASH_NOTIFIED = "crash_notified_contacts"
        private const val KEY_SIM_OVERRIDES = "sim_phone_overrides"
        private const val KEY_AUDIO_ALERTS_ENABLED = "audio_alerts_enabled"
        private const val KEY_AUDIO_REV_LIMIT = "audio_rev_limit_alert"
        private const val KEY_AUDIO_SPEED_THRESHOLD = "audio_speed_threshold_alert"
        private const val KEY_AUDIO_LEAN_ANGLE = "audio_lean_angle_alert"
        private const val KEY_AUDIO_LEAN_DEG = "audio_lean_threshold_deg"

        fun getDefaultCurrencySymbol(): String {
            return try {
                val curr = java.util.Currency.getInstance(Locale.getDefault())
                curr.symbol
            } catch (_: Exception) {
                "₹"
            }
        }

        @Volatile
        private var INSTANCE: AppPreferences? = null

        fun getInstance(context: Context): AppPreferences {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppPreferences(context.applicationContext).also { INSTANCE = it }
            }
        }

        private data class AchievementBackupField(
            val valueKey: String,
            val dateKey: String,
            val prefValueKey: String,
            val prefDateKey: String,
            val isLong: Boolean = false
        )

        private val ACHIEVEMENT_BACKUP_FIELDS = listOf(
            AchievementBackupField("topSpeedKmh", "topSpeedDate", KEY_TOP_SPEED, KEY_TOP_SPEED_DATE),
            AchievementBackupField("zeroToHundredSec", "zeroToHundredDate", KEY_ZERO_HUNDRED, KEY_ZERO_HUNDRED_DATE),
            AchievementBackupField("maxGForce", "maxGForceDate", KEY_MAX_G, KEY_MAX_G_DATE),
            AchievementBackupField("maxLeanLeft", "maxLeanLeftDate", KEY_MAX_LEAN_L, KEY_MAX_LEAN_L_DATE),
            AchievementBackupField("maxLeanRight", "maxLeanRightDate", KEY_MAX_LEAN_R, KEY_MAX_LEAN_R_DATE),
            AchievementBackupField("maxWheelie", "maxWheelieDate", KEY_MAX_WHEELIE, KEY_MAX_WHEELIE_DATE),
            AchievementBackupField("maxStoppie", "maxStoppieDate", KEY_MAX_STOPPIE, KEY_MAX_STOPPIE_DATE),
            AchievementBackupField("longestDistKm", "longestDistDate", KEY_LONGEST_DIST, KEY_LONGEST_DIST_DATE),
            AchievementBackupField("longestDurationSec", "longestDurationDate", KEY_LONGEST_TIME, KEY_LONGEST_TIME_DATE, isLong = true)
        )
    }

    private fun loadPreferences(): UserPreferences {
        val themeModeStr = prefs.getString(KEY_THEME_MODE, ThemeMode.DARK.name) ?: ThemeMode.DARK.name
        val currentThemeMode = ThemeMode.fromName(themeModeStr)

        val unitStr = prefs.getString(KEY_SPEED_UNIT, SpeedUnit.KMH.name) ?: SpeedUnit.KMH.name
        val unit = try { SpeedUnit.valueOf(unitStr) } catch (_: Exception) { SpeedUnit.KMH }

        val tempUnitStr = prefs.getString(KEY_TEMP_UNIT, TemperatureUnit.CELSIUS.name) ?: TemperatureUnit.CELSIUS.name
        val currentTempUnit = try { TemperatureUnit.valueOf(tempUnitStr) } catch (_: Exception) { TemperatureUnit.CELSIUS }

// No initial seeding – first launch will show zero/empty values until a telemetry update occurs.

        val rawTopSpeed = prefs.getFloat(KEY_TOP_SPEED, 0f)
        val rawLongestDist = prefs.getFloat(KEY_LONGEST_DIST, 0f).toDouble()
        // Sanitize legacy test values if previously stored
        val cleanTopSpeed = if (rawTopSpeed in 250.5f..251.5f || rawTopSpeed in 185.5f..186.5f) 0f else rawTopSpeed
        val cleanLongestDist = if (rawLongestDist in 342.0..343.0 || rawLongestDist in 64.0..65.0) 0.0 else rawLongestDist

        val navSourceStr = prefs.getString(KEY_NAV_SOURCE, NavSource.DEFAULT_MAP.name) ?: NavSource.DEFAULT_MAP.name
        val currentNavSource = try { NavSource.valueOf(navSourceStr) } catch (_: Exception) { NavSource.DEFAULT_MAP }
        val rawOlaKey = prefs.getString(KEY_OLA_MAPS_API_KEY, "") ?: ""
        val currentOlaKey = PreferenceEncryptor.decrypt(rawOlaKey)
        if (rawOlaKey.isNotBlank() && !rawOlaKey.startsWith("enc_v1:")) {
            prefs.edit().putString(KEY_OLA_MAPS_API_KEY, PreferenceEncryptor.encrypt(currentOlaKey)).apply()
        }
        val mapStyleStr = prefs.getString(KEY_OLA_MAP_STYLE, OlaMapStyle.OLA_DARK.styleId) ?: OlaMapStyle.OLA_DARK.styleId
        val currentMapStyle = OlaMapStyle.fromStyleId(mapStyleStr)

        return UserPreferences(
            themeMode = currentThemeMode,
            speedUnit = unit,
            temperatureUnit = currentTempUnit,
            callNotifications = prefs.getBoolean(KEY_CALL_NOTIF, true),
            smsNotifications = prefs.getBoolean(KEY_SMS_NOTIF, true),
            turnByTurnNav = prefs.getBoolean(KEY_TBT_NAV, true),
            navSource = currentNavSource,
            olaMapsApiKey = currentOlaKey,
            olaMapStyle = currentMapStyle,
            phoneBatteryMirroring = prefs.getBoolean(KEY_BATTERY_MIRROR, true),
            dashClockSync = prefs.getBoolean(KEY_DASH_CLOCK_SYNC, true),
            isDevUnlocked = prefs.getBoolean(KEY_DEV_UNLOCKED, false),
            topSpeedKmh = cleanTopSpeed,
            topSpeedDate = if (cleanTopSpeed > 0f) (prefs.getString(KEY_TOP_SPEED_DATE, "") ?: "") else "",
            zeroToHundredSec = prefs.getFloat(KEY_ZERO_HUNDRED, 0f),
            zeroToHundredDate = prefs.getString(KEY_ZERO_HUNDRED_DATE, "") ?: "",
            maxGForce = prefs.getFloat(KEY_MAX_G, 0f),
            maxGForceDate = prefs.getString(KEY_MAX_G_DATE, "") ?: "",
            maxLeanLeft = prefs.getFloat(KEY_MAX_LEAN_L, 0f),
            maxLeanLeftDate = prefs.getString(KEY_MAX_LEAN_L_DATE, "") ?: "",
            maxLeanRight = prefs.getFloat(KEY_MAX_LEAN_R, 0f),
            maxLeanRightDate = prefs.getString(KEY_MAX_LEAN_R_DATE, "") ?: "",
            maxWheelie = prefs.getFloat(KEY_MAX_WHEELIE, 0f),
            maxWheelieDate = prefs.getString(KEY_MAX_WHEELIE_DATE, "") ?: "",
            maxStoppie = prefs.getFloat(KEY_MAX_STOPPIE, 0f),
            maxStoppieDate = prefs.getString(KEY_MAX_STOPPIE_DATE, "") ?: "",
            longestDistKm = cleanLongestDist,
            longestDistDate = if (cleanLongestDist > 0.0) (prefs.getString(KEY_LONGEST_DIST_DATE, "") ?: "") else "",
            longestDurationSec = prefs.getLong(KEY_LONGEST_TIME, 0L),
            longestDurationDate = prefs.getString(KEY_LONGEST_TIME_DATE, "") ?: "",
            lastOdo = prefs.getString(KEY_LAST_ODO, "0.0")?.toDoubleOrNull() ?: 0.0,
            lastTripA = prefs.getFloat(KEY_LAST_TRIP_A, 0.0f),
            lastTripB = prefs.getFloat(KEY_LAST_TRIP_B, 0.0f),
            lastSyncDateTime = prefs.getString(KEY_LAST_SYNC_DATE, "") ?: "",
            lastSessionTimeMs = prefs.getLong(KEY_LAST_SESSION_TIME_MS, 0L),
            lastSessionDistanceKm = prefs.getString(KEY_LAST_SESSION_DIST_KM, "0.0")?.toDoubleOrNull() ?: 0.0,
            lastSessionMaxLeanL = prefs.getFloat(KEY_LAST_SESSION_MAX_LEAN_L, 0f),
            lastSessionMaxLeanR = prefs.getFloat(KEY_LAST_SESSION_MAX_LEAN_R, 0f),
            lastSessionMaxWheelie = prefs.getFloat(KEY_LAST_SESSION_MAX_WHEELIE, 0f),
            lastSessionMaxStoppie = prefs.getFloat(KEY_LAST_SESSION_MAX_STOPPIE, 0f),
            lastSessionMaxG = prefs.getFloat(KEY_LAST_SESSION_MAX_G, 0f),
            parkedLat = prefs.getString(KEY_PARKED_LAT, "0.0")?.toDoubleOrNull() ?: 0.0,
            parkedLon = prefs.getString(KEY_PARKED_LON, "0.0")?.toDoubleOrNull() ?: 0.0,
            parkedAccuracy = prefs.getFloat(KEY_PARKED_ACCURACY, 0f),
            parkedTimeMs = prefs.getLong(KEY_PARKED_TIME, 0L),
            parkedTimeFormatted = prefs.getString(KEY_PARKED_TIME_FORMATTED, "") ?: "",
            bikeModelName = {
                val stored = prefs.getString(KEY_BIKE_MODEL_NAME, "") ?: ""
                val storedVin = prefs.getString(KEY_BIKE_VIN, "") ?: ""
                com.kalaiselvan.ride.bluetooth.BikeModelDecoder.decode(stored, storedVin)
            }(),
            bikeVin = prefs.getString(KEY_BIKE_VIN, "") ?: "",
            ktrcLevel = prefs.getString(KEY_KTRC_LEVEL, "1") ?: "1",
            lastBikeMac = prefs.getString(KEY_LAST_BIKE_MAC, "") ?: "",
            hasShownFirstLaunchPrompt = prefs.getBoolean(KEY_FIRST_LAUNCH_PROMPT, false),
            parkedExpiryHours = prefs.getInt(KEY_PARKED_EXPIRY_HOURS, 12),
            shiftLampRpm = prefs.getInt(KEY_SHIFT_LAMP_RPM, 0),
            hasAcceptedDisclaimer = prefs.getBoolean(KEY_DISCLAIMER_ACCEPTED, false),
            customBrandName = {
                val raw = prefs.getString(KEY_CUSTOM_BRAND_NAME, "MyRide") ?: "MyRide"
                if (raw.isBlank()) "MyRide" else raw
            }(),
            preferredCurrency = prefs.getString(KEY_PREFERRED_CURRENCY, null) ?: getDefaultCurrencySymbol(),
            keepDashboardScreenOn = prefs.getBoolean(KEY_KEEP_DASHBOARD_SCREEN_ON, true),
            autoRotateDashboard = prefs.getBoolean(KEY_AUTO_ROTATE_DASHBOARD, false),
            autoChooseFastestRoute = prefs.getBoolean(KEY_AUTO_CHOOSE_FASTEST_ROUTE, true),
            autoBackupEnabled = prefs.getBoolean(KEY_AUTO_BACKUP_ENABLED, false),
            autoBackupSchedule = BackupSchedule.fromName(prefs.getString(KEY_AUTO_BACKUP_SCHEDULE, BackupSchedule.DAILY.name) ?: BackupSchedule.DAILY.name),
            autoBackupFolderUri = prefs.getString(KEY_AUTO_BACKUP_FOLDER_URI, "") ?: "",
            autoBackupFolderName = prefs.getString(KEY_AUTO_BACKUP_FOLDER_NAME, "") ?: "",
            autoBackupEncrypt = prefs.getBoolean(KEY_AUTO_BACKUP_ENCRYPT, false),
            autoBackupPasswordEncrypted = prefs.getString(KEY_AUTO_BACKUP_PASSWORD_ENC, "") ?: "",
            lastBackupTimestampMs = prefs.getLong(KEY_LAST_BACKUP_TIMESTAMP, 0L),
            riderName = prefs.getString(KEY_RIDER_NAME, "") ?: "",
            crashDetectionEnabled = prefs.getBoolean(KEY_CRASH_ENABLED, false),
            crashSensitivity = CrashSensitivity.fromName(prefs.getString(KEY_CRASH_SENSITIVITY, CrashSensitivity.MEDIUM.name) ?: CrashSensitivity.MEDIUM.name),
            crashCountdownSeconds = prefs.getInt(KEY_CRASH_COUNTDOWN, 30),
            crashSmsSubscriptionId = prefs.getInt(KEY_CRASH_SIM_ID, -1),
            crashEmergencyContacts = prefs.getString(KEY_CRASH_CONTACTS, "[]") ?: "[]",
            crashNotifiedContacts = prefs.getString(KEY_CRASH_NOTIFIED, "[]") ?: "[]",
            simPhoneOverrides = prefs.getString(KEY_SIM_OVERRIDES, "{}") ?: "{}",
            audioAlertsEnabled = prefs.getBoolean(KEY_AUDIO_ALERTS_ENABLED, false),
            audioRevLimitAlert = prefs.getBoolean(KEY_AUDIO_REV_LIMIT, true),
            audioSpeedThresholdAlert = prefs.getBoolean(KEY_AUDIO_SPEED_THRESHOLD, true),
            audioLeanAngleAlert = prefs.getBoolean(KEY_AUDIO_LEAN_ANGLE, true),
            audioLeanThresholdDeg = prefs.getInt(KEY_AUDIO_LEAN_DEG, 40)
        )
    }

    fun setBikeModelAndVin(model: String?, vin: String?) {
        val editor = prefs.edit()
        var curVin = _userPreferences.value.bikeVin
        if (!vin.isNullOrBlank()) {
            editor.putString(KEY_BIKE_VIN, vin)
            curVin = vin
        }
        var curModel = _userPreferences.value.bikeModelName
        if (!model.isNullOrBlank()) {
            val decoded = com.kalaiselvan.ride.bluetooth.BikeModelDecoder.decode(model, curVin)
            if (decoded.isNotBlank()) {
                editor.putString(KEY_BIKE_MODEL_NAME, decoded)
                curModel = decoded
            }
        } else if (!curVin.isNullOrBlank()) {
            val decoded = com.kalaiselvan.ride.bluetooth.BikeModelDecoder.decode(curModel, curVin)
            if (decoded.isNotBlank()) {
                editor.putString(KEY_BIKE_MODEL_NAME, decoded)
                curModel = decoded
            }
        }
        editor.apply()
        _userPreferences.value = _userPreferences.value.copy(
            bikeModelName = curModel,
            bikeVin = curVin
        )
    }

    fun setKtrcLevel(level: String) {
        if (level.isBlank()) return
        prefs.edit().putString(KEY_KTRC_LEVEL, level).apply()
        _userPreferences.value = _userPreferences.value.copy(ktrcLevel = level)
    }

    fun setLastBikeMac(mac: String) {
        if (mac.isBlank()) return
        prefs.edit().putString(KEY_LAST_BIKE_MAC, mac).apply()
        _userPreferences.value = _userPreferences.value.copy(lastBikeMac = mac)
    }

    fun setFirstLaunchPromptShown(shown: Boolean = true) {
        prefs.edit().putBoolean(KEY_FIRST_LAUNCH_PROMPT, shown).apply()
        _userPreferences.value = _userPreferences.value.copy(hasShownFirstLaunchPrompt = shown)
    }

    fun setDisclaimerAccepted(accepted: Boolean = true) {
        prefs.edit().putBoolean(KEY_DISCLAIMER_ACCEPTED, accepted).apply()
        _userPreferences.value = _userPreferences.value.copy(hasAcceptedDisclaimer = accepted)
    }

    fun setCustomBrandName(name: String) {
        // Enforce letters and whitespace only, maximum 15 characters
        val sanitized = name.filter { it.isLetter() || it.isWhitespace() }.take(15).trim()
        val finalName = sanitized.ifBlank { "MyRide" }
        prefs.edit().putString(KEY_CUSTOM_BRAND_NAME, finalName).apply()
        _userPreferences.value = _userPreferences.value.copy(customBrandName = finalName)
    }

    fun setPreferredCurrency(currency: String) {
        val sanitized = currency.trim().take(5).ifBlank { getDefaultCurrencySymbol() }
        prefs.edit().putString(KEY_PREFERRED_CURRENCY, sanitized).apply()
        _userPreferences.value = _userPreferences.value.copy(preferredCurrency = sanitized)
    }

    fun setKeepDashboardScreenOn(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_KEEP_DASHBOARD_SCREEN_ON, enabled).apply()
        _userPreferences.value = _userPreferences.value.copy(keepDashboardScreenOn = enabled)
    }

    fun setAutoRotateDashboard(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_ROTATE_DASHBOARD, enabled).apply()
        _userPreferences.value = _userPreferences.value.copy(autoRotateDashboard = enabled)
    }

    fun updateLastTelemetry(odo: Double? = null, tripA: Float? = null, tripB: Float? = null) {
        val editor = prefs.edit()
        var curOdo = _userPreferences.value.lastOdo
        var curTripA = _userPreferences.value.lastTripA
        var curTripB = _userPreferences.value.lastTripB

        if (odo != null && odo > 0) {
            editor.putString(KEY_LAST_ODO, odo.toString())
            curOdo = odo
        }
        if (tripA != null && tripA >= 0f) {
            editor.putFloat(KEY_LAST_TRIP_A, tripA)
            curTripA = tripA
        }
        if (tripB != null && tripB >= 0f) {
            editor.putFloat(KEY_LAST_TRIP_B, tripB)
            curTripB = tripB
        }

        val now = SimpleDateFormat("dd MMM, yyyy HH:mm", Locale.getDefault()).format(Date())
        editor.putString(KEY_LAST_SYNC_DATE, now)
        editor.apply()

        _userPreferences.value = _userPreferences.value.copy(
            lastOdo = curOdo,
            lastTripA = curTripA,
            lastTripB = curTripB,
            lastSyncDateTime = now
        )
    }

    fun saveLastSessionSummary(
        timeMs: Long,
        distKm: Double,
        maxLeanL: Float,
        maxLeanR: Float,
        maxWheelie: Float,
        maxStoppie: Float,
        maxG: Float
    ) {
        prefs.edit()
            .putLong(KEY_LAST_SESSION_TIME_MS, timeMs)
            .putString(KEY_LAST_SESSION_DIST_KM, distKm.toString())
            .putFloat(KEY_LAST_SESSION_MAX_LEAN_L, maxLeanL)
            .putFloat(KEY_LAST_SESSION_MAX_LEAN_R, maxLeanR)
            .putFloat(KEY_LAST_SESSION_MAX_WHEELIE, maxWheelie)
            .putFloat(KEY_LAST_SESSION_MAX_STOPPIE, maxStoppie)
            .putFloat(KEY_LAST_SESSION_MAX_G, maxG)
            .apply()

        _userPreferences.value = _userPreferences.value.copy(
            lastSessionTimeMs = timeMs,
            lastSessionDistanceKm = distKm,
            lastSessionMaxLeanL = maxLeanL,
            lastSessionMaxLeanR = maxLeanR,
            lastSessionMaxWheelie = maxWheelie,
            lastSessionMaxStoppie = maxStoppie,
            lastSessionMaxG = maxG
        )
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _userPreferences.value = _userPreferences.value.copy(themeMode = mode)
    }

    fun setSpeedUnit(unit: SpeedUnit) {
        prefs.edit().putString(KEY_SPEED_UNIT, unit.name).apply()
        _userPreferences.value = _userPreferences.value.copy(speedUnit = unit)
    }

    fun setTemperatureUnit(unit: TemperatureUnit) {
        prefs.edit().putString(KEY_TEMP_UNIT, unit.name).apply()
        _userPreferences.value = _userPreferences.value.copy(temperatureUnit = unit)
    }

    fun setCallNotifications(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CALL_NOTIF, enabled).apply()
        _userPreferences.value = _userPreferences.value.copy(callNotifications = enabled)
    }

    fun setSmsNotifications(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SMS_NOTIF, enabled).apply()
        _userPreferences.value = _userPreferences.value.copy(smsNotifications = enabled)
    }

    fun setTurnByTurnNav(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_TBT_NAV, enabled).apply()
        _userPreferences.value = _userPreferences.value.copy(turnByTurnNav = enabled)
    }

    fun setNavSource(source: NavSource) {
        prefs.edit().putString(KEY_NAV_SOURCE, source.name).apply()
        _userPreferences.value = _userPreferences.value.copy(navSource = source)
    }

    fun setOlaMapsApiKey(key: String) {
        val trimmed = key.trim()
        val encrypted = PreferenceEncryptor.encrypt(trimmed)
        prefs.edit().putString(KEY_OLA_MAPS_API_KEY, encrypted).apply()
        _userPreferences.value = _userPreferences.value.copy(olaMapsApiKey = trimmed)
    }

    fun setOlaMapStyle(style: OlaMapStyle) {
        prefs.edit().putString(KEY_OLA_MAP_STYLE, style.styleId).apply()
        _userPreferences.value = _userPreferences.value.copy(olaMapStyle = style)
    }

    fun setAutoChooseFastestRoute(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_CHOOSE_FASTEST_ROUTE, enabled).apply()
        _userPreferences.value = _userPreferences.value.copy(autoChooseFastestRoute = enabled)
    }

    private fun loadRecentDestinations(): List<RecentDestination> {
        val jsonStr = prefs.getString(KEY_RECENT_DESTINATIONS, null) ?: return emptyList()
        val list = mutableListOf<RecentDestination>()
        val cutoff = System.currentTimeMillis() - (5L * 24 * 60 * 60 * 1000L) // 5 days expiry
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val ts = obj.optLong("timestamp", 0L)
                if (ts >= cutoff) {
                    list.add(
                        RecentDestination(
                            placeId = obj.optString("placeId"),
                            mainText = obj.optString("mainText"),
                            secondaryText = obj.optString("secondaryText"),
                            lat = obj.optDouble("lat"),
                            lng = obj.optDouble("lng"),
                            timestamp = ts,
                            routeSummary = obj.optString("routeSummary", ""),
                            routeDistance = obj.optString("routeDistance", "")
                        )
                    )
                }
            }
        } catch (_: Exception) {}
        return list.take(5)
    }

    fun reloadRecentDestinations() {
        _recentDestinations.value = loadRecentDestinations()
    }

    fun saveRecentDestination(dest: RecentDestination) {
        val current = loadRecentDestinations().toMutableList()
        current.removeAll { it.placeId == dest.placeId || (it.mainText.equals(dest.mainText, ignoreCase = true) && it.secondaryText.equals(dest.secondaryText, ignoreCase = true)) }
        current.add(0, dest.copy(timestamp = System.currentTimeMillis()))
        val trimmed = current.take(5)
        _recentDestinations.value = trimmed

        try {
            val arr = JSONArray()
            for (item in trimmed) {
                val obj = JSONObject().apply {
                    put("placeId", item.placeId)
                    put("mainText", item.mainText)
                    put("secondaryText", item.secondaryText)
                    put("lat", item.lat)
                    put("lng", item.lng)
                    put("timestamp", item.timestamp)
                    put("routeSummary", item.routeSummary)
                    put("routeDistance", item.routeDistance)
                }
                arr.put(obj)
            }
            prefs.edit().putString(KEY_RECENT_DESTINATIONS, arr.toString()).apply()
        } catch (_: Exception) {}
    }

    fun clearRecentDestinations() {
        _recentDestinations.value = emptyList()
        try {
            prefs.edit().remove(KEY_RECENT_DESTINATIONS).apply()
        } catch (_: Exception) {}
    }

    fun setPhoneBatteryMirroring(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BATTERY_MIRROR, enabled).apply()
        _userPreferences.value = _userPreferences.value.copy(phoneBatteryMirroring = enabled)
    }

    fun setDashClockSync(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DASH_CLOCK_SYNC, enabled).apply()
        _userPreferences.value = _userPreferences.value.copy(dashClockSync = enabled)
    }

    fun setDevUnlocked(unlocked: Boolean) {
        prefs.edit().putBoolean(KEY_DEV_UNLOCKED, unlocked).apply()
        _userPreferences.value = _userPreferences.value.copy(isDevUnlocked = unlocked)
    }

    fun setParkedExpiryHours(hours: Int) {
        val validHours = if (hours in listOf(1, 3, 6, 12, 24)) hours else 12
        prefs.edit().putInt(KEY_PARKED_EXPIRY_HOURS, validHours).apply()
        _userPreferences.value = _userPreferences.value.copy(parkedExpiryHours = validHours)
    }

    fun setShiftLampRpm(rpm: Int) {
        if (rpm in 3000..18000) {
            prefs.edit().putInt(KEY_SHIFT_LAMP_RPM, rpm).apply()
            _userPreferences.value = _userPreferences.value.copy(shiftLampRpm = rpm)
        }
    }

    fun clearParkedLocation() {
        prefs.edit()
            .remove(KEY_PARKED_LAT)
            .remove(KEY_PARKED_LON)
            .remove(KEY_PARKED_ACCURACY)
            .remove(KEY_PARKED_TIME)
            .remove(KEY_PARKED_TIME_FORMATTED)
            .apply()

        _userPreferences.value = _userPreferences.value.copy(
            parkedLat = 0.0,
            parkedLon = 0.0,
            parkedAccuracy = 0f,
            parkedTimeMs = 0L,
            parkedTimeFormatted = ""
        )
    }

    fun setParkedLocation(lat: Double, lon: Double, accuracy: Float, timeMs: Long = System.currentTimeMillis()) {
        val formatted = SimpleDateFormat("hh:mm a, dd MMM", Locale.getDefault()).format(Date(timeMs))
        prefs.edit()
            .putString(KEY_PARKED_LAT, lat.toString())
            .putString(KEY_PARKED_LON, lon.toString())
            .putFloat(KEY_PARKED_ACCURACY, accuracy)
            .putLong(KEY_PARKED_TIME, timeMs)
            .putString(KEY_PARKED_TIME_FORMATTED, formatted)
            .apply()

        _userPreferences.value = _userPreferences.value.copy(
            parkedLat = lat,
            parkedLon = lon,
            parkedAccuracy = accuracy,
            parkedTimeMs = timeMs,
            parkedTimeFormatted = formatted
        )
    }

    fun updateTopSpeed(speedKmh: Float) {
        if (speedKmh in 2f..299f && speedKmh > _userPreferences.value.topSpeedKmh) {
            val dateStr = SimpleDateFormat("dd MMM, yyyy HH:mm", Locale.getDefault()).format(Date())
            prefs.edit()
                .putFloat(KEY_TOP_SPEED, speedKmh)
                .putString(KEY_TOP_SPEED_DATE, dateStr)
                .apply()
            _userPreferences.value = _userPreferences.value.copy(
                topSpeedKmh = speedKmh,
                topSpeedDate = dateStr
            )
        }
    }

    fun updateZeroToHundred(timeSec: Float) {
        val currentBest = _userPreferences.value.zeroToHundredSec
        if (timeSec in 1.5f..20.0f && (currentBest <= 0f || timeSec < currentBest)) {
            val dateStr = SimpleDateFormat("dd MMM, yyyy HH:mm", Locale.getDefault()).format(Date())
            prefs.edit()
                .putFloat(KEY_ZERO_HUNDRED, timeSec)
                .putString(KEY_ZERO_HUNDRED_DATE, dateStr)
                .apply()
            _userPreferences.value = _userPreferences.value.copy(
                zeroToHundredSec = timeSec,
                zeroToHundredDate = dateStr
            )
            com.kalaiselvan.ride.logs.LogStore.logEvent("AppPreferences: Recorded new fastest 0-100 sprint: ${String.format(Locale.US, "%.2f", timeSec)}s on $dateStr")
        }
    }

    fun updateMaxG(g: Float) {
        if (g in 0.1f..4.0f && g > _userPreferences.value.maxGForce) {
            val dateStr = SimpleDateFormat("dd MMM, yyyy HH:mm", Locale.getDefault()).format(Date())
            prefs.edit()
                .putFloat(KEY_MAX_G, g)
                .putString(KEY_MAX_G_DATE, dateStr)
                .apply()
            _userPreferences.value = _userPreferences.value.copy(
                maxGForce = g,
                maxGForceDate = dateStr
            )
        }
    }

    fun updateMaxLean(left: Float, right: Float) {
        var changed = false
        var newLeft = _userPreferences.value.maxLeanLeft
        var newLeftDate = _userPreferences.value.maxLeanLeftDate
        var newRight = _userPreferences.value.maxLeanRight
        var newRightDate = _userPreferences.value.maxLeanRightDate

        val now = SimpleDateFormat("dd MMM, yyyy HH:mm", Locale.getDefault()).format(Date())
        if (left in 1f..65f && left > newLeft) {
            newLeft = left
            newLeftDate = now
            changed = true
        }
        if (right in 1f..65f && right > newRight) {
            newRight = right
            newRightDate = now
            changed = true
        }
        if (changed) {
            prefs.edit()
                .putFloat(KEY_MAX_LEAN_L, newLeft)
                .putString(KEY_MAX_LEAN_L_DATE, newLeftDate)
                .putFloat(KEY_MAX_LEAN_R, newRight)
                .putString(KEY_MAX_LEAN_R_DATE, newRightDate)
                .apply()
            _userPreferences.value = _userPreferences.value.copy(
                maxLeanLeft = newLeft,
                maxLeanLeftDate = newLeftDate,
                maxLeanRight = newRight,
                maxLeanRightDate = newRightDate
            )
        }
    }

    fun updateMaxStoppie(stoppie: Float) {
        if (stoppie > _userPreferences.value.maxStoppie) {
            val now = SimpleDateFormat("dd MMM, yyyy HH:mm", Locale.getDefault()).format(Date())
            prefs.edit()
                .putFloat(KEY_MAX_STOPPIE, stoppie)
                .putString(KEY_MAX_STOPPIE_DATE, now)
                .apply()
            _userPreferences.value = _userPreferences.value.copy(
                maxStoppie = stoppie,
                maxStoppieDate = now
            )
        }
    }

    fun updateMaxWheelie(wheelie: Float) {
        if (wheelie > _userPreferences.value.maxWheelie) {
            val now = SimpleDateFormat("dd MMM, yyyy HH:mm", Locale.getDefault()).format(Date())
            prefs.edit()
                .putFloat(KEY_MAX_WHEELIE, wheelie)
                .putString(KEY_MAX_WHEELIE_DATE, now)
                .apply()
            _userPreferences.value = _userPreferences.value.copy(
                maxWheelie = wheelie,
                maxWheelieDate = now
            )
        }
    }

    fun updateLongestRide(distKm: Double, durationSec: Long) {
        var changed = false
        var newDist = _userPreferences.value.longestDistKm
        var newDistDate = _userPreferences.value.longestDistDate
        var newDuration = _userPreferences.value.longestDurationSec
        var newDurationDate = _userPreferences.value.longestDurationDate

        val now = SimpleDateFormat("dd MMM, yyyy HH:mm", Locale.getDefault()).format(Date())
        if (distKm > newDist) {
            newDist = distKm
            newDistDate = now
            changed = true
        }
        if (durationSec > newDuration) {
            newDuration = durationSec
            newDurationDate = now
            changed = true
        }
        if (changed) {
            prefs.edit()
                .putFloat(KEY_LONGEST_DIST, newDist.toFloat())
                .putString(KEY_LONGEST_DIST_DATE, newDistDate)
                .putLong(KEY_LONGEST_TIME, newDuration)
                .putString(KEY_LONGEST_TIME_DATE, newDurationDate)
                .apply()
            _userPreferences.value = _userPreferences.value.copy(
                longestDistKm = newDist,
                longestDistDate = newDistDate,
                longestDurationSec = newDuration,
                longestDurationDate = newDurationDate
            )
        }
    }
    fun forceSetLongestRide(distKm: Double, durationSec: Long) {
        val now = SimpleDateFormat("dd MMM, yyyy HH:mm", Locale.getDefault()).format(Date())
        prefs.edit()
            .putFloat(KEY_LONGEST_DIST, distKm.toFloat())
            .putString(KEY_LONGEST_DIST_DATE, now)
            .putLong(KEY_LONGEST_TIME, durationSec)
            .putString(KEY_LONGEST_TIME_DATE, now)
            .apply()
        _userPreferences.value = _userPreferences.value.copy(
            longestDistKm = distKm,
            longestDistDate = now,
            longestDurationSec = durationSec,
            longestDurationDate = now
        )
    }
    /** Force set top speed with a custom date */
    fun forceSetTopSpeed(speedKmh: Float, dateStr: String) {
        prefs.edit()
            .putFloat(KEY_TOP_SPEED, speedKmh)
            .putString(KEY_TOP_SPEED_DATE, dateStr)
            .apply()
        _userPreferences.value = _userPreferences.value.copy(
            topSpeedKmh = speedKmh,
            topSpeedDate = dateStr
        )
    }

    /** Force set longest ride with custom dates */
    fun forceSetLongestRideWithDates(
        distKm: Double,
        distDate: String,
        durationSec: Long,
        durationDate: String
    ) {
        prefs.edit()
            .putFloat(KEY_LONGEST_DIST, distKm.toFloat())
            .putString(KEY_LONGEST_DIST_DATE, distDate)
            .putLong(KEY_LONGEST_TIME, durationSec)
            .putString(KEY_LONGEST_TIME_DATE, durationDate)
            .apply()
        _userPreferences.value = _userPreferences.value.copy(
            longestDistKm = distKm,
            longestDistDate = distDate,
            longestDurationSec = durationSec,
            longestDurationDate = durationDate
        )
        }

    /** Reset all achievement data to default (zero/empty) */
    fun resetAchievements() {
        com.kalaiselvan.ride.logs.RideSessionRecorder.clearCompletedSessions()
        // Remove stored achievement keys
        prefs.edit()
            .remove(KEY_TOP_SPEED)
            .remove(KEY_TOP_SPEED_DATE)
            .remove(KEY_ZERO_HUNDRED)
            .remove(KEY_ZERO_HUNDRED_DATE)
            .remove(KEY_MAX_G)
            .remove(KEY_MAX_G_DATE)
            .remove(KEY_MAX_LEAN_L)
            .remove(KEY_MAX_LEAN_L_DATE)
            .remove(KEY_MAX_LEAN_R)
            .remove(KEY_MAX_LEAN_R_DATE)
            .remove(KEY_MAX_WHEELIE)
            .remove(KEY_MAX_WHEELIE_DATE)
            .remove(KEY_MAX_STOPPIE)
            .remove(KEY_MAX_STOPPIE_DATE)
            .remove(KEY_LONGEST_DIST)
            .remove(KEY_LONGEST_DIST_DATE)
            .remove(KEY_LONGEST_TIME)
            .remove(KEY_LONGEST_TIME_DATE)
            .apply()

        // Update the StateFlow with cleared achievement values while preserving other settings
        _userPreferences.value = _userPreferences.value.copy(
            topSpeedKmh = 0f,
            topSpeedDate = "",
            zeroToHundredSec = 0f,
            zeroToHundredDate = "",
            maxGForce = 0f,
            maxGForceDate = "",
            maxLeanLeft = 0f,
            maxLeanLeftDate = "",
            maxLeanRight = 0f,
            maxLeanRightDate = "",
            maxWheelie = 0f,
            maxWheelieDate = "",
            maxStoppie = 0f,
            maxStoppieDate = "",
            longestDistKm = 0.0,
            longestDistDate = "",
            longestDurationSec = 0L,
            longestDurationDate = ""
        )
    }

    fun setAutoBackupEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_BACKUP_ENABLED, enabled).apply()
        _userPreferences.value = _userPreferences.value.copy(autoBackupEnabled = enabled)
    }

    fun setAutoBackupSchedule(schedule: BackupSchedule) {
        prefs.edit().putString(KEY_AUTO_BACKUP_SCHEDULE, schedule.name).apply()
        _userPreferences.value = _userPreferences.value.copy(autoBackupSchedule = schedule)
    }

    fun setAutoBackupFolder(uri: String, name: String) {
        prefs.edit()
            .putString(KEY_AUTO_BACKUP_FOLDER_URI, uri)
            .putString(KEY_AUTO_BACKUP_FOLDER_NAME, name)
            .apply()
        _userPreferences.value = _userPreferences.value.copy(
            autoBackupFolderUri = uri,
            autoBackupFolderName = name
        )
    }

    fun setAutoBackupEncrypt(encrypt: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_BACKUP_ENCRYPT, encrypt).apply()
        _userPreferences.value = _userPreferences.value.copy(autoBackupEncrypt = encrypt)
    }

    fun setAutoBackupPassword(password: String) {
        val encrypted = if (password.isNotBlank()) PreferenceEncryptor.encrypt(password) else ""
        prefs.edit().putString(KEY_AUTO_BACKUP_PASSWORD_ENC, encrypted).apply()
        _userPreferences.value = _userPreferences.value.copy(autoBackupPasswordEncrypted = encrypted)
    }

    fun getAutoBackupPassword(): String {
        val enc = _userPreferences.value.autoBackupPasswordEncrypted
        return if (enc.isNotBlank()) PreferenceEncryptor.decrypt(enc) else ""
    }

    fun setLastBackupTimestamp(timestampMs: Long = System.currentTimeMillis()) {
        prefs.edit().putLong(KEY_LAST_BACKUP_TIMESTAMP, timestampMs).apply()
        _userPreferences.value = _userPreferences.value.copy(lastBackupTimestampMs = timestampMs)
    }

    fun setRiderName(name: String) {
        prefs.edit().putString(KEY_RIDER_NAME, name).apply()
        _userPreferences.value = _userPreferences.value.copy(riderName = name)
    }

    fun setCrashDetectionEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CRASH_ENABLED, enabled).apply()
        _userPreferences.value = _userPreferences.value.copy(crashDetectionEnabled = enabled)
    }

    fun setCrashSensitivity(sensitivity: CrashSensitivity) {
        prefs.edit().putString(KEY_CRASH_SENSITIVITY, sensitivity.name).apply()
        _userPreferences.value = _userPreferences.value.copy(crashSensitivity = sensitivity)
    }

    fun setCrashCountdownSeconds(seconds: Int) {
        prefs.edit().putInt(KEY_CRASH_COUNTDOWN, seconds).apply()
        _userPreferences.value = _userPreferences.value.copy(crashCountdownSeconds = seconds)
    }

    fun setCrashSmsSubscriptionId(subId: Int) {
        prefs.edit().putInt(KEY_CRASH_SIM_ID, subId).apply()
        _userPreferences.value = _userPreferences.value.copy(crashSmsSubscriptionId = subId)
    }

    fun setCrashEmergencyContacts(contacts: List<EmergencyContact>) {
        val json = org.json.JSONArray().apply {
            contacts.forEach { c ->
                put(org.json.JSONObject().apply {
                    put("name", c.name)
                    put("number", c.number)
                })
            }
        }.toString()
        prefs.edit().putString(KEY_CRASH_CONTACTS, json).apply()
        _userPreferences.value = _userPreferences.value.copy(crashEmergencyContacts = json)
    }

    fun setAudioAlertsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUDIO_ALERTS_ENABLED, enabled).apply()
        _userPreferences.value = _userPreferences.value.copy(audioAlertsEnabled = enabled)
    }

    fun setAudioRevLimitAlert(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUDIO_REV_LIMIT, enabled).apply()
        _userPreferences.value = _userPreferences.value.copy(audioRevLimitAlert = enabled)
    }

    fun setAudioSpeedThresholdAlert(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUDIO_SPEED_THRESHOLD, enabled).apply()
        _userPreferences.value = _userPreferences.value.copy(audioSpeedThresholdAlert = enabled)
    }

    fun setAudioLeanAngleAlert(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUDIO_LEAN_ANGLE, enabled).apply()
        _userPreferences.value = _userPreferences.value.copy(audioLeanAngleAlert = enabled)
    }

    fun setAudioLeanThresholdDeg(deg: Int) {
        prefs.edit().putInt(KEY_AUDIO_LEAN_DEG, deg).apply()
        _userPreferences.value = _userPreferences.value.copy(audioLeanThresholdDeg = deg)
    }

    fun getCrashEmergencyContacts(): List<EmergencyContact> {
        val json = _userPreferences.value.crashEmergencyContacts
        return try {
            val arr = org.json.JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                EmergencyContact(
                    name = obj.optString("name", ""),
                    number = obj.optString("number", "")
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    fun markContactNotified(number: String) {
        val current = try {
            val arr = org.json.JSONArray(_userPreferences.value.crashNotifiedContacts)
            (0 until arr.length()).map { arr.getString(it) }.toMutableSet()
        } catch (_: Exception) { mutableSetOf() }
        current.add(number)
        val json = org.json.JSONArray(current.toList()).toString()
        prefs.edit().putString(KEY_CRASH_NOTIFIED, json).apply()
        _userPreferences.value = _userPreferences.value.copy(crashNotifiedContacts = json)
    }

    fun isContactNotified(number: String): Boolean {
        return try {
            val arr = org.json.JSONArray(_userPreferences.value.crashNotifiedContacts)
            (0 until arr.length()).any { arr.getString(it) == number }
        } catch (_: Exception) { false }
    }

    fun setSimPhoneOverride(subId: Int, number: String) {
        val current = try {
            org.json.JSONObject(_userPreferences.value.simPhoneOverrides)
        } catch (_: Exception) { org.json.JSONObject() }
        current.put(subId.toString(), number)
        val json = current.toString()
        prefs.edit().putString(KEY_SIM_OVERRIDES, json).apply()
        _userPreferences.value = _userPreferences.value.copy(simPhoneOverrides = json)
    }

    fun getSimPhoneOverride(subId: Int): String {
        return try {
            val json = _userPreferences.value.simPhoneOverrides
            val obj = org.json.JSONObject(json)
            obj.optString(subId.toString(), "")
        } catch (_: Exception) { "" }
    }

    /**
     * Serializes all preferences, dashboard metrics, navigation history,
     * personal best achievements, and settings into a comprehensive JSONObject for backup.
     */
    fun exportPreferencesJson(): JSONObject {
        val p = _userPreferences.value
        return JSONObject().apply {
            // 1. Dashboard (Sensitive VIN and ephemeral parked GPS coordinates omitted per privacy policy)
            put("bikeModelName", p.bikeModelName)
            put("ktrcLevel", p.ktrcLevel)
            put("lastBikeMac", p.lastBikeMac)
            put("customBrandName", p.customBrandName)
            put("shiftLampRpm", p.shiftLampRpm)
            put("lastOdo", p.lastOdo)
            put("lastTripA", p.lastTripA.toDouble())
            put("lastTripB", p.lastTripB.toDouble())
            put("lastSyncDateTime", p.lastSyncDateTime)
            put("lastSessionTimeMs", p.lastSessionTimeMs)
            put("lastSessionDistanceKm", p.lastSessionDistanceKm)
            put("lastSessionMaxLeanL", p.lastSessionMaxLeanL.toDouble())
            put("lastSessionMaxLeanR", p.lastSessionMaxLeanR.toDouble())
            put("lastSessionMaxWheelie", p.lastSessionMaxWheelie.toDouble())
            put("lastSessionMaxStoppie", p.lastSessionMaxStoppie.toDouble())
            put("lastSessionMaxG", p.lastSessionMaxG.toDouble())
            put("parkedExpiryHours", p.parkedExpiryHours)

            // 2. Maps & Navigation
            put("recentDestinationsJson", prefs.getString(KEY_RECENT_DESTINATIONS, "[]"))
            put("turnByTurnNav", p.turnByTurnNav)
            put("navSource", p.navSource.name)
            put("olaMapStyle", p.olaMapStyle.styleId)
            put("olaMapsApiKey", p.olaMapsApiKey)
            put("autoChooseFastestRoute", p.autoChooseFastestRoute)

            // 3. Personal Best Records / Achievements
            put("topSpeedKmh", p.topSpeedKmh.toDouble())
            put("topSpeedDate", p.topSpeedDate)
            put("zeroToHundredSec", p.zeroToHundredSec.toDouble())
            put("zeroToHundredDate", p.zeroToHundredDate)
            put("maxGForce", p.maxGForce.toDouble())
            put("maxGForceDate", p.maxGForceDate)
            put("maxLeanLeft", p.maxLeanLeft.toDouble())
            put("maxLeanLeftDate", p.maxLeanLeftDate)
            put("maxLeanRight", p.maxLeanRight.toDouble())
            put("maxLeanRightDate", p.maxLeanRightDate)
            put("maxWheelie", p.maxWheelie.toDouble())
            put("maxWheelieDate", p.maxWheelieDate)
            put("maxStoppie", p.maxStoppie.toDouble())
            put("maxStoppieDate", p.maxStoppieDate)
            put("longestDistKm", p.longestDistKm)
            put("longestDistDate", p.longestDistDate)
            put("longestDurationSec", p.longestDurationSec)
            put("longestDurationDate", p.longestDurationDate)

            // 4. Settings & Profile
            put("riderName", p.riderName)
            put("themeMode", p.themeMode.name)
            put("speedUnit", p.speedUnit.name)
            put("temperatureUnit", p.temperatureUnit.name)
            put("preferredCurrency", p.preferredCurrency)
            put("callNotifications", p.callNotifications)
            put("smsNotifications", p.smsNotifications)
            put("phoneBatteryMirroring", p.phoneBatteryMirroring)
            put("dashClockSync", p.dashClockSync)
            put("keepDashboardScreenOn", p.keepDashboardScreenOn)
            put("autoRotateDashboard", p.autoRotateDashboard)

            // Crash Detection & Emergency SOS
            put("crashDetectionEnabled", p.crashDetectionEnabled)
            put("crashSensitivity", p.crashSensitivity.name)
            put("crashCountdownSeconds", p.crashCountdownSeconds)
            put("crashSmsSubscriptionId", p.crashSmsSubscriptionId)
            put("crashEmergencyContacts", p.crashEmergencyContacts)
            put("crashNotifiedContacts", p.crashNotifiedContacts)
            put("simPhoneOverrides", p.simPhoneOverrides)
        }
    }

    /**
     * Atomically restores all preferences, dashboard metrics, destinations,
     * achievements, and settings from a backup JSONObject.
     */
    fun applyBackupPreferences(json: JSONObject) {
        val editor = prefs.edit()

        // 1. Dashboard
        if (json.has("bikeModelName")) {
            val name = json.optString("bikeModelName", "")
            if (name.isNotBlank()) editor.putString(KEY_BIKE_MODEL_NAME, name)
        }
        if (json.has("bikeVin")) {
            val vin = json.optString("bikeVin", "")
            if (vin.isNotBlank()) editor.putString(KEY_BIKE_VIN, vin)
        }
        if (json.has("ktrcLevel")) {
            editor.putString(KEY_KTRC_LEVEL, json.optString("ktrcLevel", "1"))
        }
        if (json.has("lastBikeMac")) {
            val mac = json.optString("lastBikeMac", "")
            if (mac.isNotBlank()) editor.putString(KEY_LAST_BIKE_MAC, mac)
        }
        if (json.has("customBrandName")) {
            val brand = json.optString("customBrandName", "MyRide").filter { it.isLetter() || it.isWhitespace() }.take(15).trim()
            editor.putString(KEY_CUSTOM_BRAND_NAME, brand.ifBlank { "MyRide" })
        }
        if (json.has("shiftLampRpm")) {
            val rpm = json.optInt("shiftLampRpm", 0)
            if (rpm in 3000..18000) editor.putInt(KEY_SHIFT_LAMP_RPM, rpm)
        }
        if (json.has("lastOdo")) {
            editor.putString(KEY_LAST_ODO, json.optDouble("lastOdo", 0.0).toString())
        }
        if (json.has("lastTripA")) {
            editor.putFloat(KEY_LAST_TRIP_A, json.optDouble("lastTripA", 0.0).toFloat())
        }
        if (json.has("lastTripB")) {
            editor.putFloat(KEY_LAST_TRIP_B, json.optDouble("lastTripB", 0.0).toFloat())
        }
        if (json.has("lastSyncDateTime")) {
            val syncDate = json.optString("lastSyncDateTime", "")
            if (syncDate.isNotBlank()) editor.putString(KEY_LAST_SYNC_DATE, syncDate)
        }
        if (json.has("lastSessionTimeMs")) {
            editor.putLong(KEY_LAST_SESSION_TIME_MS, json.optLong("lastSessionTimeMs", 0L))
        }
        if (json.has("lastSessionDistanceKm")) {
            editor.putString(KEY_LAST_SESSION_DIST_KM, json.optDouble("lastSessionDistanceKm", 0.0).toString())
        }
        if (json.has("lastSessionMaxLeanL")) {
            editor.putFloat(KEY_LAST_SESSION_MAX_LEAN_L, json.optDouble("lastSessionMaxLeanL", 0.0).toFloat())
        }
        if (json.has("lastSessionMaxLeanR")) {
            editor.putFloat(KEY_LAST_SESSION_MAX_LEAN_R, json.optDouble("lastSessionMaxLeanR", 0.0).toFloat())
        }
        if (json.has("lastSessionMaxWheelie")) {
            editor.putFloat(KEY_LAST_SESSION_MAX_WHEELIE, json.optDouble("lastSessionMaxWheelie", 0.0).toFloat())
        }
        if (json.has("lastSessionMaxStoppie")) {
            editor.putFloat(KEY_LAST_SESSION_MAX_STOPPIE, json.optDouble("lastSessionMaxStoppie", 0.0).toFloat())
        }
        if (json.has("lastSessionMaxG")) {
            editor.putFloat(KEY_LAST_SESSION_MAX_G, json.optDouble("lastSessionMaxG", 0.0).toFloat())
        }

        // Parked bike location memory
        val pLat = json.optDouble("parkedLat", 0.0)
        val pLon = json.optDouble("parkedLon", 0.0)
        val pTime = json.optLong("parkedTimeMs", 0L)
        if (pLat != 0.0 && pLon != 0.0 && pTime > 0L) {
            editor.putString(KEY_PARKED_LAT, pLat.toString())
            editor.putString(KEY_PARKED_LON, pLon.toString())
            editor.putFloat(KEY_PARKED_ACCURACY, json.optDouble("parkedAccuracy", 0.0).toFloat())
            editor.putLong(KEY_PARKED_TIME, pTime)
            val pFormatted = json.optString("parkedTimeFormatted", "").ifBlank {
                SimpleDateFormat("hh:mm a, dd MMM", Locale.getDefault()).format(Date(pTime))
            }
            editor.putString(KEY_PARKED_TIME_FORMATTED, pFormatted)
        }
        if (json.has("parkedExpiryHours")) {
            val exp = json.optInt("parkedExpiryHours", 12)
            editor.putInt(KEY_PARKED_EXPIRY_HOURS, if (exp in listOf(1, 3, 6, 12, 24)) exp else 12)
        }

        // 2. Maps & Navigation
        if (json.has("recentDestinationsJson")) {
            val raw = json.optString("recentDestinationsJson", "[]")
            try {
                val arr = JSONArray(raw)
                val now = System.currentTimeMillis()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    obj.put("timestamp", now)
                }
                editor.putString(KEY_RECENT_DESTINATIONS, arr.toString())
            } catch (_: Exception) {
                editor.putString(KEY_RECENT_DESTINATIONS, raw)
            }
        }
        if (json.has("turnByTurnNav")) editor.putBoolean(KEY_TBT_NAV, json.optBoolean("turnByTurnNav", true))
        if (json.has("navSource")) {
            val src = json.optString("navSource", NavSource.DEFAULT_MAP.name)
            try { NavSource.valueOf(src); editor.putString(KEY_NAV_SOURCE, src) } catch (_: Exception) {}
        }
        if (json.has("olaMapStyle")) {
            editor.putString(KEY_OLA_MAP_STYLE, json.optString("olaMapStyle", OlaMapStyle.OLA_DARK.styleId))
        }
        if (json.has("olaMapsApiKey")) {
            val rawKey = json.optString("olaMapsApiKey", "").trim()
            if (rawKey.isNotBlank()) {
                editor.putString(KEY_OLA_MAPS_API_KEY, PreferenceEncryptor.encrypt(rawKey))
            }
        }
        if (json.has("autoChooseFastestRoute")) {
            editor.putBoolean(KEY_AUTO_CHOOSE_FASTEST_ROUTE, json.optBoolean("autoChooseFastestRoute", true))
        }

        // 3. Personal Best Records / Achievements
        ACHIEVEMENT_BACKUP_FIELDS.forEach { field ->
            if (json.has(field.valueKey)) {
                if (field.isLong) {
                    val v = json.optLong(field.valueKey, 0L)
                    if (v > 0L) {
                        editor.putLong(field.prefValueKey, v)
                        editor.putString(field.prefDateKey, json.optString(field.dateKey, ""))
                    }
                } else {
                    val v = json.optDouble(field.valueKey, 0.0).toFloat()
                    if (v > 0f) {
                        editor.putFloat(field.prefValueKey, v)
                        editor.putString(field.prefDateKey, json.optString(field.dateKey, ""))
                    }
                }
            }
        }

        // 4. Settings & Profile
        if (json.has("riderName")) {
            editor.putString(KEY_RIDER_NAME, json.optString("riderName", ""))
        }
        if (json.has("themeMode")) {
            val tm = json.optString("themeMode", ThemeMode.DARK.name)
            editor.putString(KEY_THEME_MODE, ThemeMode.fromName(tm).name)
        }
        if (json.has("speedUnit")) {
            try {
                val su = SpeedUnit.valueOf(json.optString("speedUnit"))
                editor.putString(KEY_SPEED_UNIT, su.name)
            } catch (_: Exception) {}
        }
        if (json.has("temperatureUnit")) {
            try {
                val tu = TemperatureUnit.valueOf(json.optString("temperatureUnit"))
                editor.putString(KEY_TEMP_UNIT, tu.name)
            } catch (_: Exception) {}
        }
        if (json.has("preferredCurrency")) {
            val curr = json.optString("preferredCurrency", getDefaultCurrencySymbol()).trim().take(5)
            editor.putString(KEY_PREFERRED_CURRENCY, curr.ifBlank { getDefaultCurrencySymbol() })
        }
        if (json.has("callNotifications")) editor.putBoolean(KEY_CALL_NOTIF, json.optBoolean("callNotifications", true))
        if (json.has("smsNotifications")) editor.putBoolean(KEY_SMS_NOTIF, json.optBoolean("smsNotifications", true))
        if (json.has("phoneBatteryMirroring")) editor.putBoolean(KEY_BATTERY_MIRROR, json.optBoolean("phoneBatteryMirroring", true))
        if (json.has("dashClockSync")) editor.putBoolean(KEY_DASH_CLOCK_SYNC, json.optBoolean("dashClockSync", true))
        if (json.has("keepDashboardScreenOn")) editor.putBoolean(KEY_KEEP_DASHBOARD_SCREEN_ON, json.optBoolean("keepDashboardScreenOn", true))
        if (json.has("autoRotateDashboard")) editor.putBoolean(KEY_AUTO_ROTATE_DASHBOARD, json.optBoolean("autoRotateDashboard", false))

        // Crash Detection & Emergency SOS
        if (json.has("crashDetectionEnabled")) editor.putBoolean(KEY_CRASH_ENABLED, json.optBoolean("crashDetectionEnabled", false))
        if (json.has("crashSensitivity")) {
            val cs = json.optString("crashSensitivity", CrashSensitivity.MEDIUM.name)
            editor.putString(KEY_CRASH_SENSITIVITY, CrashSensitivity.fromName(cs).name)
        }
        if (json.has("crashCountdownSeconds")) editor.putInt(KEY_CRASH_COUNTDOWN, json.optInt("crashCountdownSeconds", 30))
        if (json.has("crashSmsSubscriptionId")) editor.putInt(KEY_CRASH_SIM_ID, json.optInt("crashSmsSubscriptionId", -1))
        if (json.has("crashEmergencyContacts")) editor.putString(KEY_CRASH_CONTACTS, json.optString("crashEmergencyContacts", "[]"))
        if (json.has("crashNotifiedContacts")) editor.putString(KEY_CRASH_NOTIFIED, json.optString("crashNotifiedContacts", "[]"))
        if (json.has("simPhoneOverrides")) editor.putString(KEY_SIM_OVERRIDES, json.optString("simPhoneOverrides", "{}"))

        editor.apply()

        // Atomically refresh active in-memory StateFlows
        _userPreferences.value = loadPreferences()
        _recentDestinations.value = loadRecentDestinations()
    }
}

