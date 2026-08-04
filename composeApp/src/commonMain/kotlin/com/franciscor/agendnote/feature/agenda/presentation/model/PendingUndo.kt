package com.franciscor.agendnote.feature.agenda.presentation.model

import com.franciscor.agendnote.core.model.TaskItem
import kotlinx.datetime.LocalDate

/**
 * The most recently completed task, offered to the user as an undo affordance (shown via a
 * snackbar with a "Deshacer" action). Cleared once the snackbar is dismissed, times out, or the
 * same task is unmarked through any other path.
 */
data class PendingUndo(
    val date: LocalDate,
    val task: TaskItem,
)
