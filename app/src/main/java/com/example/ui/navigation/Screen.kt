package com.example.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed class Screen(val route: String, val title: String) : NavKey {
    // 3 primary bottom navigation tabs: Połączenia, Zlecenia, Zadania
    @Serializable
    object Calls : Screen("calls", "Połączenia")
    @Serializable
    object Jobs : Screen("jobs", "Zlecenia")
    @Serializable
    object Tasks : Screen("tasks", "Zadania")

    // Production secondary screens
    @Serializable
    object NewJob : Screen("new_job", "Nowe zlecenie")
    @Serializable
    data class ClientDetail(val clientId: String) : Screen("client_detail/$clientId", "Klient") {
        companion object {
            const val route = "client_detail/{clientId}"
            fun createRoute(clientId: String) = "client_detail/$clientId"
        }
    }
    @Serializable
    data class NumberDetail(val phoneKey: String) : Screen("number_detail/$phoneKey", "Szczegóły numeru") {
        companion object {
            const val route = "number_detail/{phoneKey}"
            fun createRoute(phoneKey: String) = "number_detail/$phoneKey"
        }
    }
    @Serializable
    data class JobDetail(val jobId: String) : Screen("job_detail/$jobId", "Szczegóły zlecenia") {
        companion object {
            const val route = "job_detail/{jobId}"
            fun createRoute(jobId: String) = "job_detail/$jobId"
        }
    }
    @Serializable
    object Settings : Screen("settings", "Ustawienia")
    @Serializable
    object ServicesSettings : Screen("services_settings", "Cennik i Usługi")
    @Serializable
    object SmsTemplates : Screen("sms_templates", "Szablony SMS")
    @Serializable
    object Trash : Screen("trash", "Kosz")
    @Serializable
    object Simulator : Screen("simulator", "Symulator")
    @Serializable
    object Onboarding : Screen("onboarding", "Wprowadzenie")
}
