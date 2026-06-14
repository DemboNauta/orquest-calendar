package com.misturnos.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.misturnos.MainActivity
import com.misturnos.R
import com.misturnos.data.ScheduleStore
import com.misturnos.model.DayType
import com.misturnos.model.WeekSchedule
import com.misturnos.util.ShiftFormat

/** Widget de pantalla de inicio con el resumen semanal de turnos y navegación de semana. */
class ShiftWidgetProvider : AppWidgetProvider() {

    // Filas fijas con IDs únicos (sin addView ni include: lo más compatible con todos los launchers).
    private val rowIds = intArrayOf(R.id.w_row0, R.id.w_row1, R.id.w_row2, R.id.w_row3, R.id.w_row4, R.id.w_row5, R.id.w_row6)
    private val badgeIds = intArrayOf(R.id.w_badge0, R.id.w_badge1, R.id.w_badge2, R.id.w_badge3, R.id.w_badge4, R.id.w_badge5, R.id.w_badge6)
    private val dayNumIds = intArrayOf(R.id.w_daynum0, R.id.w_daynum1, R.id.w_daynum2, R.id.w_daynum3, R.id.w_daynum4, R.id.w_daynum5, R.id.w_daynum6)
    private val dayAbbrIds = intArrayOf(R.id.w_dayabbr0, R.id.w_dayabbr1, R.id.w_dayabbr2, R.id.w_dayabbr3, R.id.w_dayabbr4, R.id.w_dayabbr5, R.id.w_dayabbr6)
    private val detailIds = intArrayOf(R.id.w_detail0, R.id.w_detail1, R.id.w_detail2, R.id.w_detail3, R.id.w_detail4, R.id.w_detail5, R.id.w_detail6)
    private val hoursIds = intArrayOf(R.id.w_hours0, R.id.w_hours1, R.id.w_hours2, R.id.w_hours3, R.id.w_hours4, R.id.w_hours5, R.id.w_hours6)

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action
        if (action == ACTION_PREV || action == ACTION_NEXT) {
            val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return
            val store = ScheduleStore(context)
            val weeks = store.loadSorted()
            val base = store.currentIndex()
            if (base >= 0) {
                val delta = if (action == ACTION_NEXT) 1 else -1
                val newOffset = store.getOffset(id) + delta
                // Solo aceptamos el desplazamiento si cae dentro del rango de semanas disponibles.
                if (base + newOffset in weeks.indices) store.setOffset(id, newOffset)
            }
            updateWidget(context, AppWidgetManager.getInstance(context), id)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id -> updateWidget(context, appWidgetManager, id) }
    }

    private fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_summary)
        try {
            val store = ScheduleStore(context)
            val weeks = store.loadSorted()
            val base = store.currentIndex()
            val target = if (base >= 0) base + store.getOffset(widgetId) else -1
            val week = weeks.getOrNull(target)
            renderWeek(views, week)
        } catch (_: Exception) {
            views.setTextViewText(R.id.w_week, "Mis Turnos")
            views.setTextViewText(R.id.w_subtitle, "")
            views.setTextViewText(R.id.w_total, "")
        }

        // Navegación: flechas envían un broadcast a este provider.
        views.setOnClickPendingIntent(R.id.w_prev, navIntent(context, widgetId, ACTION_PREV))
        views.setOnClickPendingIntent(R.id.w_next, navIntent(context, widgetId, ACTION_NEXT))
        // Pulsar el resto abre la app.
        views.setOnClickPendingIntent(R.id.w_week, openAppIntent(context))

        manager.updateAppWidget(widgetId, views)
    }

    private fun renderWeek(views: RemoteViews, week: WeekSchedule?) {
        if (week == null) {
            views.setTextViewText(R.id.w_week, "Mis Turnos")
            views.setTextViewText(R.id.w_subtitle, "Importa una captura en la app")
            views.setTextViewText(R.id.w_total, "—")
            rowIds.forEach { views.setViewVisibility(it, View.GONE) }
            return
        }
        views.setTextViewText(R.id.w_week, ShiftFormat.rangeLabel(week.start, week.end))
        views.setTextViewText(R.id.w_total, ShiftFormat.hours(week.totalHours))

        // Solo se muestran los días con turno; los descansos no aparecen (ahorra espacio y no se corta).
        val workDays = week.days.filter { it.dayType == DayType.WORK }
        views.setTextViewText(
            R.id.w_subtitle,
            if (workDays.isEmpty()) "Semana de descanso" else "${workDays.size} días trabajados",
        )

        rowIds.indices.forEach { i ->
            val day = workDays.getOrNull(i)
            if (day == null) {
                views.setViewVisibility(rowIds[i], View.GONE)
                return@forEach
            }
            views.setViewVisibility(rowIds[i], View.VISIBLE)
            views.setInt(badgeIds[i], "setBackgroundResource", R.drawable.widget_badge_work)
            views.setTextViewText(dayNumIds[i], ShiftFormat.dayNumber(day))
            views.setTextViewText(dayAbbrIds[i], ShiftFormat.dayAbbr(day))
            views.setTextViewText(detailIds[i], ShiftFormat.dayDetail(day))
            views.setTextColor(detailIds[i], 0xFF14201B.toInt())
            views.setTextViewText(hoursIds[i], ShiftFormat.hours(day.totalHours))
        }
    }

    private fun navIntent(context: Context, widgetId: Int, action: String): PendingIntent {
        val intent = Intent(context, ShiftWidgetProvider::class.java).apply {
            this.action = action
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        }
        val requestCode = widgetId * 10 + if (action == ACTION_NEXT) 1 else 2
        return PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun openAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        private const val ACTION_PREV = "com.misturnos.widget.PREV"
        private const val ACTION_NEXT = "com.misturnos.widget.NEXT"

        /** Fuerza el refresco de todas las instancias del widget (tras importar/sincronizar). */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, ShiftWidgetProvider::class.java))
            if (ids.isEmpty()) return
            val intent = Intent(context, ShiftWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            context.sendBroadcast(intent)
        }
    }
}
