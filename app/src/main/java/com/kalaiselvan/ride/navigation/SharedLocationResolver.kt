package com.kalaiselvan.ride.navigation

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.util.regex.Pattern

/**
 * Universal Shared Location Parser and Resolver.
 *
 * Extracts geographical coordinates (lat, lng) and place labels from:
 * 1. Plain text GPS drops: "12.971598, 77.594566"
 * 2. Standard geo: URIs: "geo:12.971598,77.594566?q=..."
 * 3. Google Maps links (both full and shortened):
 *    - "https://maps.app.goo.gl/..."
 *    - "https://goo.gl/maps/..."
 *    - "https://www.google.com/maps/place/..."
 *    - "https://www.google.com/maps/dir/..."       
 * 4. HERE WeGo links:
 *    - "https://share.here.com/p/..."
 *    - "https://wego.here.com/location/?map=12.971598,77.594566"
 * 5. Waze links:
 *    - "https://waze.com/ul?ll=12.971598,77.594566"
 * 6. Apple Maps links:
 *    - "https://maps.apple.com/?ll=12.971598,77.594566"
 * 7. OpenStreetMap / OsmAnd:
 *    - "https://www.openstreetmap.org/#map=16/12.9716/77.5946"
 */
data class ResolvedLocation(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val label: String? = null,
    val placeQuery: String? = null
) {
    val hasCoordinates: Boolean
        get() = latitude != null && longitude != null && (latitude != 0.0 || longitude != 0.0)
}

object SharedLocationResolver {

    private const val TAG = "MyRideSharedLoc"

    // Regex for decimal latitude, longitude pairs (e.g. 12.971598, 77.594566 or 12.971598,77.594566)
    private val COORD_PATTERN = Pattern.compile("([-+]?\\d{1,2}(?:\\.\\d+)?)[,\\s]+([-+]?\\d{1,3}(?:\\.\\d+)?)")

    // Regex to match URLs in shared text
    private val URL_PATTERN = Pattern.compile("https?://[^\\s]+")

    /**
     * Resolves an incoming text payload or URI string into precise latitude and longitude or place query.
     */
    suspend fun resolve(input: String?): ResolvedLocation? = withContext(Dispatchers.IO) {
        if (input.isNullOrBlank()) return@withContext null
        val trimmed = input.trim()
        val sanitizedForLog = trimmed.take(80)
        Log.d(TAG, "Resolving incoming shared payload length: ${trimmed.length} prefix: $sanitizedForLog")

        // 1. Check for standard geo: URI (e.g. "geo:12.971598,77.594566" or "geo:0,0?q=12.971598,77.594566(Label)")
        if (trimmed.startsWith("geo:", ignoreCase = true)) {
            val parsedGeo = parseGeoUri(trimmed)
            if (parsedGeo != null) {
                Log.d(TAG, "Resolved from geo URI: $parsedGeo")
                return@withContext parsedGeo
            }
        }

        // 2. Check if text contains a web URL (Google Maps, HERE WeGo, Waze, Apple Maps, etc.)
        val urlMatcher = URL_PATTERN.matcher(trimmed)
        if (urlMatcher.find()) {
            val rawUrl = urlMatcher.group()
            val cleanUrl = rawUrl.trimEnd('.', ',', ')', '>', ']', '!', ';', '\'')
            val parsedFromUrl = resolveUrl(cleanUrl)
            if (parsedFromUrl != null) {
                Log.d(TAG, "Resolved from URL: $parsedFromUrl")
                return@withContext parsedFromUrl
            }
        }

        // 3. Fallback: Direct coordinate regex scan across the whole text payload
        val coordMatcher = COORD_PATTERN.matcher(trimmed)
        while (coordMatcher.find()) {
            val lat = coordMatcher.group(1)?.toDoubleOrNull()
            val lng = coordMatcher.group(2)?.toDoubleOrNull()
            if (lat != null && lng != null && isValidCoordinate(lat, lng)) {
                val loc = ResolvedLocation(latitude = lat, longitude = lng, label = "Shared Location")
                Log.d(TAG, "Resolved from direct coordinate match: $loc")
                return@withContext loc
            }
        }

        // 4. Last resort: if text doesn't have coordinates, treat non-empty string as a place search query
        if (trimmed.length in 3..120 && !trimmed.contains("http://") && !trimmed.contains("https://")) {
            val loc = ResolvedLocation(label = trimmed, placeQuery = trimmed)
            Log.d(TAG, "Resolved from plain place name: $loc")
            return@withContext loc
        }

        Log.w(TAG, "Could not resolve coordinates from payload: $trimmed")
        null
    }

