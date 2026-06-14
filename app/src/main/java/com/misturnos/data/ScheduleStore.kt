package com.misturnos.data

import android.content.Context
import com.misturnos.model.DayShift
import com.misturnos.model.DayType
import com.misturnos.model.ShiftSegment
import com.misturnos.model.WeekSchedule
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime

/**
 * Guarda las semanas parseadas en SharedPreferences (serializadas a JSON con la librería nativa
 * `org.json`, sin dependencias extra) para que el widget y futuras sesiones puedan leerlas.
 */
class ScheduleStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("mis_turnos", Context.MODE_PRIVATE)

    fun save(weeks: List<WeekSchedule>) {
        val arr = JSONArray()
        weeks.forEach { week ->
            val days = JSONArray()
            week.days.forEach { day ->
                val segments = JSONArray()
                day.segments.forEach { seg ->
                    segments.put(
                        JSONObject()
                            .put("start", seg.start.toString())
                            .put("end", seg.end.toString()),
                    )
                }
                days.put(
                    JSONObject()
                        .put("date", day.date.toString())
                        .put("type", day.dayType.name)
                        .put("location", day.location ?: JSONObject.NULL)
                        .put("segments", segments),
                )
            }
            arr.put(
                JSONObject()
                    .put("start", week.start.toString())
                    .put("end", week.end.toString())
                    .put("days", days),
            )
        }
        prefs.edit().putString(KEY_WEEKS, arr.toString()).apply()
    }

    fun load(): List<WeekSchedule> {
        val raw = prefs.getString(KEY_WEEKS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val w = arr.getJSONObject(i)
                val daysArr = w.getJSONArray("days")
                val days = (0 until daysArr.length()).map { j ->
                    val d = daysArr.getJSONObject(j)
                    val segArr = d.getJSONArray("segments")
                    val segments = (0 until segArr.length()).map { k ->
                        val s = segArr.getJSONObject(k)
                        ShiftSegment(
                            LocalTime.parse(s.getString("start")),
                            LocalTime.parse(s.getString("end")),
                        )
                    }
                    DayShift(
                        date = LocalDate.parse(d.getString("date")),
                        dayType = DayType.valueOf(d.getString("type")),
                        segments = segments,
                        location = d.optString("location").takeIf { it.isNotEmpty() && it != "null" },
                    )
                }
                WeekSchedule(
                    start = LocalDate.parse(w.getString("start")),
                    end = LocalDate.parse(w.getString("end")),
                    days = days,
                )
            }
        }.getOrDefault(emptyList())
    }

    fun loadSorted(): List<WeekSchedule> = load().sortedBy { it.start }

    /** Índice de la semana que contiene [today]; si no, la próxima futura; si tampoco, la última. */
    fun currentIndex(today: LocalDate = LocalDate.now()): Int {
        val weeks = loadSorted()
        if (weeks.isEmpty()) return -1
        val containing = weeks.indexOfFirst { !today.isBefore(it.start) && !today.isAfter(it.end) }
        if (containing >= 0) return containing
        val upcoming = weeks.indexOfFirst { it.start.isAfter(today) }
        return if (upcoming >= 0) upcoming else weeks.lastIndex
    }

    /** La semana que contiene [today]; si no hay, la próxima futura; si tampoco, la última conocida. */
    fun weekFor(today: LocalDate = LocalDate.now()): WeekSchedule? =
        loadSorted().getOrNull(currentIndex(today))

    /** Desplazamiento (en semanas) que está mostrando un widget concreto respecto a la semana actual. */
    fun getOffset(widgetId: Int): Int = prefs.getInt("$KEY_OFFSET$widgetId", 0)

    fun setOffset(widgetId: Int, offset: Int) =
        prefs.edit().putInt("$KEY_OFFSET$widgetId", offset).apply()

    fun clear() = prefs.edit().remove(KEY_WEEKS).apply()

    private companion object {
        const val KEY_WEEKS = "weeks_json"
        const val KEY_OFFSET = "widget_offset_"
    }
}
