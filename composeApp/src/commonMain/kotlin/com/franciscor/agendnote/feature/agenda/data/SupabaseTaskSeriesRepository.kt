package com.franciscor.agendnote.feature.agenda.data

import com.franciscor.agendnote.core.model.LabelTag
import com.franciscor.agendnote.core.model.TaskSeries
import com.franciscor.agendnote.core.network.AgendaApiClient
import com.franciscor.agendnote.core.network.CreateTaskSeriesRequest
import com.franciscor.agendnote.core.network.TaskSeriesDto
import com.franciscor.agendnote.core.network.UpdateTaskSeriesRequest
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
        )
        return api.createTaskSeries(request).toTaskSeries()
    }

    override suspend fun markMaterialized(seriesId: String, until: LocalDate): Boolean {
        return runCatching {
            api.updateTaskSeries(UpdateTaskSeriesRequest(id = seriesId, materialized_until = until.toString()))
        }.isSuccess
    }

    override suspend fun deleteSeries(id: String): Boolean = api.deleteTaskSeries(id)
}

private fun RecurrenceRule.toRecurrenceType(): String = when (this) {
    is RecurrenceRule.Daily -> "daily"
    is RecurrenceRule.WeeklyDays -> "weekly_days"
    is RecurrenceRule.Monthly -> "monthly"
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
