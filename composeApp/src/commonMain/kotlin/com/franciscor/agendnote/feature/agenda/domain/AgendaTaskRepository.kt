package com.franciscor.agendnote.feature.agenda.domain

import com.franciscor.agendnote.core.model.TaskDraft
import com.franciscor.agendnote.core.model.TaskItem
import kotlinx.datetime.LocalDate

interface AgendaTaskRepository {
    suspend fun fetchTasks(date: LocalDate): List<TaskItem>

    suspend fun fetchTasksInRange(from: LocalDate, to: LocalDate): Map<LocalDate, List<TaskItem>>

    suspend fun createTask(date: LocalDate, draft: TaskDraft): TaskItem

    suspend fun updateTaskDone(id: String, isDone: Boolean): TaskItem

    suspend fun deleteTask(id: String): Boolean

    suspend fun deleteAllTasks(): Boolean
}
