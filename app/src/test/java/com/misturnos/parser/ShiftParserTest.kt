package com.misturnos.parser

import com.misturnos.model.DayType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class ShiftParserTest {

    private val parser = ShiftParser(fallbackYear = 2026)

    /** Texto tal como saldría del OCR de la primera captura (8 jun - 14 jun). */
    private val week1 = """
        Mis turnos
        9 de junio de 2026
        8 jun - 14 jun
        Lun Mar Mié Jue Vie Sáb Dom
        8 9 10 11 12 13 14
        Tus turnos
        8
        Lun
        Sin asignaciones
        Sin tienda asignada
        9
        Mar
        Sin asignaciones
        Sin tienda asignada
        10
        Mié
        10:00 - 14:00
        Cuenca: General
        11
        Jue
        13:30 - 17:00 / 19:30 - 23:00
        Cuenca: General
        12
        Vie
        13:00 - 15:30 / 18:00 - 22:30
        Cuenca: General
        13
        Sáb
        Día libre
        Cuenca: General
        14
        Dom
        Día libre
        Cuenca: General
    """.trimIndent()

    /** Segunda captura (15 jun - 21 jun). */
    private val week2 = """
        Mis turnos
        9 de junio de 2026
        15 jun - 21 jun
        Lun Mar Mié Jue Vie Sáb Dom
        15 16 17 18 19 20 21
        Tus turnos
        15
        Lun
        Día libre
        Cuenca: General
        16
        Mar
        Día libre
        Cuenca: General
        17
        Mié
        10:30 - 12:30 / 20:00 - 22:30
        Cuenca: General
        18
        Jue
        13:30 - 16:30 / 20:30 - 23:00
        Cuenca: General
        19
        Vie
        13:30 - 15:30 / 20:00 - 23:30
        Cuenca: General
        20
        Sáb
        20:30 - 23:30
        Cuenca: General
        21
        Dom
        17:30 - 22:30
        Cuenca: General
    """.trimIndent()

    @Test
    fun `parses week range and dates`() {
        val w = parser.parse(week1)
        assertNotNull(w)
        assertEquals(LocalDate.of(2026, 6, 8), w!!.start)
        assertEquals(LocalDate.of(2026, 6, 14), w.end)
        assertEquals(7, w.days.size)
    }

    @Test
    fun `classifies day types`() {
        val w = parser.parse(week1)!!
        val byDate = w.days.associateBy { it.date }
        assertEquals(DayType.UNASSIGNED, byDate[LocalDate.of(2026, 6, 8)]!!.dayType)
        assertEquals(DayType.WORK, byDate[LocalDate.of(2026, 6, 10)]!!.dayType)
        assertEquals(DayType.DAY_OFF, byDate[LocalDate.of(2026, 6, 13)]!!.dayType)
    }

    @Test
    fun `parses single shift segment`() {
        val w = parser.parse(week1)!!
        val wed = w.days.first { it.date == LocalDate.of(2026, 6, 10) }
        assertEquals(1, wed.segments.size)
        assertEquals(LocalTime.of(10, 0), wed.segments[0].start)
        assertEquals(LocalTime.of(14, 0), wed.segments[0].end)
        assertEquals(4.0, wed.totalHours, 0.001)
    }

    @Test
    fun `parses split shift segments`() {
        val w = parser.parse(week1)!!
        val thu = w.days.first { it.date == LocalDate.of(2026, 6, 11) }
        assertEquals(2, thu.segments.size)
        // 13:30-17:00 = 3.5h, 19:30-23:00 = 3.5h -> 7h
        assertEquals(7.0, thu.totalHours, 0.001)
    }

    @Test
    fun `computes weekly total hours for week1`() {
        val w = parser.parse(week1)!!
        // Mié 4 + Jue 7 + Vie (2.5 + 4.5 = 7) = 18h
        assertEquals(18.0, w.totalHours, 0.001)
        assertEquals(3, w.workedDays)
    }

    @Test
    fun `computes weekly total hours for week2`() {
        val w = parser.parse(week2)!!
        // Mié 2+2.5=4.5 | Jue 3+2.5=5.5 | Vie 2+3.5=5.5 | Sáb 3 | Dom 5  => 23.5h
        assertEquals(23.5, w.totalHours, 0.001)
        assertEquals(5, w.workedDays)
        assertEquals(LocalDate.of(2026, 6, 21), w.days.last().date)
    }

    @Test
    fun `captures location`() {
        val w = parser.parse(week1)!!
        val wed = w.days.first { it.date == LocalDate.of(2026, 6, 10) }
        assertEquals("Cuenca: General", wed.location)
    }

    @Test
    fun `tolerates merged day number and weekday`() {
        val merged = week1.replace("10\nMié", "10 Mié")
        val w = parser.parse(merged)
        assertNotNull(w)
        assertTrue(w!!.days.any { it.date == LocalDate.of(2026, 6, 10) && it.dayType == DayType.WORK })
    }

    @Test
    fun `handles month boundary in week range`() {
        val text = """
            Mis turnos
            30 de junio de 2026
            29 jun - 5 jul
            Tus turnos
            29
            Lun
            10:00 - 14:00
            Cuenca: General
            5
            Dom
            09:00 - 13:00
            Cuenca: General
        """.trimIndent()
        val w = parser.parse(text)!!
        assertEquals(LocalDate.of(2026, 6, 29), w.start)
        assertEquals(LocalDate.of(2026, 7, 5), w.end)
        assertTrue(w.days.any { it.date == LocalDate.of(2026, 7, 5) })
    }

    @Test
    fun `captures sunday even with trailing navigation noise`() {
        val withNav = week2 + "\n" + """
            Inicio
            Turnos
            Peticiones
            Vacantes
            Menú
        """.trimIndent()
        val w = parser.parse(withNav)!!
        val sunday = w.days.firstOrNull { it.date == LocalDate.of(2026, 6, 21) }
        assertNotNull("El domingo debe capturarse", sunday)
        assertEquals(DayType.WORK, sunday!!.dayType)
        assertEquals(5.0, sunday.totalHours, 0.001)
    }

    @Test
    fun `returns null on unrelated text`() {
        assertEquals(null, parser.parse("hola qué tal\nesto no es orquest"))
    }
}
