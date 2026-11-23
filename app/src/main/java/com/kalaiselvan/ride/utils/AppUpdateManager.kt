package com.kalaiselvan.ride.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.kalaiselvan.ride.logs.LogStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Metadata for an available application update published on GitHub.
 */
data class AppUpdateInfo(
    val latestVersionName: String,
    val releaseTitle: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val isNewer: Boolean
)

object AppUpdateManager {

    private const val TAG = "MyRideUpdate"
    private const val GITHUB_REPO = "kalaiselvan-arumugam/MyRide"
    private const val GITHUB_API_LATEST = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"

    /**
     * Checks GitHub for the latest published release and compares it with the currently installed version.
     */
    suspend fun checkForUpdate(context: Context): Result<AppUpdateInfo?> = withContext(Dispatchers.IO) {
        try {
            val currentVersion = try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
            } catch (_: Exception) {
                "1.0.0"
            }

            val url = URL(GITHUB_API_LATEST)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 6000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "MyRide-Android-$currentVersion")
            }

            val responseCode = conn.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                LogStore.logEvent("UpdateCheck: GitHub API returned $responseCode")
                return@withContext Result.failure(IllegalStateException("HTTP $responseCode from GitHub"))
            }

            val responseBody = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val json = JSONObject(responseBody)
            val tagName = json.optString("tag_name", "").removePrefix("v").trim()
            val releaseTitle = json.optString("name", "v$tagName")
            val releaseBody = json.optString("body", "")

            // Choose the appropriate APK asset based on debuggable build type
            val isDebuggable = (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
            val targetAssetName = if (isDebuggable) "debug.apk" else "release.apk"

            var chosenDownloadUrl = ""
            val assetsArray = json.optJSONArray("assets")
            if (assetsArray != null) {
                for (i in 0 until assetsArray.length()) {
                    val asset = assetsArray.getJSONObject(i)
                    val name = asset.optString("name", "")
                    val downloadUrl = asset.optString("browser_download_url", "")
                    if (name.endsWith(targetAssetName, ignoreCase = true)) {
                        chosenDownloadUrl = downloadUrl
                        break
                    }
                }
                // Fallback to any APK asset in release if exact match not found
                if (chosenDownloadUrl.isBlank()) {
                    for (i in 0 until assetsArray.length()) {
                        val asset = assetsArray.getJSONObject(i)
                        val name = asset.optString("name", "")
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            chosenDownloadUrl = asset.optString("browser_download_url", "")
                            break
                        }
                    }
                }
            }

            val isNewer = isVersionNewer(currentVersion, tagName)
            Log.d(TAG, "Current: $currentVersion, Latest: $tagName, isNewer: $isNewer")

            if (isNewer && chosenDownloadUrl.isNotBlank()) {
                val info = AppUpdateInfo(
                    latestVersionName = tagName,
                    releaseTitle = releaseTitle,
                    releaseNotes = releaseBody,
                    downloadUrl = chosenDownloadUrl,
                    isNewer = true
                )
                Result.success(info)
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Check for update failed: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * SemVer-style version comparison (e.g. 1.0.2 > 1.0.1).
     */
    fun isVersionNewer(current: String, latest: String): Boolean {
        if (latest.isBlank()) return false
        val currParts = current.split(".").mapNotNull { it.toIntOrNull() }
        val latestParts = latest.split(".").mapNotNull { it.toIntOrNull() }

        val length = maxOf(currParts.size, latestParts.size)
        for (i in 0 until length) {
            val c = currParts.getOrElse(i) { 0 }
            val l = latestParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }

    /**
     * Directs the user to the verified GitHub release asset download in the system browser.
     */
    fun downloadAndInstall(context: Context, downloadUrl: String, versionName: String) {
        try {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(browserIntent)
            LogStore.logEvent("Update: Launched browser download for MyRide v$versionName")
        } catch (e: Exception) {
            Log.e(TAG, "Browser download launch failed: ${e.message}")
        }
    }
}

