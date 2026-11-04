package com.kalaiselvan.ride.backup

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.kalaiselvan.ride.logs.LogStore
import com.kalaiselvan.ride.maintenance.MaintenanceStore
import com.kalaiselvan.ride.maintenance.ServiceRecord
import com.kalaiselvan.ride.preferences.AppPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Manifest information describing a backup archive.
 */
data class BackupManifest(
    val backupVersion: Int = 1,
    val appVersion: String = "1.0.0",
    val createdAtMs: Long = System.currentTimeMillis(),
    val serviceRecordCount: Int = 0,
    val receiptFileCount: Int = 0,
    val hasSettings: Boolean = true,
    val hasRides: Boolean = true,
    val isEncrypted: Boolean = false
) {
    val formattedDate: String
        get() = SimpleDateFormat("dd MMM, yyyy • HH:mm", Locale.getDefault()).format(Date(createdAtMs))

    fun toJsonObject(): JSONObject = JSONObject().apply {
        put("backupVersion", backupVersion)
        put("appVersion", appVersion)
        put("createdAtMs", createdAtMs)
        put("serviceRecordCount", serviceRecordCount)
        put("receiptFileCount", receiptFileCount)
        put("hasSettings", hasSettings)
        put("hasRides", hasRides)
        put("isEncrypted", isEncrypted)
    }

    companion object {
        fun fromJsonObject(json: JSONObject): BackupManifest {
            return BackupManifest(
                backupVersion = json.optInt("backupVersion", 1),
                appVersion = json.optString("appVersion", "1.0.0"),
                createdAtMs = json.optLong("createdAtMs", System.currentTimeMillis()),
                serviceRecordCount = json.optInt("serviceRecordCount", 0),
                receiptFileCount = json.optInt("receiptFileCount", 0),
                hasSettings = json.optBoolean("hasSettings", true),
                hasRides = json.optBoolean("hasRides", true),
                isEncrypted = json.optBoolean("isEncrypted", false)
            )
        }
    }
}

sealed class BackupInspectionResult {
    data class Success(val manifest: BackupManifest, val isEncrypted: Boolean) : BackupInspectionResult()
    object PasswordRequired : BackupInspectionResult()
    object InvalidPassword : BackupInspectionResult()
    data class Error(val message: String) : BackupInspectionResult()
}

sealed class RestoreResult {
    data class Success(
        val serviceRecordsRestored: Int,
        val receiptFilesRestored: Int,
        val settingsRestored: Boolean
    ) : RestoreResult()
    object InvalidPassword : RestoreResult()
    data class Error(val message: String) : RestoreResult()
}

/**
 * Robust, 100% clean-room backup and restore manager for MyRide Telemetry.
 *
 * Supports streaming AES-256-GCM authenticated encryption with PBKDF2 key derivation,
 * unlimited file sizes for large PDFs & photos, and zero OutOfMemory memory overhead.
 */
object BackupManager {

    private const val TAG = "BackupManager"
    private val MAGIC_HEADER_V1 = "MYRIDE_BKP_V1\n".toByteArray(Charsets.UTF_8)
    private val MAGIC_HEADER_V2 = "MYRIDE_BKP_V2\n".toByteArray(Charsets.UTF_8)
    private const val FLAG_PLAIN: Byte = 0x00
    private const val FLAG_ENCRYPTED: Byte = 0x01
    private const val SALT_LENGTH = 16
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128
    private const val VERIFIER_LENGTH = 16
    private val VERIFIER_PAYLOAD = "MYRIDE_AUTH_TOKEN_V2".toByteArray(Charsets.UTF_8)
    private const val PBKDF2_ITERATIONS = 100_000
    private const val PBKDF2_LEGACY_V2_ITERATIONS = 5_000
    private const val PBKDF2_LEGACY_ITERATIONS = 100_000
    private const val KEY_LENGTH_BITS = 256

