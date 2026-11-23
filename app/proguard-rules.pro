# ==============================================================================
# MyRide - Proguard / R8 Optimization & Obfuscation Rules
# ==============================================================================

# Keep Jetpack Compose runtime & compiler metadata
-keepclassmembers class * {
    @androidx.compose.runtime.Composable *;
}

# Keep WebView JavaScript Interface methods (Critical for VisualMapView Leaflet bridge)
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep Android KeyStore and cryptographic classes
-keep class com.kalaiselvan.ride.utils.PreferenceEncryptor { *; }

# Keep data models used in reflection or JSON serialization
-keep class com.kalaiselvan.ride.maintenance.ServiceRecord { *; }
-keep class com.kalaiselvan.ride.preferences.UserPreferences { *; }
-keep class com.kalaiselvan.ride.telemetry.TelemetryState { *; }
-keep class com.kalaiselvan.ride.logs.HexFrameEntry { *; }
-keep class com.kalaiselvan.ride.logs.RideSession { *; }
-keep class com.kalaiselvan.ride.navigation.ResolvedLocation { *; }

# Keep Android NotificationListenerService & CompanionDeviceService
-keep public class com.kalaiselvan.ride.notifications.BikeNotificationListenerService extends android.service.notification.NotificationListenerService
-keep public class com.kalaiselvan.ride.bluetooth.BikePresenceService extends android.companion.CompanionDeviceService
-keep public class com.kalaiselvan.ride.bluetooth.BikeForegroundService extends android.app.Service


