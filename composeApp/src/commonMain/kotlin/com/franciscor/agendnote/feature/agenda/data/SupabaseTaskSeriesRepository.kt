package com.franciscor.agendnote.feature.agenda.data

import com.franciscor.agendnote.core.model.LabelTag
import com.franciscor.agendnote.core.model.TaskSeries
import com.franciscor.agendnote.core.network.AgendaApiClient
import com.franciscor.agendnote.core.network.CreateTaskSeriesRequest
import com.franciscor.agendnote.core.network.TaskSeriesDto
import com.franciscor.agendnote.core.network.UpdateTaskSeriesRequest
import com.franciscor.agendnote.feature.agenda.domain.RecurrenceEnd
import com.franciscor.agendnote.feature.agenda.domain.RecurrenceRule
import com.franciscor.agendnote.feature.agenda.domain.TaskSeriesRepository
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.isoDayNumber

class SupabaseTaskSeriesRepository(
    private val api: AgendaApiClient,
) : TaskSeriesRepository {
    override suspend fun fetchActiveSeries(): List<TaskSeries> {
        return api.fetchTaskSeries().map { it.toTaskSeries() }
    }

    override suspend fun createSeries(
        title: String,
        details: String?,
        time: LocalTime?,
        rule: RecurrenceRule,
        labels: List<LabelTag>,
        startDate: LocalDate,
        end: RecurrenceEnd,
    ): TaskSeries {
        val request = CreateTaskSeriesRequest(
            title = title,
            body = details,
            time = time?.toString(),
            recurrence_type = rule.toRecurrenceType(),
            days_of_week = (rule as? RecurrenceRule.WeeklyDays)?.days?.map { it.isoDayNumber },
            day_of_month = (rule as? RecurrenceRule.Monthly)?.dayOfMonth,
            label_ids = labels.map { it.id },
            start_date = startDate.toString(),
            end_type = end.toEndType(),
            end_date = (end as? RecurrenceEnd.OnDate)?.date?.toString(),
            end_occurrences = (end as? RecurrenceEnd.AfterOccurrences)?.count,
        )
        return api.createTaskSeries(request).toTaskSeries()
    }

    override suspend fun markMaterialized(seriesId: String, until: LocalDate): Boolean {
        return runCatching {
            api.updateTaskSeries(UpdateTaskSeriesRequest(id = seriesId, materialized_until = until.toString()))
        }.isSuccess
    }

    override suspend fun deactivateSeries(seriesId: String): Boolean {
        return runCatching {
            api.updateTaskSeries(UpdateTaskSeriesRequest(id = seriesId, is_active = false))
        }.isSuccess
    }

    override suspend fun deleteSeries(id: String): Boolean = api.deleteTaskSeries(id)
}

private fun RecurrenceRule.toRecurrenceType(): String = when (this) {
    is RecurrenceRule.Daily -> "daily"
    is RecurrenceRule.WeeklyDays -> "weekly_days"
    is RecurrenceRule.Monthly -> "monthly"
}

private fun RecurrenceEnd.toEndType(): String = when (this) {
    RecurrenceEnd.Never -> "never"
    is RecurrenceEnd.OnDate -> "on_date"
    is RecurrenceEnd.AfterOccurrences -> "after_occurrences"
}

private fun TaskSeriesDto.toRecurrenceEnd(): RecurrenceEnd {
    return when (end_type) {
        "on_date" -> end_date?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?.let { RecurrenceEnd.OnDate(it) }
            ?: RecurrenceEnd.Never
        "after_occurrences" -> end_occurrences?.let { RecurrenceEnd.AfterOccurrences(it) } ?: RecurrenceEnd.Never
        else -> RecurrenceEnd.Never
    }
}

private fun TaskSeriesDto.toTaskSeries(): TaskSeries {
    return TaskSeries(
        id = id,
        title = title,
        details = body,
        time = time?.let { runCatching { LocalTime.parse(it) }.getOrNull() },
        rule = toRecurrenceRule(),
        labelIds = label_ids,
        startDate = LocalDate.parse(start_date),
        isActive = is_active,
        materializedUntil = LocalDate.parse(materialized_until),
        end = toRecurrenceEnd(),
    )
}

private fun TaskSeriesDto.toRecurrenceRule(): RecurrenceRule {
    return when (recurrence_type) {
        "weekly_days" -> RecurrenceRule.WeeklyDays(
            days = (days_of_week ?: emptyList())
                .mapNotNull { isoDayNumber -> DayOfWeek.entries.find { it.isoDayNumber == isoDayNumber } }
                .toSet(),
        )
        "monthly" -> RecurrenceRule.Monthly(dayOfMonth = day_of_month ?: 1)
        else -> RecurrenceRule.Daily
    }
}
