package com.example.whatsappscheduler.util

import android.content.Context
import android.provider.ContactsContract

data class DeviceContact(
    val id: Long,
    val displayName: String,
    val phoneDigits: String,
    val phoneDisplay: String
)

object ContactDirectory {
    fun search(context: Context, query: String, limit: Int = 40): List<DeviceContact> {
        val cr = context.contentResolver
        val selection: String?
        val args: Array<String>?
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            selection = "${ContactsContract.CommonDataKinds.Phone.NUMBER} IS NOT NULL"
            args = null
        } else {
            selection =
                "(${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} LIKE ? OR " +
                    "${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?)"
            args = arrayOf("%$trimmed%", "%$trimmed%")
        }

        val results = linkedMapOf<String, DeviceContact>()
        cr.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            selection,
            args,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} ASC"
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY)
            val numberIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (cursor.moveToNext() && results.size < limit) {
                val raw = cursor.getString(numberIdx) ?: continue
                val digits = PhoneNormalizer.toWhatsAppDigits(
                    raw,
                    CountryPreferences(context).getCallingCode()
                ) ?: continue
                if (results.containsKey(digits)) continue
                val name = cursor.getString(nameIdx)?.takeIf { it.isNotBlank() } ?: digits
                results[digits] = DeviceContact(
                    id = cursor.getLong(idIdx),
                    displayName = name,
                    phoneDigits = digits,
                    phoneDisplay = raw
                )
            }
        }
        return results.values.toList()
    }
}
