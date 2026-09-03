package com.example.ai

import com.example.ai.model.AddressCandidate
import com.example.ai.model.JobSummaryUpdate
import com.example.ai.model.SmsExtractionInput
import com.example.ai.model.StructuredExtractionResult
import com.example.ai.model.TermCandidate
import com.example.core.model.TimeQualifier
import java.time.LocalDate
import java.util.regex.Pattern

/**
 * Deterministic fake SMS extraction engine for AI Studio.
 * Extracts address patterns, dates/times, and summaries deterministically from SMS body.
 */
class FakeSmsExtractionEngine : SmsExtractionEngine {

    override suspend fun extract(input: SmsExtractionInput): StructuredExtractionResult? {
        val text = input.smsBody.trim()

        if (text.contains("[FAIL]", ignoreCase = true)) {
            return null
        }

        val isLowConfidence = text.contains("[LOW_CONFIDENCE]", ignoreCase = true)
        val confidence = if (isLowConfidence) "LOW" else "HIGH"

        // 1. Address extraction
        var addressCandidate: AddressCandidate? = null
        // Look for patterns like: "ul. Kwiatowa 12 m 4, Warszawa", "Lipowa 5A", "Kraków, Długa 10"
        val addressRegex = Regex("(?:ul\\.?\\s*)?([A-ZĄĆĘŁŃÓŚŹŻ][a-ząćęłńóśźż]+(?:\\s+[A-ZĄĆĘŁŃÓŚŹŻ][a-ząćęłńóśźż]+)?)\\s+(\\d+[a-zA-Z]?)(?:\\s*(?:/|m\\.?\\s*)(\\d+))?(?:[\\s,]+([A-ZĄĆĘŁŃÓŚŹŻ][a-ząćęłńóśźż]+))?")
        val addressMatch = addressRegex.find(text)
        if (addressMatch != null) {
            val street = addressMatch.groupValues[1]
            val building = addressMatch.groupValues[2]
            val unit = addressMatch.groupValues.getOrNull(3)?.takeIf { it.isNotBlank() }
            val city = addressMatch.groupValues.getOrNull(4)?.takeIf { it.isNotBlank() } ?: "Warszawa"
            addressCandidate = AddressCandidate(
                city = city,
                street = street,
                buildingNumber = building,
                unitNumber = unit,
                confidence = confidence
            )
        }

        // 2. Term extraction
        var termCandidate: TermCandidate? = null
        val today = LocalDate.now()
        val isTomorrow = text.contains("jutro", ignoreCase = true)
        val isToday = text.contains("dziś", ignoreCase = true) || text.contains("dzisiaj", ignoreCase = true)
        val targetDate = when {
            isTomorrow -> today.plusDays(1)
            isToday -> today
            else -> null
        }

        val timeRegex = Regex("(?i)(?:ok\\.?|około|po|przed)?\\s*(\\d{1,2})[:.](\\d{2})")
        val timeMatch = timeRegex.find(text)
        val timeMinute = if (timeMatch != null) {
            val h = timeMatch.groupValues[1].toIntOrNull() ?: 0
            val m = timeMatch.groupValues[2].toIntOrNull() ?: 0
            h * 60 + m
        } else if (text.contains("14:00") || text.contains("14")) {
            14 * 60
        } else null

        val qualifier = when {
            text.contains("około", ignoreCase = true) || text.contains("ok.", ignoreCase = true) -> TimeQualifier.AROUND
            text.contains("po", ignoreCase = true) -> TimeQualifier.AFTER
            text.contains("przed", ignoreCase = true) -> TimeQualifier.BEFORE
            else -> TimeQualifier.EXACT
        }

        if (targetDate != null || timeMinute != null) {
            termCandidate = TermCandidate(
                dateEpochDay = targetDate?.toEpochDay(),
                timeMinute = timeMinute,
                qualifier = qualifier,
                confidence = confidence
            )
        }

        // 3. Additional contact info
        var additionalContact: String? = null
        val phoneInText = Regex("(?:kontakt|tel|telefon)?[:\\s]*(\\+?\\d{9,12})")
        val contactMatch = phoneInText.find(text)
        if (contactMatch != null && !text.contains("Dzień dobry", ignoreCase = true)) {
            additionalContact = "Dodatkowy kontakt z SMS: " + contactMatch.groupValues[1]
        }

        // 4. Summaries for active jobs
        val summaries = input.activeJobIds.map { jobId ->
            val summarySnippet = if (text.length > 200) text.take(197) + "..." else text
            JobSummaryUpdate(
                jobId = jobId,
                updatedSummary = summarySnippet
            )
        }

        return StructuredExtractionResult(
            addressCandidate = addressCandidate,
            termCandidate = termCandidate,
            additionalContactInfo = additionalContact,
            jobSummaries = summaries
        )
    }
}
