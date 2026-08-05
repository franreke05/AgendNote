package com.franciscor.agendnote

import com.franciscor.agendnote.core.network.SubtaskDto
import com.franciscor.agendnote.core.network.TaskDto
import com.franciscor.agendnote.feature.agenda.data.toTaskItem
import kotlinx.datetime.Instant
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
}
