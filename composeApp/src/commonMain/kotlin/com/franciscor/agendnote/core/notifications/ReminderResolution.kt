package com.franciscor.agendnote.core.notifications

import com.franciscor.agendnote.core.model.TaskItem
import kotlinx.datetime.Instant

/**
 * The single instant a platform notification scheduler should fire for [task], or `null` if
 * none of its explicit reminders apply.
 *
 * [TaskItem.reminders] can hold several instants (see docs/agendnote/FASE4_PROPUESTA.md), but
 * today's platform schedulers (`AndroidReminderScheduler`, `IosNotificationService`) still
 * schedule at most one alarm/notification per task - true multi-notification scheduling needs
 * its own pass with device verification, which this environment cannot do. Picking the
 * earliest reminder is the safest single choice: it is the next thing the user would expect to
 * be reminded about for this task.
 */
fun earliestReminderInstant(task: TaskItem): Instant? = task.reminders.minOrNull()
