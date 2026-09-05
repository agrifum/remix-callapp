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
import com.example.ui.screens.*

sealed class BottomNavItem(val route: String, val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Calls : BottomNavItem(Screen.Calls.route, "Połączenia", Icons.Default.Phone)
    object Jobs : BottomNavItem(Screen.Jobs.route, "Zlecenia", Icons.Default.Build)
    object Tasks : BottomNavItem(Screen.Tasks.route, "Zadania", Icons.Default.CheckCircle)
}

@Composable
fun AppNavHost(container: AppContainer, navController: NavHostController = rememberNavController()) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val bottomItems = listOf(BottomNavItem.Calls, BottomNavItem.Jobs, BottomNavItem.Tasks)
    val showBottomBar = bottomItems.any { it.route == currentRoute }

    Scaffold(bottomBar = {
        if (showBottomBar) NavigationBar {
            bottomItems.forEach { item ->
                NavigationBarItem(
                    icon = { Icon(item.icon, contentDescription = item.title) },
                    label = { Text(item.title) },
                    selected = currentRoute == item.route,
                    onClick = { navController.navigate(item.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    } }
                )
            }
        }
    }) { innerPadding ->
        NavHost(navController = navController, startDestination = Screen.Calls.route, modifier = Modifier.padding(innerPadding)) {
            composable(Screen.Calls.route) {
                CallsScreen(
                    callLogRepository = container.callLogRepository,
                    clientRepository = container.clientRepository,
                    reengagementRepository = container.reengagementRepository,
                    onClientClick = { navController.navigate(Screen.ClientDetail.createRoute(it)) },
                    onNumberClick = { navController.navigate(Screen.NumberDetail.createRoute(it)) },
                    onOpenSettings = { navController.navigate(Screen.Settings.route) }
                )
            }
            composable(Screen.Jobs.route) {
                JobsScreen(container.jobRepository, container.clientRepository,
                    onJobClick = { navController.navigate(Screen.JobDetail.createRoute(it)) },
                    onNewJobClick = { navController.navigate(Screen.NewJob.route) })
            }
            composable(Screen.Tasks.route) { TasksScreen(container.taskRepository, container.noteRepository) }
            composable(Screen.Simulator.route) {
                SimulatorScreen(container.clientRepository, container.serviceRepository, container.callDraftRepository,
                    onNavigateToJobs = { navController.navigate(Screen.Jobs.route) })
            }
            composable(Screen.NewJob.route) {
                NewJobScreen(container.jobRepository, container.clientRepository, container.serviceRepository,
                    onNavigateBack = { navController.popBackStack() })
            }
            composable(Screen.ClientDetail.route, arguments = listOf(navArgument("clientId") { type = NavType.StringType })) { entry ->
                ClientDetailScreen(
                    clientId = entry.arguments?.getString("clientId") ?: "",
                    clientRepository = container.clientRepository,
                    jobRepository = container.jobRepository,
                    noteRepository = container.noteRepository,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToJob = { navController.navigate(Screen.JobDetail.createRoute(it)) }
                )
            }
            composable(Screen.NumberDetail.route, arguments = listOf(navArgument("phoneKey") { type = NavType.StringType })) { entry ->
                NumberDetailScreen(
                    phoneKey = entry.arguments?.getString("phoneKey") ?: "",
                    clientRepository = container.clientRepository,
                    noteRepository = container.noteRepository,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToClient = { navController.navigate(Screen.ClientDetail.createRoute(it)) }
                )
            }
            composable(Screen.JobDetail.route, arguments = listOf(navArgument("jobId") { type = NavType.StringType })) { entry ->
                JobDetailScreen(
                    jobId = entry.arguments?.getString("jobId") ?: "",
                    jobRepository = container.jobRepository,
                    clientRepository = container.clientRepository,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToClient = { navController.navigate(Screen.ClientDetail.createRoute(it)) },
                    calendarManager = container.calendarManager
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    appPreferences = container.appPreferences,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToServices = { navController.navigate(Screen.ServicesSettings.route) },
                    onNavigateToTrash = { navController.navigate(Screen.Trash.route) },
                    onNavigateToSmsTemplates = { navController.navigate(Screen.SmsTemplates.route) },
                    onNavigateToPermissions = { navController.navigate(Screen.Permissions.route) }
                )
            }
            composable(Screen.Permissions.route) { PermissionsScreen(onNavigateBack = { navController.popBackStack() }) }
            composable(Screen.ServicesSettings.route) { ServicesSettingsScreen(container.serviceRepository) { navController.popBackStack() } }
            composable(Screen.SmsTemplates.route) { SmsTemplatesScreen(container.smsTemplateRepository) { navController.popBackStack() } }
            composable(Screen.Trash.route) {
                TrashScreen(container.jobRepository, container.noteRepository, container.taskRepository) { navController.popBackStack() }
            }
        }
    }
}
