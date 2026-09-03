package com.example.ui.navigation

sealed class Screen(val route: String, val title: String) {
    // 3 primary bottom navigation tabs: Połączenia, Zlecenia, Zadania
    object Calls : Screen("calls", "Połączenia")
    object Jobs : Screen("jobs", "Zlecenia")
    object Tasks : Screen("tasks", "Zadania")

    // Production secondary screens
    object NewJob : Screen("new_job", "Nowe zlecenie")
    object ClientDetail : Screen("client_detail/{clientId}", "Klient") {
        fun createRoute(clientId: String) = "client_detail/$clientId"
    }
    object NumberDetail : Screen("number_detail/{phoneKey}", "Szczegóły numeru") {
        fun createRoute(phoneKey: String) = "number_detail/$phoneKey"
    }
    object JobDetail : Screen("job_detail/{jobId}", "Szczegóły zlecenia") {
        fun createRoute(jobId: String) = "job_detail/$jobId"
    }
    object Settings : Screen("settings", "Ustawienia")
    object ServicesSettings : Screen("services_settings", "Cennik i Usługi")
    object Trash : Screen("trash", "Kosz")
    object Simulator : Screen("simulator", "Symulator")
}
