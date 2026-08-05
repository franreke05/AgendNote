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
     * Genera las apariciones que falten para [series] hasta el horizonte rodante (o hasta que
     * termine antes, ver [RecurrenceEnd]). Devuelve true si no habia nada que hacer o si todo
     * el lote se creo correctamente (y en ese caso avanza `materialized_until`, y desactiva la
     * serie si [RecurrenceEnd] se alcanzo). Devuelve false si alguna creacion fallo, sin avanzar
     * el cursor - el proximo llamado reintenta el mismo tramo.
     */
    suspend fun materializeSeries(series: TaskSeries, today: LocalDate): Boolean {
        val horizonEnd = today.plus(horizonWeeks * 7, DateTimeUnit.DAY)
        val from = maxOf(series.materializedUntil.plus(1, DateTimeUnit.DAY), series.startDate)

        val plan = planMaterialization(
            rule = series.rule,
            seriesStartDate = series.startDate,
            currentMaterializedUntil = series.materializedUntil,
            end = series.end,
            from = from,
            horizonEnd = horizonEnd,
        )

        val draft = TaskDraft(
            title = series.title,
            details = series.details,
            time = series.time,
            labels = emptyList(),
            seriesId = series.id,
        )

        for (date in plan.datesToCreate) {
            val created = runCatching { agendaTaskRepository.createTask(date, draft) }
            if (created.isFailure) return false
        }

        val marked = if (plan.materializedUntil != series.materializedUntil) {
            taskSeriesRepository.markMaterialized(series.id, plan.materializedUntil)
        } else {
            true
        }
        if (!marked) return false

        if (plan.shouldDeactivate) {
            // No falla la operacion completa si esto falla: los datos ya quedaron consistentes
            // (todas las apariciones dentro del limite existen), y planMaterialization vuelve a
            // pedir shouldDeactivate=true en la proxima pasada si esta llamada no lo logra (ver
            // el test "already past the end date...").
            runCatching { taskSeriesRepository.deactivateSeries(series.id) }
        }

        return true
    }
}

internal data class MaterializationPlan(
    val datesToCreate: List<LocalDate>,
    val shouldDeactivate: Boolean,
    val materializedUntil: LocalDate,
)

/**
 * Decide, de forma pura, que fechas materializar en este lote y si la serie debe desactivarse
 * al terminar - separado de [SeriesMaterializer.materializeSeries] para poder probarlo sin
 * repositorios ni red (ver SeriesMaterializationPlanTest).
 */
internal fun planMaterialization(
    rule: RecurrenceRule,
    seriesStartDate: LocalDate,
    currentMaterializedUntil: LocalDate,
    end: RecurrenceEnd,
    from: LocalDate,
    horizonEnd: LocalDate,
): MaterializationPlan {
    val cappedHorizonEnd = if (end is RecurrenceEnd.OnDate) minOf(horizonEnd, end.date) else horizonEnd

    if (from > cappedHorizonEnd) {
        val reachedEnd = end is RecurrenceEnd.OnDate && currentMaterializedUntil >= end.date
        return MaterializationPlan(
            datesToCreate = emptyList(),
            shouldDeactivate = reachedEnd,
            materializedUntil = currentMaterializedUntil,
        )
    }

    var dates = occurrencesBetween(rule, from, cappedHorizonEnd)
    var shouldDeactivate = end is RecurrenceEnd.OnDate && cappedHorizonEnd == end.date

    if (end is RecurrenceEnd.AfterOccurrences) {
        val alreadyMaterialized = occurrencesBetween(rule, seriesStartDate, currentMaterializedUntil).size
        val remaining = (end.count - alreadyMaterialized).coerceAtLeast(0)
        if (dates.size >= remaining) {
            dates = dates.take(remaining)
            shouldDeactivate = true
        }
    }

    return MaterializationPlan(
        datesToCreate = dates,
        shouldDeactivate = shouldDeactivate,
        materializedUntil = cappedHorizonEnd,
    )
}
