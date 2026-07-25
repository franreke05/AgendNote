package com.franciscor.agendnote.app.navigation

sealed interface AppRoute {
    val route: String

    data object Agenda : AppRoute {
        override val route: String = "agenda"
    }

    data object Calendar : AppRoute {
        override val route: String = "calendar"
    }

    data object Labels : AppRoute {
        override val route: String = "labels"
    }

    data object Settings : AppRoute {
        override val route: String = "settings"
    }
}
