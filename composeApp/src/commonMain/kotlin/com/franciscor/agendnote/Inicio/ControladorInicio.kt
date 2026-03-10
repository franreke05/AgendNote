package com.franciscor.agendnote.Inicio

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
    val isDone: Boolean = false,
    val source: String? = null,
    val bookingStatus: String? = null,
    val appointmentId: String? = null,
    val clientName: String? = null,
    val clientEmail: String? = null,
    val clientPhone: String? = null,
)

@Immutable
data class TaskDraft(
    val title: String,
    val details: String?,
    val time: LocalTime?,
    val labels: List<LabelTag>,
)
