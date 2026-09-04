package com.example.system.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.example.data.entity.ClientEntity
import com.example.data.entity.JobEntity
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.TimeZone

interface CalendarManager {
    fun hasCalendarPermission(): Boolean
    suspend fun createEvent(job: JobEntity, client: ClientEntity?): Long?
    suspend fun updateEvent(calendarEventId: Long, job: JobEntity, client: ClientEntity?): Boolean
    suspend fun deleteEvent(calendarEventId: Long): Boolean
}

class AndroidCalendarManager(
    private val context: Context
) : CalendarManager {

    companion object {
        const val DEFAULT_DURATION_MILLIS: Long = 60 * 60 * 1000L // 60 minutes per spec §46
    }

    override fun hasCalendarPermission(): Boolean {
        val read = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
        val write = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR)
        return read == PackageManager.PERMISSION_GRANTED && write == PackageManager.PERMISSION_GRANTED
    }

    private fun getPrimaryCalendarId(): Long? {
        if (!hasCalendarPermission()) return null
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.IS_PRIMARY,
            CalendarContract.Calendars.VISIBLE
        )
        val uri = CalendarContract.Calendars.CONTENT_URI
        try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                var fallbackId: Long? = null
                val idIdx = cursor.getColumnIndex(CalendarContract.Calendars._ID)
                val primaryIdx = cursor.getColumnIndex(CalendarContract.Calendars.IS_PRIMARY)
                val visibleIdx = cursor.getColumnIndex(CalendarContract.Calendars.VISIBLE)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIdx)
                    val isPrimary = if (primaryIdx >= 0) cursor.getInt(primaryIdx) == 1 else false
                    val isVisible = if (visibleIdx >= 0) cursor.getInt(visibleIdx) == 1 else true

                    if (isPrimary) {
                        return id
                    }
                    if (fallbackId == null && isVisible) {
                        fallbackId = id
                    }
                }
                return fallbackId
            }
        } catch (_: SecurityException) {
            return null
        } catch (_: Exception) {
            return null
        }
        return null
    }

    private fun computeStartTimeMillis(job: JobEntity): Long? {
        if (job.confirmedStartAt != null) {
            return job.confirmedStartAt
        }
        if (job.preliminaryDateEpochDay != null) {
            val date = LocalDate.ofEpochDay(job.preliminaryDateEpochDay)
            val time = if (job.preliminaryTimeMinute != null) {
                LocalTime.of(job.preliminaryTimeMinute / 60, job.preliminaryTimeMinute % 60)
            } else {
                LocalTime.of(9, 0) // default morning start if only date is specified
            }
            return date.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
        return null
    }

    private fun buildTitle(job: JobEntity, client: ClientEntity?): String {
        val serviceName = job.serviceNameSnapshot ?: "Zlecenie"
        val clientName = client?.displayName ?: client?.phoneDisplay ?: "Klient"
        return "$serviceName - $clientName"
    }

    private fun buildLocation(job: JobEntity, client: ClientEntity?): String {
        val parts = listOfNotNull(
            job.addressStreetSnapshot ?: client?.street,
            job.addressBuildingSnapshot ?: client?.buildingNumber,
            job.addressUnitSnapshot?.let { "m. $it" } ?: client?.unitNumber?.let { "m. $it" },
            job.addressCitySnapshot ?: client?.city
        )
        return parts.joinToString(" ").trim()
    }

    private fun buildDescription(job: JobEntity, client: ClientEntity?): String {
        val builder = StringBuilder()
        if (!job.manualNotes.isNullOrBlank()) {
            builder.append("Notatki: ").append(job.manualNotes).append("\n")
        }
        if (!job.smsSummary.isNullOrBlank()) {
            builder.append("SMS: ").append(job.smsSummary).append("\n")
        }
        if (client != null) {
            builder.append("Telefon: ").append(client.phoneDisplay).append("\n")
        }
        if (job.priceMinor != null) {
            builder.append("Cena: ").append(job.priceMinor / 100).append(" zł\n")
        }
        return builder.toString().trim()
    }

    override suspend fun createEvent(job: JobEntity, client: ClientEntity?): Long? {
        if (!hasCalendarPermission()) return null
        val calendarId = getPrimaryCalendarId() ?: return null
        val startMillis = computeStartTimeMillis(job) ?: return null
        val endMillis = startMillis + DEFAULT_DURATION_MILLIS

        val values = ContentValues().apply {
            put(CalendarContract.Events.DTSTART, startMillis)
            put(CalendarContract.Events.DTEND, endMillis)
            put(CalendarContract.Events.TITLE, buildTitle(job, client))
            put(CalendarContract.Events.DESCRIPTION, buildDescription(job, client))
            put(CalendarContract.Events.EVENT_LOCATION, buildLocation(job, client))
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
        }

        return try {
            val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            uri?.lastPathSegment?.toLongOrNull()
        } catch (_: SecurityException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun updateEvent(calendarEventId: Long, job: JobEntity, client: ClientEntity?): Boolean {
        if (!hasCalendarPermission()) return false
        val startMillis = computeStartTimeMillis(job) ?: return false
        val endMillis = startMillis + DEFAULT_DURATION_MILLIS

        val values = ContentValues().apply {
            put(CalendarContract.Events.DTSTART, startMillis)
            put(CalendarContract.Events.DTEND, endMillis)
            put(CalendarContract.Events.TITLE, buildTitle(job, client))
            put(CalendarContract.Events.DESCRIPTION, buildDescription(job, client))
            put(CalendarContract.Events.EVENT_LOCATION, buildLocation(job, client))
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
        }

        return try {
            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, calendarEventId)
            val updated = context.contentResolver.update(uri, values, null, null)
            updated > 0
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun deleteEvent(calendarEventId: Long): Boolean {
        if (!hasCalendarPermission()) return false
        return try {
            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, calendarEventId)
            val deleted = context.contentResolver.delete(uri, null, null)
            deleted > 0
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        }
    }
}