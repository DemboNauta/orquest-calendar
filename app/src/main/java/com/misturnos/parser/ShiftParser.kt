package com.misturnos.parser

import com.misturnos.model.DayShift
import com.misturnos.model.DayType
import com.misturnos.model.ShiftSegment
import com.misturnos.model.WeekSchedule
import java.time.LocalDate
import java.time.LocalTime

/**
 * Convierte el texto reconocido (OCR) de la pantalla "Mis turnos" de Orquest en un [WeekSchedule].
 *
 * El parser es tolerante al orden y a pequeños fallos del OCR: trabaja línea a línea con una
 * máquina de estados que reconoce números de día, abreviaturas de día, tramos horarios y estados
 * ("Día libre" / "Sin asignaciones").
 */
class ShiftParser(
    /** Año por defecto si la captura no incluye uno (p. ej. cabecera "9 de junio de 2026"). */
    private val fallbackYear: Int = LocalDate.now().year,
) {

    private val timeRange = Regex("""(\d{1,2})[:.](\d{2})\s*[-–—]\s*(\d{1,2})[:.](\d{2})""")
    // "8 jun - 14 jun"  /  "29 jun - 5 jul"
    private val weekRange = Regex(
        """(\d{1,2})\s*(?:de\s+)?([A-Za-zÁÉÍÓÚáéíóúñ]{3,})\.?\s*[-–—]\s*(\d{1,2})\s*(?:de\s+)?([A-Za-zÁÉÍÓÚáéíóúñ]{3,})""",
    )
    // "9 de junio de 2026"  ->  año
    private val fullDateYear = Regex("""\bde\s+(\d{4})\b""")
    // Línea que es solo un número de día (1-31), opcionalmente seguido de la abreviatura del día.
    private val dayLine = Regex("""^(\d{1,2})\s*([A-Za-zÁÉÍÓÚáéíóúñ]{2,4})?\.?$""")

    fun parse(rawText: String): WeekSchedule? = parse(rawText.lines())

    fun parse(lines: List<String>): WeekSchedule? {
        val clean = lines.map { it.trim() }.filter { it.isNotEmpty() }
        if (clean.isEmpty()) return null

        val year = clean.firstNotNullOfOrNull { fullDateYear.find(it)?.groupValues?.get(1)?.toInt() }
            ?: fallbackYear

        val weekStart = findWeekStart(clean, year) ?: return null
        val dateForDay = buildDayToDate(weekStart)

        val days = parseDays(clean, dateForDay)
        if (days.isEmpty()) return null

        val sorted = days.sortedBy { it.date }
        return WeekSchedule(start = weekStart, end = weekStart.plusDays(6), days = sorted)
    }

    /** Localiza la cabecera de rango semanal ("8 jun - 14 jun") y devuelve el lunes de esa semana. */
    private fun findWeekStart(lines: List<String>, year: Int): LocalDate? {
        for (line in lines) {
            val m = weekRange.find(line) ?: continue
            val startDay = m.groupValues[1].toIntOrNull() ?: continue
            val startMonth = SpanishDates.monthNumber(m.groupValues[2]) ?: continue
            // Validamos que el segundo token sea también un mes para no confundir con otras frases.
            if (SpanishDates.monthNumber(m.groupValues[4]) == null) continue
            return runCatching { LocalDate.of(year, startMonth, startDay) }.getOrNull()
        }
        return null
    }

    /** Mapa día-del-mes -> fecha completa, recorriendo los 7 días de la semana (cubre cambios de mes). */
    private fun buildDayToDate(weekStart: LocalDate): Map<Int, LocalDate> =
        (0L..6L).associate { offset ->
            val d = weekStart.plusDays(offset)
            d.dayOfMonth to d
        }

    private fun parseDays(lines: List<String>, dateForDay: Map<Int, LocalDate>): List<DayShift> {
        // Empezamos a leer las tarjetas tras el rótulo "Tus turnos" para evitar la cabecera y la
        // tira del calendario (que también contienen números de día).
        val startIdx = lines.indexOfFirst { SpanishDates.normalize(it).startsWith("tus turnos") }
        val cards = if (startIdx >= 0) lines.drop(startIdx + 1) else lines

        val result = mutableListOf<DayShift>()
        var currentDay: Int? = null
        var dayType: DayType? = null
        val segments = mutableListOf<ShiftSegment>()
        var location: String? = null

        fun flush() {
            val day = currentDay ?: return
            val date = dateForDay[day] ?: return
            val type = when {
                segments.isNotEmpty() -> DayType.WORK
                dayType != null -> dayType!!
                else -> return // tarjeta sin contenido reconocible
            }
            result += DayShift(date, type, segments.toList(), location)
        }

        for (raw in cards) {
            val dayMatch = dayLine.matchEntire(raw)
            val asDay = dayMatch?.groupValues?.get(1)?.toIntOrNull()

            // Es un número de día válido y, si trae sufijo, es una abreviatura de día de la semana.
            val suffix = dayMatch?.groupValues?.get(2).orEmpty()
            val suffixOk = suffix.isEmpty() || SpanishDates.isWeekday(suffix)

            if (asDay != null && asDay in 1..31 && suffixOk && dateForDay.containsKey(asDay)) {
                flush()
                currentDay = asDay
                dayType = null
                segments.clear()
                location = null
                continue
            }

            if (currentDay == null) continue

            val times = timeRange.findAll(raw).toList()
            if (times.isNotEmpty()) {
                times.forEach { t ->
                    val s = safeTime(t.groupValues[1], t.groupValues[2])
                    val e = safeTime(t.groupValues[3], t.groupValues[4])
                    if (s != null && e != null) segments += ShiftSegment(s, e)
                }
                dayType = DayType.WORK
                continue
            }

            val norm = SpanishDates.normalize(raw)
            when {
                norm.startsWith("dia libre") -> dayType = DayType.DAY_OFF
                norm.startsWith("sin asignaciones") -> dayType = DayType.UNASSIGNED
                norm.startsWith("sin tienda") -> { /* subtítulo de UNASSIGNED, se ignora */ }
                // Subtítulo de ubicación tipo "Cuenca: General".
                raw.contains(":") && !raw.contains("-") -> location = raw.trim()
            }
        }
        flush()
        return result
    }

    private fun safeTime(h: String, m: String): LocalTime? {
        val hh = h.toIntOrNull() ?: return null
        val mm = m.toIntOrNull() ?: return null
        if (hh !in 0..23 || mm !in 0..59) return null
        return LocalTime.of(hh, mm)
    }
}
