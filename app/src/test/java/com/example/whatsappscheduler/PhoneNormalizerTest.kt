package com.example.whatsappscheduler

import com.example.whatsappscheduler.util.PhoneNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneNormalizerTest {
    @Test
    fun stripsFormattingAndKeepsDigits() {
        assertEquals("15551234567", PhoneNormalizer.toWhatsAppDigits("+1 (555) 123-4567"))
    }

    @Test
    fun rejectsTooShort() {
        assertNull(PhoneNormalizer.toWhatsAppDigits("123"))
        assertFalse(PhoneNormalizer.isValid("123"))
    }

    @Test
    fun rejectsEmpty() {
        assertNull(PhoneNormalizer.toWhatsAppDigits("   "))
    }

    @Test
    fun acceptsPlainInternational() {
        assertTrue(PhoneNormalizer.isValid("5215512345678"))
    }

    @Test
    fun prependsColombiaCodeForLocalMobile() {
        assertEquals(
            "573007162262",
            PhoneNormalizer.toWhatsAppDigits("3007162262", defaultCountryCallingCode = "57")
        )
    }

    @Test
    fun doesNotDoublePrefixWhenAlreadyInternational() {
        assertEquals(
            "573007162262",
            PhoneNormalizer.toWhatsAppDigits("573007162262", defaultCountryCallingCode = "57")
        )
        assertEquals(
            "573007162262",
            PhoneNormalizer.toWhatsAppDigits("+57 300 716 2262", defaultCountryCallingCode = "57")
        )
    }

    @Test
    fun plusNumberSkipsDefaultCountryPrefix() {
        // Explicit + keeps digits as entered (after stripping non-digits).
        assertEquals(
            "15551234567",
            PhoneNormalizer.toWhatsAppDigits("+15551234567", defaultCountryCallingCode = "57")
        )
    }
}
