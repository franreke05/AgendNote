package com.franciscor.agendnote

import com.franciscor.agendnote.core.model.LabelTag
import com.franciscor.agendnote.core.model.TaskDraft
import com.franciscor.agendnote.core.model.TaskItem
import com.franciscor.agendnote.core.model.TaskSeries
import com.franciscor.agendnote.feature.agenda.domain.AgendaTaskRepository
import com.franciscor.agendnote.feature.agenda.domain.RecurrenceEnd
import com.franciscor.agendnote.feature.agenda.domain.RecurrenceRule
import com.franciscor.agendnote.feature.agenda.domain.SeriesMaterializer
import com.franciscor.agendnote.feature.agenda.domain.TaskSeriesRepository
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class FakeTaskSeriesRepositoryForMaterializer(
    initialSeries: List<TaskSeries>,
) : TaskSeriesRepository {
    private val series = initialSeries.toMutableList()
    val markedUntil = mutableMapOf<String, LocalDate>()
    val deactivatedIds = mutableListOf<String>()

    override suspend fun fetchActiveSeries(): List<TaskSeries> = series.filter { it.isActive }

    override suspend fun createSeries(
        title: String,
        details: String?,
        time: kotlinx.datetime.LocalTime?,
        rule: RecurrenceRule,
        labels: List<LabelTag>,
        startDate: LocalDate,
        end: RecurrenceEnd,
    ): TaskSeries {
        error("not used in this test")
    }

    override suspend fun markMaterialized(seriesId: String, until: LocalDate): Boolean {
        markedUntil[seriesId] = until
        val index = series.indexOfFirst { it.id == seriesId }
        if (index >= 0) {
            series[index] = series[index].copy(materializedUntil = until)
        }
        return true
    }

    override suspend fun deactivateSeries(seriesId: String): Boolean {
        deactivatedIds.add(seriesId)
        val index = series.indexOfFirst { it.id == seriesId }
        if (index >= 0) {
            series[index] = series[index].copy(isActive = false)
        }
        return true
    }

    override suspend fun deleteSeries(id: String): Boolean {
        series.removeAll { it.id == id }
        return true
    }
}

private class FakeAgendaTaskRepositoryForMaterializer : AgendaTaskRepository {
    val createdDrafts = mutableListOf<Pair<LocalDate, TaskDraft>>()
    var failAfter: Int = Int.MAX_VALUE

    override suspend fun fetchTasks(date: LocalDate): List<TaskItem> = emptyList()

    override suspend fun fetchTasksInRange(from: LocalDate, to: LocalDate): Map<LocalDate, List<TaskItem>> {
        error("not used in this test")
    }

    override suspend fun createTask(date: LocalDate, draft: TaskDraft): TaskItem {
        if (createdDrafts.size >= failAfter) {
            throw RuntimeException("simulated failure")
        }
        createdDrafts.add(date to draft)
        return TaskItem(
            id = "task-${createdDrafts.size}",
            title = draft.title,
            details = draft.details,
            time = draft.time,
            labels = draft.labels,
            seriesId = draft.seriesId,
        )
    }

    override suspend fun updateTaskDone(id: String, isDone: Boolean): TaskItem {
        error("not used in this test")
    }

    override suspend fun deleteTask(id: String): Boolean = true

    override suspend fun deleteAllTasks(): Boolean = true
}

class SeriesMaterializerTest {
    private val today = LocalDate(2026, 8, 1)

    private fun dailySeries(materializedUntil: LocalDate, end: RecurrenceEnd = RecurrenceEnd.Never) = TaskSeries(
        id = "series-1",
        title = "Tomar vitaminas",
        details = null,
        time = null,
        rule = RecurrenceRule.Daily,
        labelIds = emptyList(),
        startDate = LocalDate(2026, 8, 1),
        isActive = true,
        materializedUntil = materializedUntil,
        end = end,
    )

    @Test
    fun `materializeSeries creates one task per occurrence and advances the cursor`() = runTest {
        val series = dailySeries(materializedUntil = LocalDate(2026, 7, 31))
        val seriesRepo = FakeTaskSeriesRepositoryForMaterializer(listOf(series))
        val taskRepo = FakeAgendaTaskRepositoryForMaterializer()
        val materializer = SeriesMaterializer(seriesRepo, taskRepo, horizonWeeks = 1)

        val success = materializer.materializeSeries(series, today)

        assertTrue(success)
        // Horizonte de 1 semana desde "today": 2026-08-01 al 2026-08-08 inclusive = 8 dias.
        assertEquals(8, taskRepo.createdDrafts.size)
        assertTrue(taskRepo.createdDrafts.all { (_, draft) -> draft.seriesId == series.id })
        assertEquals(LocalDate(2026, 8, 8), seriesRepo.markedUntil[series.id])
    }

