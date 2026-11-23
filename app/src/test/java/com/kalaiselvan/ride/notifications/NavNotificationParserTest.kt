package com.kalaiselvan.ride.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavNotificationParserTest {

    @Test
    fun testParseDistance_Meters() {
        val (dist, unit) = NavNotificationParser.parseDistance("In 500 m, turn right")
        assertEquals(500, dist)
        assertEquals("m", unit)
    }

    @Test
    fun testParseDistance_Kilometers() {
        val (dist, unit) = NavNotificationParser.parseDistance("In 1.5 km, take exit")
        assertEquals(1500, dist)
        assertEquals("km", unit)
    }

    @Test
    fun testParseDistance_CommaDecimal() {
        val (dist, unit) = NavNotificationParser.parseDistance("In 2,5 km, turn left")
        assertEquals(2500, dist)
        assertEquals("km", unit)
    }

    @Test
    fun testParseDistance_ImperialMiles() {
        val (dist, unit) = NavNotificationParser.parseDistance("In 2 mi, continue straight")
        assertEquals(3218, dist)
        assertEquals("mi", unit)
    }

    @Test
    fun testParseDistance_EmptyOrInvalid() {
        val (dist, unit) = NavNotificationParser.parseDistance("")
        assertEquals(0, dist)
        assertEquals("", unit)

        val (noDist, noUnit) = NavNotificationParser.parseDistance("Continue onto Main Street")
        assertEquals(0, noDist)
        assertEquals("", noUnit)
    }

    @Test
    fun testHasRoundaboutExitNumber() {
        assertTrue(NavNotificationParser.hasRoundaboutExitNumber("At the roundabout, take the 2nd exit"))
        assertTrue(NavNotificationParser.hasRoundaboutExitNumber("Rotary: exit 3"))
        assertFalse(NavNotificationParser.hasRoundaboutExitNumber("Turn left on Main St"))
    }

    @Test
    fun testParseManeuver_Arrival() {
        assertEquals(43, NavNotificationParser.parseManeuver("You have arrived at your destination"))
        assertEquals(43, NavNotificationParser.parseManeuver("Destination reached"))
    }

    @Test
    fun testParseManeuver_Turns() {
        assertEquals(3, NavNotificationParser.parseManeuver("Turn sharp left"))
        assertEquals(4, NavNotificationParser.parseManeuver("Turn sharp right"))
        assertEquals(5, NavNotificationParser.parseManeuver("Turn slight left"))
        assertEquals(6, NavNotificationParser.parseManeuver("Turn slight right"))
        assertEquals(7, NavNotificationParser.parseManeuver("Make a U-turn"))
        assertEquals(8, NavNotificationParser.parseManeuver("Make a right U-turn"))
    }

    @Test
    fun testParseManeuver_Roundabout() {
        // Left hand traffic (CW: 17 + exit)
        assertEquals(19, NavNotificationParser.parseManeuver("Take the 2nd exit at roundabout", leftHandTraffic = true))
        // Right hand traffic (CCW: 29 + exit)
        assertEquals(31, NavNotificationParser.parseManeuver("Take the 2nd exit at roundabout", leftHandTraffic = false))
    }

    @Test
    fun testParseManeuver_WazeInstructions() {
        assertEquals(9, NavNotificationParser.parseManeuver("Keep left on US-101 North"))
        assertEquals(10, NavNotificationParser.parseManeuver("Keep right towards Downtown"))
        assertEquals(15, NavNotificationParser.parseManeuver("Exit 14B on right"))
        val (dist, unit) = NavNotificationParser.parseDistance("In 400 m, turn right on Main St")
        assertEquals(400, dist)
        assertEquals("m", unit)
        assertEquals(2, NavNotificationParser.parseManeuver("In 400 m, turn right on Main St"))
    }

    @Test
    fun testParseManeuver_HereWeGoInstructions() {
        val (dist, unit) = NavNotificationParser.parseDistance("In 1.2 km, turn slight left onto Ring Road")
        assertEquals(1200, dist)
        assertEquals("km", unit)
        assertEquals(5, NavNotificationParser.parseManeuver("In 1.2 km, turn slight left onto Ring Road"))
        assertEquals(43, NavNotificationParser.parseManeuver("Destination reached: Grand Hotel"))
    }

    @Test
    fun testParse_IndianLanguages() {
        // Hindi
        val (hiDist, _) = NavNotificationParser.parseDistance("500 मी. में, दाएं मुड़ें")
        assertEquals(500, hiDist)
        assertEquals(2, NavNotificationParser.parseManeuver("500 मी. में, दाएं मुड़ें"))
        assertEquals(1, NavNotificationParser.parseManeuver("1.2 किमी में, बाएं मुड़ें"))
        assertEquals(7, NavNotificationParser.parseManeuver("आगे यू-टर्न लें"))
        assertEquals(43, NavNotificationParser.parseManeuver("गंतव्य पर पहुंच गए हैं"))

        // Tamil
        val (taDist, _) = NavNotificationParser.parseDistance("300 மீ தொலைவில் வலதுபுறம் திரும்பவும்")
        assertEquals(300, taDist)
        assertEquals(2, NavNotificationParser.parseManeuver("வலதுபுறம் திரும்பவும்"))
        assertEquals(1, NavNotificationParser.parseManeuver("இடதுபுறம் திரும்பவும்"))
        assertEquals(43, NavNotificationParser.parseManeuver("சேருமிடம் சென்றடைந்தீர்கள்"))

        // Telugu
        assertEquals(2, NavNotificationParser.parseManeuver("కుడివైపు తిరగండి"))
        assertEquals(1, NavNotificationParser.parseManeuver("ఎడమవైపు తిరగండి"))

        // Kannada
        assertEquals(2, NavNotificationParser.parseManeuver("ಬಲಕ್ಕೆ ತಿರುಗಿ"))
        assertEquals(1, NavNotificationParser.parseManeuver("ಎಡಕ್ಕೆ ತಿರುಗಿ"))

        // Malayalam
        assertEquals(2, NavNotificationParser.parseManeuver("വലത്തോട്ട് തിരിയുക"))
        assertEquals(1, NavNotificationParser.parseManeuver("ഇടത്തോട്ട് തിരിയുക"))

        // Bengali & Gujarati & Marathi
        assertEquals(2, NavNotificationParser.parseManeuver("ডানদিকে ঘুরুন"))
        assertEquals(2, NavNotificationParser.parseManeuver("જમણે વળો"))
        assertEquals(2, NavNotificationParser.parseManeuver("उजवीकडे वळा"))
    }

    @Test
    fun testParse_InternationalLanguages() {
        // Spanish
        val (esDist, _) = NavNotificationParser.parseDistance("En 300 m, gire a la derecha")
        assertEquals(300, esDist)
        assertEquals(2, NavNotificationParser.parseManeuver("En 300 m, gire a la derecha"))
        assertEquals(19, NavNotificationParser.parseManeuver("En la rotonda, tome la 2.ª salida", leftHandTraffic = true))
        assertEquals(43, NavNotificationParser.parseManeuver("Ha llegado a su destino"))

        // German
        val (deDist, _) = NavNotificationParser.parseDistance("In 1,5 km scharf rechts abbiegen")
        assertEquals(1500, deDist)
        assertEquals(4, NavNotificationParser.parseManeuver("In 1,5 km scharf rechts abbiegen"))
        assertEquals(43, NavNotificationParser.parseManeuver("Ziel erreicht"))

        // French
        val (frDist, _) = NavNotificationParser.parseDistance("Dans 200 m, tournez à gauche")
        assertEquals(200, frDist)
        assertEquals(1, NavNotificationParser.parseManeuver("Dans 200 m, tournez à gauche"))
        assertEquals(20, NavNotificationParser.parseManeuver("Au rond-point, prenez la 3e sortie", leftHandTraffic = true))

        // Japanese
        val (jaDist, _) = NavNotificationParser.parseDistance("300m先、右方向です")
        assertEquals(300, jaDist)
        assertEquals(2, NavNotificationParser.parseManeuver("300m先、右方向です"))
        assertEquals(43, NavNotificationParser.parseManeuver("目的地に到着しました"))

        // Arabic
        val (arDist, _) = NavNotificationParser.parseDistance("بعد 400 متر، اتجه إلى اليمين")
        assertEquals(400, arDist)
        assertEquals(2, NavNotificationParser.parseManeuver("بعد 400 متر، اتجه إلى اليمين"))
    }

    @Test
    fun testParse_IndicNumeralsNormalization() {
        // Devanagari numerals ५०० = 500
        val (dist, _) = NavNotificationParser.parseDistance("५०० मी. में दाएं मुड़ें")
        assertEquals(500, dist)
        assertEquals(2, NavNotificationParser.parseManeuver("५०० मी. में दाएं मुड़ें"))
    }
}

