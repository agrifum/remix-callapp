package com.example.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
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
import com.example.ui.screens.CallsScreen
import com.example.ui.screens.ClientDetailScreen
import com.example.ui.screens.JobDetailScreen
import com.example.ui.screens.JobsScreen
import com.example.ui.screens.NewJobScreen
import com.example.ui.screens.NumberDetailScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.ServicesSettingsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SimulatorScreen
import com.example.ui.screens.SmsTemplatesScreen
import com.example.ui.screens.TasksScreen
import com.example.ui.screens.TrashScreen

sealed class BottomNavItem(val screen: Screen, val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Calls : BottomNavItem(Screen.Calls, "Połączenia", Icons.Default.Phone)
    object Jobs : BottomNavItem(Screen.Jobs, "Zlecenia", Icons.Default.Build)
    object Tasks : BottomNavItem(Screen.Tasks, "Zadania", Icons.Default.CheckCircle)
}

@Composable
fun AppNavHost(
    callLogRepository: CallLogRepository,
    clientRepository: ClientRepository,
    reengagementRepository: ReengagementRepository,
    jobRepository: JobRepository,
    taskRepository: TaskRepository,
    noteRepository: NoteRepository,
    serviceRepository: ServiceRepository,
    callDraftRepository: CallDraftRepository,
    appPreferences: AppPreferences,
    smsTemplateRepository: SmsTemplateRepository,
    calendarManager: CalendarManager,
    contactLookupRepository: ContactLookupRepository
) {
    val onboardingCompleted by appPreferences.onboardingCompleted.collectAsState(initial = null)
    val backStack = rememberNavBackStack(Screen.Calls)
    val currentScreen = backStack.lastOrNull() as? Screen ?: Screen.Calls

    LaunchedEffect(onboardingCompleted) {
        if (onboardingCompleted == false && !backStack.contains(Screen.Onboarding)) {
            backStack.clear()
            backStack.add(Screen.Onboarding)
        }
    }

    val bottomItems = listOf(
        BottomNavItem.Calls,
        BottomNavItem.Jobs,
        BottomNavItem.Tasks
    )

    val showBottomBar = bottomItems.any { it.screen == currentScreen }

    fun navigateTo(screen: Screen) {
        if (backStack.lastOrNull() != screen) {
            backStack.add(screen)
        }
    }

    fun popBackStack() {
        if (backStack.size > 1) {
            backStack.removeAt(backStack.size - 1)
        }
    }

    fun navigateBottomTab(targetScreen: Screen) {
        if (currentScreen != targetScreen) {
            // Keep root and switch tab cleanly
            backStack.clear()
            backStack.add(targetScreen)
        }
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomItems.forEach { item ->
                        NavigationBarItem(
                            icon = { Icon(item.icon, contentDescription = item.title) },
                            label = { Text(item.title) },
                            selected = currentScreen == item.screen,
                            onClick = {
                                navigateBottomTab(item.screen)
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavDisplay(
            backStack = backStack,
            modifier = Modifier.padding(innerPadding),
            entryProvider = entryProvider {
                entry<Screen.Calls> {
                    CallsScreen(
                        callLogRepository = callLogRepository,
                        clientRepository = clientRepository,
                        reengagementRepository = reengagementRepository,
                        onClientClick = { clientId ->
                            navigateTo(Screen.ClientDetail(clientId))
                        },
                        onNumberClick = { phoneKey ->
                            navigateTo(Screen.NumberDetail(phoneKey))
                        },
                        onOpenSettings = {
                            navigateTo(Screen.Settings)
                        }
                    )
                }

                entry<Screen.Jobs> {
                    JobsScreen(
                        jobRepository = jobRepository,
                        clientRepository = clientRepository,
                        onJobClick = { jobId ->
                            navigateTo(Screen.JobDetail(jobId))
                        },
                        onNewJobClick = {
                            navigateTo(Screen.NewJob)
                        }
                    )
                }

                entry<Screen.Tasks> {
                    TasksScreen(
                        taskRepository = taskRepository,
                        noteRepository = noteRepository
                    )
                }

                entry<Screen.Simulator> {
                    SimulatorScreen(
                        clientRepository = clientRepository,
                        serviceRepository = serviceRepository,
                        callDraftRepository = callDraftRepository,
                        onNavigateToJobs = {
                            navigateBottomTab(Screen.Jobs)
                        }
                    )
                }

                entry<Screen.NewJob> {
                    NewJobScreen(
                        jobRepository = jobRepository,
                        clientRepository = clientRepository,
                        serviceRepository = serviceRepository,
                        onNavigateBack = { popBackStack() }
                    )
                }

                entry<Screen.ClientDetail> { clientDetail ->
                    ClientDetailScreen(
                        clientId = clientDetail.clientId,
                        clientRepository = clientRepository,
                        jobRepository = jobRepository,
                        noteRepository = noteRepository,
                        taskRepository = taskRepository,
                        callLogRepository = callLogRepository,
                        appPreferences = appPreferences,
                        onNavigateBack = { popBackStack() },
                        onNavigateToJob = { jobId ->
                            navigateTo(Screen.JobDetail(jobId))
                        },
                        onNewJobForClient = {
                            navigateTo(Screen.NewJob)
                        }
                    )
                }

                entry<Screen.NumberDetail> { numberDetail ->
                    NumberDetailScreen(
                        phoneKey = numberDetail.phoneKey,
                        clientRepository = clientRepository,
                        noteRepository = noteRepository,
                        taskRepository = taskRepository,
                        callLogRepository = callLogRepository,
                        contactLookupRepository = contactLookupRepository,
                        onNavigateBack = { popBackStack() },
                        onNavigateToClient = { clientId ->
                            navigateTo(Screen.ClientDetail(clientId))
                        }
                    )
                }

                entry<Screen.JobDetail> { jobDetail ->
                    JobDetailScreen(
                        jobId = jobDetail.jobId,
                        jobRepository = jobRepository,
                        clientRepository = clientRepository,
                        onNavigateBack = { popBackStack() },
                        onNavigateToClient = { clientId ->
                            navigateTo(Screen.ClientDetail(clientId))
                        },
                        calendarManager = calendarManager
                    )
                }

                entry<Screen.Settings> {
                    SettingsScreen(
                        appPreferences = appPreferences,
                        onNavigateBack = { popBackStack() },
                        onNavigateToServices = {
                            navigateTo(Screen.ServicesSettings)
                        },
                        onNavigateToTrash = {
                            navigateTo(Screen.Trash)
                        },
                        onNavigateToSmsTemplates = {
                            navigateTo(Screen.SmsTemplates)
                        }
                    )
                }

                entry<Screen.ServicesSettings> {
                    ServicesSettingsScreen(
                        serviceRepository = serviceRepository,
                        onNavigateBack = { popBackStack() }
                    )
                }

                entry<Screen.SmsTemplates> {
                    SmsTemplatesScreen(
                        smsTemplateRepository = smsTemplateRepository,
                        onNavigateBack = { popBackStack() }
                    )
                }

                entry<Screen.Trash> {
                    TrashScreen(
                        jobRepository = jobRepository,
                        noteRepository = noteRepository,
                        taskRepository = taskRepository,
                        onNavigateBack = { popBackStack() }
                    )
                }

                entry<Screen.Onboarding> {
                    OnboardingScreen(
                        appPreferences = appPreferences,
                        onComplete = {
                            navigateBottomTab(Screen.Calls)
                        }
                    )
                }
            }
        )
    }
}
