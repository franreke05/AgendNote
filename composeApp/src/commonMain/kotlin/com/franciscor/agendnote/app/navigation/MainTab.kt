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

/**
 * Dirección de un cambio de pestaña respecto al orden fijo de [MainTab.entries] (el mismo orden
 * en que aparecen en `BottomBar`). Se usa tanto para el gesto de borde en `AppNavHost` como para
 * la dirección de la transición de deslizamiento del `NavHost` (ver [tabSlideDirection]).
 */
enum class SwipeDirection { NEXT, PREVIOUS }

/** Pestaña siguiente en el orden de [MainTab.entries], o `null` si ya es la última. */
fun MainTab.next(): MainTab? {
    val entries = MainTab.entries
    return entries.getOrNull(entries.indexOf(this) + 1)
}

/** Pestaña anterior en el orden de [MainTab.entries], o `null` si ya es la primera. */
fun MainTab.previous(): MainTab? {
    val entries = MainTab.entries
    return entries.getOrNull(entries.indexOf(this) - 1)
}

/**
 * Dirección del cambio entre dos rutas del `NavHost` en términos del orden fijo de pestañas
 * ([MainTab.entries]), o `null` si alguna ruta no corresponde a una pestaña conocida o ambas
 * rutas son la misma pestaña.
 */
fun tabSlideDirection(fromRoute: String?, toRoute: String?): SwipeDirection? {
    val from = MainTab.fromRoute(fromRoute) ?: return null
    val to = MainTab.fromRoute(toRoute) ?: return null
    val entries = MainTab.entries
    val fromIndex = entries.indexOf(from)
    val toIndex = entries.indexOf(to)
    return when {
        toIndex > fromIndex -> SwipeDirection.NEXT
        toIndex < fromIndex -> SwipeDirection.PREVIOUS
        else -> null
    }
}
