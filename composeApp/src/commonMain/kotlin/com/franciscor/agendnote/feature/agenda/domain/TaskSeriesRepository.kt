package com.franciscor.agendnote.feature.agenda.domain

import com.franciscor.agendnote.core.model.LabelTag
import com.franciscor.agendnote.core.model.TaskSeries
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

interface TaskSeriesRepository {
    suspend fun fetchActiveSeries(): List<TaskSeries>

    suspend fun createSeries(
        title: String,
        details: String?,
        time: LocalTime?,
        rule: RecurrenceRule,
        labels: List<LabelTag>,
        startDate: LocalDate,
        end: RecurrenceEnd = RecurrenceEnd.Never,
    ): TaskSeries

    suspend fun markMaterialized(seriesId: String, until: LocalDate): Boolean

    /** Sets `is_active = false`; called once a series reaches its [RecurrenceEnd]. */
    suspend fun deactivateSeries(seriesId: String): Boolean

    suspend fun deleteSeries(id: String): Boolean
}
