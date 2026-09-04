package com.example.ai

/**
 * Allows deterministic tests to replace the runtime engine without changing the
 * production Firebase binding.
 */
object SmsExtractionEngineProvider {
    @Volatile
    var override: SmsExtractionEngine? = null
}
