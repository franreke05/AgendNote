package com.franciscor.agendnote.core.nlp

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus

/**
 * Result of [parseQuickCapture]: [title] is the input with every recognized date/time phrase
 * removed, [date]/[time] are what was recognized (`null` when nothing was).
 */
data class QuickCaptureResult(
    val title: String,
    val date: LocalDate?,
    val time: LocalTime?,
)

/**
 * A curated set of Spanish date/time phrases, not general-purpose NLU (see
 * docs/agendnote/IMPLEMENTATION_PLAN.md, Fase 6). Recognizes, at most one date phrase and one
 * time phrase per call:
 * - `hoy`, `mañana`/`manana`, `pasado mañana`/`pasado manana`.
 * - a weekday name (`lunes`..`domingo`, with or without the accent on `miércoles`/`sábado`),
 *   optionally preceded by an article (`el`, `este`, `esta`, `próximo`, `próxima`...). Always
 *   resolves to the *next* occurrence strictly after [today] - if today is that weekday, it
 *   resolves to next week, never to today itself. This is a documented, deliberate choice, not
 *   an accident: "el martes" said out loud is ambiguous about whether today counts, and
 *   "always the next one, never today" is the least surprising single rule.
 * - `en N días`/`dias` (today + N days).
 * - a time phrase, always introduced by `a las`/`a la` (e.g. `a las 17:30`, `a la 1`), optionally
 *   followed by `de la mañana`/`de la tarde`/`de la noche` to disambiguate a 12-hour hour into
 *   24-hour time. A bare number without `a las`/`a la` is never treated as a time - that would
 *   misfire on titles like "Comprar 2 entradas".
 *
 * Callers decide what to do with the result - this function never mutates any state and never
 * applies anything automatically (see `NewTaskSheet`'s "Aplicar sugerencia" affordance).
 */
fun parseQuickCapture(input: String, today: LocalDate): QuickCaptureResult {
    var remaining = input

    var time: LocalTime? = null
    TIME_REGEX.find(remaining)?.let { match ->
        val hour = match.groupValues[1].toIntOrNull()
        val minute = match.groupValues[2].toIntOrNull() ?: 0
        val period = match.groupValues[3].lowercase()
        if (hour != null && hour in 0..23 && minute in 0..59) {
            val resolvedHour = if (period in setOf("tarde", "noche") && hour in 1..11) {
                hour + 12
            } else {
                hour
            }
            time = LocalTime(resolvedHour.coerceIn(0, 23), minute)
            remaining = remaining.removeRange(match.range)
        }
    }

    val tokens = remaining.trim().split(WHITESPACE_REGEX).filter { it.isNotEmpty() }
    val consumed = BooleanArray(tokens.size)
    var date: LocalDate? = null

    // "pasado mañana" es una frase de dos tokens - se busca antes que "mañana" sola para que
    // esta ultima no la capture a medias.
    for (i in 0 until (tokens.size - 1).coerceAtLeast(0)) {
        val first = normalize(tokens[i])
        val second = normalize(tokens[i + 1])
        if (first == "pasado" && (second == "mañana" || second == "manana")) {
            date = today.plus(2, DateTimeUnit.DAY)
            consumed[i] = true
            consumed[i + 1] = true
            break
        }
    }

    if (date == null) {
        for (i in tokens.indices) {
            if (consumed[i]) continue
            val word = normalize(tokens[i])
            when {
                word == "hoy" -> {
                    date = today
                    consumed[i] = true
                }

                word == "mañana" || word == "manana" -> {
                    date = today.plus(1, DateTimeUnit.DAY)
                    consumed[i] = true
                }

                word in WEEKDAY_NAMES -> {
                    date = nextStrictlyFutureWeekday(today, WEEKDAY_NAMES.getValue(word))
                    consumed[i] = true
                    if (i > 0 && !consumed[i - 1] && normalize(tokens[i - 1]) in WEEKDAY_ARTICLES) {
                        consumed[i - 1] = true
                    }
                }

                word == "dias" || word == "días" -> {
                    if (i >= 2 && !consumed[i - 1] && !consumed[i - 2]) {
                        val count = tokens[i - 1].toIntOrNull()
                        if (count != null && count > 0 && normalize(tokens[i - 2]) == "en") {
                            date = today.plus(count, DateTimeUnit.DAY)
                            consumed[i] = true
                            consumed[i - 1] = true
                            consumed[i - 2] = true
                        }
                    }
                }
            }
            if (date != null) break
        }
    }

    val title = tokens.filterIndexed { index, _ -> !consumed[index] }.joinToString(" ").trim()
    return QuickCaptureResult(title = title, date = date, time = time)
}

private val WHITESPACE_REGEX = Regex("\\s+")

private val TIME_REGEX = Regex(
    "\\ba las? (\\d{1,2})(?::(\\d{2}))?(?:\\s+de la (mañana|tarde|noche))?",
    RegexOption.IGNORE_CASE,
)

private val WEEKDAY_NAMES: Map<String, DayOfWeek> = mapOf(
    "lunes" to DayOfWeek.MONDAY,
    "martes" to DayOfWeek.TUESDAY,
    "miercoles" to DayOfWeek.WEDNESDAY,
    "miércoles" to DayOfWeek.WEDNESDAY,
    "jueves" to DayOfWeek.THURSDAY,
    "viernes" to DayOfWeek.FRIDAY,
    "sabado" to DayOfWeek.SATURDAY,
    "sábado" to DayOfWeek.SATURDAY,
    "domingo" to DayOfWeek.SUNDAY,
)

private val WEEKDAY_ARTICLES = setOf("el", "este", "esta", "proximo", "próximo", "proxima", "próxima")

private fun normalize(token: String): String {
    return token.lowercase().trim { !it.isLetter() && !it.isDigit() }
}

private fun nextStrictlyFutureWeekday(from: LocalDate, target: DayOfWeek): LocalDate {
    var candidate = from.plus(1, DateTimeUnit.DAY)
    while (candidate.dayOfWeek != target) {
        candidate = candidate.plus(1, DateTimeUnit.DAY)
    }
    return candidate
}
