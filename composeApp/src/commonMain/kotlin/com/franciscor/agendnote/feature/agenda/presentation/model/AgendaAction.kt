package com.franciscor.agendnote.feature.agenda.presentation.model

import kotlinx.datetime.LocalDate

sealed interface AgendaAction {
    data class SelectDate(val date: LocalDate) : AgendaAction

    data class MoveDay(val delta: Int) : AgendaAction

    data object RefreshSelectedDate : AgendaAction
}
