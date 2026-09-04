package com.example.ui.navigation

import androidx.navigation3.runtime.NavKey

sealed class Screen(val route: String, val title: String) : NavKey {
    // 3 primary bottom navigation tabs: Połączenia, Zlecenia, Zadania
    object Calls : Screen("calls", "Połączenia")
    object Jobs : Screen("jobs", "Zlecenia")
    object Tasks : Screen("tasks", "Zadania")

    // Production secondary screens
    object NewJob : Screen("new_job", "Nowe zlecenie")
    data class ClientDetail(val clientId: String) : Screen("client_detail/$clientId", "Klient") {
        companion object {
            const val route = "client_detail/{clientId}"
            fun createRoute(clientId: String) = "client_detail/$clientId"
        }
    }
    data class NumberDetail(val phoneKey: String) : Screen("number_detail/$phoneKey", "Szczegóły numeru") {
        companion object {
            const val route = "number_detail/{phoneKey}"
            fun createRoute(phoneKey: String) = "number_detail/$phoneKey"
        }
    }
    data class JobDetail(val jobId: String) : Screen("job_detail/$jobId", "Szczegóły zlecenia") {
        companion object {
            const val route = "job_detail/{jobId}"
            fun createRoute(jobId: String) = "job_detail/$jobId"
        }
    }
    object Settings : Screen("settings", "Ustawienia")
    object ServicesSettings : Screen("services_settings", "Cennik i Usługi")
    object SmsTemplates : Screen("sms_templates", "Szablony SMS")
    object Trash : Screen("trash", "Kosz")
    object Simulator : Screen("simulator", "Symulator")
    object Onboarding : Screen("onboarding", "Wprowadzenie")
}
