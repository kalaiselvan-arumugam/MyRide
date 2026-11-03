package com.kalaiselvan.ride.navigation

import android.location.Location
import com.kalaiselvan.ride.logs.LogStore
import com.kalaiselvan.ride.notifications.NavNotificationParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Data model for an autocomplete place prediction from Ola Maps.
 */
data class OlaPrediction(
    val placeId: String,
    val description: String,
    val mainText: String,
    val secondaryText: String,
    val lat: Double? = null,
    val lng: Double? = null
)

/**
 * Geographic coordinate pair.
 */
data class OlaLatLng(
    val latitude: Double,
    val longitude: Double
)

/**
 * Individual turn-by-turn maneuver step.
 */
data class OlaManeuver(
    val type: String,
    val modifier: String?,
    val exit: Int?,
    val location: OlaLatLng,
    val instruction: String,
    val distanceMeters: Int,
    val durationSeconds: Int,
    val bikeIcon: Int
)

/**
 * Full calculated route with step maneuvers and decoded polyline.
 */
data class OlaRoute(
    val destinationName: String,
    val destinationLatLng: OlaLatLng,
    val totalDistanceMeters: Int,
    val totalDurationSeconds: Int,
    val polyline: List<OlaLatLng>,
    val steps: List<OlaManeuver>,
    val summary: String
)

/**
 * Lightweight, zero-dependency REST HTTP client for Ola Maps.
 * Handles Places Autocomplete, Place Details, and Routing/Directions APIs.
 */
object OlaMapsClient {

    private const val BASE_URL_PLACES = "https://api.olamaps.io/places/v1"
    private const val BASE_URL_ROUTING = "https://api.olamaps.io/routing/v1"
    private const val TIMEOUT_MS = 15000

