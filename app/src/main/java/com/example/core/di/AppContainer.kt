package com.example.core.di

import android.content.Context
import com.example.ai.FakeSmsExtractionEngine
import com.example.ai.SmsAnalysisCoordinator
import com.example.ai.SmsExtractionEngine
import com.example.data.database.CallUppDatabase
import com.example.data.preferences.AppPreferences
import com.example.data.repository.AiSuggestionRepository
import com.example.data.repository.CallDraftRepository
import com.example.data.repository.ClientRepository
import com.example.data.repository.JobRepository
import com.example.data.repository.NoteRepository
import com.example.data.repository.ReengagementRepository
import com.example.data.repository.ServiceRepository
import com.example.data.repository.SmsTemplateRepository
import com.example.data.repository.TaskRepository
import com.example.system.calls.CallLogRepository
import com.example.system.contacts.ContactLookupRepository
import com.example.system.sms.DefaultSystemSmsReader
import com.example.system.sms.SystemSmsReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppContainer(val context: Context) {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Database & DAOs
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

    // Preferences
    val appPreferences = AppPreferences(context)

    // Repositories
    val clientRepository = ClientRepository(clientDao, jobDao)
    val noteRepository = NoteRepository(noteDao)
    val taskRepository = TaskRepository(taskDao)
    val serviceRepository = ServiceRepository(serviceDao)
    val jobCompletionScheduler: com.example.system.work.JobCompletionScheduler =
        com.example.system.work.WorkManagerJobCompletionScheduler(context)
    val jobRepository = JobRepository(database, jobDao, windowDao, jobCompletionScheduler)
    val callDraftRepository = CallDraftRepository(
        database = database,
        callDraftDao = callDraftDao,
        noteDao = noteDao,
        clientDao = clientDao,
        jobDao = jobDao,
        windowDao = windowDao,
        taskDao = taskDao,
        serviceDao = serviceDao
    )
    val aiSuggestionRepository = AiSuggestionRepository(
        database = database,
        suggestionDao = aiSuggestionDao,
        clientDao = clientDao,
        jobDao = jobDao,
        scheduler = jobCompletionScheduler
    )
    val reengagementRepository = ReengagementRepository(
        database = database,
        reengagementDao = reengagementEventDao,
        jobDao = jobDao,
        jobRepository = jobRepository
    )
    val smsTemplateRepository = SmsTemplateRepository(smsTemplateDao)
    val callLogRepository = CallLogRepository(
        context = context,
        clientDao = clientDao,
        noteDao = noteDao
    )
    val contactLookupRepository = ContactLookupRepository(context)
    var systemSmsReader: SystemSmsReader = DefaultSystemSmsReader(context)

    val smsExtractionEngine: SmsExtractionEngine = FakeSmsExtractionEngine()
    val smsAnalysisCoordinator = SmsAnalysisCoordinator(
        database = database,
        clientDao = clientDao,
        jobDao = jobDao,
        windowDao = windowDao,
        suggestionDao = aiSuggestionDao,
        triggerDao = smsTriggerDao,
        appPreferences = appPreferences,
        extractionEngine = smsExtractionEngine
    )
    val smsTriggerRecovery = com.example.system.work.SmsTriggerRecovery(context, this)
}
