package com.franciscor.agendnote.feature.agenda.domain

import com.franciscor.agendnote.core.model.TaskDraft
import com.franciscor.agendnote.core.model.TaskItem
import kotlinx.datetime.LocalDate

interface AgendaTaskRepository {
    suspend fun fetchTasks(date: LocalDate): List<TaskItem>

    suspend fun fetchTasksInRange(from: LocalDate, to: LocalDate): Map<LocalDate, List<TaskItem>>

    suspend fun createTask(date: LocalDate, draft: TaskDraft): TaskItem

    /**
     * [remindersTouched] indicates whether the user actively interacted with the "Recordatorios"
     * section during this edit session. When `false`, implementations must leave the task's
     * existing reminders untouched on the server rather than overwriting them with whatever the
     * UI happened to prefill (see [com.franciscor.agendnote.feature.agenda.data.toUpdateTaskRequest]).
     */
    suspend fun updateTask(id: String, date: LocalDate, draft: TaskDraft, remindersTouched: Boolean): TaskItem

    suspend fun updateTaskDone(id: String, isDone: Boolean): TaskItem

    suspend fun deleteTask(id: String): Boolean

    suspend fun deleteAllTasks(): Boolean
}
