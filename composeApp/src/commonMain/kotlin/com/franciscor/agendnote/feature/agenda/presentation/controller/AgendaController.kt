package com.franciscor.agendnote.feature.agenda.presentation.controller

import com.franciscor.agendnote.core.model.TaskDraft
import com.franciscor.agendnote.core.model.TaskItem
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
        }
    }

    suspend fun saveTask(date: LocalDate, draft: TaskDraft): SaveResult {
        return viewModel.saveTask(date, draft)
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

    fun removeLabelFromTasks(labelId: String) {
        viewModel.removeLabelFromTasks(labelId)
    }

    fun clearLabelsFromTasks() {
        viewModel.clearLabelsFromTasks()
    }
}
