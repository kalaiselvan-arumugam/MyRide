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
                === MyRide Session: ${session.displayTitle} ===
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
                putExtra(Intent.EXTRA_SUBJECT, "MyRide Session: ${session.displayTitle}")
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

    /**
     * Exports a RideSession to an industry-standard GPX 1.1 XML track.
     * Fully compatible with Strava, Garmin Connect, Google Earth, Calimoto, and GPX viewers.
     */
    fun exportRideAsGpx(context: Context, session: RideSession) {
        try {
            if (session.breadcrumbs.isEmpty()) {
                Toast.makeText(context, "No GPS route data recorded for this ride", Toast.LENGTH_SHORT).show()
                return
            }

            val exportDir = File(context.cacheDir, "export")
            if (!exportDir.exists()) exportDir.mkdirs()

            val isoDateFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }

            val startTimeIso = isoDateFmt.format(Date(session.startTime))
            val sb = java.lang.StringBuilder()
            sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            sb.append("<gpx version=\"1.1\" creator=\"MyRide Motorcycle Companion App\"\n")
            sb.append("     xmlns=\"http://www.topografix.com/GPX/1/1\"\n")
            sb.append("     xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n")
            sb.append("     xsi:schemaLocation=\"http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd\">\n")
            sb.append("  <metadata>\n")
            sb.append("    <name>${session.displayTitle}</name>\n")
            sb.append("    <time>$startTimeIso</time>\n")
            sb.append("  </metadata>\n")
            sb.append("  <trk>\n")
            sb.append("    <name>${session.displayTitle}</name>\n")
            sb.append("    <desc>Distance: ${String.format(Locale.US, "%.1f", session.distanceKm)} km, Top Speed: ${session.topSpeedKmh.toInt()} km/h, Max Lean: L ${session.maxLeanLeft.toInt()}° / R ${session.maxLeanRight.toInt()}°</desc>\n")
            sb.append("    <trkseg>\n")

            for (pt in session.breadcrumbs) {
                val timeIso = isoDateFmt.format(Date(pt.timestamp))
                sb.append("      <trkpt lat=\"${pt.lat}\" lon=\"${pt.lon}\">\n")
                sb.append("        <time>$timeIso</time>\n")
                sb.append("        <extensions>\n")
                sb.append("          <speed>${String.format(Locale.US, "%.2f", pt.speedKmh / 3.6f)}</speed>\n") // m/s in GPX standard
                sb.append("          <lean>${String.format(Locale.US, "%.1f", pt.leanAngle)}</lean>\n")
                sb.append("        </extensions>\n")
                sb.append("      </trkpt>\n")
            }

            sb.append("    </trkseg>\n")
            sb.append("  </trk>\n")
            sb.append("</gpx>\n")

            val file = File(exportDir, "Ride_${session.id}.gpx")
            FileOutputStream(file).use { it.write(sb.toString().toByteArray(Charsets.UTF_8)) }

            val uri: Uri = FileProvider.getUriForFile(
                context,
                "com.kalaiselvan.ride.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/gpx+xml"
                putExtra(Intent.EXTRA_SUBJECT, "MyRide GPX Route: ${session.formattedDate}")
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, "GPX route recorded with MyRide (${String.format(Locale.US, "%.1f", session.distanceKm)} km).")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Share GPX Track").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "GPX export error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
