package com.misturnos.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.misturnos.MainActivity
import com.misturnos.R
import com.misturnos.data.ScheduleStore
import com.misturnos.model.DayType
import com.misturnos.util.ShiftFormat

/** Widget de pantalla de inicio con el resumen semanal de turnos. */
class ShiftWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { id -> updateWidget(context, appWidgetManager, id) }
    }

    private fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_summary)
        val week = ScheduleStore(context).weekFor()

        if (week == null) {
            views.setTextViewText(R.id.widget_week, "Mis Turnos")
            views.setTextViewText(R.id.widget_total, "Sin datos")
            views.removeAllViews(R.id.widget_days)
            val empty = RemoteViews(context.packageName, R.layout.widget_day_row)
            empty.setTextViewText(R.id.row_day, "")
            empty.setTextViewText(R.id.row_detail, "Importa una captura en la app")
            empty.setTextViewText(R.id.row_hours, "")
            views.addView(R.id.widget_days, empty)
        } else {
            views.setTextViewText(R.id.widget_week, ShiftFormat.rangeLabel(week.start, week.end))
            views.setTextViewText(R.id.widget_total, ShiftFormat.hours(week.totalHours))
            views.removeAllViews(R.id.widget_days)
            week.days.forEach { day ->
                val row = RemoteViews(context.packageName, R.layout.widget_day_row)
                row.setTextViewText(R.id.row_day, ShiftFormat.dayLabel(day))
                row.setTextViewText(R.id.row_detail, ShiftFormat.dayDetail(day))
                row.setTextViewText(
                    R.id.row_hours,
                    if (day.dayType == DayType.WORK) ShiftFormat.hours(day.totalHours) else "",
                )
                views.addView(R.id.widget_days, row)
            }
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
