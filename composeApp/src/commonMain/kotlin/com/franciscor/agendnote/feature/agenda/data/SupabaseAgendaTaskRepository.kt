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

    override suspend fun updateTask(id: String, date: LocalDate, draft: TaskDraft, remindersTouched: Boolean): TaskItem {
        return api.updateTask(draft.toUpdateTaskRequest(id, date, timeZone, remindersTouched)).toTaskItem(timeZone)
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
 *
 * [reminders] is the one exception to that "always present" rule, on purpose. The edit sheet
 * prefills [selectedReminderOffsetMinutes] from a heuristic match against the task's already-saved
 * reminder instants (see `AgendaOverlays.kt`'s `LaunchedEffect(mode)`), which can legitimately fail
 * to reconstruct the original selection (device timezone changed between edits, reminders that
 * were never one of the four presets, etc.). If that happens and this function still sent
 * `reminders = draft.reminders.map { ... }` unconditionally, an edit where the user never opened
 * "Recordatorios" would submit a `reminders` key backed by an incomplete/empty reconstruction, and
 * the Edge Function's `hasField(body, "reminders")` check would then wipe the task's real
 * reminders - a silent, permanent data loss reported to the user as a successful save. So
 * `reminders` is only ever included when [remindersTouched] says the user actually interacted with
 * that section this session; otherwise it is left `null`/omitted so the server-side `hasField`
 * check never sees the key and the column is left untouched.
 */
internal fun TaskDraft.toUpdateTaskRequest(
    id: String,
    date: LocalDate,
    timeZone: TimeZone,
    remindersTouched: Boolean,
): UpdateTaskRequest {
    val dueAt = time?.let { LocalDateTime(date, it).toInstant(timeZone).toString() } ?: ""
    return UpdateTaskRequest(
        id = id,
        title = title,
        body = details ?: "",
        day = date.toString(),
        due_at = dueAt,
        deadline_at = deadline?.toString() ?: "",
        label_ids = labels.map { it.id },
        reminders = if (remindersTouched) reminders.map { it.toString() } else null,
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
