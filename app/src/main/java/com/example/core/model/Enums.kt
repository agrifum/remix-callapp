package com.example.core.model

enum class NameSource {
    AUTO,
    CONTACT,
    MANUAL
}

enum class SmsAnalysisMode {
    INHERIT,
    ENABLED,
    DISABLED
}

enum class NoteSource {
    CALL,
    MANUAL
}

enum class CallDirection {
    INCOMING,
    OUTGOING
}

enum class TaskStatus {
    OPEN,
    DONE
}

enum class JobStatus {
    ACTIVE,
    COMPLETED,
    CLOSED
}

enum class TimeQualifier {
    EXACT,
    AROUND,
    AFTER,
    BEFORE,
    UNKNOWN
}

enum class EtaSource {
    MAPS_NOTIFICATION,
    MANUAL
}

enum class WindowReason {
    CREATED,
    REOPENED
}

enum class SuggestionType {
    ADDRESS_CHANGE,
    TERM_CHANGE,
    ADDITIONAL_CONTACT_INFO
}

enum class SuggestionStatus {
    PENDING,
    ACCEPTED,
    IGNORED
}

enum class TriggerState {
    PENDING,
    PROCESSED,
    DISCARDED,
    FAILED
}

enum class ReengagementSource {
    INCOMING_CALL,
    INCOMING_SMS
}

enum class ReengagementStatus {
    PENDING,
    RESUMED,
    NEW_JOB,
    IGNORED
}
