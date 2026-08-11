package com.franciscor.agendnote.core.notifications

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.LocalDate

/**
 * Where tapping a delivered notification should take the user. Small and explicit on purpose
 * (Operación Aniversario, "Sprint Final" directive item 9: "no serialices objetos gigantes") -
 * just enough to resolve the real content client-side, not a snapshot of it.
 *
 * [Task.day] rides along because [com.franciscor.agendnote.feature.agenda.presentation.view.AgendaScreen]
 * only resolves a task id against the currently *selected* day's already-loaded task list - a
 * task notification for a different day than whatever happens to be selected when the app opens
 * cold needs to select that day first, or the id would resolve to nothing.
 */
sealed interface NotificationRoute {
    data class Task(val taskId: String, val day: LocalDate) : NotificationRoute
    data class PersonalMessage(val messageId: String) : NotificationRoute
}

/**
 * Bridges a platform-native notification tap (iOS: `UNUserNotificationCenterDelegate`, see
 * `IosNotificationDelegate`; Android: `AndroidNotificationReceiver`/intent extras) to the shared
 * Compose navigation layer (`AppNavHost`), across all three states the directive calls out
 * (foreground/background/cold start) without either side needing a direct reference to the
 * other.
 *
 * [MutableStateFlow], not a one-shot event bus: a route posted before `AppNavHost` has started
 * collecting (the cold-start case - the app process didn't exist yet when the user tapped the
 * notification) must not be lost. A new collector on a `StateFlow` always receives the current
 * value immediately, which a `SharedFlow`/`Channel` with no replay would not guarantee here.
 * [consume] clears it after `AppNavHost` acts on it, so backgrounding and returning to the app
 * later doesn't replay a stale route.
 */
object NotificationRouter {
    private val _pendingRoute = MutableStateFlow<NotificationRoute?>(null)
    val pendingRoute: StateFlow<NotificationRoute?> = _pendingRoute

    fun route(route: NotificationRoute) {
        _pendingRoute.value = route
    }

    /** Call once the route has actually been acted on (state updated to open the right screen). */
    fun consume() {
        _pendingRoute.value = null
    }
}