    /**
     * Non-draining wrapper around InputStream to prevent CipherInputStream from
     * reading through millions of bytes of attachments to EOF on close during inspection.
     */
    private class NonDrainingInputStream(private val delegate: InputStream) : InputStream() {
        override fun read(): Int = delegate.read()
        override fun read(b: ByteArray, off: Int, len: Int): Int = delegate.read(b, off, len)
        override fun available(): Int = delegate.available()
        override fun close() {
            // Intentionally NO-OP. Do not delegate close to CipherInputStream,
            // which otherwise drains the entire archive to EOF to verify the AEAD tag.
        }
    }

    // Buffer size for streaming IO
    private const val BUFFER_SIZE = 64 * 1024 // 64 KB

    /**
     * Derives a 256-bit AES key from a user password using PBKDF2WithHmacSHA256.
     */
    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int = PBKDF2_ITERATIONS): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password, salt, iterations, KEY_LENGTH_BITS)
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    private fun computeVerifierToken(secretKey: SecretKeySpec): ByteArray {
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(secretKey)
        return mac.doFinal(VERIFIER_PAYLOAD).copyOf(VERIFIER_LENGTH)
    }

    /**
     * Exports all application data (maintenance records, receipts, preferences) into an OutputStream.
     */
    fun createBackup(
        context: Context,
        outputStream: OutputStream,
        password: String? = null,
        onProgress: ((progress: Float, status: String) -> Unit)? = null
    ): Boolean {
        return try {
            onProgress?.invoke(0.05f, "Preparing backup...")
            val isEncrypted = !password.isNullOrBlank()

            // 1. Write Magic Header (V2 format with fast verification token)
            outputStream.write(MAGIC_HEADER_V2)
            outputStream.write(if (isEncrypted) FLAG_ENCRYPTED.toInt() else FLAG_PLAIN.toInt())

            var targetStream: OutputStream = outputStream

            if (isEncrypted) {
                onProgress?.invoke(0.12f, "Securing backup with password...")
                val secureRandom = SecureRandom()
                val salt = ByteArray(SALT_LENGTH).apply { secureRandom.nextBytes(this) }
                val iv = ByteArray(GCM_IV_LENGTH).apply { secureRandom.nextBytes(this) }

                val secretKey = deriveKey(password.toCharArray(), salt, PBKDF2_ITERATIONS)
                val verifierToken = computeVerifierToken(secretKey)

                // Write Salt, IV, and 16-byte Verifier Token in header
                outputStream.write(salt)
                outputStream.write(iv)
                outputStream.write(verifierToken)

                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
                cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)

                targetStream = CipherOutputStream(outputStream, cipher)
            }

            // 2. Package into ZIP stream
            val zipOut = ZipOutputStream(BufferedOutputStream(targetStream, BUFFER_SIZE))
            try {
                val maintenanceStore = MaintenanceStore.getInstance(context)
                val serviceRecords = maintenanceStore.records.value
                val receiptsDir = File(context.filesDir, "receipts")
                val receiptFiles = receiptsDir.listFiles()?.filter { it.isFile } ?: emptyList()

                // 2a. Write manifest.json
                onProgress?.invoke(0.20f, "Preparing backup details...")
                val manifest = BackupManifest(
                    createdAtMs = System.currentTimeMillis(),
                    serviceRecordCount = serviceRecords.size,
                    receiptFileCount = receiptFiles.size,
                    hasSettings = true,
                    hasRides = true,
                    isEncrypted = isEncrypted
                )
                zipOut.putNextEntry(ZipEntry("manifest.json"))
                zipOut.write(manifest.toJsonObject().toString(2).toByteArray(Charsets.UTF_8))
                zipOut.closeEntry()

                // 2b. Write maintenance_records.json
                onProgress?.invoke(0.30f, "Backing up ${serviceRecords.size} service records...")
                val maintenanceFile = File(context.filesDir, "maintenance_records.json")
                if (maintenanceFile.exists()) {
                    zipOut.putNextEntry(ZipEntry("maintenance_records.json"))
                    maintenanceFile.inputStream().buffered().use { input ->
                        input.copyTo(zipOut, BUFFER_SIZE)
                    }
                    zipOut.closeEntry()
                }

                // 2c. Write receipts directory (images, PDFs)
                val totalReceipts = receiptFiles.size
                receiptFiles.forEachIndexed { idx, file ->
                    val progressFraction = 0.35f + ((idx + 1).toFloat() / totalReceipts.coerceAtLeast(1).toFloat()) * 0.50f
                    onProgress?.invoke(progressFraction, "Backing up photo ${idx + 1} of $totalReceipts...")
                    zipOut.putNextEntry(ZipEntry("receipts/${file.name}"))
                    file.inputStream().buffered().use { input ->
                        input.copyTo(zipOut, BUFFER_SIZE)
                    }
                    zipOut.closeEntry()
                }

                // 2d. Write preferences.json
                onProgress?.invoke(0.90f, "Backing up app settings...")
                val appPrefs = AppPreferences.getInstance(context)
                val prefs = appPrefs.userPreferences.value
                val prefsJson = JSONObject().apply {
                    put("speedUnit", prefs.speedUnit.name)
                    put("temperatureUnit", prefs.temperatureUnit.name)
                    put("preferredCurrency", prefs.preferredCurrency)
                    put("customBrandName", prefs.customBrandName)
                    put("callNotifications", prefs.callNotifications)
                    put("smsNotifications", prefs.smsNotifications)
                    put("turnByTurnNav", prefs.turnByTurnNav)
                    put("navSource", prefs.navSource.name)
                    put("olaMapStyle", prefs.olaMapStyle.styleId)
                    put("phoneBatteryMirroring", prefs.phoneBatteryMirroring)
                    put("dashClockSync", prefs.dashClockSync)
                    put("keepDashboardScreenOn", prefs.keepDashboardScreenOn)
                    put("parkedLat", prefs.parkedLat)
                    put("parkedLon", prefs.parkedLon)
                    put("parkedAccuracy", prefs.parkedAccuracy.toDouble())
                    put("parkedTimeMs", prefs.parkedTimeMs)
                    put("parkedTimeFormatted", prefs.parkedTimeFormatted)
                    put("bikeModelName", prefs.bikeModelName)
                    // Personal bests
                    put("topSpeedKmh", prefs.topSpeedKmh.toDouble())
                    put("topSpeedDate", prefs.topSpeedDate)
                    put("maxLeanLeft", prefs.maxLeanLeft.toDouble())
                    put("maxLeanLeftDate", prefs.maxLeanLeftDate)
                    put("maxLeanRight", prefs.maxLeanRight.toDouble())
                    put("maxLeanRightDate", prefs.maxLeanRightDate)
                    put("longestDistKm", prefs.longestDistKm)
                    put("longestDistDate", prefs.longestDistDate)
                }
                zipOut.putNextEntry(ZipEntry("preferences.json"))
                zipOut.write(prefsJson.toString(2).toByteArray(Charsets.UTF_8))
                zipOut.closeEntry()

                onProgress?.invoke(0.95f, "Finalizing backup...")
                zipOut.finish()
                zipOut.flush()
            } finally {
                // Critical: closing zipOut closes BufferedOutputStream and CipherOutputStream,
                // which triggers cipher.doFinal() and writes all encrypted blocks + GCM tag.
                zipOut.close()
            }
            onProgress?.invoke(1.0f, "Backup complete!")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create backup: ${e.message}", e)
            false
        }
    }

    /**
     * Inspects a backup archive header and reads its manifest without fully extracting the archive.
     */
    fun inspectBackup(
        inputStream: InputStream,
        password: String? = null
    ): BackupInspectionResult {
        return try {
            val bufferedIn = BufferedInputStream(inputStream, BUFFER_SIZE)

            // Read magic header
            val magic = ByteArray(MAGIC_HEADER_V2.size)
            bufferedIn.read(magic)
            val isV2 = magic.contentEquals(MAGIC_HEADER_V2)
            val isV1 = magic.contentEquals(MAGIC_HEADER_V1)
            if (!isV2 && !isV1) {
                return BackupInspectionResult.Error("Not a valid MyRide backup file")
            }

            val flag = bufferedIn.read()
            val isEncrypted = (flag == FLAG_ENCRYPTED.toInt())

            var streamToRead: InputStream = bufferedIn

            if (isEncrypted) {
                if (password.isNullOrBlank()) {
                    return BackupInspectionResult.PasswordRequired
                }

                val salt = ByteArray(SALT_LENGTH)
                bufferedIn.read(salt)
                val iv = ByteArray(GCM_IV_LENGTH)
                bufferedIn.read(iv)

                val secretKey: SecretKeySpec
                if (isV2) {
                    val expectedToken = ByteArray(VERIFIER_LENGTH)
                    bufferedIn.read(expectedToken)

                    var key = deriveKey(password.toCharArray(), salt, PBKDF2_ITERATIONS)
                    var computedToken = computeVerifierToken(key)
                    if (!computedToken.contentEquals(expectedToken)) {
                        // Check legacy 5k iteration v2 format
                        val legacyKey = deriveKey(password.toCharArray(), salt, PBKDF2_LEGACY_V2_ITERATIONS)
                        val legacyToken = computeVerifierToken(legacyKey)
                        if (legacyToken.contentEquals(expectedToken)) {
                            key = legacyKey
                        } else {
                            return BackupInspectionResult.InvalidPassword
                        }
                    }
                    secretKey = key
                } else {
                    // Legacy V1 without verifier token
                    secretKey = deriveKey(password.toCharArray(), salt, PBKDF2_LEGACY_ITERATIONS)
                }

                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)

                streamToRead = CipherInputStream(bufferedIn, cipher)
            }

            val nonDrainingStream = NonDrainingInputStream(streamToRead)
            val zipIn = ZipInputStream(nonDrainingStream)
            var manifest: BackupManifest? = null
            try {
                var entry: ZipEntry? = zipIn.nextEntry
                while (entry != null) {
                    if (entry.name == "manifest.json") {
                        val out = ByteArrayOutputStream()
                        val buf = ByteArray(1024)
                        var count: Int
                        while (zipIn.read(buf).also { count = it } != -1) {
                            out.write(buf, 0, count)
                        }
                        val jsonStr = out.toString(Charsets.UTF_8.name())
                        manifest = BackupManifest.fromJsonObject(JSONObject(jsonStr))
                        break
                    }
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
            } catch (e: Exception) {
                return BackupInspectionResult.InvalidPassword
            } finally {
                try { zipIn.close() } catch (_: Exception) {}
                try { bufferedIn.close() } catch (_: Exception) {}
            }

            if (manifest != null) {
                BackupInspectionResult.Success(manifest, isEncrypted)
            } else {
                if (isEncrypted) BackupInspectionResult.InvalidPassword
                else BackupInspectionResult.Error("Backup manifest not found in archive")
            }
        } catch (e: javax.crypto.AEADBadTagException) {
            BackupInspectionResult.InvalidPassword
        } catch (e: Exception) {
            val msg = e.message ?: ""
            if (msg.contains("tag", ignoreCase = true) || msg.contains("cipher", ignoreCase = true) || msg.contains("padding", ignoreCase = true)) {
                BackupInspectionResult.InvalidPassword
            } else {
                BackupInspectionResult.Error("Failed to inspect backup: ${e.message}")
            }
        }
    }

    /**
     * Restores application data from a backup archive stream.
     */
    fun restoreBackup(
        context: Context,
        inputStream: InputStream,
        password: String? = null,
        onProgress: ((progress: Float, status: String) -> Unit)? = null
    ): RestoreResult {
        return try {
            onProgress?.invoke(0.05f, "Checking backup file...")
            val bufferedIn = BufferedInputStream(inputStream, BUFFER_SIZE)

            // Read magic header
            val magic = ByteArray(MAGIC_HEADER_V2.size)
            bufferedIn.read(magic)
            val isV2 = magic.contentEquals(MAGIC_HEADER_V2)
            val isV1 = magic.contentEquals(MAGIC_HEADER_V1)
            if (!isV2 && !isV1) {
                return RestoreResult.Error("Not a valid MyRide backup file")
            }

            val flag = bufferedIn.read()
            val isEncrypted = (flag == FLAG_ENCRYPTED.toInt())

            var streamToRead: InputStream = bufferedIn

            if (isEncrypted) {
                if (password.isNullOrBlank()) {
                    return RestoreResult.InvalidPassword
                }

                onProgress?.invoke(0.12f, "Unlocking backup...")
                val salt = ByteArray(SALT_LENGTH)
                bufferedIn.read(salt)
                val iv = ByteArray(GCM_IV_LENGTH)
                bufferedIn.read(iv)

                val secretKey: SecretKeySpec
                if (isV2) {
                    val expectedToken = ByteArray(VERIFIER_LENGTH)
                    bufferedIn.read(expectedToken)

                    var key = deriveKey(password.toCharArray(), salt, PBKDF2_ITERATIONS)
                    var computedToken = computeVerifierToken(key)
                    if (!computedToken.contentEquals(expectedToken)) {
                        // Check legacy 5k iteration v2 format
                        val legacyKey = deriveKey(password.toCharArray(), salt, PBKDF2_LEGACY_V2_ITERATIONS)
                        val legacyToken = computeVerifierToken(legacyKey)
                        if (legacyToken.contentEquals(expectedToken)) {
                            key = legacyKey
                        } else {
                            return RestoreResult.InvalidPassword
                        }
                    }
                    secretKey = key
                } else {
                    secretKey = deriveKey(password.toCharArray(), salt, PBKDF2_LEGACY_ITERATIONS)
                }

                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)

                streamToRead = CipherInputStream(bufferedIn, cipher)
            }

            val zipIn = ZipInputStream(streamToRead)
            val receiptsDir = File(context.filesDir, "receipts").apply { mkdirs() }

            var serviceRecordsRestored = 0
            var receiptFilesRestored = 0
            var settingsRestored = false

            try {
                var entry: ZipEntry? = zipIn.nextEntry
                while (entry != null) {
                    val name = entry.name
                    when {
                        name == "maintenance_records.json" -> {
                            onProgress?.invoke(0.35f, "Restoring maintenance log...")
                            val destFile = File(context.filesDir, "maintenance_records.json")
                            val outBytes = ByteArrayOutputStream()
                            zipIn.copyTo(outBytes)
                            val rawJson = outBytes.toString(Charsets.UTF_8.name())
                            try {
                                val arr = JSONArray(rawJson)
                                serviceRecordsRestored = arr.length()
                                // Re-point receipt paths to this installation's filesDir
                                for (i in 0 until arr.length()) {
                                    val obj = arr.getJSONObject(i)
                                    if (obj.has("receiptImagePaths")) {
                                        val paths = obj.getJSONArray("receiptImagePaths")
                                        val normalized = JSONArray()
                                        for (j in 0 until paths.length()) {
                                            val p = paths.getString(j)
                                            val fName = File(p).name
                                            normalized.put(File(receiptsDir, fName).absolutePath)
                                        }
                                        obj.put("receiptImagePaths", normalized)
                                        if (normalized.length() > 0) {
                                            obj.put("receiptImagePath", normalized.getString(0))
                                        }
                                    }
                                }
                                destFile.writeText(arr.toString(2))
                            } catch (_: Exception) {
                                destFile.writeText(rawJson)
                            }
                        }
                        name.startsWith("receipts/") && !entry.isDirectory -> {
                            val fileName = name.removePrefix("receipts/")
                            if (fileName.isNotBlank()) {
                                onProgress?.invoke(0.60f, "Restoring attachment: $fileName...")
                                val destFile = File(receiptsDir, fileName)
                                FileOutputStream(destFile).use { out ->
                                    zipIn.copyTo(out, BUFFER_SIZE)
                                }
                                receiptFilesRestored++
                            }
                        }
                        name == "preferences.json" -> {
                            onProgress?.invoke(0.88f, "Restoring preferences...")
                            val outBytes = ByteArrayOutputStream()
                            zipIn.copyTo(outBytes)
                            val jsonStr = outBytes.toString(Charsets.UTF_8.name())
                            try {
                                applyPreferencesJson(context, JSONObject(jsonStr))
                                settingsRestored = true
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to apply restored preferences: ${e.message}")
                            }
                        }
                    }
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
            } finally {
                try { zipIn.close() } catch (_: Exception) {}
            }

            // Refresh maintenance store in memory
            onProgress?.invoke(0.96f, "Reloading data stores...")
            try {
                MaintenanceStore.getInstance(context).loadRecords()
            } catch (e: Exception) {
                Log.w(TAG, "Could not call reload on store: ${e.message}")
            }

            onProgress?.invoke(1.0f, "Restore completed!")
            RestoreResult.Success(
                serviceRecordsRestored = serviceRecordsRestored,
                receiptFilesRestored = receiptFilesRestored,
                settingsRestored = settingsRestored
            )
        } catch (e: javax.crypto.AEADBadTagException) {
            RestoreResult.InvalidPassword
        } catch (e: Exception) {
            val msg = e.message ?: ""
            if (msg.contains("tag", ignoreCase = true) || msg.contains("cipher", ignoreCase = true)) {
                RestoreResult.InvalidPassword
            } else {
                RestoreResult.Error("Failed to restore backup: ${e.message}")
            }
        }
    }

    private fun applyPreferencesJson(context: Context, json: JSONObject) {
        val appPrefs = AppPreferences.getInstance(context)
        json.optString("speedUnit").takeIf { it.isNotBlank() }?.let {
            try { appPrefs.setSpeedUnit(com.kalaiselvan.ride.preferences.SpeedUnit.valueOf(it)) } catch (_: Exception) {}
        }
        json.optString("temperatureUnit").takeIf { it.isNotBlank() }?.let {
            try { appPrefs.setTemperatureUnit(com.kalaiselvan.ride.preferences.TemperatureUnit.valueOf(it)) } catch (_: Exception) {}
        }
        json.optString("preferredCurrency").takeIf { it.isNotBlank() }?.let {
            appPrefs.setPreferredCurrency(it)
        }
        json.optString("customBrandName").takeIf { it.isNotBlank() }?.let {
            appPrefs.setCustomBrandName(it)
        }
        if (json.has("callNotifications")) appPrefs.setCallNotifications(json.optBoolean("callNotifications", true))
        if (json.has("smsNotifications")) appPrefs.setSmsNotifications(json.optBoolean("smsNotifications", true))
        if (json.has("turnByTurnNav")) appPrefs.setTurnByTurnNav(json.optBoolean("turnByTurnNav", true))
        if (json.has("phoneBatteryMirroring")) appPrefs.setPhoneBatteryMirroring(json.optBoolean("phoneBatteryMirroring", true))
        if (json.has("dashClockSync")) appPrefs.setDashClockSync(json.optBoolean("dashClockSync", true))
        if (json.has("keepDashboardScreenOn")) appPrefs.setKeepDashboardScreenOn(json.optBoolean("keepDashboardScreenOn", true))

        val lat = json.optDouble("parkedLat", 0.0)
        val lon = json.optDouble("parkedLon", 0.0)
        val timeMs = json.optLong("parkedTimeMs", 0L)
        if (lat != 0.0 && lon != 0.0 && timeMs > 0) {
            appPrefs.setParkedLocation(
                lat = lat,
                lon = lon,
                accuracy = json.optDouble("parkedAccuracy", 0.0).toFloat(),
                timeMs = timeMs
            )
        }
    }

    /**
     * Executes an automatic backup to a user-selected SAF DocumentTree folder.
     */
    fun performAutoBackupToDirectory(
        context: Context,
        treeUri: Uri,
        password: String? = null
    ): Boolean {
        return try {
            val rootDoc = DocumentFile.fromTreeUri(context, treeUri)
            if (rootDoc == null || !rootDoc.canWrite()) {
                Log.w(TAG, "Cannot write to selected backup tree URI: $treeUri")
                return false
            }

            val fileName = "MyRide_Backup_Auto.myride"
            val existing = rootDoc.findFile(fileName)
            val targetDoc = existing ?: rootDoc.createFile("application/octet-stream", fileName)
            if (targetDoc == null) {
                Log.w(TAG, "Failed to create auto-backup document in folder")
                return false
            }

            context.contentResolver.openOutputStream(targetDoc.uri, "wt")?.use { out ->
                createBackup(context, out, password)
            } ?: false
        } catch (e: Exception) {
            Log.e(TAG, "performAutoBackupToDirectory failed: ${e.message}", e)
            false
        }
    }
}
