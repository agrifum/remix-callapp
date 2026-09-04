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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.core.di.AppContainer
import com.example.ui.screens.CallsScreen
import com.example.ui.screens.ClientDetailScreen
import com.example.ui.screens.JobDetailScreen
import com.example.ui.screens.JobsScreen
import com.example.ui.screens.NewJobScreen
import com.example.ui.screens.NumberDetailScreen
import com.example.ui.screens.ServicesSettingsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SimulatorScreen
import com.example.ui.screens.SmsTemplatesScreen
import com.example.ui.screens.TasksScreen
import com.example.ui.screens.TrashScreen

sealed class BottomNavItem(val route: String, val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Calls : BottomNavItem(Screen.Calls.route, "Połączenia", Icons.Default.Phone)
    object Jobs : BottomNavItem(Screen.Jobs.route, "Zlecenia", Icons.Default.Build)
    object Tasks : BottomNavItem(Screen.Tasks.route, "Zadania", Icons.Default.CheckCircle)
}

@Composable
fun AppNavHost(
    container: AppContainer,
    navController: NavHostController = rememberNavController()
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val bottomItems = listOf(
        BottomNavItem.Calls,
        BottomNavItem.Jobs,
        BottomNavItem.Tasks
    )

    val showBottomBar = bottomItems.any { it.route == currentRoute }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomItems.forEach { item ->
                        NavigationBarItem(
                            icon = { Icon(item.icon, contentDescription = item.title) },
                            label = { Text(item.title) },
                            selected = currentRoute == item.route,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Calls.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Calls.route) {
                CallsScreen(
                    callLogRepository = container.callLogRepository,
                    clientRepository = container.clientRepository,
                    reengagementRepository = container.reengagementRepository,
                    onClientClick = { clientId ->
                        navController.navigate(Screen.ClientDetail.createRoute(clientId))
                    },
                    onNumberClick = { phoneKey ->
                        navController.navigate(Screen.NumberDetail.createRoute(phoneKey))
                    },
                    onOpenSettings = {
                        navController.navigate(Screen.Settings.route)
                    }
                )
            }

            composable(Screen.Jobs.route) {
                JobsScreen(
                    jobRepository = container.jobRepository,
                    clientRepository = container.clientRepository,
                    onJobClick = { jobId ->
                        navController.navigate(Screen.JobDetail.createRoute(jobId))
                    },
                    onNewJobClick = {
                        navController.navigate(Screen.NewJob.route)
                    }
                )
            }

            composable(Screen.Tasks.route) {
                TasksScreen(
                    taskRepository = container.taskRepository,
                    noteRepository = container.noteRepository
                )
            }

            composable(Screen.Simulator.route) {
                SimulatorScreen(
                    clientRepository = container.clientRepository,
                    serviceRepository = container.serviceRepository,
                    callDraftRepository = container.callDraftRepository,
                    onNavigateToJobs = {
                        navController.navigate(Screen.Jobs.route)
                    }
                )
            }

            composable(Screen.NewJob.route) {
                NewJobScreen(
                    jobRepository = container.jobRepository,
                    clientRepository = container.clientRepository,
                    serviceRepository = container.serviceRepository,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.ClientDetail.route,
                arguments = listOf(navArgument("clientId") { type = NavType.StringType })
            ) { backStackEntry ->
                val clientId = backStackEntry.arguments?.getString("clientId") ?: ""
                ClientDetailScreen(
                    clientId = clientId,
                    clientRepository = container.clientRepository,
                    jobRepository = container.jobRepository,
                    noteRepository = container.noteRepository,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToJob = { jobId ->
                        navController.navigate(Screen.JobDetail.createRoute(jobId))
                    }
                )
            }

            composable(
                route = Screen.NumberDetail.route,
                arguments = listOf(navArgument("phoneKey") { type = NavType.StringType })
            ) { backStackEntry ->
                val phoneKey = backStackEntry.arguments?.getString("phoneKey") ?: ""
                NumberDetailScreen(
                    phoneKey = phoneKey,
                    clientRepository = container.clientRepository,
                    noteRepository = container.noteRepository,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToClient = { clientId ->
                        navController.navigate(Screen.ClientDetail.createRoute(clientId))
                    }
                )
            }

            composable(
                route = Screen.JobDetail.route,
                arguments = listOf(navArgument("jobId") { type = NavType.StringType })
            ) { backStackEntry ->
                val jobId = backStackEntry.arguments?.getString("jobId") ?: ""
                JobDetailScreen(
                    jobId = jobId,
                    jobRepository = container.jobRepository,
                    clientRepository = container.clientRepository,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToClient = { clientId ->
                        navController.navigate(Screen.ClientDetail.createRoute(clientId))
                    },
                    calendarManager = container.calendarManager
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    appPreferences = container.appPreferences,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToServices = {
                        navController.navigate(Screen.ServicesSettings.route)
                    },
                    onNavigateToTrash = {
                        navController.navigate(Screen.Trash.route)
                    },
                    onNavigateToSmsTemplates = {
                        navController.navigate(Screen.SmsTemplates.route)
                    }
                )
            }

            composable(Screen.ServicesSettings.route) {
                ServicesSettingsScreen(
                    serviceRepository = container.serviceRepository,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.SmsTemplates.route) {
                SmsTemplatesScreen(
                    smsTemplateRepository = container.smsTemplateRepository,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Trash.route) {
                TrashScreen(
                    jobRepository = container.jobRepository,
                    noteRepository = container.noteRepository,
                    taskRepository = container.taskRepository,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
