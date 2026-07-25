package com.franciscor.agendnote.feature.agenda.presentation.controller

import com.franciscor.agendnote.core.model.TaskDraft
import com.franciscor.agendnote.core.model.TaskItem
import com.franciscor.agendnote.feature.agenda.domain.RecurrenceRule
import com.franciscor.agendnote.feature.agenda.presentation.model.AgendaAction
import com.franciscor.agendnote.feature.agenda.presentation.model.SaveResult
import com.franciscor.agendnote.feature.agenda.presentation.viewmodel.AgendaViewModel
import kotlinx.datetime.LocalDate

class AgendaController(
    private val viewModel: AgendaViewModel,
) {
    suspend fun handle(action: AgendaAction) {
        when (action) {
            is AgendaAction.MoveDay -> {
                val targetDate = viewModel.moveDay(action.delta)
                viewModel.loadTasksForDate(targetDate)
            }

            is AgendaAction.SelectDate -> {
                viewModel.selectDate(action.date)
                viewModel.loadTasksForDate(action.date)
            }

            AgendaAction.RefreshSelectedDate -> {
                viewModel.loadTasksForDate(viewModel.uiState.selectedDate)
            }

            is AgendaAction.LoadMonth -> {
                viewModel.loadMonth(action.month)
            }
        }
    }

    suspend fun saveTask(date: LocalDate, draft: TaskDraft): SaveResult {
        return viewModel.saveTask(date, draft)
    }

    suspend fun saveRecurringTask(date: LocalDate, draft: TaskDraft, rule: RecurrenceRule): SaveResult {
        return viewModel.saveRecurringTask(date, draft, rule)
    }

    suspend fun toggleTaskDone(date: LocalDate, task: TaskItem, isDone: Boolean): Boolean {
        return viewModel.toggleTaskDone(date, task, isDone)
    }

    suspend fun deleteTask(date: LocalDate, task: TaskItem): Boolean {
        return viewModel.deleteTask(date, task)
    }

    suspend fun deleteAllTasks(): Boolean {
        return viewModel.deleteAllTasks()
    }

    // --- Fire-and-forget wrappers -------------------------------------------------------
    // Non-suspend entry points backed by the ViewModel's own internal scope (see
    // AgendaViewModel.viewModelScope). Screen composables should call these directly instead of
    // wrapping the suspend functions above in their own `rememberCoroutineScope().launch { }` -
    // that pattern silently cancels the mutation if the user switches tabs mid-request.

    fun handleAsync(action: AgendaAction) {
        when (action) {
            is AgendaAction.MoveDay -> viewModel.moveDayAndLoad(action.delta)
            is AgendaAction.SelectDate -> viewModel.selectDateAndLoad(action.date)
            AgendaAction.RefreshSelectedDate -> viewModel.refreshSelectedDateAsync()
            is AgendaAction.LoadMonth -> viewModel.loadMonthAsync(action.month)
        }
    }

    fun saveTaskAsync(date: LocalDate, draft: TaskDraft, onResult: (SaveResult) -> Unit = {}) {
        viewModel.saveTaskAsync(date, draft, onResult)
    }

    fun toggleTaskDoneAsync(
        date: LocalDate,
        task: TaskItem,
        isDone: Boolean,
        onResult: (Boolean) -> Unit = {},
    ) {
        viewModel.toggleTaskDoneAsync(date, task, isDone, onResult)
    }

    fun deleteTaskAsync(date: LocalDate, task: TaskItem, onResult: (Boolean) -> Unit = {}) {
        viewModel.deleteTaskAsync(date, task, onResult)
    }

    fun removeLabelFromTasks(labelId: String) {
        viewModel.removeLabelFromTasks(labelId)
    }

    fun clearLabelsFromTasks() {
        viewModel.clearLabelsFromTasks()
    }
}
