package com.kalaiselvan.ride.maintenance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ServiceRecordTest {

    @Test
    fun testServiceRecordJsonSerialization() {
        val record = ServiceRecord(
            id = "srv_test_001",
            odoKm = 6000.0,
            serviceDateMs = 1700000000000L,
            totalCost = 3500.0,
            currency = "₹",
            notes = "Full synthetic Motul 300V oil replaced",
            reminderEnabled = true,
            nextServiceOdoKm = 12000.0,
            nextServiceDateMs = 1715000000000L,
            receiptImagePaths = listOf("/path/to/receipt.jpg")
        )

        val json = record.toJsonObject()
        val deserialized = ServiceRecord.fromJsonObject(json)

        assertEquals(record.id, deserialized.id)
        assertEquals(record.odoKm, deserialized.odoKm, 0.01)
        assertEquals(record.serviceDateMs, deserialized.serviceDateMs)
        assertEquals(record.totalCost, deserialized.totalCost, 0.01)
        assertEquals(record.currency, deserialized.currency)
        assertEquals(record.notes, deserialized.notes)
        assertEquals(record.reminderEnabled, deserialized.reminderEnabled)
        assertEquals(record.nextServiceOdoKm, deserialized.nextServiceOdoKm)
        assertEquals(record.nextServiceDateMs, deserialized.nextServiceDateMs)
        assertEquals(record.receiptImagePaths, deserialized.receiptImagePaths)
    }

    @Test
    fun testServiceRecordDefaults() {
        val record = ServiceRecord(
            odoKm = 1000.0,
            serviceDateMs = 1700000000000L,
            notes = "First Free Service"
        )

        assertNotNull(record.id)
        assertEquals(0.0, record.totalCost, 0.001)
        assertEquals(true, record.reminderEnabled)
    }
}
