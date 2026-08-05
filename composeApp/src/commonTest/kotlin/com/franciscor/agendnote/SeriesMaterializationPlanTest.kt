package com.franciscor.agendnote

import com.franciscor.agendnote.feature.agenda.domain.RecurrenceEnd
import com.franciscor.agendnote.feature.agenda.domain.RecurrenceRule
import com.franciscor.agendnote.feature.agenda.domain.planMaterialization
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SeriesMaterializationPlanTest {
    private val seriesStart = LocalDate(2026, 1, 1)

    @Test
    fun `never-ending series materializes the whole window and never deactivates`() {
        val plan = planMaterialization(
            rule = RecurrenceRule.Daily,
            seriesStartDate = seriesStart,
            currentMaterializedUntil = LocalDate(2026, 3, 1),
            end = RecurrenceEnd.Never,
            from = LocalDate(2026, 3, 2),
            horizonEnd = LocalDate(2026, 3, 10),
        )

        assertEquals(9, plan.datesToCreate.size)
        assertEquals(LocalDate(2026, 3, 10), plan.materializedUntil)
        assertFalse(plan.shouldDeactivate)
    }

    @Test
    fun `end date within the horizon caps the batch and deactivates`() {
        val plan = planMaterialization(
            rule = RecurrenceRule.Daily,
            seriesStartDate = seriesStart,
            currentMaterializedUntil = LocalDate(2026, 3, 1),
            end = RecurrenceEnd.OnDate(LocalDate(2026, 3, 5)),
            from = LocalDate(2026, 3, 2),
            horizonEnd = LocalDate(2026, 3, 10),
        )

        assertEquals(
            listOf(
                LocalDate(2026, 3, 2),
                LocalDate(2026, 3, 3),
                LocalDate(2026, 3, 4),
                LocalDate(2026, 3, 5),
            ),
            plan.datesToCreate,
        )
        assertEquals(LocalDate(2026, 3, 5), plan.materializedUntil)
        assertTrue(plan.shouldDeactivate)
    }

    @Test
    fun `end date beyond the horizon does not deactivate yet`() {
        val plan = planMaterialization(
            rule = RecurrenceRule.Daily,
            seriesStartDate = seriesStart,
            currentMaterializedUntil = LocalDate(2026, 3, 1),
            end = RecurrenceEnd.OnDate(LocalDate(2026, 6, 1)),
            from = LocalDate(2026, 3, 2),
            horizonEnd = LocalDate(2026, 3, 10),
        )

        assertEquals(9, plan.datesToCreate.size)
        assertEquals(LocalDate(2026, 3, 10), plan.materializedUntil)
        assertFalse(plan.shouldDeactivate)
    }

    @Test
    fun `already past the end date with nothing left to do still signals deactivate`() {
        // Defensivo: cubre el caso de una llamada anterior que genero hasta la fecha de fin
        // pero fallo al desactivar la serie (markMaterialized funciono, deactivateSeries no).
        val plan = planMaterialization(
            rule = RecurrenceRule.Daily,
            seriesStartDate = seriesStart,
            currentMaterializedUntil = LocalDate(2026, 3, 5),
            end = RecurrenceEnd.OnDate(LocalDate(2026, 3, 5)),
            from = LocalDate(2026, 3, 6),
            horizonEnd = LocalDate(2026, 3, 20),
        )

        assertEquals(emptyList(), plan.datesToCreate)
        assertTrue(plan.shouldDeactivate)
    }

    @Test
    fun `after-occurrences end below the batch size truncates and deactivates`() {
        val plan = planMaterialization(
            rule = RecurrenceRule.Daily,
            seriesStartDate = seriesStart,
            currentMaterializedUntil = seriesStart.minusOneDay(),
            end = RecurrenceEnd.AfterOccurrences(3),
            from = seriesStart,
            horizonEnd = LocalDate(2026, 3, 10),
        )

        assertEquals(
            listOf(LocalDate(2026, 1, 1), LocalDate(2026, 1, 2), LocalDate(2026, 1, 3)),
            plan.datesToCreate,
        )
        assertTrue(plan.shouldDeactivate)
    }

    @Test
    fun `after-occurrences counts what was already materialized in earlier batches`() {
        // La serie ya genero 8 apariciones diarias (1 al 8 de enero) en una pasada anterior;
        // el limite es 10, asi que solo quedan 2 mas.
        val plan = planMaterialization(
            rule = RecurrenceRule.Daily,
            seriesStartDate = seriesStart,
            currentMaterializedUntil = LocalDate(2026, 1, 8),
            end = RecurrenceEnd.AfterOccurrences(10),
            from = LocalDate(2026, 1, 9),
            horizonEnd = LocalDate(2026, 1, 20),
        )

        assertEquals(listOf(LocalDate(2026, 1, 9), LocalDate(2026, 1, 10)), plan.datesToCreate)
        assertTrue(plan.shouldDeactivate)
    }

    @Test
    fun `after-occurrences with room to spare in this batch does not deactivate yet`() {
        val plan = planMaterialization(
            rule = RecurrenceRule.Daily,
            seriesStartDate = seriesStart,
            currentMaterializedUntil = seriesStart.minusOneDay(),
            end = RecurrenceEnd.AfterOccurrences(100),
            from = seriesStart,
            horizonEnd = LocalDate(2026, 1, 10),
        )

        assertEquals(10, plan.datesToCreate.size)
        assertFalse(plan.shouldDeactivate)
    }

    private fun LocalDate.minusOneDay(): LocalDate {
        return kotlinx.datetime.LocalDate.fromEpochDays(this.toEpochDays() - 1)
    }
}