    @Test
    fun `materializeSeries does not advance the cursor when a creation fails`() = runTest {
        val series = dailySeries(materializedUntil = LocalDate(2026, 7, 31))
        val seriesRepo = FakeTaskSeriesRepositoryForMaterializer(listOf(series))
        val taskRepo = FakeAgendaTaskRepositoryForMaterializer()
        taskRepo.failAfter = 2
        val materializer = SeriesMaterializer(seriesRepo, taskRepo, horizonWeeks = 1)

        val success = materializer.materializeSeries(series, today)

        assertEquals(false, success)
        assertEquals(2, taskRepo.createdDrafts.size)
        assertEquals(null, seriesRepo.markedUntil[series.id])
    }

    @Test
    fun `materializeSeries does nothing when already materialized past the horizon`() = runTest {
        val series = dailySeries(materializedUntil = LocalDate(2026, 12, 31))
        val seriesRepo = FakeTaskSeriesRepositoryForMaterializer(listOf(series))
        val taskRepo = FakeAgendaTaskRepositoryForMaterializer()
        val materializer = SeriesMaterializer(seriesRepo, taskRepo, horizonWeeks = 8)

        val success = materializer.materializeSeries(series, today)

        assertTrue(success)
        assertEquals(0, taskRepo.createdDrafts.size)
    }

    @Test
    fun `materializeAll processes every active series`() = runTest {
        val seriesA = dailySeries(materializedUntil = LocalDate(2026, 7, 31)).copy(id = "series-a")
        val seriesB = dailySeries(materializedUntil = LocalDate(2026, 7, 31)).copy(id = "series-b")
        val seriesRepo = FakeTaskSeriesRepositoryForMaterializer(listOf(seriesA, seriesB))
        val taskRepo = FakeAgendaTaskRepositoryForMaterializer()
        val materializer = SeriesMaterializer(seriesRepo, taskRepo, horizonWeeks = 1)

        materializer.materializeAll(today)

        assertEquals(16, taskRepo.createdDrafts.size)
        assertEquals(LocalDate(2026, 8, 8), seriesRepo.markedUntil["series-a"])
        assertEquals(LocalDate(2026, 8, 8), seriesRepo.markedUntil["series-b"])
    }

    @Test
    fun `materializeSeries deactivates the series once its end date is reached`() = runTest {
        val series = dailySeries(
            materializedUntil = LocalDate(2026, 7, 31),
            end = RecurrenceEnd.OnDate(LocalDate(2026, 8, 3)),
        )
        val seriesRepo = FakeTaskSeriesRepositoryForMaterializer(listOf(series))
        val taskRepo = FakeAgendaTaskRepositoryForMaterializer()
        val materializer = SeriesMaterializer(seriesRepo, taskRepo, horizonWeeks = 1)

        val success = materializer.materializeSeries(series, today)

        assertTrue(success)
        // 1, 2 y 3 de agosto - se detiene en la fecha de fin aunque el horizonte llegaria al 8.
        assertEquals(3, taskRepo.createdDrafts.size)
        assertEquals(LocalDate(2026, 8, 3), seriesRepo.markedUntil[series.id])
        assertEquals(listOf(series.id), seriesRepo.deactivatedIds)
    }

    @Test
    fun `materializeSeries deactivates the series once its occurrence count is reached`() = runTest {
        val series = dailySeries(
            materializedUntil = LocalDate(2026, 7, 31),
            end = RecurrenceEnd.AfterOccurrences(3),
        )
        val seriesRepo = FakeTaskSeriesRepositoryForMaterializer(listOf(series))
        val taskRepo = FakeAgendaTaskRepositoryForMaterializer()
        val materializer = SeriesMaterializer(seriesRepo, taskRepo, horizonWeeks = 1)

        val success = materializer.materializeSeries(series, today)

        assertTrue(success)
        assertEquals(3, taskRepo.createdDrafts.size)
        assertEquals(listOf(series.id), seriesRepo.deactivatedIds)
    }

    @Test
    fun `materializeSeries does not deactivate a never-ending series`() = runTest {
        val series = dailySeries(materializedUntil = LocalDate(2026, 7, 31), end = RecurrenceEnd.Never)
        val seriesRepo = FakeTaskSeriesRepositoryForMaterializer(listOf(series))
        val taskRepo = FakeAgendaTaskRepositoryForMaterializer()
        val materializer = SeriesMaterializer(seriesRepo, taskRepo, horizonWeeks = 1)

        materializer.materializeSeries(series, today)

        assertEquals(emptyList(), seriesRepo.deactivatedIds)
    }
}
