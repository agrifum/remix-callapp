package com.example.core.time

import com.example.core.model.TimeQualifier
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object DateTimeFormatters {

    private val POLISH_LOCALE = Locale("pl", "PL")
    private val DATE_FORMATTER = DateTimeFormatter.ofPattern("d MMM yyyy", POLISH_LOCALE)
    private val DATE_SHORT_FORMATTER = DateTimeFormatter.ofPattern("d MMM", POLISH_LOCALE)
    private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm", POLISH_LOCALE)
    private val DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", POLISH_LOCALE)

    fun formatDateTime(epochMillis: Long): String {
        val dt = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault())
        return dt.format(DATE_TIME_FORMATTER)
    }

    fun formatDate(epochMillis: Long): String {
        val dt = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault())
        return dt.format(DATE_FORMATTER)
    }

    fun formatTime(epochMillis: Long): String {
        val dt = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault())
        return dt.format(TIME_FORMATTER)
    }

    fun formatDuration(seconds: Long): String {
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return if (minutes > 0) {
            String.format(POLISH_LOCALE, "%d min %02d s", minutes, remainingSeconds)
        } else {
            String.format(POLISH_LOCALE, "%d s", remainingSeconds)
        }
    }

    fun formatMoney(minorUnits: Long?): String {
        if (minorUnits == null) return ""
        val main = minorUnits / 100
        val minor = Math.abs(minorUnits % 100)
        return if (minor == 0L) {
            "$main zł"
        } else {
            String.format(POLISH_LOCALE, "%d,%02d zł", main, minor)
        }
    }

    fun formatJobTerm(
        dateEpochDay: Long?,
        timeMinute: Int?,
        qualifier: TimeQualifier = TimeQualifier.EXACT,
        confirmedStartAt: Long? = null
    ): String {
        if (confirmedStartAt != null) {
            return "Potwierdzony: " + formatDateTime(confirmedStartAt)
        }
        if (dateEpochDay == null) return "Brak terminu"
        val date = LocalDate.ofEpochDay(dateEpochDay)
        val today = LocalDate.now()
        val dateStr = when (date) {
            today -> "Dziś"
            today.plusDays(1) -> "Jutro"
            else -> date.format(DATE_SHORT_FORMATTER)
        }
        if (timeMinute == null) return dateStr

        val hours = timeMinute / 60
        val minutes = timeMinute % 60
        val timeStr = String.format(POLISH_LOCALE, "%02d:%02d", hours, minutes)

        return when (qualifier) {
            TimeQualifier.EXACT -> "$dateStr, $timeStr"
            TimeQualifier.AROUND -> "$dateStr, ok. $timeStr"
            TimeQualifier.AFTER -> "$dateStr, po $timeStr"
            TimeQualifier.BEFORE -> "$dateStr, przed $timeStr"
            TimeQualifier.UNKNOWN -> "$dateStr, $timeStr (?)"
        }
    }
}
