package com.franciscor.agendnote.feature.agenda.presentation.model

import com.franciscor.agendnote.core.model.TaskItem
import kotlinx.datetime.LocalDate

/**
 * A task delete mutation awaiting user confirmation (shown via the shared `GlassConfirmDialog`).
 */
data class PendingDelete(
    val date: LocalDate,
    val task: TaskItem,
)
