package com.kalaiselvan.ride.bluetooth

import org.junit.Assert.assertEquals
import org.junit.Test

class BikeModelDecoderTest {

    @Test
    fun testDecodeFromVin_Z900() {
        val result = BikeModelDecoder.decode(modelCode = null, vin = "ML5ZRDS1A00012345")
        assertEquals("Z900", result)
    }

    @Test
    fun testDecodeFromVin_Ninja400() {
        val result = BikeModelDecoder.decode(modelCode = null, vin = "JKAEX400A00012345")
        assertEquals("Ninja 400", result)
    }

    @Test
    fun testDecodeFromVin_NinjaZX6R() {
        val result = BikeModelDecoder.decode(modelCode = null, vin = "ZX636A00012345678")
        assertEquals("Ninja ZX-6R", result)
    }

    @Test
    fun testDecodeFromModelCode_Direct() {
        assertEquals("Z900", BikeModelDecoder.decode(modelCode = "ZR900B"))
        assertEquals("Ninja 650", BikeModelDecoder.decode(modelCode = "EX650"))
        assertEquals("Z650", BikeModelDecoder.decode(modelCode = "ER650"))
        assertEquals("Versys 650", BikeModelDecoder.decode(modelCode = "KLE650"))
    }

    @Test
    fun testDecodeFromBleDeviceName() {
        assertEquals("Z900", BikeModelDecoder.decode(modelCode = "Bike-ZR900S"))
    }

    @Test
    fun testDecode_NullOrBlank_ReturnsEmpty() {
        assertEquals("", BikeModelDecoder.decode(modelCode = null, vin = null))
        assertEquals("", BikeModelDecoder.decode(modelCode = "", vin = ""))
        assertEquals("", BikeModelDecoder.decode(modelCode = "   ", vin = "   "))
    }

    @Test
    fun testDecode_UnknownString_ReturnsEmpty() {
        assertEquals("", BikeModelDecoder.decode(modelCode = "RandomBluetoothSpeaker"))
    }
}

