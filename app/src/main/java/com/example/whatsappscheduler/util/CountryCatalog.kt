package com.example.whatsappscheduler.util

data class CountryOption(
    val iso: String,
    val name: String,
    val callingCode: String
) {
    val label: String get() = "$name (+$callingCode)"
}

object CountryCatalog {
    val DEFAULT_ISO = "CO"
    val DEFAULT_CALLING_CODE = "57"

    /** Colombia first as the app default; rest alphabetical by name. */
    val all: List<CountryOption> = listOf(
        CountryOption("CO", "Colombia", "57"),
        CountryOption("AR", "Argentina", "54"),
        CountryOption("AU", "Australia", "61"),
        CountryOption("BR", "Brazil", "55"),
        CountryOption("CA", "Canada", "1"),
        CountryOption("CL", "Chile", "56"),
        CountryOption("EC", "Ecuador", "593"),
        CountryOption("FR", "France", "33"),
        CountryOption("DE", "Germany", "49"),
        CountryOption("IN", "India", "91"),
        CountryOption("IT", "Italy", "39"),
        CountryOption("MX", "Mexico", "52"),
        CountryOption("PE", "Peru", "51"),
        CountryOption("ES", "Spain", "34"),
        CountryOption("GB", "United Kingdom", "44"),
        CountryOption("US", "United States", "1"),
        CountryOption("VE", "Venezuela", "58")
    )

    fun byIso(iso: String): CountryOption =
        all.firstOrNull { it.iso.equals(iso, ignoreCase = true) }
            ?: all.first { it.iso == DEFAULT_ISO }

    fun byCallingCode(code: String): CountryOption? =
        all.firstOrNull { it.callingCode == code.filter { ch -> ch.isDigit() } }
}
