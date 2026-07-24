package com.franciscor.agendnote.feature.agenda.domain

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

sealed interface RecurrenceRule {
    data object Daily : RecurrenceRule

    data class WeeklyDays(val days: Set<DayOfWeek>) : RecurrenceRule

    data class Monthly(val dayOfMonth: Int) : RecurrenceRule
}

/**
 * Todas las fechas entre [from] y [to] (ambos inclusive) que caen dentro de [rule].
 * Lista vacia si [from] es posterior a [to].
 */
fun occurrencesBetween(rule: RecurrenceRule, from: LocalDate, to: LocalDate): List<LocalDate> {
    if (from > to) return emptyList()
    val dates = mutableListOf<LocalDate>()
    var current = from
    while (current <= to) {
        if (matchesRule(rule, current)) {
            dates.add(current)
        }
        current = current.plus(1, DateTimeUnit.DAY)
    }
    return dates
}

private fun matchesRule(rule: RecurrenceRule, date: LocalDate): Boolean {
    return when (rule) {
        is RecurrenceRule.Daily -> true
        is RecurrenceRule.WeeklyDays -> rule.days.contains(date.dayOfWeek)
        is RecurrenceRule.Monthly -> date.dayOfMonth == effectiveDayOfMonth(rule.dayOfMonth, date)
    }
}

/** Si [requestedDay] no existe en el mes de [date] (p. ej. 31 en abril), usa el ultimo dia del mes. */
private fun effectiveDayOfMonth(requestedDay: Int, date: LocalDate): Int {
    val lastDayOfMonth = LocalDate(date.year, date.monthNumber, 1)
        .plus(1, DateTimeUnit.MONTH)
        .minus(1, DateTimeUnit.DAY)
        .dayOfMonth
    return requestedDay.coerceAtMost(lastDayOfMonth)
}
