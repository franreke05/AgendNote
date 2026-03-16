package com.franciscor.agendnote.feature.agenda.presentation.model

import com.franciscor.agendnote.core.model.TaskItem
import kotlinx.datetime.LocalDate

data class AgendaDayUiState(
    val date: LocalDate,
    val tasks: List<TaskItem> = emptyList(),
    val hasCachedTasks: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
) {
    val shouldShowRefreshing: Boolean
        get() = isLoading && hasCachedTasks

    val shouldShowInitialLoader: Boolean
        get() = isLoading && !hasCachedTasks

    val shouldShowEmptyState: Boolean
        get() = !isLoading && tasks.isEmpty() && errorMessage == null
}
