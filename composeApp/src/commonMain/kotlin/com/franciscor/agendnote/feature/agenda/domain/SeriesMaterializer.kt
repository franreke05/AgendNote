package com.franciscor.agendnote.feature.agenda.domain

import com.franciscor.agendnote.core.model.TaskDraft
import com.franciscor.agendnote.core.model.TaskSeries
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

class SeriesMaterializer(
    private val taskSeriesRepository: TaskSeriesRepository,
    private val agendaTaskRepository: AgendaTaskRepository,
    private val horizonWeeks: Int = 8,
) {
    suspend fun materializeAll(today: LocalDate) {
        val activeSeries = runCatching { taskSeriesRepository.fetchActiveSeries() }.getOrNull() ?: return
        for (series in activeSeries) {
            materializeSeries(series, today)
        }
    }

    /**
     * Genera las apariciones que falten para [series] hasta el horizonte rodante.
     * Devuelve true si no habia nada que hacer o si todo el lote se creo correctamente
     * (y en ese caso avanza `materialized_until`). Devuelve false si alguna creacion fallo,
     * sin avanzar el cursor - el proximo llamado reintenta el mismo tramo.
     */
    suspend fun materializeSeries(series: TaskSeries, today: LocalDate): Boolean {
        val horizonEnd = today.plus(horizonWeeks * 7, DateTimeUnit.DAY)
        val from = maxOf(series.materializedUntil.plus(1, DateTimeUnit.DAY), series.startDate)
        if (from > horizonEnd) return true

        val dates = occurrencesBetween(series.rule, from, horizonEnd)
        val draft = TaskDraft(
            title = series.title,
            details = series.details,
            time = series.time,
            labels = emptyList(),
            seriesId = series.id,
        )

        for (date in dates) {
            val created = runCatching { agendaTaskRepository.createTask(date, draft) }
            if (created.isFailure) return false
        }

        return taskSeriesRepository.markMaterialized(series.id, horizonEnd)
    }
}
