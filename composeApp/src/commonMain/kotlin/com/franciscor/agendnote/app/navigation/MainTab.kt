package com.franciscor.agendnote.app.navigation

enum class MainTab(
    val label: String,
    val route: AppRoute,
) {
    AGENDA("Agenda", AppRoute.Agenda),
    CALENDAR("Calendario", AppRoute.Calendar),
    LABELS("Etiquetas", AppRoute.Labels),
    SETTINGS("Ajustes", AppRoute.Settings),
    ;

    companion object {
        fun fromRoute(route: String?): MainTab? {
            return entries.firstOrNull { it.route.route == route }
        }
    }
}
