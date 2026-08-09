package com.franciscor.agendnote.feature.agenda.data

import com.franciscor.agendnote.core.model.LabelTag
import com.franciscor.agendnote.core.model.Subtask
import com.franciscor.agendnote.core.model.TaskDraft
import com.franciscor.agendnote.core.model.TaskItem
import com.franciscor.agendnote.core.network.AgendaApiClient
import com.franciscor.agendnote.core.network.CreateTaskRequest
import com.franciscor.agendnote.core.network.LabelDto
import com.franciscor.agendnote.core.network.SubtaskDto
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

    override suspend fun fetchTasksInRange(from: LocalDate, to: LocalDate): Map<LocalDate, List<TaskItem>> {
        return api.fetchTasksInRange(from.toString(), to.toString())
            .groupBy({ LocalDate.parse(it.day) }, { it.toTaskItem(timeZone) })
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
            deadline_at = draft.deadline?.toString(),
            is_done = false,
            order_index = 0,
            label_ids = draft.labels.map { it.id },
            series_id = draft.seriesId,
            reminders = draft.reminders.takeIf { it.isNotEmpty() }?.map { it.toString() },
            subtasks = draft.subtasks.takeIf { it.isNotEmpty() }?.map { it.toSubtaskDto() },
        )
        return api.createTask(request).toTaskItem(timeZone)
    }

    override suspend fun updateTask(id: String, date: LocalDate, draft: TaskDraft): TaskItem {
        return api.updateTask(draft.toUpdateTaskRequest(id, date, timeZone)).toTaskItem(timeZone)
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

/**
 * Internal (not private) so [SupabaseAgendaTaskRepositoryMappingTest] can exercise it directly.
 *
 * Optional fields ([body], [due_at], [deadline_at]) are always sent as `""` rather than omitted
 * when the draft has no value, never as `null`: the Ktor client's `Json` has `explicitNulls =
 * false`, so a `null` field is dropped from the JSON entirely, and the Edge Function
 * (`buildUpdatePayload` in supabase/functions/api-tasks/index.ts) only touches a column when its
 * key is present in the body - an omitted key never clears the column. `""` travels as a present
 * key and `normalizeOptionalString` on the server turns it back into `null` there.
 */
internal fun TaskDraft.toUpdateTaskRequest(id: String, date: LocalDate, timeZone: TimeZone): UpdateTaskRequest {
    val dueAt = time?.let { LocalDateTime(date, it).toInstant(timeZone).toString() } ?: ""
    return UpdateTaskRequest(
        id = id,
        title = title,
        body = details ?: "",
        day = date.toString(),
        due_at = dueAt,
        deadline_at = deadline?.toString() ?: "",
        label_ids = labels.map { it.id },
        reminders = reminders.map { it.toString() },
        subtasks = subtasks.mapIndexed { index, subtask -> subtask.toSubtaskDto().copy(order_index = index) },
    )
}

/** Internal (not private) so [SupabaseAgendaTaskRepositoryMappingTest] can exercise it directly. */
internal fun TaskDto.toTaskItem(timeZone: TimeZone): TaskItem {
    val time = due_at?.let { parseTime(it, timeZone) }
    val endTime = slot_end_at?.let { parseTime(it, timeZone) }
    val deadline = deadline_at?.let { parseInstant(it) }
    val reminderInstants = reminders.mapNotNull { parseInstant(it) }
    return TaskItem(
        id = id,
        title = title,
        details = body,
        time = time,
        endTime = endTime,
        labels = labels.map { it.toLabelTag() },
        isDone = is_done,
        seriesId = series_id,
        deadline = deadline,
        reminders = reminderInstants,
        subtasks = subtasks.map { it.toSubtask() },
    )
}

private fun LabelDto.toLabelTag(): LabelTag = LabelTag(
    id = id,
    name = name,
    colorHex = color_hex,
)

private fun SubtaskDto.toSubtask(): Subtask = Subtask(
    id = id,
    title = title,
    isDone = is_done,
    orderIndex = order_index,
)

private fun Subtask.toSubtaskDto(): SubtaskDto = SubtaskDto(
    id = id,
    title = title,
    is_done = isDone,
    order_index = orderIndex,
)

private fun parseTime(value: String, timeZone: TimeZone): LocalTime? {
    return parseInstant(value)?.toLocalDateTime(timeZone)?.time
}

private fun parseInstant(value: String): Instant? = runCatching { Instant.parse(value) }.getOrNull()
