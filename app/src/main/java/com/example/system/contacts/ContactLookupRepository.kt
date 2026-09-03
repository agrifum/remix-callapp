package com.example.system.contacts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ContactLookupRepository:
 * Resolves contact display names from system Android Contacts by phone number.
 * Uses ContactsContract.PhoneLookup off the main thread.
 * Returns fallback "Klient <phoneNumber>" if contact is not found, permissions are missing, or errors occur.
 */
class ContactLookupRepository(private val context: Context) {

    suspend fun resolveDisplayName(phoneNumber: String): String = withContext(Dispatchers.IO) {
        val fallbackName = "Klient $phoneNumber"
        if (phoneNumber.isBlank()) {
            return@withContext fallbackName
        }

        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            return@withContext fallbackName
        }

        try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
            )
            val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)

            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        val name = cursor.getString(nameIndex)
                        if (!name.isNullOrBlank()) {
                            return@withContext name
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Secure fallback on any query or security exception
        }

        return@withContext fallbackName
    }
}
