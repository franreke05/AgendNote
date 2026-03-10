package com.franciscor.agendnote.data

import com.franciscor.agendnote.Inicio.LabelTag
import com.franciscor.agendnote.Inicio.TaskDraft
import com.franciscor.agendnote.Inicio.TaskItem
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

class SupabaseAgendaRepository(
    private val api: AgendaApiClient,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    suspend fun fetchLabels(): List<LabelTag> = api.fetchLabels().map { it.toLabelTag() }

    suspend fun fetchTasks(date: LocalDate): List<TaskItem> {
        return api.fetchTasks(date.toString()).map { it.toTaskItem(timeZone) }
    }

    suspend fun createLabel(name: String, colorHex: String): LabelTag {
        return api.createLabel(name, colorHex).toLabelTag()
    }

    suspend fun createTask(date: LocalDate, draft: TaskDraft): TaskItem {
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

    suspend fun updateTaskDone(id: String, isDone: Boolean): TaskItem {
        val request = UpdateTaskRequest(
            id = id,
            is_done = isDone,
        )
        return api.updateTask(request).toTaskItem(timeZone)
    }

    suspend fun fetchBackgroundUrl(): String? = api.fetchSetting("background_url")

    suspend fun updateBackgroundUrl(value: String): String {
        return api.updateSetting("background_url", value).value
    }

    suspend fun fetchThemeMode(): Boolean? {
        val value = api.fetchSetting("theme_mode")?.trim()?.lowercase()
        return when (value) {
            "dark", "oscuro", "true" -> true
            "light", "claro", "false" -> false
            else -> null
        }
    }

    suspend fun updateThemeMode(isDark: Boolean) {
        val value = if (isDark) "dark" else "light"
        api.updateSetting("theme_mode", value)
    }

    suspend fun deleteLabel(id: String): Boolean {
        return api.deleteLabel(id)
    }

    suspend fun deleteTask(id: String): Boolean {
        return api.deleteTask(id)
    }

    suspend fun deleteAllTasks(): Boolean {
        return api.deleteAllTasks()
    }

    suspend fun deleteAllLabels(): Boolean {
        return api.deleteAllLabels()
    }
}

private fun LabelDto.toLabelTag(): LabelTag = LabelTag(
    id = id,
    name = name,
    colorHex = color_hex,
)

private fun TaskDto.toTaskItem(timeZone: TimeZone): TaskItem {
    val time = due_at?.let { parseTime(it, timeZone) }
    return TaskItem(
        id = id,
        title = title,
        details = body,
        time = time,
        labels = labels.map { it.toLabelTag() },
        isDone = is_done,
        source = source,
        bookingStatus = booking_status,
        appointmentId = appointment_id,
        clientName = client_name,
        clientEmail = client_email,
        clientPhone = client_phone,
    )
}

private fun parseTime(value: String, timeZone: TimeZone): LocalTime? {
    return runCatching {
        val instant = Instant.parse(value)
        instant.toLocalDateTime(timeZone).time
    }.getOrNull()
}
