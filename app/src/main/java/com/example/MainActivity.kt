package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.ui.navigation.AppNavHost
import com.example.ui.theme.MyApplicationTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import com.example.data.preferences.AppPreferences
import com.example.data.repository.CallDraftRepository
import com.example.data.repository.ClientRepository
import com.example.data.repository.JobRepository
import com.example.data.repository.NoteRepository
import com.example.data.repository.ReengagementRepository
import com.example.data.repository.ServiceRepository
import com.example.data.repository.SmsTemplateRepository
import com.example.data.repository.TaskRepository
import com.example.system.calendar.CalendarManager
import com.example.system.calls.CallLogRepository
import com.example.system.contacts.ContactLookupRepository

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var callLogRepository: CallLogRepository
    @Inject lateinit var clientRepository: ClientRepository
    @Inject lateinit var reengagementRepository: ReengagementRepository
    @Inject lateinit var jobRepository: JobRepository
    @Inject lateinit var taskRepository: TaskRepository
    @Inject lateinit var noteRepository: NoteRepository
    @Inject lateinit var serviceRepository: ServiceRepository
    @Inject lateinit var callDraftRepository: CallDraftRepository
    @Inject lateinit var appPreferences: AppPreferences
    @Inject lateinit var smsTemplateRepository: SmsTemplateRepository
    @Inject lateinit var calendarManager: CalendarManager
    @Inject lateinit var contactLookupRepository: ContactLookupRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavHost(
                        callLogRepository = callLogRepository,
                        clientRepository = clientRepository,
                        reengagementRepository = reengagementRepository,
                        jobRepository = jobRepository,
                        taskRepository = taskRepository,
                        noteRepository = noteRepository,
                        serviceRepository = serviceRepository,
                        callDraftRepository = callDraftRepository,
                        appPreferences = appPreferences,
                        smsTemplateRepository = smsTemplateRepository,
                        calendarManager = calendarManager,
                        contactLookupRepository = contactLookupRepository
                    )
                }
            }
        }
    }
}
