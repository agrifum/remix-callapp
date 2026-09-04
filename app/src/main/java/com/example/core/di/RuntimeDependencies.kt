package com.example.core.di

import android.content.Context
import com.example.ai.SmsAnalysisCoordinator
import com.example.data.dao.AiSuggestionDao
import com.example.data.dao.ClientDao
import com.example.data.dao.JobAnalysisWindowDao
import com.example.data.dao.JobDao
import com.example.data.dao.SmsTriggerDao
import com.example.data.dao.NoteDao
import com.example.data.dao.TaskDao
import com.example.data.preferences.AppPreferences
import com.example.data.repository.ClientRepository
import com.example.data.repository.JobRepository
import com.example.data.repository.NoteRepository
import com.example.data.repository.ReengagementRepository
import com.example.data.repository.CallDraftRepository
import com.example.system.sms.SystemSmsReader
import com.example.system.work.JobCompletionScheduler
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope

@EntryPoint
@InstallIn(SingletonComponent::class)
interface RuntimeDependencies {
    @ApplicationContext fun context(): Context
    fun appScope(): CoroutineScope
    fun appPreferences(): AppPreferences
    fun clientDao(): ClientDao
    fun jobDao(): JobDao
    fun jobAnalysisWindowDao(): JobAnalysisWindowDao
    fun smsTriggerDao(): SmsTriggerDao
    fun noteDao(): NoteDao
    fun taskDao(): TaskDao
    fun aiSuggestionDao(): AiSuggestionDao
    fun clientRepository(): ClientRepository
    fun jobRepository(): JobRepository
    fun noteRepository(): NoteRepository
    fun callDraftRepository(): CallDraftRepository
    fun reengagementRepository(): ReengagementRepository
    fun systemSmsReader(): SystemSmsReader
    fun smsAnalysisCoordinator(): SmsAnalysisCoordinator
    fun jobCompletionScheduler(): JobCompletionScheduler
}

fun Context.runtimeDependencies(): RuntimeDependencies =
    EntryPointAccessors.fromApplication(applicationContext, RuntimeDependencies::class.java)