    /**
     * Search places with auto-suggestions.
     * Biased by current location if coordinates are available.
     */
    suspend fun autocomplete(
        input: String,
        apiKey: String,
        currentLat: Double? = null,
        currentLng: Double? = null
    ): Result<List<OlaPrediction>> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Ola Maps API key is missing. Please configure it in Settings."))
        }
        if (input.trim().length < 2) {
            return@withContext Result.success(emptyList())
        }

        try {
            val encodedInput = URLEncoder.encode(input.trim(), "UTF-8")
            val urlBuilder = StringBuilder("$BASE_URL_PLACES/autocomplete?input=$encodedInput&api_key=$apiKey")
            if (currentLat != null && currentLng != null && currentLat != 0.0 && currentLng != 0.0) {
                urlBuilder.append("&location=$currentLat,$currentLng")
            }

            val responseString = executeGet(urlBuilder.toString(), apiKey)
            val json = JSONObject(responseString)

            val predictionsList = mutableListOf<OlaPrediction>()
            val predictionsArray = json.optJSONArray("predictions") ?: JSONArray()

            for (i in 0 until predictionsArray.length()) {
                val item = predictionsArray.optJSONObject(i) ?: continue
                val placeId = item.optString("place_id", item.optString("id", ""))
                val description = item.optString("description", "")
                val structured = item.optJSONObject("structured_formatting")
                val mainText = structured?.optString("main_text") ?: description.substringBefore(",")
                val secondaryText = structured?.optString("secondary_text") ?: description.substringAfter(",", "")

                // Some responses embed geometry directly in autocomplete
                var lat: Double? = null
                var lng: Double? = null
                val geom = item.optJSONObject("geometry")
                val loc = geom?.optJSONObject("location")
                if (loc != null) {
                    lat = loc.optDouble("lat")
                    lng = loc.optDouble("lng")
                }

                predictionsList.add(
                    OlaPrediction(
                        placeId = placeId,
                        description = description,
                        mainText = mainText.trim(),
                        secondaryText = secondaryText.trim(),
                        lat = if (lat != null && !lat.isNaN()) lat else null,
                        lng = if (lng != null && !lng.isNaN()) lng else null
                    )
                )
            }

            LogStore.logEvent("OlaMaps Autocomplete: '${input.take(15)}' returned ${predictionsList.size} results")
            Result.success(predictionsList)
        } catch (e: Exception) {
            LogStore.logEvent("OlaMaps Autocomplete error: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Resolves exact coordinates for a selected place.
     */
    suspend fun getPlaceDetails(
        placeId: String,
        apiKey: String
    ): Result<OlaLatLng> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Ola Maps API key is missing."))
        }

        try {
            val encodedId = URLEncoder.encode(placeId, "UTF-8")
            val url = "$BASE_URL_PLACES/details?place_id=$encodedId&api_key=$apiKey"
            val responseString = executeGet(url, apiKey)
            val json = JSONObject(responseString)

            val resultObj = json.optJSONObject("result") ?: json
            val geom = resultObj.optJSONObject("geometry")
            val loc = geom?.optJSONObject("location")

            if (loc != null) {
                val lat = loc.optDouble("lat", Double.NaN)
                val lng = loc.optDouble("lng", Double.NaN)
                if (!lat.isNaN() && !lng.isNaN()) {
                    return@withContext Result.success(OlaLatLng(lat, lng))
                }
            }

            Result.failure(IllegalStateException("No geometry coordinates found in place details"))
        } catch (e: Exception) {
            LogStore.logEvent("OlaMaps Details error: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Calculates driving/riding directions between origin and destination.
     * Single HTTP request downloads the full polyline and all step maneuvers.
     */
    suspend fun getDirections(
        origin: OlaLatLng,
        destination: OlaLatLng,
        apiKey: String,
        destinationName: String = ""
    ): Result<OlaRoute> = getRoutes(
        origin = origin,
        destination = destination,
        apiKey = apiKey,
        destinationName = destinationName,
        alternatives = false
    ).map { it.first() }

    suspend fun getRoutes(
        origin: OlaLatLng,
        destination: OlaLatLng,
        apiKey: String,
        destinationName: String = "",
        alternatives: Boolean = true
    ): Result<List<OlaRoute>> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Ola Maps API key is missing."))
        }

        try {
            val altParam = if (alternatives) "&alternatives=true" else ""
            val urlStr = "$BASE_URL_ROUTING/directions?origin=${origin.latitude},${origin.longitude}" +
                    "&destination=${destination.latitude},${destination.longitude}" +
                    "&steps=true&overview=full&mode=bike$altParam&api_key=$apiKey"

            val responseString = executePost(urlStr, apiKey, requestBody = "")
            val json = JSONObject(responseString)

            val routesArray = json.optJSONArray("routes")
            if (routesArray == null || routesArray.length() == 0) {
                val errorMsg = json.optString("error_message", json.optString("status", "No route found"))
                return@withContext Result.failure(IllegalStateException(errorMsg))
            }

            val routesList = mutableListOf<OlaRoute>()
            for (r in 0 until routesArray.length()) {
                val routeObj = routesArray.optJSONObject(r) ?: continue
                routesList.add(parseRoute(routeObj, origin, destination, destinationName, r))
            }

            LogStore.logEvent("OlaMaps getRoutes: found ${routesList.size} route candidate(s)")
            Result.success(routesList)
        } catch (e: Exception) {
            LogStore.logEvent("OlaMaps getRoutes error: ${e.message}")
            Result.failure(e)
        }
    }

    private fun parseRoute(
        routeObj: JSONObject,
        origin: OlaLatLng,
        destination: OlaLatLng,
        destinationName: String,
        routeIndex: Int
    ): OlaRoute {
        val legsArray = routeObj.optJSONArray("legs")
        val firstLeg = legsArray?.optJSONObject(0)

            val totalDistance = when {
                firstLeg?.has("distance") == true -> {
                    val d = firstLeg.get("distance")
                    if (d is JSONObject) d.optInt("value", 0) else firstLeg.optInt("distance", 0)
                }
                routeObj.has("distance") -> routeObj.optInt("distance", 0)
                else -> 0
            }

            val totalDuration = when {
                firstLeg?.has("duration") == true -> {
                    val d = firstLeg.get("duration")
                    if (d is JSONObject) d.optInt("value", 0) else firstLeg.optInt("duration", 0)
                }
                routeObj.has("duration") -> routeObj.optInt("duration", 0)
                else -> 0
            }

            // Decode polyline points: Ola Maps returns overview_polyline directly as a String
            val polylineString = when {
                routeObj.has("overview_polyline") -> {
                    val op = routeObj.get("overview_polyline")
                    if (op is String) op else (op as? JSONObject)?.optString("points", "") ?: ""
                }
                routeObj.has("geometry") -> routeObj.optString("geometry", "")
                else -> ""
            }
            val decodedPolyline = decodePolyline(polylineString).ifEmpty {
                listOf(origin, destination)
            }

            // Parse Step Maneuvers
            val stepsList = mutableListOf<OlaManeuver>()
            val stepsArray = firstLeg?.optJSONArray("steps") ?: JSONArray()

            for (i in 0 until stepsArray.length()) {
                val stepObj = stepsArray.optJSONObject(i) ?: continue
                val stepDist = when {
                    stepObj.has("distance") -> {
                        val d = stepObj.get("distance")
                        if (d is JSONObject) d.optInt("value", 0) else stepObj.optInt("distance", 0)
                    }
                    else -> 0
                }
                val stepDur = when {
                    stepObj.has("duration") -> {
                        val d = stepObj.get("duration")
                        if (d is JSONObject) d.optInt("value", 0) else stepObj.optInt("duration", 0)
                    }
                    else -> 0
                }

                val instructions = cleanHtmlInstructions(
                    stepObj.optString("instructions", stepObj.optString("html_instructions", ""))
                )

                val maneuverObj = stepObj.optJSONObject("maneuver")
                val type = maneuverObj?.optString("type")
                    ?: stepObj.optString("maneuver", "straight")
                val modifier = maneuverObj?.optString("modifier")
                val exit = if (maneuverObj?.has("exit") == true) maneuverObj.optInt("exit") else null

                // Maneuver location
                var maneuverLat = origin.latitude
                var maneuverLng = origin.longitude
                if (maneuverObj != null && maneuverObj.has("location")) {
                    val locArray = maneuverObj.optJSONArray("location")
                    if (locArray != null && locArray.length() >= 2) {
                        // GeoJSON is [longitude, latitude]
                        maneuverLng = locArray.optDouble(0, maneuverLng)
                        maneuverLat = locArray.optDouble(1, maneuverLat)
                    }
                } else if (stepObj.has("start_location")) {
                    val sl = stepObj.optJSONObject("start_location")
                    if (sl != null) {
                        maneuverLat = sl.optDouble("lat", maneuverLat)
                        maneuverLng = sl.optDouble("lng", maneuverLng)
                    }
                }

                val bikeIcon = mapManeuverToBikeIcon(type, modifier, exit, instructions, leftHandTraffic = true)

                stepsList.add(
                    OlaManeuver(
                        type = type,
                        modifier = modifier,
                        exit = exit,
                        location = OlaLatLng(maneuverLat, maneuverLng),
                        instruction = instructions.ifBlank { "Continue on route" },
                        distanceMeters = stepDist,
                        durationSeconds = stepDur,
                        bikeIcon = bikeIcon
                    )
                )
            }

            // Ensure destination arrival step exists at the end
            if (stepsList.isEmpty() || stepsList.last().bikeIcon != 43) {
                stepsList.add(
                    OlaManeuver(
                        type = "arrive",
                        modifier = null,
                        exit = null,
                        location = destination,
                        instruction = "Arrive at $destinationName".trim(),
                        distanceMeters = 0,
                        durationSeconds = 0,
                        bikeIcon = 43
                    )
                )
            }

        var summary = routeObj.optString("summary", firstLeg?.optString("summary") ?: "")
        if (summary.isBlank()) {
            summary = if (routeIndex == 0) "Fastest Route" else "Alternative Route $routeIndex"
        }

        return OlaRoute(
            destinationName = destinationName.ifBlank { "Destination" },
            destinationLatLng = destination,
            totalDistanceMeters = totalDistance,
            totalDurationSeconds = totalDuration,
            polyline = decodedPolyline,
            steps = stepsList,
            summary = summary
        )
    }

    /**
     * Deterministic mapping from Ola Maps maneuver properties to Motorcycle TFT Turn Icon IDs (0..55).
     */
    fun mapManeuverToBikeIcon(
        type: String,
        modifier: String?,
        exit: Int?,
        instruction: String,
        leftHandTraffic: Boolean = true
    ): Int {
        val cleanType = type.lowercase().trim()
        val cleanMod = (modifier ?: "").lowercase().trim()
        val cleanInst = instruction.lowercase()

        // 1. Arrival
        if (cleanType == "arrive" || cleanInst.contains("arrive") || cleanInst.contains("destination")) {
            return 43
        }

        // 2. Roundabouts (India = left-hand traffic, clockwise CW: 18..29)
        if (cleanType.contains("roundabout") || cleanType.contains("rotary")) {
            val exitNum = exit ?: NavNotificationParser.parseManeuver(instruction, leftHandTraffic).let {
                if (it in 18..41) (if (leftHandTraffic) it - 17 else it - 29) else null
            } ?: 1
            val safeExit = exitNum.coerceIn(1, 12)
            return if (leftHandTraffic) 17 + safeExit else 29 + safeExit
        }

        // 3. U-Turns
        if (cleanType == "uturn" || cleanMod.contains("uturn") || cleanInst.contains("u-turn")) {
            return if (cleanMod.contains("right")) 8 else 7
        }

        // 4. Highway Ramps
        if (cleanType.contains("on ramp") || cleanType.contains("ramp enter")) {
            return if (cleanMod.contains("right")) 13 else 12
        }
        if (cleanType.contains("off ramp") || cleanType.contains("ramp exit") || cleanType.contains("motorway exit")) {
            return if (cleanMod.contains("right")) 15 else 14
        }

        // 5. Merges
        if (cleanType.contains("merge")) {
            return if (cleanMod.contains("right")) 17 else 16
        }

        // 6. Forks & Lane Keep
        if (cleanType.contains("fork") || cleanType.contains("keep") || cleanInst.contains("keep")) {
            return when {
                cleanMod.contains("middle") || cleanMod.contains("center") || cleanInst.contains("middle") -> 11
                cleanMod.contains("right") || cleanInst.contains("right") -> 10
                cleanMod.contains("left") || cleanInst.contains("left") -> 9
                else -> 0
            }
        }

        // 7. Standard & Sharp & Slight Turns
        when (cleanMod) {
            "sharp right" -> return 4
            "sharp left" -> return 3
            "slight right" -> return 6
            "slight left" -> return 5
            "right" -> return 2
            "left" -> return 1
            "straight" -> return 0
        }

        // 8. Fallback to clean-room NLP keyword parser
        val parsed = NavNotificationParser.parseManeuver(instruction, leftHandTraffic)
        return if (parsed == 42) 0 else parsed
    }

    /**
     * Decodes an encoded polyline string into a list of LatLng coordinates.
     * Compatible with Google / OSRM 5-decimal place encoded polyline algorithm.
     */
    fun decodePolyline(encoded: String): List<OlaLatLng> {
        val poly = ArrayList<OlaLatLng>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0

        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                if (index >= len) break
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lat += dlat

            shift = 0
            result = 0
            do {
                if (index >= len) break
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lng += dlng

            val p = OlaLatLng(lat.toDouble() / 1E5, lng.toDouble() / 1E5)
            poly.add(p)
        }
        return poly
    }

    /**
     * Strips HTML formatting tags (<b>, <div>, etc.) commonly returned in directions instructions.
     */
    private fun cleanHtmlInstructions(html: String): String {
        return html
            .replace(Regex("<[^>]*>"), " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun executeGet(urlStr: String, apiKey: String): String {
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = TIMEOUT_MS
        conn.readTimeout = TIMEOUT_MS
        conn.setRequestProperty("Accept", "application/json")
        conn.setRequestProperty("User-Agent", "Z900RideTelemetry/1.0")
        conn.setRequestProperty("X-Request-Id", System.currentTimeMillis().toString())

        val responseCode = conn.responseCode
        val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
        val reader = BufferedReader(InputStreamReader(stream))
        val sb = StringBuilder()
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            sb.append(line)
        }
        reader.close()
        conn.disconnect()

        if (responseCode !in 200..299) {
            throw IllegalStateException("HTTP $responseCode from Ola Maps: $sb")
        }
        return sb.toString()
    }

    private fun executePost(urlStr: String, apiKey: String, requestBody: String): String {
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = TIMEOUT_MS
        conn.readTimeout = TIMEOUT_MS
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Accept", "application/json")
        conn.setRequestProperty("User-Agent", "Z900RideTelemetry/1.0")
        conn.setRequestProperty("X-Request-Id", System.currentTimeMillis().toString())

        if (requestBody.isNotEmpty()) {
            conn.outputStream.use { os ->
                os.write(requestBody.toByteArray(Charsets.UTF_8))
            }
        } else {
            conn.outputStream.use { os ->
                os.write("{}".toByteArray(Charsets.UTF_8))
            }
        }

        val responseCode = conn.responseCode
        val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
        val reader = BufferedReader(InputStreamReader(stream))
        val sb = StringBuilder()
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            sb.append(line)
        }
        reader.close()
        conn.disconnect()

        if (responseCode !in 200..299) {
            throw IllegalStateException("HTTP $responseCode from Ola Maps: $sb")
        }
        return sb.toString()
    }
}
