package com.example.core.di

import android.content.Context
import com.example.ai.FakeSmsExtractionEngine
import com.example.ai.SmsAnalysisCoordinator
import com.example.ai.SmsExtractionEngine
import com.example.data.database.CallUppDatabase
import com.example.data.preferences.AppPreferences
import com.example.data.repository.*
import com.example.system.calendar.AndroidCalendarManager
import com.example.system.calendar.CalendarManager
import com.example.system.calls.CallLogRepository
import com.example.system.contacts.ContactLookupRepository
import com.example.system.sms.DefaultSystemSmsReader
import com.example.system.sms.SystemSmsReader
import com.example.system.sms.SystemSmsReaderProvider
import com.example.system.work.JobCompletionScheduler
import com.example.system.work.SmsTriggerRecovery
import com.example.system.work.WorkManagerJobCompletionScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Test-only dependency fixture. Production wiring is Hilt-only. */
class AppContainer(val context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val database = CallUppDatabase.getInstance(context)
    val clientDao = database.clientDao()
    val noteDao = database.noteDao()
    val taskDao = database.taskDao()
    val serviceDao = database.serviceDao()
    val jobDao = database.jobDao()
    val windowDao = database.jobAnalysisWindowDao()
    val aiSuggestionDao = database.aiSuggestionDao()
    val smsTriggerDao = database.smsTriggerDao()
    val reengagementEventDao = database.reengagementEventDao()
    val smsTemplateDao = database.smsTemplateDao()
    val callDraftDao = database.callDraftDao()
    val appPreferences = AppPreferences(context)
    val jobCompletionScheduler: JobCompletionScheduler = WorkManagerJobCompletionScheduler(context)
    val calendarManager: CalendarManager = AndroidCalendarManager(context)
    val clientRepository = ClientRepository(database, clientDao, jobDao)
    val noteRepository = NoteRepository(noteDao)
    val taskRepository = TaskRepository(taskDao)
    val serviceRepository = ServiceRepository(serviceDao)
    val jobRepository = com.example.data.repository.JobRepository(
        database, jobDao, windowDao, jobCompletionScheduler, calendarManager, clientDao
    )
    val callDraftRepository = CallDraftRepository(
        database, callDraftDao, noteDao, clientDao, jobDao, windowDao, taskDao, serviceDao, jobCompletionScheduler
    )
    val aiSuggestionRepository = AiSuggestionRepository(
        database, aiSuggestionDao, clientDao, jobDao, jobCompletionScheduler, calendarManager
    )
    val reengagementRepository = ReengagementRepository(
        database, reengagementEventDao, jobDao, windowDao, jobCompletionScheduler
    )
    val smsTemplateRepository = SmsTemplateRepository(smsTemplateDao)
    val callLogRepository = CallLogRepository(context, clientDao, noteDao)
    val contactLookupRepository = ContactLookupRepository(context)
    var systemSmsReader: SystemSmsReader = DefaultSystemSmsReader(context)
        set(value) {
            field = value
            SystemSmsReaderProvider.override = value
        }
    val smsExtractionEngine: SmsExtractionEngine = FakeSmsExtractionEngine()
    val smsAnalysisCoordinator = SmsAnalysisCoordinator(
        database, clientDao, jobDao, windowDao, aiSuggestionDao, smsTriggerDao,
        appPreferences, smsExtractionEngine
    )
    val smsTriggerRecovery = SmsTriggerRecovery(
        context, appPreferences, clientDao, jobDao, windowDao, smsTriggerDao
    )
}
