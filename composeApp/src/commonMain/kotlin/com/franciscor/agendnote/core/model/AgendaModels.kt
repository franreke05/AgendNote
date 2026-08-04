package com.franciscor.agendnote.core.model

import androidx.compose.runtime.Immutable
import com.franciscor.agendnote.feature.agenda.domain.RecurrenceRule
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

@Immutable
data class LabelTag(
    val id: String,
    val name: String,
    val colorHex: String,
)

/** A single checklist item within a task. [id] is empty for a not-yet-saved subtask. */
@Immutable
data class Subtask(
    val id: String = "",
    val title: String,
    val isDone: Boolean = false,
    val orderIndex: Int = 0,
)

@Immutable
data class TaskItem(
    val id: String,
    val title: String,
    val details: String?,
    val time: LocalTime?,
    val labels: List<LabelTag>,
    val endTime: LocalTime? = null,
    val isDone: Boolean = false,
    val seriesId: String? = null,
    /** Last acceptable moment to finish the task - distinct from [time], which is when the
     * user planned to start it (see docs/agendnote/FASE4_PROPUESTA.md). */
    val deadline: Instant? = null,
    val reminders: List<Instant> = emptyList(),
    val subtasks: List<Subtask> = emptyList(),
)

@Immutable
data class TaskDraft(
    val title: String,
    val details: String?,
    val time: LocalTime?,
    val labels: List<LabelTag>,
    val seriesId: String? = null,
    val deadline: Instant? = null,
    val reminders: List<Instant> = emptyList(),
    val subtasks: List<Subtask> = emptyList(),
)

@Immutable
data class TaskSeries(
    val id: String,
    val title: String,
    val details: String?,
    val time: LocalTime?,
    val rule: RecurrenceRule,
    val labelIds: List<String>,
    val startDate: LocalDate,
    val isActive: Boolean,
    val materializedUntil: LocalDate,
)
