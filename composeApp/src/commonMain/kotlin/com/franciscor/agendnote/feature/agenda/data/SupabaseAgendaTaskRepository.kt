package com.franciscor.agendnote.feature.agenda.data

import com.franciscor.agendnote.core.model.LabelTag
import com.franciscor.agendnote.core.model.TaskDraft
import com.franciscor.agendnote.core.model.TaskItem
import com.franciscor.agendnote.core.network.AgendaApiClient
import com.franciscor.agendnote.core.network.CreateTaskRequest
import com.franciscor.agendnote.core.network.LabelDto
import com.franciscor.agendnote.core.network.TaskDto
import com.franciscor.agendnote.core.network.UpdateTaskRequest
import com.franciscor.agendnote.feature.agenda.domain.AgendaTaskRepository
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

class SupabaseAgendaTaskRepository(
    private val api: AgendaApiClient,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : AgendaTaskRepository {
    override suspend fun fetchTasks(date: LocalDate): List<TaskItem> {
        return api.fetchTasks(date.toString()).map { it.toTaskItem(timeZone) }
    }

    override suspend fun createTask(date: LocalDate, draft: TaskDraft): TaskItem {
        val dueAt = draft.time?.let { time ->
            LocalDateTime(date, time).toInstant(timeZone).toString()
        }
        val request = CreateTaskRequest(
            title = draft.title,
            body = draft.details,
            day = date.toString(),
            due_at = dueAt,
            is_done = false,
            order_index = 0,
            label_ids = draft.labels.map { it.id },
        )
        return api.createTask(request).toTaskItem(timeZone)
    }

    override suspend fun updateTaskDone(id: String, isDone: Boolean): TaskItem {
        val request = UpdateTaskRequest(
            id = id,
            is_done = isDone,
        )
        return api.updateTask(request).toTaskItem(timeZone)
    }

    override suspend fun deleteTask(id: String): Boolean = api.deleteTask(id)

    override suspend fun deleteAllTasks(): Boolean = api.deleteAllTasks()
}

private fun TaskDto.toTaskItem(timeZone: TimeZone): TaskItem {
    val time = due_at?.let { parseTime(it, timeZone) }
    val endTime = slot_end_at?.let { parseTime(it, timeZone) }
    return TaskItem(
        id = id,
        title = title,
        details = body,
        time = time,
        endTime = endTime,
        labels = labels.map { it.toLabelTag() },
        isDone = is_done,
    )
}

private fun LabelDto.toLabelTag(): LabelTag = LabelTag(
    id = id,
    name = name,
    colorHex = color_hex,
)

private fun parseTime(value: String, timeZone: TimeZone): LocalTime? {
    return runCatching {
        val instant = Instant.parse(value)
        instant.toLocalDateTime(timeZone).time
    }.getOrNull()
}
