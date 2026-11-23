package com.kalaiselvan.ride.logs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RideSessionTest {

    @Test
    fun testBreadcrumbJsonSerialization() {
        val bc = RideBreadcrumb(
            lat = 12.971598,
            lon = 77.594562,
            speedKmh = 72.5f,
            leanAngle = 28.3f,
            timestamp = 1700000000000L
        )

        val json = bc.toJsonObject()
        val deserialized = RideBreadcrumb.fromJsonObject(json)

        assertEquals(bc.lat, deserialized.lat, 0.0001)
        assertEquals(bc.lon, deserialized.lon, 0.0001)
        assertEquals(bc.speedKmh, deserialized.speedKmh, 0.1f)
        assertEquals(bc.leanAngle, deserialized.leanAngle, 0.1f)
        assertEquals(bc.timestamp, deserialized.timestamp)
    }

    @Test
    fun testRideSessionJsonSerialization() {
        val breadcrumbs = listOf(
            RideBreadcrumb(12.971, 77.594, 30.0f, 5.0f, 1700000000000L),
            RideBreadcrumb(12.972, 77.595, 65.0f, 34.0f, 1700000002000L),
            RideBreadcrumb(12.973, 77.596, 92.0f, -28.0f, 1700000004000L)
        )

        val session = RideSession(
            id = "test_ride_123",
            startTime = 1700000000000L,
            endTime = 1700001800000L,
            durationSeconds = 1800L,
            distanceKm = 24.8,
            topSpeedKmh = 118.5f,
            avgSpeedKmh = 54.2f,
            maxLeanLeft = 36.5f,
            maxLeanRight = 41.2f,
            maxGForce = 1.08f,
            maxWheelie = 2.4f,
            maxStoppie = 1.1f,
            avgFuelMileage = 18.5f,
            breadcrumbs = breadcrumbs
        )

        val json = session.toJsonObject()
        val deserialized = RideSession.fromJsonObject(json)

        assertEquals(session.id, deserialized.id)
        assertEquals(session.startTime, deserialized.startTime)
        assertEquals(session.endTime, deserialized.endTime)
        assertEquals(session.durationSeconds, deserialized.durationSeconds)
        assertEquals(session.distanceKm, deserialized.distanceKm, 0.01)
        assertEquals(session.topSpeedKmh, deserialized.topSpeedKmh, 0.1f)
        assertEquals(session.avgSpeedKmh, deserialized.avgSpeedKmh, 0.1f)
        assertEquals(session.maxLeanLeft, deserialized.maxLeanLeft, 0.1f)
        assertEquals(session.maxLeanRight, deserialized.maxLeanRight, 0.1f)
        assertEquals(session.maxGForce, deserialized.maxGForce, 0.01f)
        assertEquals(session.maxWheelie, deserialized.maxWheelie, 0.1f)
        assertEquals(session.maxStoppie, deserialized.maxStoppie, 0.1f)
        assertEquals(session.avgFuelMileage, deserialized.avgFuelMileage, 0.1f)
        assertEquals(3, deserialized.breadcrumbs.size)
        assertEquals(breadcrumbs[1].speedKmh, deserialized.breadcrumbs[1].speedKmh, 0.1f)
    }

    @Test
    fun testRideSessionRecorderFlow() {
        RideSessionRecorder.clearCompletedSessions()

        RideSessionRecorder.startSession(currentOdo = 1000.0)
        assertTrue(RideSessionRecorder.isRecording)

        RideSessionRecorder.updateTelemetry(speedKmh = 45f, leanL = 10f, leanR = 12f)
        RideSessionRecorder.updateTelemetry(speedKmh = 88f, leanL = 35f, leanR = 20f)
        RideSessionRecorder.updateTelemetry(speedKmh = 60f, leanL = 15f, leanR = 40f)

        RideSessionRecorder.addBreadcrumb(12.971, 77.594, 45f, 12f)
        RideSessionRecorder.addBreadcrumb(12.972, 77.595, 88f, -35f)

        val recorded = RideSessionRecorder.stopSession(
            finalOdo = 1015.5,
            explicitDistKm = 15.5,
            maxGForce = 1.12f,
            maxWheelie = 3.2f,
            maxStoppie = 0.5f,
            avgFuel = 19.2f
        )

        assertNotNull(recorded)
        assertEquals(88f, recorded!!.topSpeedKmh, 0.1f)
        assertEquals(35f, recorded.maxLeanLeft, 0.1f)
        assertEquals(40f, recorded.maxLeanRight, 0.1f)
        assertEquals(15.5, recorded.distanceKm, 0.01)
        assertEquals(1.12f, recorded.maxGForce, 0.01f)
        assertEquals(3.2f, recorded.maxWheelie, 0.1f)
        assertEquals(0.5f, recorded.maxStoppie, 0.1f)

        val completed = RideSessionRecorder.completedSessions.value
        assertEquals(1, completed.size)
        assertEquals(recorded.id, completed[0].id)

        // Test delete session
        RideSessionRecorder.deleteSession(recorded.id)
        assertTrue(RideSessionRecorder.completedSessions.value.isEmpty())
    }
}

