package com.franciscor.agendnote

import com.franciscor.agendnote.core.model.TaskItem
import com.franciscor.agendnote.core.notifications.earliestReminderInstant
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReminderResolutionTest {
    private fun task(reminders: List<Instant>): TaskItem = TaskItem(
        id = "t-1",
        title = "Tarea",
        details = null,
        time = null,
        labels = emptyList(),
        reminders = reminders,
    )

    @Test
    fun `returns the earliest of several reminders regardless of input order`() {
        val earliest = Instant.parse("2026-03-11T08:00:00Z")
        val later = Instant.parse("2026-03-12T08:00:00Z")

        assertEquals(earliest, earliestReminderInstant(task(listOf(later, earliest))))
    }

    @Test
    fun `returns null when the task has no reminders`() {
        assertNull(earliestReminderInstant(task(emptyList())))
    }
}
