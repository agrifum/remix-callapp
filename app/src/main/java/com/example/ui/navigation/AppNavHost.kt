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
import com.example.core.di.AppContainer
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
    container: AppContainer
) {
    val onboardingCompleted by container.appPreferences.onboardingCompleted.collectAsState(initial = null)
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
                        callLogRepository = container.callLogRepository,
                        clientRepository = container.clientRepository,
                        reengagementRepository = container.reengagementRepository,
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
                        jobRepository = container.jobRepository,
                        clientRepository = container.clientRepository,
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
                        taskRepository = container.taskRepository,
                        noteRepository = container.noteRepository
                    )
                }

                entry<Screen.Simulator> {
                    SimulatorScreen(
                        clientRepository = container.clientRepository,
                        serviceRepository = container.serviceRepository,
                        callDraftRepository = container.callDraftRepository,
                        onNavigateToJobs = {
                            navigateBottomTab(Screen.Jobs)
                        }
                    )
                }

                entry<Screen.NewJob> {
                    NewJobScreen(
                        jobRepository = container.jobRepository,
                        clientRepository = container.clientRepository,
                        serviceRepository = container.serviceRepository,
                        onNavigateBack = { popBackStack() }
                    )
                }

                entry<Screen.ClientDetail> { clientDetail ->
                    ClientDetailScreen(
                        clientId = clientDetail.clientId,
                        clientRepository = container.clientRepository,
                        jobRepository = container.jobRepository,
                        noteRepository = container.noteRepository,
                        onNavigateBack = { popBackStack() },
                        onNavigateToJob = { jobId ->
                            navigateTo(Screen.JobDetail(jobId))
                        }
                    )
                }

                entry<Screen.NumberDetail> { numberDetail ->
                    NumberDetailScreen(
                        phoneKey = numberDetail.phoneKey,
                        clientRepository = container.clientRepository,
                        noteRepository = container.noteRepository,
                        onNavigateBack = { popBackStack() },
                        onNavigateToClient = { clientId ->
                            navigateTo(Screen.ClientDetail(clientId))
                        }
                    )
                }

                entry<Screen.JobDetail> { jobDetail ->
                    JobDetailScreen(
                        jobId = jobDetail.jobId,
                        jobRepository = container.jobRepository,
                        clientRepository = container.clientRepository,
                        onNavigateBack = { popBackStack() },
                        onNavigateToClient = { clientId ->
                            navigateTo(Screen.ClientDetail(clientId))
                        },
                        calendarManager = container.calendarManager
                    )
                }

                entry<Screen.Settings> {
                    SettingsScreen(
                        appPreferences = container.appPreferences,
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
                        serviceRepository = container.serviceRepository,
                        onNavigateBack = { popBackStack() }
                    )
                }

                entry<Screen.SmsTemplates> {
                    SmsTemplatesScreen(
                        smsTemplateRepository = container.smsTemplateRepository,
                        onNavigateBack = { popBackStack() }
                    )
                }

                entry<Screen.Trash> {
                    TrashScreen(
                        jobRepository = container.jobRepository,
                        noteRepository = container.noteRepository,
                        taskRepository = container.taskRepository,
                        onNavigateBack = { popBackStack() }
                    )
                }

                entry<Screen.Onboarding> {
                    OnboardingScreen(
                        appPreferences = container.appPreferences,
                        onComplete = {
                            navigateBottomTab(Screen.Calls)
                        }
                    )
                }
            }
        )
    }
}
