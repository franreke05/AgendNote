package com.franciscor.agendnote

import com.franciscor.agendnote.core.model.LabelTag
import com.franciscor.agendnote.core.model.Subtask
import com.franciscor.agendnote.core.model.TaskDraft
import com.franciscor.agendnote.core.network.SubtaskDto
import com.franciscor.agendnote.core.network.TaskDto
import com.franciscor.agendnote.feature.agenda.data.toTaskItem
import com.franciscor.agendnote.feature.agenda.data.toUpdateTaskRequest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SupabaseAgendaTaskRepositoryMappingTest {
    private val timeZone = TimeZone.UTC

    @Test
    fun `toTaskItem maps deadline_at to an Instant`() {
        val dto = TaskDto(
            id = "t-1",
            title = "Entregar informe",
            day = "2026-03-11",
            deadline_at = "2026-03-12T18:00:00Z",
        )

        val task = dto.toTaskItem(timeZone)

        assertEquals(Instant.parse("2026-03-12T18:00:00Z"), task.deadline)
    }

    @Test
    fun `toTaskItem leaves deadline null when deadline_at is absent`() {
        val dto = TaskDto(id = "t-1", title = "Sin deadline", day = "2026-03-11")

        val task = dto.toTaskItem(timeZone)

        assertNull(task.deadline)
    }

    @Test
    fun `toTaskItem maps reminders to a list of Instant, ignoring unparseable entries`() {
        val dto = TaskDto(
            id = "t-1",
            title = "Con recordatorios",
            day = "2026-03-11",
            reminders = listOf("2026-03-11T08:00:00Z", "not-a-timestamp", "2026-03-12T08:00:00Z"),
        )

        val task = dto.toTaskItem(timeZone)

        assertEquals(
            listOf(Instant.parse("2026-03-11T08:00:00Z"), Instant.parse("2026-03-12T08:00:00Z")),
            task.reminders,
        )
    }

    @Test
    fun `toTaskItem maps subtasks preserving order and done state`() {
        val dto = TaskDto(
            id = "t-1",
            title = "Con subtareas",
            day = "2026-03-11",
            subtasks = listOf(
                SubtaskDto(id = "s-2", title = "Segunda", is_done = false, order_index = 1),
                SubtaskDto(id = "s-1", title = "Primera", is_done = true, order_index = 0),
            ),
        )

        val task = dto.toTaskItem(timeZone)

        assertEquals(
            listOf(
                com.franciscor.agendnote.core.model.Subtask("s-2", "Segunda", false, 1),
                com.franciscor.agendnote.core.model.Subtask("s-1", "Primera", true, 0),
            ),
            task.subtasks,
        )
    }

    @Test
    fun `toUpdateTaskRequest sends due_at as empty string when time is null, never omitted`() {
        val draft = TaskDraft(
            title = "Sin hora",
            details = "Detalles",
            time = null,
            labels = emptyList(),
        )

        val request = draft.toUpdateTaskRequest("t-1", LocalDate(2026, 3, 11), timeZone)

        assertEquals("", request.due_at)
    }

    @Test
    fun `toUpdateTaskRequest sends deadline_at as empty string when deadline is null`() {
        val draft = TaskDraft(
            title = "Sin deadline",
            details = "Detalles",
            time = null,
            labels = emptyList(),
            deadline = null,
        )

        val request = draft.toUpdateTaskRequest("t-1", LocalDate(2026, 3, 11), timeZone)

        assertEquals("", request.deadline_at)
    }

    @Test
    fun `toUpdateTaskRequest sends body as empty string when details is null`() {
        val draft = TaskDraft(
            title = "Sin detalles",
            details = null,
            time = null,
            labels = emptyList(),
        )

        val request = draft.toUpdateTaskRequest("t-1", LocalDate(2026, 3, 11), timeZone)

        assertEquals("", request.body)
    }

    @Test
    fun `toUpdateTaskRequest sends explicit empty lists for empty labels, reminders and subtasks`() {
        val draft = TaskDraft(
            title = "Sin listas",
            details = null,
            time = null,
            labels = emptyList(),
            reminders = emptyList(),
            subtasks = emptyList(),
        )

        val request = draft.toUpdateTaskRequest("t-1", LocalDate(2026, 3, 11), timeZone)

        assertEquals(emptyList(), request.label_ids)
        assertEquals(emptyList(), request.reminders)
        assertEquals(emptyList(), request.subtasks)
    }

    @Test
    fun `toUpdateTaskRequest maps time, deadline, labels, reminders and subtasks when present`() {
        val draft = TaskDraft(
            title = "Con todo",
            details = "Detalles",
            time = LocalTime(9, 30),
            labels = listOf(LabelTag(id = "l-1", name = "Trabajo", colorHex = "#FF0000")),
            deadline = Instant.parse("2026-03-12T18:00:00Z"),
            reminders = listOf(Instant.parse("2026-03-11T08:00:00Z")),
            subtasks = listOf(Subtask(id = "s-1", title = "Primera", isDone = false, orderIndex = 5)),
        )

        val request = draft.toUpdateTaskRequest("t-1", LocalDate(2026, 3, 11), timeZone)

        assertEquals("t-1", request.id)
        assertEquals("Con todo", request.title)
        assertEquals("2026-03-11", request.day)
        assertEquals("2026-03-11T09:30:00Z", request.due_at)
        assertEquals("2026-03-12T18:00:00Z", request.deadline_at)
        assertEquals(listOf("l-1"), request.label_ids)
        assertEquals(listOf("2026-03-11T08:00:00Z"), request.reminders)
        assertEquals(listOf("Primera"), request.subtasks?.map { it.title })
        assertEquals(listOf(0), request.subtasks?.map { it.order_index })
    }
}
