package com.chargesense.app.service

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.chargesense.app.R
import com.chargesense.app.data.BatteryReader
import com.chargesense.app.data.BatterySnapshot
import com.chargesense.app.ui.MainActivity
import kotlin.math.roundToInt

/** Home-screen widget with level, status, current and temperature. Refreshed by the monitor service. */
class BatteryWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        BatteryReader(context).read()?.let { render(context, manager, ids, it) }
    }

    companion object {
        fun update(context: Context, s: BatterySnapshot) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, BatteryWidget::class.java))
            if (ids.isNotEmpty()) render(context, manager, ids, s)
        }

        private fun render(context: Context, manager: AppWidgetManager, ids: IntArray, s: BatterySnapshot) {
            val open = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val views = RemoteViews(context.packageName, R.layout.widget_battery).apply {
                setTextViewText(R.id.widget_level, "${s.levelPct.roundToInt()}%")
                setTextViewText(R.id.widget_status, if (s.isPlugged) "${s.statusText} · ${s.plugText}" else s.statusText)
                setTextViewText(R.id.widget_current, s.currentMa?.let { (if (it > 0) "+" else "") + "$it mA" } ?: "—")
                setTextViewText(R.id.widget_temp, "%.1f °C".format(s.temperatureC))
                setProgressBar(R.id.widget_progress, 100, s.levelPct.roundToInt(), false)
                setOnClickPendingIntent(R.id.widget_root, open)
            }
            manager.updateAppWidget(ids, views)
        }
    }
}
