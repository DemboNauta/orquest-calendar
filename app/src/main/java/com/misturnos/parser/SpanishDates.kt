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

    /** Abreviaturas de día de la semana que aparecen en las tarjetas (normalizadas, sin tilde). */
    private val weekdays: Set<String> = setOf(
        "lun", "mar", "mie", "jue", "vie", "sab", "dom",
    )

    /** Quita tildes y pasa a minúsculas para comparar de forma robusta frente a fallos de OCR. */
    fun normalize(text: String): String = text.trim().lowercase()
        .replace('á', 'a').replace('é', 'e').replace('í', 'i')
        .replace('ó', 'o').replace('ú', 'u').replace('ü', 'u')

    fun monthNumber(token: String): Int? = months[normalize(token).removeSuffix(".")]

    fun isWeekday(token: String): Boolean = normalize(token).removeSuffix(".") in weekdays
}
