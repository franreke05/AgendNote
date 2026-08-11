package com.franciscor.agendnote.core.notifications

import com.franciscor.agendnote.core.model.TaskItem
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

/**
 * Which [NotificationSoundId] a task's reminder notification should use, inferred from the task
 * itself - there is no per-task sound picker in the product today (Operación Aniversario, "Sprint
 * Final" directive, section 2 only defines the ID contract, not a business rule for tasks
 * specifically). This is a deliberate, documented interpretation, not a rule the directive stated
 * explicitly - flag it to the product owner if the intent was different:
 *
 * - [NotificationSoundId.DEADLINE] if the task has a [TaskItem.deadline] and this reminder fires
 *   at or after it (a deadline reminder should sound more urgent than a routine heads-up).
 * - [NotificationSoundId.REMINDER_NOW] if the reminder fires at or after the task's own planned
 *   [TaskItem.time] (this reminder *is* "it's happening now", not an earlier heads-up).
 * - [NotificationSoundId.REMINDER_GENERAL] otherwise (an earlier reminder, e.g. "15 minutes
 *   before").
 *
 * [NotificationSoundId.MORNING] is never returned here - see its own doc comment for why.
 */
fun resolveTaskReminderSoundId(
    task: TaskItem,
    taskDate: LocalDate,
    reminderInstant: Instant,
    zone: TimeZone = TimeZone.currentSystemDefault(),
): NotificationSoundId {
    val deadline = task.deadline
    if (deadline != null && reminderInstant >= deadline) {
        return NotificationSoundId.DEADLINE
    }
    val plannedInstant = task.time?.let { time -> LocalDateTime(taskDate, time).toInstant(zone) }
    return if (plannedInstant != null && reminderInstant >= plannedInstant) {
        NotificationSoundId.REMINDER_NOW
    } else {
        NotificationSoundId.REMINDER_GENERAL
    }
}
