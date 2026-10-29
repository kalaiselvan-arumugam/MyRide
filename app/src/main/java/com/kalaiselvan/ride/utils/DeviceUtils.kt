package com.kalaiselvan.ride.utils

import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings

object DeviceUtils {

    enum class DeviceFamily {
        XIAOMI_HYPEROS_MIUI,
        SAMSUNG,
        ONEPLUS_OPPO_REALME,
        VIVO_IQOO,
        GENERIC_STOCK
    }

    fun getDeviceFamily(): DeviceFamily {
        val mfr = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        return when {
            mfr.contains("xiaomi") || brand.contains("redmi") || brand.contains("poco") || mfr.contains("blackshark") ->
                DeviceFamily.XIAOMI_HYPEROS_MIUI
            mfr.contains("samsung") ->
                DeviceFamily.SAMSUNG
            mfr.contains("oneplus") || mfr.contains("oppo") || mfr.contains("realme") ->
                DeviceFamily.ONEPLUS_OPPO_REALME
            mfr.contains("vivo") || brand.contains("iqoo") ->
                DeviceFamily.VIVO_IQOO
            else ->
                DeviceFamily.GENERIC_STOCK
        }
    }

    fun isXiaomiOrHyperOs(): Boolean {
        return getDeviceFamily() == DeviceFamily.XIAOMI_HYPEROS_MIUI
    }

    fun getRomDisplayName(): String {
        return when (getDeviceFamily()) {
            DeviceFamily.XIAOMI_HYPEROS_MIUI -> "Xiaomi (HyperOS / MIUI)"
            DeviceFamily.SAMSUNG -> "Samsung (One UI)"
            DeviceFamily.ONEPLUS_OPPO_REALME -> "OnePlus / Oppo / Realme"
            DeviceFamily.VIVO_IQOO -> "Vivo / iQOO"
            DeviceFamily.GENERIC_STOCK -> "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} (Android ${Build.VERSION.RELEASE})"
        }
    }

    /**
     * Checks if Xiaomi background pop-up window permission is granted (AppOps OP_BACKGROUND_START_ACTIVITY = 10021).
     * On non-Xiaomi devices, this returns true.
     */
    fun isXiaomiPopUpGranted(context: Context): Boolean {
        if (!isXiaomiOrHyperOs()) return true
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return true
            val method = AppOpsManager::class.java.getMethod(
                "checkOpNoThrow",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                String::class.java
            )
            val result = method.invoke(appOps, 10021, Process.myUid(), context.packageName) as? Int
            result == AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) {
            true
        }
    }

    fun openXiaomiPopUpSettings(context: Context) {
        try {
            val intent = Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                setClassName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.permissions.PermissionsEditorActivity"
                )
                putExtra("extra_pkgname", context.packageName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            openAppDetails(context)
        }
    }

    fun openAutostartSettings(context: Context) {
        val family = getDeviceFamily()
        val intent = when (family) {
            DeviceFamily.XIAOMI_HYPEROS_MIUI -> {
                Intent().apply {
                    component = ComponentName(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.autostart.AutoStartManagementActivity"
                    )
                }
            }
            DeviceFamily.ONEPLUS_OPPO_REALME -> {
                Intent().apply {
                    component = ComponentName(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                    )
                }
            }
            DeviceFamily.VIVO_IQOO -> {
                Intent().apply {
                    component = ComponentName(
                        "com.iqoo.secure",
                        "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"
                    )
                }
            }
            else -> null
        }

        if (intent != null) {
            try {
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                return
            } catch (_: Exception) {}
        }

        openAppDetails(context)
    }

    fun openOverlaySettings(context: Context) {
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            return
        } catch (_: Exception) {}
        openAppDetails(context)
    }

    fun openAppDetails(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }
}
