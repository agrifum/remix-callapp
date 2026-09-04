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
     * received within a narrow timestamp tolerance around [receivedAt].
     * Returns the raw SMS body string, or null if no matching message exists.
     */
    fun readSms(senderPhoneKey: String, receivedAt: Long): String?
}

/**
 * Default implementation querying [Telephony.Sms.Inbox.CONTENT_URI] via [Context.getContentResolver].
 */
class DefaultSystemSmsReader(private val context: Context) : SystemSmsReader {

    override fun readSms(senderPhoneKey: String, receivedAt: Long): String? {
        val contentResolver = context.contentResolver ?: return null
        val uri = Telephony.Sms.Inbox.CONTENT_URI
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.DATE_SENT
        )

        // Tolerance window of +/- 10 seconds (10,000 ms)
        val tolerance = 10_000L
        val minTime = (receivedAt - tolerance).toString()
        val maxTime = (receivedAt + tolerance).toString()
        val selection = "(${Telephony.Sms.DATE} >= ? AND ${Telephony.Sms.DATE} <= ?) OR (${Telephony.Sms.DATE_SENT} >= ? AND ${Telephony.Sms.DATE_SENT} <= ?)"
        val selectionArgs = arrayOf(minTime, maxTime, minTime, maxTime)
        val sortOrder = "${Telephony.Sms.DATE} DESC"

        try {
            contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
                val addressIdx = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyIdx = cursor.getColumnIndex(Telephony.Sms.BODY)
                val dateIdx = cursor.getColumnIndex(Telephony.Sms.DATE)
                val dateSentIdx = cursor.getColumnIndex(Telephony.Sms.DATE_SENT)

                var bestMatchBody: String? = null
                var minDiff = Long.MAX_VALUE

                while (cursor.moveToNext()) {
                    val address = if (addressIdx >= 0) cursor.getString(addressIdx) else null
                    val body = if (bodyIdx >= 0) cursor.getString(bodyIdx) else null
                    val date = if (dateIdx >= 0) cursor.getLong(dateIdx) else 0L
                    val dateSent = if (dateSentIdx >= 0) cursor.getLong(dateSentIdx) else 0L

                    if (body.isNullOrBlank()) continue

                    val candidateKey = PhoneNumberNormalizer.normalizeKey(address)
                    if (candidateKey == senderPhoneKey) {
                        val diffSent = if (dateSent > 0) abs(dateSent - receivedAt) else Long.MAX_VALUE
                        val diffDate = abs(date - receivedAt)
                        val diff = minOf(diffSent, diffDate)
                        if (diff <= tolerance && diff < minDiff) {
                            minDiff = diff
                            bestMatchBody = body
                        }
                    }
                }
                return bestMatchBody
            }
        } catch (_: SecurityException) {
            return null
        } catch (_: Exception) {
            return null
        }

        return null
    }
}
