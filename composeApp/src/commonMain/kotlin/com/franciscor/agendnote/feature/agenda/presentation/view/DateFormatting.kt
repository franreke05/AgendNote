package com.franciscor.agendnote.feature.agenda.presentation.view

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.Month

/**
 * Small Spanish-language date/time formatting helpers shared by the agenda day list
 * (AgendaDayComponents.kt) and its overlays (AgendaOverlays.kt: date/time/calendar pickers).
 *
 * These used to be copy-pasted verbatim (`formatTime`, `monthName`) or near-duplicated
 * (`dayName`/`daysInMonth`/`isLeapYear` living only where they happened to be needed first)
 * across both files. Centralizing them here means both files stay in sync automatically and
 * there is a single place to add, say, a real i18n lookup later.
 *
 * `internal` (not `private`) so both call sites in this package can use them without an import.
 */
internal fun formatTime(time: LocalTime): String {
    val hour = time.hour.toString().padStart(2, '0')
    val minute = time.minute.toString().padStart(2, '0')
    return "$hour:$minute"
}

internal fun formatFullDate(date: LocalDate): String {
    return "${dayName(date.dayOfWeek)}, ${date.dayOfMonth} de ${monthName(date.month)}"
}

internal fun dayName(day: DayOfWeek): String = when (day) {
    DayOfWeek.MONDAY -> "Lunes"
    DayOfWeek.TUESDAY -> "Martes"
    DayOfWeek.WEDNESDAY -> "Miércoles"
    DayOfWeek.THURSDAY -> "Jueves"
    DayOfWeek.FRIDAY -> "Viernes"
    DayOfWeek.SATURDAY -> "Sábado"
    DayOfWeek.SUNDAY -> "Domingo"
}

internal fun monthName(month: Month, short: Boolean = false): String = when (month) {
    Month.JANUARY -> if (short) "ene" else "enero"
    Month.FEBRUARY -> if (short) "feb" else "febrero"
    Month.MARCH -> if (short) "mar" else "marzo"
    Month.APRIL -> if (short) "abr" else "abril"
    Month.MAY -> if (short) "may" else "mayo"
    Month.JUNE -> if (short) "jun" else "junio"
    Month.JULY -> if (short) "jul" else "julio"
    Month.AUGUST -> if (short) "ago" else "agosto"
    Month.SEPTEMBER -> if (short) "sep" else "septiembre"
    Month.OCTOBER -> if (short) "oct" else "octubre"
    Month.NOVEMBER -> if (short) "nov" else "noviembre"
    Month.DECEMBER -> if (short) "dic" else "diciembre"
}

internal fun daysInMonth(year: Int, month: Month): Int = when (month) {
    Month.JANUARY -> 31
    Month.FEBRUARY -> if (isLeapYear(year)) 29 else 28
    Month.MARCH -> 31
    Month.APRIL -> 30
    Month.MAY -> 31
    Month.JUNE -> 30
    Month.JULY -> 31
    Month.AUGUST -> 31
    Month.SEPTEMBER -> 30
    Month.OCTOBER -> 31
    Month.NOVEMBER -> 30
    Month.DECEMBER -> 31
}

internal fun isLeapYear(year: Int): Boolean {
    return year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
}
