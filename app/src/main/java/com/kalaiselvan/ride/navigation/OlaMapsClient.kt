package com.kalaiselvan.ride.navigation

import android.location.Location
import com.kalaiselvan.ride.logs.LogStore
import com.kalaiselvan.ride.notifications.NavNotificationParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
    /**
     * Resolves a place name or query text into exact coordinates via autocomplete and place details.
     */
    suspend fun geocodePlaceName(
        query: String,
        apiKey: String,
        biasLat: Double? = null,
        biasLng: Double? = null
    ): Result<OlaLatLng> = withContext(Dispatchers.IO) {
        val autoRes = autocomplete(query, apiKey, biasLat, biasLng)
        val firstPred = autoRes.getOrNull()?.firstOrNull()
            ?: return@withContext Result.failure(IllegalStateException("No matching place found for: \"$query\""))

        if (firstPred.lat != null && firstPred.lng != null) {
            return@withContext Result.success(OlaLatLng(firstPred.lat, firstPred.lng))
        }

        getPlaceDetails(firstPred.placeId, apiKey)
    }

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
     * Optional forwardWaypoint forces routing forward along the rider's travel heading, suppressing U-turns.
     */
    suspend fun getDirections(
        origin: OlaLatLng,
        destination: OlaLatLng,
        apiKey: String,
        destinationName: String = "",
        forwardWaypoint: OlaLatLng? = null
    ): Result<OlaRoute> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Ola Maps API key is missing."))
        }
        try {
            val url = if (forwardWaypoint != null) {
                "$BASE_URL_ROUTING/directions?origin=${origin.latitude},${origin.longitude}" +
                        "&destination=${destination.latitude},${destination.longitude}" +
                        "&waypoints=${forwardWaypoint.latitude},${forwardWaypoint.longitude}" +
                        "&steps=true&overview=full&api_key=$apiKey"
            } else {
                "$BASE_URL_ROUTING/directions?origin=${origin.latitude},${origin.longitude}" +
                        "&destination=${destination.latitude},${destination.longitude}" +
                        "&steps=true&overview=full&api_key=$apiKey"
            }
            val resp = executePost(url, apiKey, "")
            val routes = parseRoutesFromJson(resp, origin, destination, destinationName, "Fastest Route")
            if (routes.isEmpty()) {
                if (forwardWaypoint != null) {
                    return@withContext getDirections(origin, destination, apiKey, destinationName, null)
                }
                return@withContext Result.failure(IllegalStateException("No drivable route found for destination"))
            }
            Result.success(routes.first())
        } catch (e: Exception) {
            if (forwardWaypoint != null) {
                return@withContext getDirections(origin, destination, apiKey, destinationName, null)
            }
            LogStore.logEvent("OlaMaps getDirections error: ${e.message}")
            Result.failure(e)
        }
    }

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
            if (!alternatives) {
                val url = "$BASE_URL_ROUTING/directions?origin=${origin.latitude},${origin.longitude}" +
                        "&destination=${destination.latitude},${destination.longitude}" +
                        "&steps=true&overview=full&api_key=$apiKey"
                val resp = executePost(url, apiKey, "")
                val routes = parseRoutesFromJson(resp, origin, destination, destinationName, "Fastest Route")
                if (routes.isEmpty()) {
                    return@withContext Result.failure(IllegalStateException("No drivable route found for destination"))
                }
                return@withContext Result.success(routes)
            }

            // Sequential fallback with early exit to preserve API quota (1x instead of 4x parallel)
            val queries = listOf(
                // 1. Standard driving directions with alternatives (returns all alternatives in 1 call)
                "$BASE_URL_ROUTING/directions?origin=${origin.latitude},${origin.longitude}&destination=${destination.latitude},${destination.longitude}&alternatives=true&steps=true&overview=full&api_key=$apiKey",
                // 2. Two-wheeler / bike mode fallback
                "$BASE_URL_ROUTING/directions?origin=${origin.latitude},${origin.longitude}&destination=${destination.latitude},${destination.longitude}&mode=bike&steps=true&overview=full&alternatives=true&api_key=$apiKey",
                // 3. Shortest route preference fallback
                "$BASE_URL_ROUTING/directions?origin=${origin.latitude},${origin.longitude}&destination=${destination.latitude},${destination.longitude}&route_preference=shortest&steps=true&overview=full&alternatives=true&api_key=$apiKey",
                // 4. Basic routing fallback (no live congestion penalties)
                "$BASE_URL_ROUTING/directions/basic?origin=${origin.latitude},${origin.longitude}&destination=${destination.latitude},${destination.longitude}&steps=true&overview=full&alternatives=true&api_key=$apiKey"
            )

            var parsedRoutes: List<OlaRoute> = emptyList()
            for (url in queries) {
                try {
                    val resp = executePost(url, apiKey, "")
                    val routes = parseRoutesFromJson(resp, origin, destination, destinationName, "Alternative Route")
                    if (routes.isNotEmpty()) {
                        parsedRoutes = routes
                        break // Early-exit on first successful response to prevent burning API quota
                    }
                } catch (_: Exception) {}
            }

            if (parsedRoutes.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("No drivable route found for destination"))
            }

            // Deduplicate routes based on distance, duration, and summary similarity
            val uniqueRoutes = mutableListOf<OlaRoute>()
            for (cand in parsedRoutes.sortedBy { it.totalDurationSeconds }) {
                val isDuplicate = uniqueRoutes.any { existing ->
                    val distDiff = Math.abs(cand.totalDistanceMeters - existing.totalDistanceMeters)
                    val durDiff = Math.abs(cand.totalDurationSeconds - existing.totalDurationSeconds)
                    (distDiff < 80 && durDiff < 45) || (cand.summary == existing.summary && distDiff < 200)
                }
                if (!isDuplicate) {
                    uniqueRoutes.add(cand)
                }
            }

            LogStore.logEvent("OlaMaps getRoutes: returning ${uniqueRoutes.size} distinct route(s)")
            Result.success(uniqueRoutes)
        } catch (e: Exception) {
            LogStore.logEvent("OlaMaps getRoutes fatal error: ${e.message}")
            Result.failure(e)
        }
    }

    private fun parseRoutesFromJson(
        jsonStr: String,
        origin: OlaLatLng,
        destination: OlaLatLng,
        destinationName: String,
        defaultSummary: String,
        startIndex: Int = 0
    ): List<OlaRoute> {
        if (jsonStr.isBlank()) return emptyList()
        val list = mutableListOf<OlaRoute>()
        try {
            val json = JSONObject(jsonStr)
            val routesArray = json.optJSONArray("routes") ?: return emptyList()
            for (r in 0 until routesArray.length()) {
                val routeObj = routesArray.optJSONObject(r) ?: continue
                val parsed = parseRoute(routeObj, origin, destination, destinationName, startIndex + list.size)
                if (parsed.totalDistanceMeters > 0 && parsed.polyline.isNotEmpty()) {
                    val resolvedSummary = if (parsed.summary.isNotBlank() && !parsed.summary.startsWith("Route", ignoreCase = true)) {
                        parsed.summary
                    } else {
                        defaultSummary
                    }
                    list.add(parsed.copy(summary = resolvedSummary))
                }
            }
        } catch (e: Exception) {
            LogStore.logEvent("OlaMaps parse error: ${e.message}")
        }
        return list
    }

    private fun parseRoute(
        routeObj: JSONObject,
        origin: OlaLatLng,
        destination: OlaLatLng,
        destinationName: String,
        routeIndex: Int
    ): OlaRoute {
        val legsArray = routeObj.optJSONArray("legs")
        val numLegs = legsArray?.length() ?: 0
        val firstLeg = legsArray?.optJSONObject(0)

        var totalDistance = 0
        var totalDuration = 0
        val stepsList = mutableListOf<OlaManeuver>()

        if (legsArray != null && numLegs > 0) {
            for (legIdx in 0 until numLegs) {
                val leg = legsArray.optJSONObject(legIdx) ?: continue
                val legDist = when {
                    leg.has("distance") -> {
                        val d = leg.get("distance")
                        if (d is JSONObject) d.optInt("value", 0) else leg.optInt("distance", 0)
                    }
                    else -> 0
                }
                val legDur = when {
                    leg.has("duration") -> {
                        val d = leg.get("duration")
                        if (d is JSONObject) d.optInt("value", 0) else leg.optInt("duration", 0)
                    }
                    else -> 0
                }
                totalDistance += legDist
                totalDuration += legDur

                val stepsArray = leg.optJSONArray("steps") ?: JSONArray()
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

                    // Skip intermediate waypoint arrival dummy step if not the final leg
                    if (legIdx < numLegs - 1 && (type.equals("arrive", ignoreCase = true) || instructions.contains("waypoint", ignoreCase = true))) {
                        continue
                    }

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
            }
        }

        if (totalDistance == 0 && routeObj.has("distance")) {
            totalDistance = routeObj.optInt("distance", 0)
        }
        if (totalDuration == 0 && routeObj.has("duration")) {
            totalDuration = routeObj.optInt("duration", 0)
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
        if (summary.isBlank() || summary.startsWith("Route", ignoreCase = true)) {
            val namedRoadStep = stepsList
                .filter { step ->
                    val ins = step.instruction
                    ins.contains(Regex("\\b(Road|Rd|Salai|NH|SH|MDR|Bypass|Highway|Flyover|Street|Avenue|Lane)\\b", RegexOption.IGNORE_CASE))
                }
                .maxByOrNull { it.distanceMeters }
            val chosenStep = namedRoadStep ?: stepsList.maxByOrNull { it.distanceMeters }
            val cleaned = (chosenStep?.instruction ?: "")
                .replace(Regex("^(Turn left onto|Turn right onto|Head|Continue onto|Keep left onto|Keep right onto|Take the ramp onto|Slight right onto|Slight left onto|Keep left at the fork onto|Keep right at the fork onto|Keep left at the fork|Keep right at the fork)\\s*", RegexOption.IGNORE_CASE), "")
                .replace(Regex("\\b(towards|via)\\b.*", RegexOption.IGNORE_CASE), "")
                .trim()
            if (cleaned.isNotBlank()) {
                summary = cleaned
            } else {
                summary = if (routeIndex == 0) "Fastest Route" else "Alternative Route $routeIndex"
            }
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
