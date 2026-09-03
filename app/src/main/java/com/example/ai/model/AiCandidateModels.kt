package com.example.ai.model

import com.example.core.model.TimeQualifier

data class ClientAddressInput(
    val city: String? = null,
    val district: String? = null,
    val street: String? = null,
    val buildingNumber: String? = null,
    val unitNumber: String? = null,
    val postalCode: String? = null
) {
    val isEmpty: Boolean
        get() = city.isNullOrBlank() && street.isNullOrBlank() && buildingNumber.isNullOrBlank()
}

data class SmsExtractionInput(
    val smsBody: String,
    val receivedTimestamp: Long,
    val localDateTime: String,
    val timezone: String,
    val clientAddress: ClientAddressInput,
    val activeJobIds: List<String>,
    val activeJobTerms: Map<String, String>,
    val activeJobSummaries: Map<String, String>
)

data class AddressCandidate(
    val city: String? = null,
    val district: String? = null,
    val street: String? = null,
    val buildingNumber: String? = null,
    val unitNumber: String? = null,
    val postalCode: String? = null,
    val confidence: String = "HIGH"
) {
    val isCompleteEnough: Boolean
        get() = (!city.isNullOrBlank() || !street.isNullOrBlank()) && !buildingNumber.isNullOrBlank()
}

data class TermCandidate(
    val dateEpochDay: Long? = null,
    val timeMinute: Int? = null,
    val qualifier: TimeQualifier = TimeQualifier.EXACT,
    val confidence: String = "HIGH"
)

data class JobSummaryUpdate(
    val jobId: String,
    val updatedSummary: String
)

data class StructuredExtractionResult(
    val addressCandidate: AddressCandidate? = null,
    val termCandidate: TermCandidate? = null,
    val additionalContactInfo: String? = null,
    val jobSummaries: List<JobSummaryUpdate> = emptyList()
)
