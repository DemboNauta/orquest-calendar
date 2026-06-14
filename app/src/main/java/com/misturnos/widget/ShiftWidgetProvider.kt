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
import com.misturnos.util.ShiftFormat

/** Widget de pantalla de inicio con el resumen semanal de turnos. */
class ShiftWidgetProvider : AppWidgetProvider() {

    // Filas fijas (sin addView): el launcher de MIUI tiene problemas con RemoteViews dinámicos.
    private val rowIds = intArrayOf(
        R.id.w_row0, R.id.w_row1, R.id.w_row2, R.id.w_row3, R.id.w_row4, R.id.w_row5, R.id.w_row6,
    )
    private val dayIds = intArrayOf(
        R.id.w_day0, R.id.w_day1, R.id.w_day2, R.id.w_day3, R.id.w_day4, R.id.w_day5, R.id.w_day6,
    )
    private val detailIds = intArrayOf(
        R.id.w_detail0, R.id.w_detail1, R.id.w_detail2, R.id.w_detail3, R.id.w_detail4, R.id.w_detail5, R.id.w_detail6,
    )
    private val hoursIds = intArrayOf(
        R.id.w_hours0, R.id.w_hours1, R.id.w_hours2, R.id.w_hours3, R.id.w_hours4, R.id.w_hours5, R.id.w_hours6,
    )

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { id -> updateWidget(context, appWidgetManager, id) }
    }

    private fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_summary)
        try {
            val week = ScheduleStore(context).weekFor()

            if (week == null) {
                views.setTextViewText(R.id.widget_week, "Mis Turnos")
                views.setTextViewText(R.id.widget_total, "—")
                rowIds.indices.forEach { i ->
                    views.setViewVisibility(rowIds[i], if (i == 0) View.VISIBLE else View.GONE)
                }
                views.setTextViewText(dayIds[0], "")
                views.setTextViewText(detailIds[0], "Importa una captura en la app")
                views.setTextViewText(hoursIds[0], "")
            } else {
                views.setTextViewText(R.id.widget_week, ShiftFormat.rangeLabel(week.start, week.end))
                views.setTextViewText(R.id.widget_total, ShiftFormat.hours(week.totalHours))
                rowIds.indices.forEach { i ->
                    val day = week.days.getOrNull(i)
                    if (day == null) {
                        views.setViewVisibility(rowIds[i], View.GONE)
                    } else {
                        views.setViewVisibility(rowIds[i], View.VISIBLE)
                        views.setTextViewText(dayIds[i], ShiftFormat.dayLabel(day))
                        views.setTextViewText(detailIds[i], ShiftFormat.dayDetail(day))
                        views.setTextViewText(
                            hoursIds[i],
                            if (day.dayType == DayType.WORK) ShiftFormat.hours(day.totalHours) else "",
                        )
                    }
                }
            }
        } catch (_: Exception) {
            views.setTextViewText(R.id.widget_week, "Mis Turnos")
            views.setTextViewText(R.id.widget_total, "")
        }

        // Pulsar el widget abre la app.
        val intent = Intent(context, MainActivity::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pending = PendingIntent.getActivity(context, 0, intent, flags)
        views.setOnClickPendingIntent(R.id.widget_root, pending)

        manager.updateAppWidget(widgetId, views)
    }

    companion object {
        /** Fuerza el refresco de todas las instancias del widget (tras importar/sincronizar). */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, ShiftWidgetProvider::class.java),
            )
            if (ids.isEmpty()) return
            val intent = Intent(context, ShiftWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            context.sendBroadcast(intent)
        }
    }
}
