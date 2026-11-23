package com.kalaiselvan.ride.logs

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ExportManager {

    fun exportLogsAndShare(context: Context) {
        try {
            val content = LogStore.getFormattedExportText()
            val exportDir = File(context.cacheDir, "export")
            if (!exportDir.exists()) exportDir.mkdirs()

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val file = File(exportDir, "MyRide_Logs_$timestamp.txt")

            FileOutputStream(file).use { fos ->
                fos.write(content.toByteArray(Charsets.UTF_8))
            }

            val uri: Uri = FileProvider.getUriForFile(
                context,
                "com.kalaiselvan.ride.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "MyRide Diagnostic & Hex Stream Export")
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, "Exported MyRide telemetry & hex trace logs.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Export MyRide Logs").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "Export error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun exportRideSession(context: Context, session: RideSession) {
        try {
            val text = """
                === MyRide Session Summary ===
                Date: ${session.formattedDate}
                Duration: ${session.formattedDuration}
                Distance: ${String.format("%.1f", session.distanceKm)} km
                Top Speed: ${session.topSpeedKmh.toInt()} km/h
                Avg Speed: ${session.avgSpeedKmh.toInt()} km/h
                Max Lean Left: ${session.maxLeanLeft.toInt()}°
                Max Lean Right: ${session.maxLeanRight.toInt()}°
                Avg Fuel: ${String.format("%.1f", session.avgFuelMileage)} km/l
            """.trimIndent()

            val exportDir = File(context.cacheDir, "export")
            if (!exportDir.exists()) exportDir.mkdirs()

            val file = File(exportDir, "MyRide_Session_${session.id}.txt")
            FileOutputStream(file).use { it.write(text.toByteArray(Charsets.UTF_8)) }

            val uri: Uri = FileProvider.getUriForFile(
                context,
                "com.kalaiselvan.ride.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "MyRide Session: ${session.formattedDate}")
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, text)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Share Ride Summary").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "Share error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
