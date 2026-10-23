package com.example.routineapp.ui

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

object Formats {
    val PT_BR: Locale = Locale.forLanguageTag("pt-BR")
    private val dayMonth = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", PT_BR)
    private val dayMonthYear = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM 'de' yyyy", PT_BR)

    /** 9h, 9h30, 14h05. */
    fun time(t: LocalTime): String = if (t.minute == 0) "${t.hour}h" else "${t.hour}h%02d".format(t.minute)

    fun time(minuteOfDay: Int): String = time(LocalTime.of(minuteOfDay / 60, minuteOfDay % 60))

    /** "sexta-feira, 23 de outubro" (+ year when not the current one). */
    fun date(d: LocalDate, today: LocalDate = LocalDate.now()): String =
        (if (d.year == today.year) dayMonth else dayMonthYear).format(d)

    /** "hoje", "amanhã" or [date]. */
    fun relativeDate(d: LocalDate, today: LocalDate = LocalDate.now()): String = when (d) {
        today -> "hoje (${date(d, today)})"
        today.plusDays(1) -> "amanhã (${date(d, today)})"
        else -> date(d, today)
    }

    fun weekdayShort(d: LocalDate): String =
        d.dayOfWeek.getDisplayName(TextStyle.SHORT, PT_BR).trimEnd('.').uppercase(PT_BR)

    fun monthYear(d: LocalDate): String =
        d.month.getDisplayName(TextStyle.FULL_STANDALONE, PT_BR).replaceFirstChar { it.uppercase() } + " de ${d.year}"

    fun duration(minutes: Int): String = when {
        minutes < 60 -> "$minutes min"
        minutes % 60 == 0 -> "${minutes / 60}h"
        else -> "${minutes / 60}h${"%02d".format(minutes % 60)}"
    }
}
