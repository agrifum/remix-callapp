package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "callupp_preferences")

class AppPreferences(private val context: Context) {

    companion object {
        private val KEY_SMS_ANALYSIS_GLOBAL = booleanPreferencesKey("sms_analysis_global_enabled")
        private val KEY_SHOW_CLIENT_TAGS = booleanPreferencesKey("show_client_tags")
        private val KEY_PREFERRED_CALENDAR_ID = longPreferencesKey("preferred_calendar_id")
        private val KEY_MAPS_ETA_PARSING = booleanPreferencesKey("maps_eta_parsing_enabled")
        private val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    }

    val smsAnalysisGlobalEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_SMS_ANALYSIS_GLOBAL] ?: true
    }

    val showClientTags: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_SHOW_CLIENT_TAGS] ?: true
    }

    val preferredCalendarId: Flow<Long?> = context.dataStore.data.map { prefs ->
        prefs[KEY_PREFERRED_CALENDAR_ID]
    }

    val mapsEtaParsingEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_MAPS_ETA_PARSING] ?: true
    }

    val onboardingCompleted: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_ONBOARDING_COMPLETED] ?: false
    }

    suspend fun setSmsAnalysisGlobalEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SMS_ANALYSIS_GLOBAL] = enabled
        }
    }

    suspend fun setShowClientTags(show: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SHOW_CLIENT_TAGS] = show
        }
    }

    suspend fun setPreferredCalendarId(calendarId: Long?) {
        context.dataStore.edit { prefs ->
            if (calendarId != null) {
                prefs[KEY_PREFERRED_CALENDAR_ID] = calendarId
            } else {
                prefs.remove(KEY_PREFERRED_CALENDAR_ID)
            }
        }
    }

    suspend fun setMapsEtaParsingEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_MAPS_ETA_PARSING] = enabled
        }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ONBOARDING_COMPLETED] = completed
        }
    }
}
