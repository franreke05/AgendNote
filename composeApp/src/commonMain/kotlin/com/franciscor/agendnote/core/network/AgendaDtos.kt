package com.franciscor.agendnote.core.network

import kotlinx.serialization.Serializable

@Serializable
data class LabelDto(
    val id: String,
    val name: String,
    val color_hex: String,
)

@Serializable
data class SubtaskDto(
    val id: String = "",
    val title: String,
    val is_done: Boolean = false,
    val order_index: Int = 0,
)

@Serializable
data class TaskDto(
    val id: String,
    val title: String,
    val body: String? = null,
    val day: String,
    val due_at: String? = null,
    val slot_end_at: String? = null,
    val deadline_at: String? = null,
    val is_done: Boolean = false,
    val order_index: Int = 0,
    val labels: List<LabelDto> = emptyList(),
    val series_id: String? = null,
    val reminders: List<String> = emptyList(),
    val subtasks: List<SubtaskDto> = emptyList(),
)

@Serializable
data class LabelsResponse(
    val labels: List<LabelDto> = emptyList(),
)

@Serializable
data class LabelResponse(
    val label: LabelDto,
)

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

@Serializable
data class CreateTaskRequest(
    val title: String,
    val body: String? = null,
    val day: String,
    val due_at: String? = null,
    val slot_end_at: String? = null,
    val deadline_at: String? = null,
    val is_done: Boolean = false,
    val order_index: Int = 0,
    val label_ids: List<String> = emptyList(),
    val label_names: List<String> = emptyList(),
    val series_id: String? = null,
    val reminders: List<String>? = null,
    val subtasks: List<SubtaskDto>? = null,
)

@Serializable
data class UpdateTaskRequest(
    val id: String,
    val title: String? = null,
    val body: String? = null,
    val day: String? = null,
    val due_at: String? = null,
    val slot_end_at: String? = null,
    val deadline_at: String? = null,
    val is_done: Boolean? = null,
    val order_index: Int? = null,
    val label_ids: List<String>? = null,
    val label_names: List<String>? = null,
    val reminders: List<String>? = null,
    val subtasks: List<SubtaskDto>? = null,
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

@Serializable
data class TaskSeriesDto(
    val id: String,
    val title: String,
    val body: String? = null,
    val time: String? = null,
    val recurrence_type: String,
    val days_of_week: List<Int>? = null,
    val day_of_month: Int? = null,
    val label_ids: List<String> = emptyList(),
    val start_date: String,
    val is_active: Boolean = true,
    val materialized_until: String,
)

@Serializable
data class CreateTaskSeriesRequest(
    val title: String,
    val body: String? = null,
    val time: String? = null,
    val recurrence_type: String,
    val days_of_week: List<Int>? = null,
    val day_of_month: Int? = null,
    val label_ids: List<String> = emptyList(),
    val start_date: String,
)

@Serializable
data class UpdateTaskSeriesRequest(
    val id: String,
    val materialized_until: String? = null,
    val is_active: Boolean? = null,
)

@Serializable
data class TaskSeriesResponse(
    val series: TaskSeriesDto,
)

@Serializable
data class TaskSeriesListResponse(
    val series: List<TaskSeriesDto> = emptyList(),
)
