package com.kalaiselvan.ride.notifications

import java.util.Locale
import java.util.regex.Pattern

/**
 * Clean-room text and distance parser for turn-by-turn navigation notifications (e.g. Google Maps).
 *
 * Extracts metric and imperial countdown distances and maps maneuver instructions
 * deterministically to motorcycle TFT cluster turn symbol IDs (0..55).
 */
object NavNotificationParser {

    // Regex to match distances: e.g. "500 m", "1.5 km", "1,5 km", "300 ft", "2.1 mi", "200 yd"
    private val DISTANCE_REGEX = Pattern.compile(
        """(\d+(?:[.,]\d+)?)\s*(km|kms|kilometer|kilometers|kilometre|kilometres|mi|mile|miles|ft|feet|foot|yd|yds|yard|yards|m|meter|meters|metre|metres)\b""",
        Pattern.CASE_INSENSITIVE
    )

    private val ROUNDABOUT_EXIT_REGEX = Pattern.compile(
        """(?:take\s+the\s+)?(\d+)(?:st|nd|rd|th)?\s+exit""",
        Pattern.CASE_INSENSITIVE
    )

    private val ROUNDABOUT_EXIT_ALT_REGEX = Pattern.compile(
        """exit\s+(\d+)""",
        Pattern.CASE_INSENSITIVE
    )

    /**
     * Parses numeric distance and converts to meters.
     * Returns Pair(distanceInMeters, unitString). If no distance is found, returns Pair(0, "").
     */
    fun parseDistance(text: String): Pair<Int, String> {
        if (text.isBlank()) return Pair(0, "")
        val matcher = DISTANCE_REGEX.matcher(text)
        if (!matcher.find()) return Pair(0, "")

        val numRaw = matcher.group(1)?.replace(',', '.') ?: return Pair(0, "")
        val unitRaw = matcher.group(2)?.lowercase(Locale.ROOT) ?: ""
        val value = numRaw.toDoubleOrNull() ?: return Pair(0, "")

        val meters = when {
            unitRaw.startsWith("km") || unitRaw.startsWith("kilo") -> (value * 1000.0).toInt()
            unitRaw.startsWith("mi") -> (value * 1609.344).toInt()
            unitRaw.startsWith("ft") || unitRaw.startsWith("fee") || unitRaw.startsWith("foo") -> (value * 0.3048).toInt()
            unitRaw.startsWith("yd") || unitRaw.startsWith("yar") -> (value * 0.9144).toInt()
            else -> value.toInt() // standard meters
        }

        return Pair(meters.coerceAtLeast(0), unitRaw)
    }

    /**
     * Returns true if instruction contains an explicit roundabout exit number.
     */
    fun hasRoundaboutExitNumber(instruction: String): Boolean {
        val lower = instruction.lowercase(Locale.ROOT)
        if (!lower.contains("roundabout") && !lower.contains("rotary") && !lower.contains("traffic circle")) {
            return false
        }
        return ROUNDABOUT_EXIT_REGEX.matcher(lower).find() || ROUNDABOUT_EXIT_ALT_REGEX.matcher(lower).find()
    }

    /**
     * Maps turn instructions to motorcycle cluster TFT turn IDs (0..55).
     *
     * @param instruction The raw navigation text (e.g. "In 200m, turn sharp left onto MG Road")
     * @param leftHandTraffic True for left-hand drive countries (India, UK, Japan, Australia: CW roundabouts 18..29);
     *                        False for right-hand drive countries (US, EU: CCW roundabouts 30..41).
     */
    fun parseManeuver(instruction: String, leftHandTraffic: Boolean = true): Int {
        if (instruction.isBlank()) return 0
        val clean = instruction.lowercase(Locale.ROOT)

        // 1. Destination / Arrival (ID 43)
        if (clean.contains("destination") || clean.contains("you have arrived") ||
            clean.contains("you've arrived") || clean.contains("arrived at") ||
            clean.contains("destination reached") || clean.contains("at your destination")
        ) {
            return 43
        }

        // 2. Roundabouts (CW: 18..29, CCW: 30..41)
        if (clean.contains("roundabout") || clean.contains("traffic circle") || clean.contains("rotary")) {
            var exitNum = 1
            val m1 = ROUNDABOUT_EXIT_REGEX.matcher(clean)
            if (m1.find()) {
                exitNum = m1.group(1)?.toIntOrNull() ?: 1
            } else {
                val m2 = ROUNDABOUT_EXIT_ALT_REGEX.matcher(clean)
                if (m2.find()) {
                    exitNum = m2.group(1)?.toIntOrNull() ?: 1
                }
            }
            val safeExit = exitNum.coerceIn(1, 12)
            return if (leftHandTraffic) 17 + safeExit else 29 + safeExit
        }

        // 3. U-Turns (ID 7 = Left, ID 8 = Right)
        if (clean.contains("u-turn")) {
            return if (clean.contains("right")) 8 else 7
        }

        // 4. Sharp Turns (ID 3 = Sharp Left, ID 4 = Sharp Right)
        if (clean.contains("sharp")) {
            return if (clean.contains("right")) 4 else 3
        }

        // 5. Slight Turns (ID 5 = Slight Left, ID 6 = Slight Right)
        if (clean.contains("slight")) {
            return if (clean.contains("right")) 6 else 5
        }

        // 6. Highway On-Ramps (ID 12 = On-ramp Left, ID 13 = On-ramp Right)
        if (clean.contains("on ramp") || clean.contains("on-ramp") || clean.contains("enter freeway") || clean.contains("enter highway")) {
            return if (clean.contains("right")) 13 else 12
        }

        // 7. Highway Off-Ramps & Exits (ID 14 = Off-ramp Left, ID 15 = Off-ramp Right)
        if (clean.contains("off ramp") || clean.contains("off-ramp") || clean.contains("motorway exit") ||
            (clean.contains("exit") && !clean.contains("roundabout"))
        ) {
            return if (clean.contains("right")) 15 else 14
        }

        // 8. Highway Merges (ID 16 = Merge Left, ID 17 = Merge Right)
        if (clean.contains("merge")) {
            return if (clean.contains("right")) 17 else 16
        }

        // 9. Forks & Lane Keep (ID 9 = Fork Left, ID 10 = Fork Right, ID 11 = Middle)
        if (clean.contains("fork") || clean.contains("keep")) {
            return when {
                clean.contains("middle") || clean.contains("center") -> 11
                clean.contains("right") -> 10
                clean.contains("left") -> 9
                else -> 0
            }
        }

        // 10. Standard 90° Turns (ID 1 = Normal Left, ID 2 = Normal Right)
        val hasLeft = Pattern.compile("""\bleft\b""", Pattern.CASE_INSENSITIVE).matcher(clean).find()
        val hasRight = Pattern.compile("""\bright\b""", Pattern.CASE_INSENSITIVE).matcher(clean).find()
        if (hasLeft && !hasRight) return 1
        if (hasRight && !hasLeft) return 2

        // 11. Straight & Head (Departure 42 is remapped to 0 for motorcycle cluster compatibility)
        if (clean.contains("straight") || clean.contains("continue") || clean.contains("head ")) {
            return 0
        }

        return 0
    }
}
