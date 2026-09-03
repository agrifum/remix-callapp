package com.example.ai

import com.example.ai.model.SmsExtractionInput
import com.example.ai.model.StructuredExtractionResult

/**
 * Section 24: Interface cleanly separating extraction from application logic.
 * AI Studio uses FakeSmsExtractionEngine.
 * Future Android Studio uses FirebaseSmsExtractionEngine.
 */
interface SmsExtractionEngine {
    suspend fun extract(input: SmsExtractionInput): StructuredExtractionResult?
}
