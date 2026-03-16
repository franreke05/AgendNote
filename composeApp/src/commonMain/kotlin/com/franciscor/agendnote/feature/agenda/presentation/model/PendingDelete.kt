package com.franciscor.agendnote.feature.agenda.presentation.model

import com.franciscor.agendnote.core.model.TaskItem
import kotlinx.datetime.LocalDate

data class PendingDelete(
    val date: LocalDate,
    val task: TaskItem,
)
