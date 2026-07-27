package com.example.whatsappscheduler.automation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SendModelsTest {
    @Test
    fun sendResultDefaults() {
        assertTrue(SendResult(true).success)
        assertFalse(SendResult(false, "boom").success)
    }
}
