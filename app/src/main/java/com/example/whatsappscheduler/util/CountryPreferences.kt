package com.example.whatsappscheduler.util

import android.content.Context

class CountryPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getSelectedCountry(): CountryOption {
        val iso = prefs.getString(KEY_ISO, CountryCatalog.DEFAULT_ISO) ?: CountryCatalog.DEFAULT_ISO
        return CountryCatalog.byIso(iso)
    }

    fun getCallingCode(): String = getSelectedCountry().callingCode

    fun setSelectedIso(iso: String) {
        val option = CountryCatalog.byIso(iso)
        prefs.edit()
            .putString(KEY_ISO, option.iso)
            .putString(KEY_CALLING_CODE, option.callingCode)
            .apply()
    }

    companion object {
        private const val PREFS = "whatsapp_scheduler_prefs"
        private const val KEY_ISO = "country_iso"
        private const val KEY_CALLING_CODE = "country_calling_code"
    }
}
