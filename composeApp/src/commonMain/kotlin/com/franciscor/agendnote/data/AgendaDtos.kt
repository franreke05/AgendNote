package com.franciscor.agendnote.data

import kotlinx.serialization.Serializable

@Serializable
data class LabelDto(
    val id: String,
    val name: String,
    val color_hex: String,
)

// Shared task payload returned by api-tasks. For tasks mirrored from the portfolio,
// the response contract must include id, title, body, and day.
@Serializable
data class TaskDto(
    val id: String,
    val title: String,
    val body: String? = null,
    val day: String,
    val due_at: String? = null,
    val is_done: Boolean = false,
    val order_index: Int = 0,
    val labels: List<LabelDto> = emptyList(),
    val source: String? = null,
    val booking_status: String? = null,
    val appointment_id: String? = null,
    val client_name: String? = null,
    val client_email: String? = null,
    val client_phone: String? = null,
)

@Serializable
data class LabelsResponse(
    val labels: List<LabelDto> = emptyList(),
)

@Serializable
data class LabelResponse(
    val label: LabelDto,
)

// The agenda screen loads its day view through GET /api-tasks?day=YYYY-MM-DD.
@Serializable
data class TasksResponse(
    val tasks: List<TaskDto> = emptyList(),
)

@Serializable
data class TaskResponse(
    val task: TaskDto,
)

@Serializable
data class CreateLabelRequest(
    val name: String,
    val color_hex: String,
)

// Portfolio mirroring sends title/body/day here. The API creates the row in Supabase
// and generates the task id before returning TaskResponse.
@Serializable
data class CreateTaskRequest(
    val title: String,
    val body: String? = null,
    val day: String,
    val due_at: String? = null,
    val is_done: Boolean = false,
    val order_index: Int = 0,
    val label_ids: List<String> = emptyList(),
)

@Serializable
data class UpdateTaskRequest(
    val id: String,
    val title: String? = null,
    val body: String? = null,
    val day: String? = null,
    val due_at: String? = null,
    val is_done: Boolean? = null,
    val order_index: Int? = null,
    val label_ids: List<String>? = null,
)

@Serializable
data class SettingDto(
    val key: String,
    val value: String,
)

@Serializable
data class SettingResponse(
    val setting: SettingDto? = null,
)

@Serializable
data class SettingsResponse(
    val settings: List<SettingDto> = emptyList(),
)

@Serializable
data class UpdateSettingRequest(
    val key: String,
    val value: String,
)

@Serializable
data class SuccessResponse(
    val success: Boolean = false,
)
