package com.franciscor.agendnote.feature.agenda.presentation.model

import com.franciscor.agendnote.core.model.TaskItem
import kotlinx.datetime.LocalDate

/**
 * A task mutation awaiting user confirmation (shown via the shared `GlassConfirmDialog`).
 *
 * Used both for delete confirmations (always required) and for "mark as done" confirmations on
 * portfolio-booking tasks ([completing] = true), since completing a synced booking task does not
 * cancel the underlying appointment and the user should not be able to do that by accident.
 */
data class PendingDelete(
    val date: LocalDate,
    val task: TaskItem,
    val completing: Boolean = false,
)
