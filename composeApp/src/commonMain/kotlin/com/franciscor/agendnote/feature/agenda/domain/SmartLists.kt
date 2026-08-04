package com.franciscor.agendnote.feature.agenda.domain

import com.franciscor.agendnote.core.model.TaskItem
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * Predefined cross-date filters over whatever the caller already has loaded in
 * `tasksByDate` (see `AgendaViewModel.uiState`). These are client-side views, not a backend
 * query: they only see tasks for dates the app has already fetched (a visited day, or a
 * loaded month) - there is no "fetch all tasks regardless of date" endpoint today. Documented
 * as a real limitation in docs/agendnote/IMPLEMENTATION_LOG.md, not hidden.
 */
enum class SmartList {
    Overdue,
    Next7Days,
    WithoutTime,
    WithReminder,
    Recurring,
}

/**
 * Tasks matching [list], as (date, task) pairs ordered by date and then by the order they
 * already had within their day. Always excludes completed tasks - a smart list is about what
 * still needs attention.
 */
fun smartListTasks(
    list: SmartList,
    tasksByDate: Map<LocalDate, List<TaskItem>>,
    today: LocalDate,
): List<Pair<LocalDate, TaskItem>> {
    val predicate: (LocalDate, TaskItem) -> Boolean = when (list) {
        SmartList.Overdue -> { date, task -> date < today && !task.isDone }
        SmartList.Next7Days -> {
            val horizon = today.plus(7, DateTimeUnit.DAY)
            ({ date, task -> date in today..horizon && !task.isDone })
        }
        SmartList.WithoutTime -> { _, task -> task.time == null && !task.isDone }
        SmartList.WithReminder -> { _, task -> task.reminders.isNotEmpty() && !task.isDone }
        SmartList.Recurring -> { _, task -> task.seriesId != null && !task.isDone }
    }

    return tasksByDate.entries
        .sortedBy { it.key }
        .flatMap { (date, tasks) -> tasks.filter { predicate(date, it) }.map { date to it } }
}
