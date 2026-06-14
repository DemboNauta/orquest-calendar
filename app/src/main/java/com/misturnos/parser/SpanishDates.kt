package com.misturnos.parser

/** Utilidades para interpretar los nombres de mes y día en español que usa Orquest. */
object SpanishDates {

    /** Mapea abreviaturas y nombres completos de mes (sin tildes, en minúscula) al número de mes. */
    private val months: Map<String, Int> = buildMap {
        val full = listOf(
            "enero", "febrero", "marzo", "abril", "mayo", "junio",
            "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
        )
        full.forEachIndexed { i, name ->
            put(name, i + 1)
            // Abreviatura de 3 letras (sept -> sep). Orquest usa 3 letras: ene, feb, mar...
            put(name.take(3), i + 1)
        }
        // Variantes habituales que conviene cubrir explícitamente.
        put("sept", 9)
    }

    /** Abreviatura de día de la semana -> índice (lunes = 0 ... domingo = 6). */
    private val weekdays: Map<String, Int> = mapOf(
        "lun" to 0, "mar" to 1, "mie" to 2, "jue" to 3, "vie" to 4, "sab" to 5, "dom" to 6,
    )

    /** Quita tildes y pasa a minúsculas para comparar de forma robusta frente a fallos de OCR. */
    fun normalize(text: String): String = text.trim().lowercase()
        .replace('á', 'a').replace('é', 'e').replace('í', 'i')
        .replace('ó', 'o').replace('ú', 'u').replace('ü', 'u')

    fun monthNumber(token: String): Int? = months[normalize(token).removeSuffix(".")]

    fun isWeekday(token: String): Boolean = weekdayIndex(token) != null

    /**
     * Índice de día (lun=0 … dom=6) de una línea que sea una abreviatura de día, opcionalmente
     * precedida por el número del día (p. ej. "Dom", "21 Dom", "14"). Devuelve null si no aplica.
     */
    fun weekdayIndex(line: String): Int? {
        val n = normalize(line).removeSuffix(".")
        val withoutNumber = n.replaceFirst(Regex("^\\d{1,2}\\s*"), "")
        return weekdays[withoutNumber]
    }
}
