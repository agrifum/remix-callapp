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
import com.example.system.calendar.AndroidCalendarManager
import com.example.system.calendar.CalendarManager
import com.example.system.calls.CallLogRepository
import com.example.system.contacts.ContactLookupRepository
import com.example.system.sms.DefaultSystemSmsReader
import com.example.system.sms.SystemSmsReader
import com.example.system.work.JobCompletionScheduler
import com.example.system.work.WorkManagerJobCompletionScheduler
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideCoroutineScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): CallUppDatabase {
        return CallUppDatabase.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideClientDao(database: CallUppDatabase) = database.clientDao()

    @Provides
    @Singleton
    fun provideNoteDao(database: CallUppDatabase) = database.noteDao()

    @Provides
    @Singleton
    fun provideTaskDao(database: CallUppDatabase) = database.taskDao()

    @Provides
    @Singleton
    fun provideServiceDao(database: CallUppDatabase) = database.serviceDao()

    @Provides
    @Singleton
    fun provideJobDao(database: CallUppDatabase) = database.jobDao()

    @Provides
    @Singleton
    fun provideJobAnalysisWindowDao(database: CallUppDatabase) = database.jobAnalysisWindowDao()

    @Provides
    @Singleton
    fun provideAiSuggestionDao(database: CallUppDatabase) = database.aiSuggestionDao()

    @Provides
    @Singleton
    fun provideSmsTriggerDao(database: CallUppDatabase) = database.smsTriggerDao()

    @Provides
    @Singleton
    fun provideReengagementEventDao(database: CallUppDatabase) = database.reengagementEventDao()

    @Provides
    @Singleton
    fun provideSmsTemplateDao(database: CallUppDatabase) = database.smsTemplateDao()

    @Provides
    @Singleton
    fun provideCallDraftDao(database: CallUppDatabase) = database.callDraftDao()

    @Provides
    @Singleton
    fun provideAppPreferences(@ApplicationContext context: Context): AppPreferences {
        return AppPreferences(context)
    }

    @Provides
    @Singleton
    fun provideJobCompletionScheduler(@ApplicationContext context: Context): JobCompletionScheduler {
        return WorkManagerJobCompletionScheduler(context)
    }

    @Provides
    @Singleton
    fun provideCalendarManager(@ApplicationContext context: Context): CalendarManager {
        return AndroidCalendarManager(context)
    }

    @Provides
    @Singleton
    fun provideClientRepository(
        clientDao: com.example.data.dao.ClientDao,
        jobDao: com.example.data.dao.JobDao
    ): ClientRepository {
        return ClientRepository(clientDao, jobDao)
    }

    @Provides
    @Singleton
    fun provideNoteRepository(noteDao: com.example.data.dao.NoteDao): NoteRepository {
        return NoteRepository(noteDao)
    }

    @Provides
    @Singleton
    fun provideTaskRepository(taskDao: com.example.data.dao.TaskDao): TaskRepository {
        return TaskRepository(taskDao)
    }

    @Provides
    @Singleton
    fun provideServiceRepository(serviceDao: com.example.data.dao.ServiceDao): ServiceRepository {
        return ServiceRepository(serviceDao)
    }

    @Provides
    @Singleton
    fun provideJobRepository(
        database: CallUppDatabase,
        jobDao: com.example.data.dao.JobDao,
        windowDao: com.example.data.dao.JobAnalysisWindowDao,
        scheduler: JobCompletionScheduler,
        calendarManager: CalendarManager,
        clientDao: com.example.data.dao.ClientDao
    ): JobRepository {
        return JobRepository(database, jobDao, windowDao, scheduler, calendarManager, clientDao)
    }

    @Provides
    @Singleton
    fun provideCallDraftRepository(
        database: CallUppDatabase,
        callDraftDao: com.example.data.dao.CallDraftDao,
        noteDao: com.example.data.dao.NoteDao,
        clientDao: com.example.data.dao.ClientDao,
        jobDao: com.example.data.dao.JobDao,
        windowDao: com.example.data.dao.JobAnalysisWindowDao,
        taskDao: com.example.data.dao.TaskDao,
        serviceDao: com.example.data.dao.ServiceDao
    ): CallDraftRepository {
        return CallDraftRepository(database, callDraftDao, noteDao, clientDao, jobDao, windowDao, taskDao, serviceDao)
    }

    @Provides
    @Singleton
    fun provideAiSuggestionRepository(
        database: CallUppDatabase,
        suggestionDao: com.example.data.dao.AiSuggestionDao,
        clientDao: com.example.data.dao.ClientDao,
        jobDao: com.example.data.dao.JobDao,
        scheduler: JobCompletionScheduler,
        calendarManager: CalendarManager
    ): AiSuggestionRepository {
        return AiSuggestionRepository(database, suggestionDao, clientDao, jobDao, scheduler, calendarManager)
    }

    @Provides
    @Singleton
    fun provideReengagementRepository(
        database: CallUppDatabase,
        reengagementDao: com.example.data.dao.ReengagementEventDao,
        jobDao: com.example.data.dao.JobDao,
        jobRepository: JobRepository
    ): ReengagementRepository {
        return ReengagementRepository(database, reengagementDao, jobDao, jobRepository)
    }

    @Provides
    @Singleton
    fun provideSmsTemplateRepository(smsTemplateDao: com.example.data.dao.SmsTemplateDao): SmsTemplateRepository {
        return SmsTemplateRepository(smsTemplateDao)
    }

    @Provides
    @Singleton
    fun provideCallLogRepository(
        @ApplicationContext context: Context,
        clientDao: com.example.data.dao.ClientDao,
        noteDao: com.example.data.dao.NoteDao
    ): CallLogRepository {
        return CallLogRepository(context, clientDao, noteDao)
    }

    @Provides
    @Singleton
    fun provideContactLookupRepository(@ApplicationContext context: Context): ContactLookupRepository {
        return ContactLookupRepository(context)
    }

    @Provides
    @Singleton
    fun provideSystemSmsReader(@ApplicationContext context: Context): SystemSmsReader {
        return DefaultSystemSmsReader(context)
    }

    @Provides
    @Singleton
    fun provideSmsExtractionEngine(): SmsExtractionEngine {
        return FakeSmsExtractionEngine()
    }

    @Provides
    @Singleton
    fun provideSmsAnalysisCoordinator(
        database: CallUppDatabase,
        clientDao: com.example.data.dao.ClientDao,
        jobDao: com.example.data.dao.JobDao,
        jobAnalysisWindowDao: com.example.data.dao.JobAnalysisWindowDao,
        aiSuggestionDao: com.example.data.dao.AiSuggestionDao,
        smsTriggerDao: com.example.data.dao.SmsTriggerDao,
        preferences: AppPreferences,
        smsExtractionEngine: SmsExtractionEngine
    ): SmsAnalysisCoordinator {
        return SmsAnalysisCoordinator(
            database = database,
            clientDao = clientDao,
            jobDao = jobDao,
            windowDao = jobAnalysisWindowDao,
            suggestionDao = aiSuggestionDao,
            triggerDao = smsTriggerDao,
            appPreferences = preferences,
            extractionEngine = smsExtractionEngine
        )
    }
}
