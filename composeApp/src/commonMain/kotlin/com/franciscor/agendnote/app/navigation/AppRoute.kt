package com.franciscor.agendnote.app.navigation

sealed interface AppRoute {
    val route: String

    data object Agenda : AppRoute {
        override val route: String = "agenda"
    }

    data object Day : AppRoute {
        override val route: String = "day"
    }

    data object Labels : AppRoute {
        override val route: String = "labels"
    }

    data object Settings : AppRoute {
        override val route: String = "settings"
    }
}
