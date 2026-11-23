package com.kalaiselvan.ride.navigation

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedLocationResolverTest {

    @Test
    fun testResolve_GeoUri() = runBlocking {
        val result = SharedLocationResolver.resolve("geo:12.971598,77.594566")
        assertNotNull(result)
        assertEquals(12.971598, result!!.latitude!!, 0.0001)
        assertEquals(77.594566, result.longitude!!, 0.0001)
        assertTrue(result.hasCoordinates)
    }

    @Test
    fun testResolve_GeoUriWithLabel() = runBlocking {
        val result = SharedLocationResolver.resolve("geo:0,0?q=12.971598,77.594566(Central Cafe)")
        assertNotNull(result)
        assertEquals(12.971598, result!!.latitude!!, 0.0001)
        assertEquals(77.594566, result.longitude!!, 0.0001)
        assertEquals("Central Cafe", result.label)
    }

    @Test
    fun testResolve_PlainTextCoordinates() = runBlocking {
        val result = SharedLocationResolver.resolve("Dropped pin at 13.082680, 80.270718 on the highway")
        assertNotNull(result)
        assertEquals(13.082680, result!!.latitude!!, 0.0001)
        assertEquals(80.270718, result.longitude!!, 0.0001)
        assertTrue(result.hasCoordinates)
    }

    @Test
    fun testResolve_PlaceNameQuery() = runBlocking {
        val result = SharedLocationResolver.resolve("Indiranagar Metro Station")
        assertNotNull(result)
        assertEquals("Indiranagar Metro Station", result!!.placeQuery)
        assertEquals("Indiranagar Metro Station", result.label)
    }

    @Test
    fun testResolve_NullOrBlank() = runBlocking {
        assertNull(SharedLocationResolver.resolve(null))
        assertNull(SharedLocationResolver.resolve(""))
        assertNull(SharedLocationResolver.resolve("   "))
    }
}

