package com.franciscor.agendnote.core.model

import androidx.compose.runtime.Immutable
import com.franciscor.agendnote.feature.agenda.domain.RecurrenceRule
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

@Immutable
data class LabelTag(
    val id: String,
    val name: String,
    val colorHex: String,
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
)

@Immutable
data class TaskDraft(
    val title: String,
    val details: String?,
    val time: LocalTime?,
    val labels: List<LabelTag>,
    val seriesId: String? = null,
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
