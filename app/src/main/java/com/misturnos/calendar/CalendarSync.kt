package com.misturnos.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.misturnos.model.DayShift
import com.misturnos.model.DayType
import com.misturnos.model.ShiftSegment
import java.time.LocalDate
import java.time.ZoneId

/** Un calendario disponible en el dispositivo (normalmente la cuenta de Google sincronizada). */
data class CalendarInfo(
    val id: Long,
    val displayName: String,
    val accountName: String,
)

data class SyncResult(val inserted: Int, val updated: Int, val skipped: Int)

/**
 * Escribe los turnos en el proveedor de calendario del dispositivo. Como el calendario de Google
 * ya está sincronizado en Android, los eventos aparecen automáticamente en Google Calendar sin
 * necesidad de OAuth ni claves de API.
 *
 * Cada tramo se marca con una clave estable en [CalendarContract.Events.CUSTOM_APP_PACKAGE] +
 * descripción, de forma que re-sincronizar no duplica eventos: actualiza el existente.
 */
class CalendarSync(private val context: Context) {

    private val zone: ZoneId = ZoneId.systemDefault()
    private val marker = "[MisTurnos]"

    fun hasPermissions(): Boolean {
        val read = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
        val write = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR)
        return read == PackageManager.PERMISSION_GRANTED && write == PackageManager.PERMISSION_GRANTED
    }

    fun availableCalendars(): List<CalendarInfo> {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
        )
        val result = mutableListOf<CalendarInfo>()
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            null,
            null,
            null,
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(CalendarContract.Calendars._ID)
            val nameCol = c.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
            val accCol = c.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_NAME)
            val accessCol = c.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL)
            while (c.moveToNext()) {
                val access = c.getInt(accessCol)
                // Solo calendarios en los que podemos escribir.
                if (access >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR) {
                    result += CalendarInfo(
                        id = c.getLong(idCol),
                        displayName = c.getString(nameCol) ?: "(sin nombre)",
                        accountName = c.getString(accCol) ?: "",
                    )
                }
            }
        }
        return result
    }

    /** Sincroniza todos los días de trabajo en el calendario indicado. */
    fun sync(calendarId: Long, days: List<DayShift>): SyncResult {
        var inserted = 0
        var updated = 0
        var skipped = 0
        days.filter { it.dayType == DayType.WORK }.forEach { day ->
            day.segments.forEachIndexed { index, segment ->
                when (upsertEvent(calendarId, day, segment, index)) {
                    UpsertOutcome.INSERTED -> inserted++
                    UpsertOutcome.UPDATED -> updated++
                    UpsertOutcome.SKIPPED -> skipped++
                }
            }
        }
        return SyncResult(inserted, updated, skipped)
    }

    private enum class UpsertOutcome { INSERTED, UPDATED, SKIPPED }

    private fun upsertEvent(
        calendarId: Long,
        day: DayShift,
        segment: ShiftSegment,
        index: Int,
    ): UpsertOutcome {
        val startMillis = day.date.atTime(segment.start).atZone(zone).toInstant().toEpochMilli()
        val endDateTime = if (segment.end.isBefore(segment.start)) {
            day.date.plusDays(1).atTime(segment.end)
        } else {
            day.date.atTime(segment.end)
        }
        val endMillis = endDateTime.atZone(zone).toInstant().toEpochMilli()

        val syncKey = "$marker ${day.date}#$index"
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, day.location ?: "Turno")
            put(CalendarContract.Events.DESCRIPTION, syncKey)
            put(CalendarContract.Events.DTSTART, startMillis)
            put(CalendarContract.Events.DTEND, endMillis)
            put(CalendarContract.Events.EVENT_TIMEZONE, zone.id)
            day.location?.let { put(CalendarContract.Events.EVENT_LOCATION, it) }
        }

        val existingId = findExistingEvent(calendarId, syncKey)
        return if (existingId != null) {
            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, existingId)
            val rows = context.contentResolver.update(uri, values, null, null)
            if (rows > 0) UpsertOutcome.UPDATED else UpsertOutcome.SKIPPED
        } else {
            context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            UpsertOutcome.INSERTED
        }
    }

    /** Busca un evento previo de esta app con la misma clave (para no duplicar). */
    private fun findExistingEvent(calendarId: Long, syncKey: String): Long? {
        val projection = arrayOf(CalendarContract.Events._ID)
        val selection =
            "${CalendarContract.Events.CALENDAR_ID} = ? AND ${CalendarContract.Events.DESCRIPTION} = ?"
        context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            projection,
            selection,
            arrayOf(calendarId.toString(), syncKey),
            null,
        )?.use { c ->
            if (c.moveToFirst()) return c.getLong(0)
        }
        return null
    }
}
