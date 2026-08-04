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

    // --- Casos de Fase 5: fin de mes, ano bisiesto, cruce de ano y DST -----------------------
    // occurrencesBetween opera solo sobre LocalDate (sin Instant/TimeZone), asi que no deberia
    // existir superficie de bug de DST en absoluto - estos tests lo dejan verificado en vez de
    // asumido, tal como pide el prompt maestro para la logica de recurrencia.

    @Test
    fun `monthly rule on day 29 clamps to 28 in a non-leap February`() {
        // 2026 no es bisiesto: febrero tiene 28 dias.
        val result = occurrencesBetween(
            RecurrenceRule.Monthly(dayOfMonth = 29),
            LocalDate(2026, 1, 1),
            LocalDate(2026, 3, 31),
        )

        assertEquals(
            listOf(LocalDate(2026, 1, 29), LocalDate(2026, 2, 28), LocalDate(2026, 3, 29)),
            result,
        )
    }

    @Test
    fun `monthly rule on day 29 lands on day 29 in a leap February`() {
        // 2028 es bisiesto: febrero tiene 29 dias, no hace falta clamp.
        val result = occurrencesBetween(
            RecurrenceRule.Monthly(dayOfMonth = 29),
            LocalDate(2028, 1, 1),
            LocalDate(2028, 3, 31),
        )

        assertEquals(
            listOf(LocalDate(2028, 1, 29), LocalDate(2028, 2, 29), LocalDate(2028, 3, 29)),
            result,
        )
    }

    @Test
    fun `monthly rule crosses the year boundary without gaps or duplicates`() {
        val result = occurrencesBetween(
            RecurrenceRule.Monthly(dayOfMonth = 31),
            LocalDate(2026, 11, 1),
            LocalDate(2027, 2, 28),
        )

        // Noviembre (30 dias, clamp a 30), diciembre (31), enero (31), febrero 2027 (28, clamp).
        assertEquals(
            listOf(
                LocalDate(2026, 11, 30),
                LocalDate(2026, 12, 31),
                LocalDate(2027, 1, 31),
                LocalDate(2027, 2, 28),
            ),
            result,
        )
    }

    @Test
    fun `daily rule crossing Spain's spring-forward DST date still yields one date per calendar day`() {
        // 2026-03-29 es el cambio de horario en Espana (adelanto). occurrencesBetween no toca
        // Instant/TimeZone, asi que este rango debe verse identico a cualquier otro de 4 dias.
        val from = LocalDate(2026, 3, 27)
        val to = LocalDate(2026, 3, 30)

        val result = occurrencesBetween(RecurrenceRule.Daily, from, to)

        assertEquals(
            listOf(
                LocalDate(2026, 3, 27),
                LocalDate(2026, 3, 28),
                LocalDate(2026, 3, 29),
                LocalDate(2026, 3, 30),
            ),
            result,
        )
    }

    @Test
    fun `daily rule crossing Spain's fall-back DST date still yields one date per calendar day`() {
        // 2026-10-25 es el cambio de horario en Espana (atraso).
        val from = LocalDate(2026, 10, 24)
        val to = LocalDate(2026, 10, 26)

        val result = occurrencesBetween(RecurrenceRule.Daily, from, to)

        assertEquals(
            listOf(LocalDate(2026, 10, 24), LocalDate(2026, 10, 25), LocalDate(2026, 10, 26)),
            result,
        )
    }
}
