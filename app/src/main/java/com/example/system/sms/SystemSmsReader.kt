package com.example.system.sms

import android.content.Context
import android.provider.Telephony
import com.example.core.phone.PhoneNumberNormalizer
import kotlin.math.abs

/**
 * SystemSmsReader: Contract for selectively re-reading exactly one intended SMS
 * from the Android system SMS provider without loading arbitrary inbox history.
 */
interface SystemSmsReader {
    /**
     * Attempts to find and read the single incoming SMS matching the given [senderPhoneKey]
     * received at or around [receivedAt].
     *
     * Two-phase lookup contract:
     * - Phase 1: Resolves unique candidate row by querying metadata ONLY (ID, address, timestamps).
     *   Never projects or reads BODY in Phase 1.
     *   Ambiguous candidates (e.g. two messages from the same sender within tolerance without an
     *   exact timestamp match) fail closed and return null.
     * - Phase 2: Queries BODY for exactly that single resolved row ID.
     */
    fun readSms(senderPhoneKey: String, receivedAt: Long): String?
}

/**
 * Default implementation querying [Telephony.Sms.Inbox.CONTENT_URI] via [Context.getContentResolver].
 */
class DefaultSystemSmsReader(private val context: Context) : SystemSmsReader {

    private data class SmsMetadataCandidate(
        val id: Long,
        val date: Long,
        val dateSent: Long
    )

    override fun readSms(senderPhoneKey: String, receivedAt: Long): String? {
        val contentResolver = context.contentResolver ?: return null
        val uri = Telephony.Sms.Inbox.CONTENT_URI

        // =====================================================================
        // PHASE 1: Metadata-only candidate resolution (NO BODY projected or read)
        // =====================================================================
        val metadataProjection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.DATE,
            Telephony.Sms.DATE_SENT
        )

        val tolerance = 10_000L
        val minTime = (receivedAt - tolerance).toString()
        val maxTime = (receivedAt + tolerance).toString()
        val selection = "(${Telephony.Sms.DATE} >= ? AND ${Telephony.Sms.DATE} <= ?) OR (${Telephony.Sms.DATE_SENT} >= ? AND ${Telephony.Sms.DATE_SENT} <= ?)"
        val selectionArgs = arrayOf(minTime, maxTime, minTime, maxTime)
        val sortOrder = "${Telephony.Sms.DATE} DESC"

        val matchingCandidates = mutableListOf<SmsMetadataCandidate>()

        try {
            contentResolver.query(uri, metadataProjection, selection, selectionArgs, sortOrder)?.use { cursor ->
                val idIdx = cursor.getColumnIndex(Telephony.Sms._ID)
                val addressIdx = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
                val dateIdx = cursor.getColumnIndex(Telephony.Sms.DATE)
                val dateSentIdx = cursor.getColumnIndex(Telephony.Sms.DATE_SENT)

                while (cursor.moveToNext()) {
                    val id = if (idIdx >= 0) cursor.getLong(idIdx) else continue
                    val address = if (addressIdx >= 0) cursor.getString(addressIdx) else null
                    val date = if (dateIdx >= 0) cursor.getLong(dateIdx) else 0L
                    val dateSent = if (dateSentIdx >= 0) cursor.getLong(dateSentIdx) else 0L

                    val candidateKey = PhoneNumberNormalizer.normalizeKey(address)
                    if (candidateKey == senderPhoneKey) {
                        matchingCandidates.add(SmsMetadataCandidate(id, date, dateSent))
                    }
                }
            }
        } catch (_: SecurityException) {
            return null
        } catch (_: Exception) {
            return null
        }

        if (matchingCandidates.isEmpty()) {
            return null // Not found
        }

        // Resolution rule:
        // 1. Prefer an exact timestamp match (dateSent or date == receivedAt)
        val exactMatches = matchingCandidates.filter {
            it.dateSent == receivedAt || it.date == receivedAt
        }

        val resolvedId: Long = when {
            exactMatches.size == 1 -> exactMatches[0].id
            exactMatches.size > 1 -> return null // Ambiguous: multiple exact matches. Fail closed!
            matchingCandidates.size == 1 -> matchingCandidates[0].id // Single candidate in tolerance
            else -> return null // Ambiguous: multiple candidates in tolerance without unique match. Fail closed!
        }

        // =====================================================================
        // PHASE 2: Exact BODY read ONLY for the resolved unique _ID
        // =====================================================================
        val bodyProjection = arrayOf(Telephony.Sms.BODY)
        val singleSelection = "${Telephony.Sms._ID} = ?"
        val singleSelectionArgs = arrayOf(resolvedId.toString())

        try {
            contentResolver.query(uri, bodyProjection, singleSelection, singleSelectionArgs, null)?.use { cursor ->
                val bodyIdx = cursor.getColumnIndex(Telephony.Sms.BODY)
                if (cursor.moveToFirst() && bodyIdx >= 0) {
                    val body = cursor.getString(bodyIdx)
                    if (!body.isNullOrBlank()) {
                        return body
                    }
                }
            }
        } catch (_: SecurityException) {
            return null
        } catch (_: Exception) {
            return null
        }

        return null
    }
}
