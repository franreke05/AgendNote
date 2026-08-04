package com.franciscor.agendnote.feature.agenda.presentation.model

import com.franciscor.agendnote.core.model.TaskItem
import kotlinx.datetime.LocalDate

data class AgendaUiState(
    val selectedDate: LocalDate,
    val visibleMonth: LocalDate,
    val tasksByDate: Map<LocalDate, List<TaskItem>> = emptyMap(),
    val loadingByDate: Map<LocalDate, Boolean> = emptyMap(),
    val errorByDate: Map<LocalDate, String?> = emptyMap(),
    val isRemoteAvailable: Boolean = true,
    val isMonthLoading: Boolean = false,
    val monthErrorMessage: String? = null,
    val pendingUndo: PendingUndo? = null,
)
