package com.example.ai

import android.content.Context
import com.example.ai.model.AddressCandidate
import com.example.ai.model.JobSummaryUpdate
import com.example.ai.model.SmsExtractionInput
import com.example.ai.model.StructuredExtractionResult
import com.example.ai.model.TermCandidate
import com.example.core.model.TimeQualifier
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.FirebaseAI
import com.google.firebase.ai.type.generationConfig
import org.json.JSONObject
import java.time.LocalDate

/**
 * Firebase AI Logic extraction engine using Gemini 2.5/3.5 Flash Lite.
 * Enforces fail-closed behavior when Firebase is not configured or cloud calls fail.
 */
class FirebaseSmsExtractionEngine(
    private val context: Context,
    private val modelName: String = "gemini-2.5-flash-lite"
) : SmsExtractionEngine {

    override suspend fun extract(input: SmsExtractionInput): StructuredExtractionResult? {
        val text = input.smsBody.trim()
        if (text.isBlank()) return null

        return try {
            // Fail-closed safely if FirebaseApp has not been initialized with Google Services configuration
            val app = try {
                FirebaseApp.getInstance()
            } catch (_: Exception) {
                null
            } ?: return null

            val firebaseAI = FirebaseAI.getInstance(app)
            val config = generationConfig {
                responseMimeType = "application/json"
            }
            val generativeModel = firebaseAI.generativeModel(
                modelName = modelName,
                generationConfig = config
            )

            val prompt = """
                Extract structured job information from the following SMS body.
                Reference date/time:  ().
                SMS: ""
                Return JSON with:
                {
                  "address": { "city": string, "street": string, "buildingNumber": string, "unitNumber": string, "postalCode": string },
                  "term": { "dateEpochDay": number, "timeMinute": number, "qualifier": "EXACT"|"APPROXIMATE"|"AFTER"|"BEFORE" },
                  "additionalContactInfo": string,
                  "jobSummary": string
                }
            """.trimIndent()

            val response = generativeModel.generateContent(prompt)
            val jsonStr = response.text?.trim() ?: return null

            parseJsonResponse(jsonStr, input)
        } catch (_: Exception) {
            // Safe fail-closed boundary per MASTER_SPEC §64
            null
        }
    }

    private fun parseJsonResponse(jsonStr: String, input: SmsExtractionInput): StructuredExtractionResult? {
        return try {
            val root = JSONObject(jsonStr)

            var addressCandidate: AddressCandidate? = null
            if (root.has("address") && !root.isNull("address")) {
                val addr = root.getJSONObject("address")
                addressCandidate = AddressCandidate(
                    city = addr.optString("city").takeIf { it.isNotBlank() },
                    street = addr.optString("street").takeIf { it.isNotBlank() },
                    buildingNumber = addr.optString("buildingNumber").takeIf { it.isNotBlank() },
                    unitNumber = addr.optString("unitNumber").takeIf { it.isNotBlank() },
                    postalCode = addr.optString("postalCode").takeIf { it.isNotBlank() },
                    confidence = "HIGH"
                )
            }

            var termCandidate: TermCandidate? = null
            if (root.has("term") && !root.isNull("term")) {
                val term = root.getJSONObject("term")
                val epochDay = if (term.has("dateEpochDay") && !term.isNull("dateEpochDay")) term.getLong("dateEpochDay") else null
                val timeMinute = if (term.has("timeMinute") && !term.isNull("timeMinute")) term.getInt("timeMinute") else null
                val qualifierStr = term.optString("qualifier", "EXACT")
                val qualifier = try {
                    TimeQualifier.valueOf(qualifierStr.uppercase())
                } catch (_: Exception) {
                    TimeQualifier.EXACT
                }

                termCandidate = TermCandidate(
                    dateEpochDay = epochDay,
                    timeMinute = timeMinute,
                    qualifier = qualifier,
                    confidence = "HIGH"
                )
            }

            val additionalContact = root.optString("additionalContactInfo").takeIf { it.isNotBlank() }

            val summaries = mutableListOf<JobSummaryUpdate>()
            val generalSummary = root.optString("jobSummary").takeIf { it.isNotBlank() }
            if (generalSummary != null && input.activeJobIds.isNotEmpty()) {
                summaries.add(JobSummaryUpdate(jobId = input.activeJobIds.first(), updatedSummary = generalSummary))
            }

            StructuredExtractionResult(
                addressCandidate = addressCandidate,
                termCandidate = termCandidate,
                additionalContactInfo = additionalContact,
                jobSummaries = summaries
            )
        } catch (_: Exception) {
            null
        }
    }
}
