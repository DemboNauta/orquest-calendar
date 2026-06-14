package com.misturnos.parser

import com.misturnos.model.DayShift
import com.misturnos.model.DayType
import com.misturnos.model.ShiftSegment
import com.misturnos.model.WeekSchedule
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.abs

/** Una línea reconocida por el OCR con su posición vertical (centro Y) en la imagen. */
data class OcrLine(val text: String, val cy: Float)

/**
 * Convierte el texto reconocido (OCR) de la pantalla "Mis turnos" de Orquest en un [WeekSchedule].
 *
 * Agrupa por **geometría**: cada línea (hora, estado, ubicación) se asigna al día cuyo nombre
 * (Lun…Dom) está verticalmente más cerca. Así es indiferente el orden en que el OCR devuelva los
 * textos dentro de una tarjeta (la hora suele salir por encima del nombre del día), y el domingo
 * —que es la última tarjeta— se captura igual que el resto.
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

    /** Para los tests: usa el orden de las líneas como posición vertical aproximada. */
    fun parse(rawText: String): WeekSchedule? =
        parse(rawText.lines().mapIndexed { i, t -> OcrLine(t, i.toFloat()) })

    fun parse(lines: List<OcrLine>): WeekSchedule? {
        val clean = lines.map { OcrLine(it.text.trim(), it.cy) }.filter { it.text.isNotEmpty() }
        if (clean.isEmpty()) return null
        val sorted = clean.sortedBy { it.cy }
        val texts = sorted.map { it.text }

        val year = texts.firstNotNullOfOrNull { fullDateYear.find(it)?.groupValues?.get(1)?.toInt() }
            ?: fallbackYear
        val weekStart = findWeekStart(texts, year) ?: return null

        // Empezamos tras el rótulo "Tus turnos" para evitar la cabecera y la tira del calendario.
        val startIdx = sorted.indexOfFirst { SpanishDates.normalize(it.text).startsWith("tus turnos") }
        val cards = if (startIdx >= 0) sorted.drop(startIdx + 1) else sorted

        val days = parseDays(cards, weekStart)
        if (days.isEmpty()) return null

        return WeekSchedule(start = weekStart, end = weekStart.plusDays(6), days = days)
    }

    /** Localiza la cabecera de rango semanal ("8 jun - 14 jun") y devuelve el lunes de esa semana. */
    private fun findWeekStart(texts: List<String>, year: Int): LocalDate? {
        for (line in texts) {
            val m = weekRange.find(line) ?: continue
            val startDay = m.groupValues[1].toIntOrNull() ?: continue
            val startMonth = SpanishDates.monthNumber(m.groupValues[2]) ?: continue
            if (SpanishDates.monthNumber(m.groupValues[4]) == null) continue
            return runCatching { LocalDate.of(year, startMonth, startDay) }.getOrNull()
        }
        return null
    }

    private fun parseDays(cards: List<OcrLine>, weekStart: LocalDate): List<DayShift> {
        // Anclas: el nombre de cada día (Lun…Dom) con su posición vertical.
        val anchors = LinkedHashMap<Int, Float>() // índice de día (lun=0…dom=6) -> cy
        cards.forEach { line ->
            val idx = SpanishDates.weekdayIndex(line.text)
            if (idx != null && idx !in anchors) anchors[idx] = line.cy
        }
        if (anchors.isEmpty()) return emptyList()

        val segments = HashMap<Int, MutableList<ShiftSegment>>()
        val statuses = HashMap<Int, DayType>()
        val locations = HashMap<Int, String>()

        cards.forEach { line ->
            if (SpanishDates.weekdayIndex(line.text) != null) return@forEach
            // Asignamos la línea al día cuyo nombre esté verticalmente más cerca.
            val nearest = anchors.minByOrNull { abs(it.value - line.cy) }?.key ?: return@forEach
            val raw = line.text

            val times = timeRange.findAll(raw).toList()
            if (times.isNotEmpty()) {
                val list = segments.getOrPut(nearest) { mutableListOf() }
                times.forEach { t ->
                    val s = safeTime(t.groupValues[1], t.groupValues[2])
                    val e = safeTime(t.groupValues[3], t.groupValues[4])
                    if (s != null && e != null) list += ShiftSegment(s, e)
                }
                return@forEach
            }

            val norm = SpanishDates.normalize(raw)
            when {
                norm.startsWith("dia libre") -> statuses[nearest] = DayType.DAY_OFF
                norm.startsWith("sin asignaciones") -> statuses[nearest] = DayType.UNASSIGNED
                norm.startsWith("sin tienda") -> { /* subtítulo de UNASSIGNED, se ignora */ }
                raw.contains(":") && !raw.contains("-") -> locations[nearest] = raw
            }
        }

        return anchors.keys.mapNotNull { idx ->
            val segs = segments[idx].orEmpty()
            val type = when {
                segs.isNotEmpty() -> DayType.WORK
                statuses[idx] != null -> statuses[idx]!!
                else -> return@mapNotNull null
            }
            DayShift(weekStart.plusDays(idx.toLong()), type, segs, locations[idx])
        }.sortedBy { it.date }
    }

    private fun safeTime(h: String, m: String): LocalTime? {
        val hh = h.toIntOrNull() ?: return null
        val mm = m.toIntOrNull() ?: return null
        if (hh !in 0..23 || mm !in 0..59) return null
        return LocalTime.of(hh, mm)
    }
}
