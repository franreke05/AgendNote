package com.franciscor.agendnote

import com.franciscor.agendnote.core.model.TaskItem
import com.franciscor.agendnote.feature.agenda.domain.SmartList
import com.franciscor.agendnote.feature.agenda.domain.smartListTasks
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals

class SmartListsTest {
    private val today = LocalDate(2026, 8, 4)

    private fun task(
        id: String,
        isDone: Boolean = false,
        time: LocalTime? = null,
        seriesId: String? = null,
        reminders: List<Instant> = emptyList(),
    ): TaskItem = TaskItem(
        id = id,
        title = "Tarea $id",
        details = null,
        time = time,
        labels = emptyList(),
        isDone = isDone,
        seriesId = seriesId,
        reminders = reminders,
    )

    @Test
    fun `overdue lists incomplete tasks from days before today, not today itself`() {
        val tasksByDate = mapOf(
            LocalDate(2026, 8, 2) to listOf(task("past-pending"), task("past-done", isDone = true)),
            today to listOf(task("today-task")),
            LocalDate(2026, 8, 5) to listOf(task("future-task")),
        )

        val result = smartListTasks(SmartList.Overdue, tasksByDate, today)

        assertEquals(listOf(LocalDate(2026, 8, 2) to task("past-pending")), result)
    }

    @Test
    fun `next 7 days includes today through today plus 7, excluding done tasks`() {
        val tasksByDate = mapOf(
            LocalDate(2026, 8, 3) to listOf(task("yesterday")),
            today to listOf(task("today-done", isDone = true), task("today-pending")),
            LocalDate(2026, 8, 11) to listOf(task("in-7-days")),
            LocalDate(2026, 8, 12) to listOf(task("in-8-days")),
        )

        val result = smartListTasks(SmartList.Next7Days, tasksByDate, today)

        assertEquals(
            listOf(today to task("today-pending"), LocalDate(2026, 8, 11) to task("in-7-days")),
            result,
        )
    }

    @Test
    fun `without time lists incomplete tasks that have no planned time`() {
        val tasksByDate = mapOf(
            today to listOf(
                task("with-time", time = LocalTime(9, 0)),
                task("without-time"),
                task("without-time-done", isDone = true),
            ),
        )

        val result = smartListTasks(SmartList.WithoutTime, tasksByDate, today)

        assertEquals(listOf(today to task("without-time")), result)
    }

    @Test
    fun `with reminder lists incomplete tasks that have at least one reminder`() {
        val tasksByDate = mapOf(
            today to listOf(
                task("has-reminder", reminders = listOf(Instant.parse("2026-08-04T09:00:00Z"))),
                task("no-reminder"),
            ),
        )

        val result = smartListTasks(SmartList.WithReminder, tasksByDate, today)

        assertEquals(listOf(today to task("has-reminder", reminders = listOf(Instant.parse("2026-08-04T09:00:00Z")))), result)
    }

    @Test
    fun `recurring lists incomplete tasks that belong to a series`() {
        val tasksByDate = mapOf(
            today to listOf(task("recurring", seriesId = "series-1"), task("one-off")),
        )

        val result = smartListTasks(SmartList.Recurring, tasksByDate, today)

        assertEquals(listOf(today to task("recurring", seriesId = "series-1")), result)
    }

    @Test
    fun `results are ordered by date then by the existing within-day order`() {
        val tasksByDate = mapOf(
            LocalDate(2026, 8, 6) to listOf(task("later")),
            LocalDate(2026, 8, 5) to listOf(task("first-b"), task("first-a")),
        )

        val result = smartListTasks(SmartList.Next7Days, tasksByDate, today)

        assertEquals(
            listOf(
                LocalDate(2026, 8, 5) to task("first-b"),
                LocalDate(2026, 8, 5) to task("first-a"),
                LocalDate(2026, 8, 6) to task("later"),
            ),
            result,
        )
    }
}
