package com.franciscor.agendnote

import com.franciscor.agendnote.core.nlp.parseQuickCapture
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class QuickCaptureParserTest {
    // 2026-08-04 es un martes.
    private val today = LocalDate(2026, 8, 4)

    @Test
    fun `plain text without any recognized phrase leaves title untouched and no date or time`() {
        val result = parseQuickCapture("Comprar leche", today)

        assertEquals("Comprar leche", result.title)
        assertNull(result.date)
        assertNull(result.time)
    }

    @Test
    fun `hoy resolves to today and is removed from the title`() {
        val result = parseQuickCapture("Llamar al banco hoy", today)

        assertEquals("Llamar al banco", result.title)
        assertEquals(today, result.date)
    }

    @Test
    fun `manana resolves to tomorrow, with or without the accent`() {
        assertEquals(LocalDate(2026, 8, 5), parseQuickCapture("Dentista mañana", today).date)
        assertEquals(LocalDate(2026, 8, 5), parseQuickCapture("Dentista manana", today).date)
    }

    @Test
    fun `pasado manana resolves to the day after tomorrow as a single phrase`() {
        val result = parseQuickCapture("Entregar informe pasado mañana", today)

        assertEquals("Entregar informe", result.title)
        assertEquals(LocalDate(2026, 8, 6), result.date)
    }

    @Test
    fun `en N dias resolves to today plus N days`() {
        val result = parseQuickCapture("Renovar el DNI en 10 días", today)

        assertEquals("Renovar el DNI", result.title)
        assertEquals(LocalDate(2026, 8, 14), result.date)
    }

    @Test
    fun `a weekday name resolves to its next strictly-future occurrence`() {
        // Hoy es martes 2026-08-04. "El viernes" cae ese mismo martes+3 = 2026-08-07.
        val result = parseQuickCapture("Reunión el viernes", today)

        assertEquals("Reunión", result.title)
        assertEquals(LocalDate(2026, 8, 7), result.date)
    }

    @Test
    fun `a weekday matching today resolves to next week, never today`() {
        // Hoy es martes; "el martes" nunca significa "hoy" en este parser (regla documentada).
        val result = parseQuickCapture("Pago el martes", today)

        assertEquals(LocalDate(2026, 8, 11), result.date)
    }

    @Test
    fun `a las HH colon MM sets the time and is removed from the title`() {
        val result = parseQuickCapture("Dentista mañana a las 17:30", today)

        assertEquals("Dentista", result.title)
        assertEquals(LocalDate(2026, 8, 5), result.date)
        assertEquals(LocalTime(17, 30), result.time)
    }

    @Test
    fun `a las H with de la tarde resolves to the 24-hour equivalent`() {
        val result = parseQuickCapture("Café a las 5 de la tarde", today)

        assertEquals("Café", result.title)
        assertEquals(LocalTime(17, 0), result.time)
    }

    @Test
    fun `a las H with de la manana keeps the morning hour as-is`() {
        val result = parseQuickCapture("Gimnasio a las 7 de la mañana", today)

        assertEquals(LocalTime(7, 0), result.time)
    }

    @Test
    fun `a plain number in the title without a las prefix is not mistaken for a time`() {
        val result = parseQuickCapture("Comprar 2 entradas", today)

        assertEquals("Comprar 2 entradas", result.title)
        assertNull(result.time)
    }

    @Test
    fun `title only keeps a single trimmed space after removing multiple phrases`() {
        val result = parseQuickCapture("Dentista   mañana   a las 17:30", today)

        assertEquals("Dentista", result.title)
    }
}
