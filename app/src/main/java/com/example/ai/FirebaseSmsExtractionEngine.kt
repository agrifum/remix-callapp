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
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import org.json.JSONObject

/**
 * Production Firebase AI Logic adapter. It is deliberately fail-closed at every
 * configuration, transport, schema, and validation boundary.
 */
class FirebaseSmsExtractionEngine(
    private val context: Context,
    private val modelName: String = CURRENT_MODEL
) : SmsExtractionEngine {

    override suspend fun extract(input: SmsExtractionInput): StructuredExtractionResult? {
        if (input.smsBody.isBlank()) return null
        return try {
            val app = FirebaseApp.getInstance()
            val config = generationConfig {
                responseMimeType = "application/json"
                responseSchema = responseSchema()
            }
            val model = FirebaseAI.getInstance(app).generativeModel(
                modelName = modelName,
                generationConfig = config
            )
            val response = model.generateContent(buildPrompt(input))
            response.text?.trim()?.takeIf { it.isNotEmpty() }?.let { parseAndValidate(it, input) }
        } catch (_: Exception) {
            null
        }
    }

    private fun buildPrompt(input: SmsExtractionInput): String {
        val address = JSONObject()
            .putOpt("city", input.clientAddress.city)
            .putOpt("district", input.clientAddress.district)
            .putOpt("street", input.clientAddress.street)
            .putOpt("buildingNumber", input.clientAddress.buildingNumber)
            .putOpt("unitNumber", input.clientAddress.unitNumber)
            .putOpt("postalCode", input.clientAddress.postalCode)
        val terms = JSONObject()
        input.activeJobTerms.forEach { (id, term) -> terms.put(id, term) }
        val summaries = JSONObject()
        input.activeJobSummaries.forEach { (id, summary) -> summaries.put(id, summary) }
        return buildString {
            appendLine("Extract only facts from this SMS. Do not infer unavailable values.")
            appendLine("Permitted input context is exactly the JSON object below; use no other context.")
            appendLine(
                JSONObject()
                    .put("smsBody", input.smsBody)
                    .put("receivedTimestamp", input.receivedTimestamp)
                    .put("localDateTime", input.localDateTime)
                    .put("timezone", input.timezone)
                    .put("clientAddress", address)
                    .put("activeJobIds", input.activeJobIds)
                    .put("activeJobTerms", terms)
                    .put("activeJobSummaries", summaries)
                    .toString()
            )
            appendLine("A job summary may be returned only in jobSummaries with an exact active jobId.")
            appendLine("Return only the responseSchema JSON.")
        }
    }

    private fun responseSchema(): Schema {
        val confidence = Schema.enumeration(listOf("HIGH", "MEDIUM", "LOW"))
        val address = Schema.obj(
            properties = mapOf(
                "city" to Schema.string(nullable = true),
                "district" to Schema.string(nullable = true),
                "street" to Schema.string(nullable = true),
                "buildingNumber" to Schema.string(nullable = true),
                "unitNumber" to Schema.string(nullable = true),
                "postalCode" to Schema.string(nullable = true),
                "confidence" to confidence
            ),
            optionalProperties = listOf(
                "city", "district", "street", "buildingNumber", "unitNumber", "postalCode", "confidence"
            ),
            nullable = true
        )
        val term = Schema.obj(
            properties = mapOf(
                "dateEpochDay" to Schema.long(nullable = true),
                "timeMinute" to Schema.integer(nullable = true, minimum = 0.0, maximum = 1439.0),
                "qualifier" to Schema.enumeration(listOf("EXACT", "AROUND", "AFTER", "BEFORE", "UNKNOWN")),
                "confidence" to confidence
            ),
            optionalProperties = listOf("dateEpochDay", "timeMinute", "qualifier", "confidence"),
            nullable = true
        )
        val summary = Schema.obj(
            properties = mapOf(
                "jobId" to Schema.string(),
                "updatedSummary" to Schema.string()
            )
        )
        return Schema.obj(
            properties = mapOf(
                "addressCandidate" to address,
                "termCandidate" to term,
                "additionalContactInfo" to Schema.string(nullable = true),
                "jobSummaries" to Schema.array(summary)
            ),
            optionalProperties = listOf("addressCandidate", "termCandidate", "additionalContactInfo", "jobSummaries")
        )
    }

    private fun parseAndValidate(json: String, input: SmsExtractionInput): StructuredExtractionResult? {
        return try {
            val root = JSONObject(json)
            val address = root.optJSONObject("addressCandidate")?.let { obj ->
                AddressCandidate(
                    city = obj.optionalString("city"),
                    district = obj.optionalString("district"),
                    street = obj.optionalString("street"),
                    buildingNumber = obj.optionalString("buildingNumber"),
                    unitNumber = obj.optionalString("unitNumber"),
                    postalCode = obj.optionalString("postalCode"),
                    confidence = obj.validConfidence() ?: return null
                )
            }
            val term = root.optJSONObject("termCandidate")?.let { obj ->
                val qualifierName = obj.optionalString("qualifier") ?: return null
                val qualifier = runCatching { TimeQualifier.valueOf(qualifierName) }.getOrNull() ?: return null
                val time = if (obj.isNull("timeMinute")) null else obj.optInt("timeMinute", -1)
                    .takeIf { it in 0..1439 } ?: if (!obj.isNull("timeMinute")) return null else null
                TermCandidate(
                    dateEpochDay = if (obj.isNull("dateEpochDay")) null else obj.optLong("dateEpochDay"),
                    timeMinute = time,
                    qualifier = qualifier,
                    confidence = obj.validConfidence() ?: return null
                )
            }
            val summaries = mutableListOf<JobSummaryUpdate>()
            val summaryArray = root.optJSONArray("jobSummaries")
            if (summaryArray != null) {
                for (index in 0 until summaryArray.length()) {
                    val item = summaryArray.optJSONObject(index) ?: return null
                    val id = item.optionalString("jobId") ?: return null
                    if (id !in input.activeJobIds) continue
                    val summary = item.optionalString("updatedSummary") ?: return null
                    summaries += JobSummaryUpdate(id, summary)
                }
            }
            StructuredExtractionResult(
                addressCandidate = address,
                termCandidate = term,
                additionalContactInfo = root.optionalString("additionalContactInfo"),
                jobSummaries = summaries
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun JSONObject.optionalString(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    private fun JSONObject.validConfidence(): String? =
        optionalString("confidence")?.takeIf { it == "HIGH" || it == "MEDIUM" || it == "LOW" }

    companion object {
        const val CURRENT_MODEL = "gemini-3.5-flash-lite"
    }
}
