package com.misturnos.util

import com.misturnos.model.DayShift
import com.misturnos.model.DayType
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Formateo de turnos compartido entre la pantalla principal y el widget. */
object ShiftFormat {

    val ES: Locale = Locale("es", "ES")
    private val dayFmt = DateTimeFormatter.ofPattern("d MMM", ES)

    fun hours(hours: Double): String {
        val h = hours.toInt()
        val m = ((hours - h) * 60).toInt()
        return if (m == 0) "${h}h" else "${h}h ${m}m"
    }

    fun rangeLabel(start: java.time.LocalDate, end: java.time.LocalDate): String =
        "${start.format(dayFmt)} - ${end.format(dayFmt)}"

    fun dayLabel(day: DayShift): String {
        val name = day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, ES)
            .replaceFirstChar { it.uppercase() }
        return "$name ${day.date.dayOfMonth}"
    }

    fun dayDetail(day: DayShift): String = when (day.dayType) {
        DayType.WORK -> day.segments.joinToString(" / ") { "${it.start} - ${it.end}" }
        DayType.DAY_OFF -> "Día libre"
        DayType.UNASSIGNED -> "Sin asignaciones"
    }
}