    private fun parseGeoUri(geoUri: String): ResolvedLocation? {
        val withoutScheme = geoUri.substringAfter("geo:").substringBefore("?")
        var lat: Double? = null
        var lng: Double? = null
        var label: String? = null
        var queryName: String? = null

        val parts = withoutScheme.split(",")
        if (parts.size >= 2) {
            lat = parts[0].toDoubleOrNull()
            lng = parts[1].toDoubleOrNull()
        }

        // Query string fallback if coordinates are 0,0 (e.g. geo:0,0?q=12.9716,77.5946(Office) or geo:0,0?q=DLF+Downtown)
        if ((lat == null || lng == null || (lat == 0.0 && lng == 0.0)) && geoUri.contains("?")) {
            val query = geoUri.substringAfter("?")
            for (param in query.split("&")) {
                val kv = param.split("=")
                if (kv.size == 2 && kv[0].equals("q", ignoreCase = true)) {
                    val decoded = URLDecoder.decode(kv[1], "UTF-8")
                    if (decoded.contains("(") && decoded.endsWith(")")) {
                        label = decoded.substringAfter("(").substringBeforeLast(")")
                    }
                    val coordStr = decoded.substringBefore("(")
                    val m = COORD_PATTERN.matcher(coordStr)
                    if (m.find()) {
                        lat = m.group(1)?.toDoubleOrNull()
                        lng = m.group(2)?.toDoubleOrNull()
                    } else {
                        queryName = decoded
                    }
                }
            }
        }

        return if (lat != null && lng != null && isValidCoordinate(lat, lng)) {
            ResolvedLocation(latitude = lat, longitude = lng, label = label ?: "Dropped Pin")
        } else if (!queryName.isNullOrBlank()) {
            ResolvedLocation(label = queryName, placeQuery = queryName)
        } else null
    }

    private val TRUSTED_MAP_DOMAINS = listOf(
        "google.com",
        "maps.google.com",
        "maps.app.goo.gl",
        "goo.gl",
        "here.com",
        "share.here.com",
        "waze.com",
        "apple.com",
        "maps.apple.com",
        "openstreetmap.org"
    )

    private fun isTrustedDomain(urlStr: String): Boolean {
        return try {
            val host = URL(urlStr).host.lowercase()
            TRUSTED_MAP_DOMAINS.any { host == it || host.endsWith(".$it") }
        } catch (_: Exception) {
            false
        }
    }

    private fun resolveUrl(urlStr: String): ResolvedLocation? {
        if (!isTrustedDomain(urlStr) && !isRedirectUrl(urlStr)) {
            Log.w(TAG, "Skipping untrusted shared URL domain: $urlStr")
            return null
        }
        var currentUrl = urlStr

        // Try extracting parameters directly first before making any network redirect request
        val directCoords = extractCoordsFromUrlString(currentUrl)
        if (directCoords != null) return directCoords

        // If it's a shortened or redirect URL (e.g., maps.app.goo.gl, goo.gl, share.here.com, t.co), follow redirects
        if (isRedirectUrl(currentUrl)) {
            currentUrl = followRedirects(currentUrl)
            val redirectedCoords = extractCoordsFromUrlString(currentUrl)
            if (redirectedCoords != null) return redirectedCoords

            // If redirected URL still didn't expose coords in URL query, fetch the page HTML
            val htmlCoords = fetchCoordsFromHtml(currentUrl)
            if (htmlCoords != null) return htmlCoords
        }

        return null
    }

