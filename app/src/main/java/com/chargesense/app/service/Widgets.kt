package com.chargesense.app.service

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Bundle
import android.widget.RemoteViews
import com.chargesense.app.R
import com.chargesense.app.data.BatteryReader
import com.chargesense.app.data.BatterySnapshot
import com.chargesense.app.data.DesignCapacity
import com.chargesense.app.data.Estimates
import com.chargesense.app.data.HistoryDb
import com.chargesense.app.data.LiveState
import com.chargesense.app.data.Prefs
import com.chargesense.app.data.Sample
import com.chargesense.app.ui.MainActivity
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.roundToInt

/** Level ring (2×2). */
class RingWidget : BaseWidget() {
    override fun render(context: Context, manager: AppWidgetManager, id: Int, s: BatterySnapshot) {
        val (w, h) = sizePx(context, manager, id, 150, 150)
        val side = minOf(w, h - dp(context, 22)).coerceAtLeast(dp(context, 80))
        val views = RemoteViews(context.packageName, R.layout.widget_ring).apply {
            setImageViewBitmap(R.id.w_ring, WidgetArt.ring(context, s, side))
            setTextViewText(R.id.w_status, Widgets.statusLine(s))
            setOnClickPendingIntent(R.id.w_root, Widgets.openApp(context))
        }
        manager.updateAppWidget(id, views)
    }
}

/** Level with a 24-hour graph (4×2). */
class GraphWidget : BaseWidget() {
    override val needsHistory = true
    override fun render(context: Context, manager: AppWidgetManager, id: Int, s: BatterySnapshot) {
        val (w, h) = sizePx(context, manager, id, 320, 160)
        val samples = Widgets.lastDay(context)
        val views = RemoteViews(context.packageName, R.layout.widget_graph).apply {
            setTextViewText(R.id.w_level, "${s.levelPct.roundToInt()}%")
            setTextViewText(R.id.w_status, Widgets.statusLine(s))
            setTextViewText(R.id.w_current, s.currentMa?.let { (if (it > 0) "+" else "") + "$it mA" } ?: "")
            setImageViewBitmap(R.id.w_graph, WidgetArt.graph(context, samples, s, w - dp(context, 32), (h - dp(context, 78)).coerceAtLeast(dp(context, 50))))
            setOnClickPendingIntent(R.id.w_root, Widgets.openApp(context))
        }
        manager.updateAppWidget(id, views)
    }
}

/** Four live readings in a row (4×1). */
class StatsWidget : BaseWidget() {
    override fun render(context: Context, manager: AppWidgetManager, id: Int, s: BatterySnapshot) {
        val views = RemoteViews(context.packageName, R.layout.widget_stats).apply {
            setTextViewText(R.id.w_v1, s.currentMa?.let { (if (it > 0) "+" else "") + "$it" } ?: "—")
            setTextViewText(R.id.w_v2, "%.1f°".format(s.temperatureC))
            setTextViewText(R.id.w_v3, s.powerW?.let { "%.1f W".format(abs(it)) } ?: "—")
            setTextViewText(R.id.w_v4, Widgets.timeLeft(context, s))
            setTextViewText(R.id.w_l4, if (s.isPlugged) "To full" else "Left")
            setOnClickPendingIntent(R.id.w_root, Widgets.openApp(context))
        }
        manager.updateAppWidget(id, views)
    }
}

abstract class BaseWidget : AppWidgetProvider() {
    open val needsHistory = false
    abstract fun render(context: Context, manager: AppWidgetManager, id: Int, s: BatterySnapshot)

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val s = BatteryReader(context).read() ?: return
        Widgets.run(needsHistory) { ids.forEach { runCatching { render(context, manager, it, s) } } }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) {
        onUpdate(context, manager, intArrayOf(id))
    }

    protected fun sizePx(context: Context, manager: AppWidgetManager, id: Int, defW: Int, defH: Int): Pair<Int, Int> {
        val o = manager.getAppWidgetOptions(id)
        // Portrait: max width × min height approximates the real cell size.
        val w = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, defW).takeIf { it > 0 } ?: defW
        val h = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, defH).takeIf { it > 0 } ?: defH
        return dp(context, w) to dp(context, h)
    }

    protected fun dp(context: Context, v: Int) = (v * context.resources.displayMetrics.density).roundToInt()
}

object Widgets {
    private val executor = Executors.newSingleThreadExecutor()
    private val providers = listOf(RingWidget::class.java, GraphWidget::class.java, StatsWidget::class.java)

    /** History-reading widgets render off the main thread. */
    fun run(background: Boolean, block: () -> Unit) {
        if (background) executor.execute(block) else block()
    }

    /** Called by the monitor service to refresh every placed widget. */
    fun updateAll(context: Context, s: BatterySnapshot) {
        val manager = AppWidgetManager.getInstance(context)
        providers.forEach { cls ->
            val ids = manager.getAppWidgetIds(ComponentName(context, cls))
            if (ids.isEmpty()) return@forEach
            val provider = cls.getDeclaredConstructor().newInstance()
            run(provider.needsHistory) { ids.forEach { runCatching { provider.render(context, manager, it, s) } } }
        }
        runCatching { BatteryWidget.update(context, s) }
    }

    fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    fun statusLine(s: BatterySnapshot): String = when {
        s.isFull -> "Full"
        s.isPlugged && !s.isCharging -> "Paused"
        s.isPlugged -> "Charging"
        else -> "On battery"
    }

    fun timeLeft(context: Context, s: BatterySnapshot): String {
        val cap = Estimates.capacity(s, HistoryDb.get(context).sessions(10))?.mah ?: DesignCapacity.get(context).toFloat()
        val mins = Estimates.minutesRemaining(s, LiveState.smoothedCurrentMa.value ?: s.currentMa?.toFloat(), cap, Prefs.get(context).chargeAlarmLevel)
        return mins?.let { m -> if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m" } ?: "—"
    }

    fun lastDay(context: Context): List<Sample> =
        HistoryDb.get(context).samplesSince(System.currentTimeMillis() - 24 * 60 * 60_000L)
}

/** Bitmap drawing for widgets (RemoteViews can't host custom views). */
private object WidgetArt {
    private val CHARGING = 0xFF3DDC84.toInt()

    fun ring(context: Context, s: BatterySnapshot, side: Int): Bitmap {
        val accent = context.getColor(R.color.widget_accent)
        val track = context.getColor(R.color.widget_track)
        val text = context.getColor(R.color.widget_text)
        val bmp = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val stroke = side * 0.09f
        val r = RectF(stroke, stroke, side - stroke, side - stroke)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = stroke; strokeCap = Paint.Cap.ROUND }
        p.color = track
        c.drawArc(r, 0f, 360f, false, p)
        p.color = if (s.isPlugged && s.isCharging) CHARGING else accent
        c.drawArc(r, -90f, 360f * s.levelPct / 100f, false, p)
        val t = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = text
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            textSize = side * 0.30f
        }
        val cy = side / 2f - (t.descent() + t.ascent()) / 2
        c.drawText("${s.levelPct.roundToInt()}", side / 2f - side * 0.04f, cy, t)
        t.textSize = side * 0.12f
        c.drawText("%", side / 2f + side * 0.20f, cy, t)
        if (s.isPlugged) {
            t.textSize = side * 0.13f
            t.color = CHARGING
            c.drawText("⚡", side / 2f, side * 0.30f, t)
        }
        return bmp
    }

    fun graph(context: Context, samples: List<Sample>, s: BatterySnapshot, w: Int, h: Int): Bitmap {
        val accent = context.getColor(R.color.widget_accent)
        val grid = context.getColor(R.color.widget_track)
        val bmp = Bitmap.createBitmap(w.coerceAtLeast(10), h.coerceAtLeast(10), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val gp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = grid; strokeWidth = 2f; style = Paint.Style.STROKE
            pathEffect = DashPathEffect(floatArrayOf(8f, 8f), 0f)
        }
        listOf(0f, 0.5f, 1f).forEach { f -> val y = 4f + (h - 8f) * f; c.drawLine(0f, y, w.toFloat(), y, gp) }
        val pts = (samples.map { it.ts to (it.level to it.plugged) } + (s.timestamp to (s.levelPct to s.isPlugged)))
        if (pts.size < 2) return bmp
        val now = System.currentTimeMillis()
        val t0 = now - 24 * 60 * 60_000L
        fun x(t: Long) = ((t - t0).toFloat() / (now - t0)) * w
        fun y(v: Float) = 4f + (1f - v / 100f) * (h - 8f)
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = h * 0.035f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
        // Draw each run of same plug state separately so charging is green; break at gaps.
        var i = 0
        while (i < pts.size - 1) {
            val plugged = pts[i + 1].second.second
            val path = Path().apply { moveTo(x(pts[i].first), y(pts[i].second.first)) }
            val area = Path().apply { moveTo(x(pts[i].first), h.toFloat()); lineTo(x(pts[i].first), y(pts[i].second.first)) }
            var j = i + 1
            while (j < pts.size && pts[j].second.second == plugged && pts[j].first - pts[j - 1].first < 20 * 60_000L) {
                path.lineTo(x(pts[j].first), y(pts[j].second.first))
                area.lineTo(x(pts[j].first), y(pts[j].second.first))
                j++
            }
            val endX = x(pts[j - 1].first)
            area.lineTo(endX, h.toFloat()); area.close()
            val col = if (plugged) CHARGING else accent
            val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(0f, 0f, 0f, h.toFloat(), (col and 0x00FFFFFF) or 0x55000000, col and 0x00FFFFFF, Shader.TileMode.CLAMP)
            }
            c.drawPath(area, fill)
            line.color = col
            c.drawPath(path, line)
            i = if (j - 1 > i) j - 1 else i + 1
        }
        return bmp
    }
}
