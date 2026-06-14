package com.misturnos.model

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

/** Un tramo de trabajo continuo, p. ej. 13:30 - 17:00. */
data class ShiftSegment(
    val start: LocalTime,
    val end: LocalTime,
) {
    /** Duración del tramo; soporta turnos que cruzan medianoche. */
    val duration: Duration
        get() = if (!end.isBefore(start)) {
            Duration.between(start, end)
        } else {
            Duration.between(start, end).plusDays(1)
        }
}

enum class DayType {
    /** Día con uno o más tramos de trabajo. */
    WORK,

    /** "Día libre". */
    DAY_OFF,

    /** "Sin asignaciones" / "Sin tienda asignada". */
    UNASSIGNED,
}

/** Los turnos de un día concreto. */
data class DayShift(
    val date: LocalDate,
    val dayType: DayType,
    val segments: List<ShiftSegment> = emptyList(),
    val location: String? = null,
) {
    /** Total de horas trabajadas ese día (suma de los tramos). */
    val totalHours: Double
        get() = segments.sumOf { it.duration.toMinutes() } / 60.0
}

/** El horario de una semana (lunes a domingo) tal como aparece en una captura. */
data class WeekSchedule(
    val start: LocalDate,
    val end: LocalDate,
    val days: List<DayShift>,
) {
    val totalHours: Double
        get() = days.sumOf { it.totalHours }

    val workedDays: Int
        get() = days.count { it.dayType == DayType.WORK }
}
