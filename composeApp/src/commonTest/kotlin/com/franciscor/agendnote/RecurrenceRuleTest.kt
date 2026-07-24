package com.franciscor.agendnote

import com.franciscor.agendnote.feature.agenda.domain.RecurrenceRule
import com.franciscor.agendnote.feature.agenda.domain.occurrencesBetween
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals

class RecurrenceRuleTest {
    @Test
    fun `daily rule includes every date in range`() {
        val from = LocalDate(2026, 8, 1)
        val to = LocalDate(2026, 8, 5)

        val result = occurrencesBetween(RecurrenceRule.Daily, from, to)

        assertEquals(
            listOf(
                LocalDate(2026, 8, 1),
                LocalDate(2026, 8, 2),
                LocalDate(2026, 8, 3),
                LocalDate(2026, 8, 4),
                LocalDate(2026, 8, 5),
            ),
            result,
        )
    }

    @Test
    fun `weekly days rule only includes matching weekdays`() {
        val from = LocalDate(2026, 8, 1)
        val to = LocalDate(2026, 8, 31)
        val targetDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
        val rule = RecurrenceRule.WeeklyDays(targetDays)

        val result = occurrencesBetween(rule, from, to)

        // Recomputado de forma independiente (no reutiliza la implementación de occurrencesBetween)
        // para seguir siendo una verificación real, no una tautología.
        val expected = generateSequence(from) { it.plus(1, DateTimeUnit.DAY) }
            .takeWhile { it <= to }
            .filter { it.dayOfWeek in targetDays }
            .toList()
        assertEquals(expected, result)
        assertEquals(true, result.isNotEmpty())
        assertEquals(true, result.all { it.dayOfWeek in targetDays })
    }

    @Test
    fun `weekly days rule with a single day behaves as a weekly repeat`() {
        val from = LocalDate(2026, 8, 1)
        val to = LocalDate(2026, 8, 31)
        val rule = RecurrenceRule.WeeklyDays(setOf(DayOfWeek.TUESDAY))

        val result = occurrencesBetween(rule, from, to)

        val expected = generateSequence(from) { it.plus(1, DateTimeUnit.DAY) }
            .takeWhile { it <= to }
            .filter { it.dayOfWeek == DayOfWeek.TUESDAY }
            .toList()
        assertEquals(expected, result)
        // Un solo día por semana en un rango de 31 dias cae 4 o 5 veces.
        assertEquals(true, result.size in 4..5)
    }

    @Test
    fun `monthly rule matches the requested day of month`() {
        val from = LocalDate(2026, 6, 1)
        val to = LocalDate(2026, 9, 30)
        val rule = RecurrenceRule.Monthly(dayOfMonth = 15)

        val result = occurrencesBetween(rule, from, to)

        assertEquals(
            listOf(
                LocalDate(2026, 6, 15),
                LocalDate(2026, 7, 15),
                LocalDate(2026, 8, 15),
                LocalDate(2026, 9, 15),
            ),
            result,
        )
    }

    @Test
    fun `monthly rule clamps to the last day when the month is shorter`() {
        // Dia 31 solicitado; abril y junio solo tienen 30 dias (hecho de calendario estable,
        // no depende del ano).
        val from = LocalDate(2026, 3, 1)
        val to = LocalDate(2026, 6, 30)
        val rule = RecurrenceRule.Monthly(dayOfMonth = 31)

        val result = occurrencesBetween(rule, from, to)

        assertEquals(
            listOf(
                LocalDate(2026, 3, 31),
                LocalDate(2026, 4, 30),
                LocalDate(2026, 5, 31),
                LocalDate(2026, 6, 30),
            ),
            result,
        )
    }

    @Test
    fun `empty range when from is after to`() {
        val result = occurrencesBetween(RecurrenceRule.Daily, LocalDate(2026, 8, 10), LocalDate(2026, 8, 1))

        assertEquals(emptyList(), result)
    }

    @Test
    fun `single day range includes that day when it matches`() {
        val date = LocalDate(2026, 8, 1)

        val result = occurrencesBetween(RecurrenceRule.Daily, date, date)

        assertEquals(listOf(date), result)
    }
}
