package com.franciscor.agendnote.core.model

import androidx.compose.runtime.Immutable
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
)

@Immutable
data class TaskDraft(
    val title: String,
    val details: String?,
    val time: LocalTime?,
    val labels: List<LabelTag>,
)
