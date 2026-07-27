package com.example.whatsappscheduler.util

import android.content.Context

object PhoneNormalizer {
    /**
     * Local numbers without a country code are prefixed using [defaultCountryCallingCode].
     * Example: Colombian `3105551234` + `57` becomes `573105551234`. Without that, WhatsApp may
     * misread the first digits as another country (e.g. `30…` → Greece).
     */
    fun toWhatsAppDigits(raw: String, defaultCountryCallingCode: String? = null): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null

        val hadPlus = trimmed.startsWith('+')
        var digits = buildString {
            trimmed.forEach { ch ->
                if (ch.isDigit()) append(ch)
            }
        }
        if (digits.isEmpty() || digits.all { it == '0' }) return null

        val cc = defaultCountryCallingCode?.filter { it.isDigit() }?.takeIf { it.isNotEmpty() }

        if (!hadPlus && cc != null && !digits.startsWith(cc)) {
            if (digits.startsWith("0") && digits.length > 1) {
                digits = digits.drop(1)
            }
            digits = cc + digits
        }

        if (digits.length !in 8..15) return null
        return digits
    }

    fun isValid(raw: String, defaultCountryCallingCode: String? = null): Boolean =
        toWhatsAppDigits(raw, defaultCountryCallingCode) != null

    fun defaultCountryCallingCode(context: Context): String =
        CountryPreferences(context).getCallingCode()

    fun isoToCallingCode(iso: String?): String? =
        CountryCatalog.all.firstOrNull { it.iso.equals(iso, ignoreCase = true) }?.callingCode

    fun formatForDisplay(digits: String): String = "+$digits"
}