    private fun isRedirectUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("maps.app.goo.gl") ||
                lower.contains("goo.gl/maps") ||
                lower.contains("share.here.com") ||
                lower.contains("bit.ly") ||
                lower.contains("t.co") ||
                lower.contains("tinyurl.com") ||
                lower.contains("maps.google.com") ||
                lower.contains("google.com/maps")
    }

    private fun followRedirects(startUrl: String): String {
        var url = startUrl
        var hops = 0
        while (hops < 6) {
            var conn: HttpURLConnection? = null
            try {
                conn = URL(url).openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = false
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.requestMethod = "GET"
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")

                val code = conn.responseCode
                if (code in 300..399) {
                    val loc = conn.getHeaderField("Location")
                    if (!loc.isNullOrBlank()) {
                        url = if (loc.startsWith("http")) loc else URL(URL(url), loc).toString()
                        hops++
                        continue
                    }
                }
                break
            } catch (e: Exception) {
                Log.w(TAG, "Redirect follow failed for $url: ${e.message}")
                break
            } finally {
                conn?.disconnect()
            }
        }
        return url
    }

    private fun fetchCoordsFromHtml(urlStr: String): ResolvedLocation? {
        var conn: HttpURLConnection? = null
        try {
            conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = true
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")

            if (conn.responseCode in 200..299) {
                BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { reader ->
                    var line: String?
                    var linesRead = 0
                    while (reader.readLine().also { line = it } != null && linesRead < 150) {
                        linesRead++
                        val l = line ?: continue
                        // Look for meta content with /maps/@lat,lng or center=lat,lng or staticmap?center=lat,lng or !3d!4d
                        val m3d4d = Pattern.compile("!3d([-+]?\\d{1,2}\\.\\d+)!4d([-+]?\\d{1,3}\\.\\d+)").matcher(l)
                        if (m3d4d.find()) {
                            val lat = m3d4d.group(1)?.toDoubleOrNull()
                            val lng = m3d4d.group(2)?.toDoubleOrNull()
                            if (lat != null && lng != null && isValidCoordinate(lat, lng)) {
                                return ResolvedLocation(latitude = lat, longitude = lng, label = "Shared Location")
                            }
                        }

                        val m1 = Pattern.compile("(?i)(?:center=|/maps/@|ll=|query=)([-+]?\\d{1,2}\\.\\d+)[,%]([-+]?\\d{1,3}\\.\\d+)").matcher(l)
                        if (m1.find()) {
                            val lat = m1.group(1)?.toDoubleOrNull()
                            val lng = m1.group(2)?.toDoubleOrNull()
                            if (lat != null && lng != null && isValidCoordinate(lat, lng)) {
                                return ResolvedLocation(latitude = lat, longitude = lng, label = "Shared Location")
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "HTML fetch failed for $urlStr: ${e.message}")
        } finally {
            conn?.disconnect()
        }
        return null
    }

    private fun extractCoordsFromUrlString(url: String): ResolvedLocation? {
        val decoded = try { URLDecoder.decode(url, "UTF-8") } catch (_: Exception) { url }

        // 1. Google Directions /dir/ URL:
        // Case A: Look for !3d<lat>!4d<lng> which Google Maps embeds for the destination place
        val m3d4d = Pattern.compile("!3d([-+]?\\d{1,2}\\.\\d+)!4d([-+]?\\d{1,3}\\.\\d+)").matcher(decoded)
        if (m3d4d.find()) {
            val destLat = m3d4d.group(1)?.toDoubleOrNull()
            val destLng = m3d4d.group(2)?.toDoubleOrNull()
            if (destLat != null && destLng != null && isValidCoordinate(destLat, destLng)) {
                // Try extracting place name from the URL path if present
                val placeName = extractPlaceNameFromDirUrl(decoded) ?: "Shared Destination"
                return ResolvedLocation(latitude = destLat, longitude = destLng, label = placeName)
            }
        }

        // Case B: Explicit destination address param (daddr=lat,lng)
        val daddrRegex = Pattern.compile("(?i)daddr=([-+]?\\d{1,2}\\.\\d+)[,\\s]+([-+]?\\d{1,3}\\.\\d+)")
        val daddrMatcher = daddrRegex.matcher(decoded)
        if (daddrMatcher.find()) {
            val lat = daddrMatcher.group(1)?.toDoubleOrNull()
            val lng = daddrMatcher.group(2)?.toDoubleOrNull()
            if (lat != null && lng != null && isValidCoordinate(lat, lng)) {
                return ResolvedLocation(latitude = lat, longitude = lng, label = "Shared Destination")
            }
        }

        // 2. Google Place URL: /maps/place/<Place+Name>/data=...
        if (decoded.contains("/maps/place/")) {
            val placePart = decoded.substringAfter("/maps/place/").substringBefore("/").substringBefore("?")
            if (placePart.isNotBlank()) {
                val cleanPlace = placePart.replace("+", " ").trim()
                // If it contains explicit @lat,lng, use them
                val atRegex = Pattern.compile("@([-+]?\\d{1,2}\\.\\d+),([-+]?\\d{1,3}\\.\\d+)")
                val atMatcher = atRegex.matcher(decoded)
                if (atMatcher.find()) {
                    val lat = atMatcher.group(1)?.toDoubleOrNull()
                    val lng = atMatcher.group(2)?.toDoubleOrNull()
                    if (lat != null && lng != null && isValidCoordinate(lat, lng)) {
                        return ResolvedLocation(latitude = lat, longitude = lng, label = cleanPlace)
                    }
                }
                // Otherwise return as placeQuery to be geocoded by Ola Maps
                return ResolvedLocation(label = cleanPlace, placeQuery = cleanPlace)
            }
        }

        // 3. Query params: destination=lat,lng or q=lat,lng or ll=lat,lng or loc:lat,lng
        val paramRegex = Pattern.compile("(?i)(?:q|destination|ll|query|loc|map|center)=([-+]?\\d{1,2}\\.\\d+)[,\\s]+([-+]?\\d{1,3}\\.\\d+)")
        val paramMatcher = paramRegex.matcher(decoded)
        if (paramMatcher.find()) {
            val lat = paramMatcher.group(1)?.toDoubleOrNull()
            val lng = paramMatcher.group(2)?.toDoubleOrNull()
            if (lat != null && lng != null && isValidCoordinate(lat, lng)) {
                return ResolvedLocation(latitude = lat, longitude = lng, label = "Shared Destination")
            }
        }

        // 4. Fallback for /dir/ where destination is a place name text
        if (decoded.contains("/maps/dir/")) {
            val placeName = extractPlaceNameFromDirUrl(decoded)
            if (!placeName.isNullOrBlank()) {
                return ResolvedLocation(label = placeName, placeQuery = placeName)
            }
        }

        // 5. OpenStreetMap URL format: #map=16/12.9716/77.5946
        val osmRegex = Pattern.compile("#map=\\d+/([-+]?\\d{1,2}\\.\\d+)/([-+]?\\d{1,3}\\.\\d+)")
        val osmMatcher = osmRegex.matcher(decoded)
        if (osmMatcher.find()) {
            val lat = osmMatcher.group(1)?.toDoubleOrNull()
            val lng = osmMatcher.group(2)?.toDoubleOrNull()
            if (lat != null && lng != null && isValidCoordinate(lat, lng)) {
                return ResolvedLocation(latitude = lat, longitude = lng, label = "OpenStreetMap Location")
            }
        }

        // 6. General coordinate pattern inside URL query/path
        val generalMatcher = COORD_PATTERN.matcher(decoded)
        if (generalMatcher.find()) {
            val lat = generalMatcher.group(1)?.toDoubleOrNull()
            val lng = generalMatcher.group(2)?.toDoubleOrNull()
            if (lat != null && lng != null && isValidCoordinate(lat, lng)) {
                return ResolvedLocation(latitude = lat, longitude = lng, label = "Shared Destination")
            }
        }

        return null
    }

    private fun extractPlaceNameFromDirUrl(url: String): String? {
        val path = try { URL(url).path } catch (_: Exception) { url }
        val segments = path.split("/").filter { it.isNotBlank() }
        val dirIdx = segments.indexOf("dir")
        if (dirIdx >= 0 && dirIdx + 2 < segments.size) {
            val rawDest = segments[dirIdx + 2]
            return rawDest.replace("+", " ").trim()
        }
        return null
    }

    private fun isValidCoordinate(lat: Double, lng: Double): Boolean {
        return lat in -90.0..90.0 && lng in -180.0..180.0 && (lat != 0.0 || lng != 0.0)
    }
}
