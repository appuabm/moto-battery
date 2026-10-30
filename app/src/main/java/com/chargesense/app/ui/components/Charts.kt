package com.chargesense.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * Line chart with time axis and value labels. Press and drag across it to scrub: a marker follows
 * the finger and a tooltip shows the value and time at that point. Vertical swipes still scroll
 * the surrounding page.
 */
@Composable
fun InteractiveLineChart(
    points: List<Pair<Long, Float>>,
    color: Color,
    format: (Float) -> String,
    modifier: Modifier = Modifier,
    height: Dp = 220.dp,
    minY: Float? = null,
    maxY: Float? = null,
    fill: Boolean = true,
    /** Optional colour per point (e.g. green while charging); the segment ending at a point uses its colour. */
    pointColors: List<Color>? = null,
    /** Readings further apart than this are not joined, leaving a gap where monitoring was off. */
    gapMs: Long = 20 * 60_000L,
) {
    val measurer = rememberTextMeasurer()
    val haptics = LocalHapticFeedback.current
    var touchX by remember { mutableStateOf<Float?>(null) }
    val scheme = MaterialTheme.colorScheme
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = scheme.onSurfaceVariant)
    val tipStyle = MaterialTheme.typography.labelMedium.copy(color = scheme.inverseOnSurface)
    val tipBg = scheme.inverseSurface
    val grid = scheme.outlineVariant
    val span = if (points.size >= 2) points.last().first - points.first().first else 0L
    // Pick a time format that changes across the axis: seconds for live spans, weekday for multi-day.
    val pattern = when {
        span > 36 * 3_600_000L -> "EEE HH:mm"
        span in 1 until 10 * 60_000L -> "HH:mm:ss"
        else -> "HH:mm"
    }
    val timeFmt = remember(pattern) { SimpleDateFormat(pattern, Locale.getDefault()) }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .touchSelection(points, { p, _ -> p.x }) { touchX = it }
    ) {
        if (points.size < 2) {
            drawEmpty(measurer, labelStyle)
            return@Canvas
        }
        val values = points.map { it.second }
        var lo = minY ?: values.min()
        var hi = maxY ?: values.max()
        if (hi - lo < 1e-3f) { lo -= 1f; hi += 1f }
        if (minY == null) lo -= (hi - lo) * 0.08f
        if (maxY == null) hi += (hi - lo) * 0.08f

        val yLabels = listOf(hi, (hi + lo) / 2, lo).map { it to format(it) }
        val labelW = yLabels.maxOf { measurer.measure(it.second, labelStyle).size.width } + 8.dp.toPx()
        val left = labelW
        val top = 46.dp.toPx() // room for the two-line tooltip
        val bottom = size.height - 20.dp.toPx()
        val right = size.width - 4.dp.toPx()
        val t0 = points.first().first
        val t1 = points.last().first.coerceAtLeast(t0 + 1)
        fun px(t: Long) = left + (t - t0).toFloat() / (t1 - t0) * (right - left)
        fun py(v: Float) = bottom - (v - lo) / (hi - lo) * (bottom - top)

        // Grid + Y labels
        yLabels.forEach { (v, text) ->
            val y = py(v)
            drawLine(grid, Offset(left, y), Offset(right, y), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
            val m = measurer.measure(text, labelStyle)
            drawText(m, topLeft = Offset(0f, y - m.size.height / 2f))
        }
        if (lo < 0f && hi > 0f) {
            drawLine(grid, Offset(left, py(0f)), Offset(right, py(0f)), 1.5.dp.toPx())
        }
        // X labels: start, middle, end
        listOf(t0, (t0 + t1) / 2, t1).forEachIndexed { i, t ->
            val m = measurer.measure(timeFmt.format(Date(t)), labelStyle)
            val x = when (i) {
                0 -> left
                1 -> px(t) - m.size.width / 2f
                else -> right - m.size.width
            }
            drawText(m, topLeft = Offset(x, bottom + 4.dp.toPx()))
        }

        // Thin out very dense series to roughly one point per pixel column.
        val step = (points.size / (right - left)).toInt().coerceAtLeast(1)
        val shownIdx = points.indices.filter { it % step == 0 || it == points.lastIndex }
        // Split into runs at gaps, then into same-colour pieces within each run.
        val runs = mutableListOf<MutableList<Int>>()
        shownIdx.forEach { i ->
            val prev = runs.lastOrNull()?.lastOrNull()
            if (prev == null || points[i].first - points[prev].first > gapMs) runs += mutableListOf(i) else runs.last() += i
        }
        val stroke = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun colorAt(i: Int) = pointColors?.getOrNull(i) ?: color
        fun at(i: Int) = Offset(px(points[i].first), py(points[i].second))
        runs.forEach { run ->
            if (run.size == 1) {
                drawCircle(colorAt(run[0]), 2.5.dp.toPx(), at(run[0]))
                return@forEach
            }
            // Each segment takes the colour of its end point; consecutive same-colour segments share a path.
            var k = 1
            while (k < run.size) {
                val c = colorAt(run[k])
                val first = run[k - 1]
                var last = first
                val line = Path().apply { moveTo(at(first).x, at(first).y) }
                var prev = at(first)
                while (k < run.size && colorAt(run[k]) == c) {
                    // Horizontal-tangent cubic between points: smooth, and never overshoots vertically.
                    val p = at(run[k])
                    val mx = (prev.x + p.x) / 2
                    line.cubicTo(mx, prev.y, mx, p.y, p.x, p.y)
                    prev = p
                    last = run[k]
                    k++
                }
                if (fill) {
                    val area = Path().apply {
                        addPath(line)
                        lineTo(at(last).x, bottom)
                        lineTo(at(first).x, bottom)
                        close()
                    }
                    drawPath(area, Brush.verticalGradient(listOf(c.copy(alpha = 0.32f), Color.Transparent), startY = top, endY = bottom))
                }
                drawPath(line, c, style = stroke)
            }
        }

        // Latest reading marked with a ringed dot.
        if (touchX == null) {
            val end = at(points.lastIndex)
            drawCircle(scheme.surfaceContainer, 6.dp.toPx(), end)
            drawCircle(colorAt(points.lastIndex), 4.dp.toPx(), end)
        }

        // Scrub marker + tooltip
        val tx = touchX ?: return@Canvas
        // In whole milliseconds: as a Float, epoch times only resolve to ~2 minutes.
        val target = t0 + (((tx - left) / (right - left)).coerceIn(0f, 1f) * (t1 - t0)).toLong()
        val (pt, pv) = points.minBy { abs(it.first - target) }
        val x = px(pt)
        val y = py(pv)
        // Dashed marker with small triangles at both ends, a ringed dot, and a dark tooltip.
        val ink = scheme.onSurface
        drawLine(ink, Offset(x, top), Offset(x, bottom), 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
        val tri = 5.dp.toPx()
        drawPath(Path().apply { moveTo(x - tri, top - tri); lineTo(x + tri, top - tri); lineTo(x, top + 1f); close() }, ink)
        drawPath(Path().apply { moveTo(x - tri, bottom + tri); lineTo(x + tri, bottom + tri); lineTo(x, bottom - 1f); close() }, ink)
        val dotColor = pointColors?.getOrNull(points.indexOfFirst { it.first == pt }) ?: color
        drawCircle(scheme.surface, 7.dp.toPx(), Offset(x, y))
        drawCircle(dotColor, 5.dp.toPx(), Offset(x, y))
        darkTooltip(measurer, listOf(format(pv), timeFmt.format(Date(pt))), x, 0f, tipBg, tipStyle, dot = dotColor)
    }
}

/**
 * Bar chart of (label, value 0–100). Tap or drag over a bar to select it; the selected bar is
 * highlighted and its label/value shown above. With nothing selected, the last bar is highlighted.
 */
@Composable
fun InteractiveBarChart(
    bars: List<Pair<String, Float>>,
    highlight: Color,
    format: (Float) -> String,
    modifier: Modifier = Modifier,
    height: Dp = 128.dp,
    showTooltip: Boolean = true,
) {
    val measurer = rememberTextMeasurer()
    val haptics = LocalHapticFeedback.current
    var selected by remember(bars.size) { mutableStateOf<Int?>(null) }
    val scheme = MaterialTheme.colorScheme
    val tipStyle = MaterialTheme.typography.labelMedium.copy(color = scheme.inverseOnSurface)
    val track = scheme.surfaceContainerHighest
    val slots = maxOf(bars.size, 12)

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .touchSelection(bars, { p, s ->
                val i = (p.x / (s.width / slots)).toInt() - (slots - bars.size)
                i.takeIf { it in bars.indices }
            }) { selected = it }
    ) {
        if (bars.isEmpty()) return@Canvas
        val tipSpace = if (showTooltip) 30.dp.toPx() else 0f
        val chartH = size.height - tipSpace
        val slotW = size.width / slots
        val w = slotW * 0.62f
        val r = CornerRadius(w / 2, w / 2)
        val offset = slots - bars.size
        val active = selected ?: bars.lastIndex
        bars.forEachIndexed { i, (_, v) ->
            val h = (v.coerceIn(2f, 100f) / 100f) * chartH
            val x = (offset + i) * slotW + (slotW - w) / 2
            drawRoundRect(if (i == active) highlight else track, Offset(x, size.height - h), Size(w, h), r)
        }
        val sel = selected ?: return@Canvas
        if (!showTooltip) return@Canvas
        val (label, v) = bars[sel]
        val tip = measurer.measure("$label  ·  ${format(v)}", tipStyle)
        val padH = 10.dp.toPx()
        val padV = 5.dp.toPx()
        val tw = tip.size.width + padH * 2
        val th = tip.size.height + padV * 2
        val cx = (offset + sel) * slotW + slotW / 2
        val bx = (cx - tw / 2).coerceIn(0f, size.width - tw)
        drawRoundRect(scheme.inverseSurface, Offset(bx, 0f), Size(tw, th), CornerRadius(th / 2, th / 2))
        drawText(tip, topLeft = Offset(bx + padH, padV))
    }
}

private fun DrawScope.drawEmpty(measurer: TextMeasurer, style: TextStyle) {
    val m = measurer.measure("Not enough data yet", style)
    drawText(m, topLeft = Offset((size.width - m.size.width) / 2, (size.height - m.size.height) / 2))
}

/** One horizontal lane of a timeline: a label and the spans where [active] was true. */
data class Lane(val label: String, val color: Color, val active: (Int) -> Boolean)

/**
 * Stacked timeline lanes (Screen on, Screen off, Deep sleep, Awake, Charging…) over the same time
 * range as a chart above it. Each sample covers the interval up to the next one, capped at [gapMs].
 */
@Composable
fun TimelineLanes(
    timestamps: List<Long>,
    lanes: List<Lane>,
    modifier: Modifier = Modifier,
    gapMs: Long = 20 * 60_000L,
    leftInset: Dp = 0.dp,
) {
    val scheme = MaterialTheme.colorScheme
    val density = androidx.compose.ui.platform.LocalDensity.current
    val timeFmt = remember { SimpleDateFormat("EEE HH:mm", Locale.getDefault()) }
    var selected by remember { mutableStateOf<Int?>(null) }
    // Maps a touch x to the sample covering that moment.
    val pick: (Offset, Size) -> Int? = pick@{ p, s ->
        if (timestamps.size < 2) return@pick null
        val left = with(density) { leftInset.toPx() }
        val t0 = timestamps.first()
        val t1 = timestamps.last().coerceAtLeast(t0 + 1)
        val t = t0 + (((p.x - left) / (s.width - left)).coerceIn(0f, 1f) * (t1 - t0)).toLong()
        timestamps.indexOfLast { it <= t }.coerceAtLeast(0)
    }
    androidx.compose.foundation.layout.Column(modifier.fillMaxWidth()) {
        val sel = selected
        androidx.compose.material3.Text(
            if (sel == null) "Press the lanes to see a moment"
            else timeFmt.format(Date(timestamps[sel])) + " · " +
                lanes.filter { it.active(sel) }.joinToString(", ") { it.label }.ifEmpty { "No data" },
            style = MaterialTheme.typography.labelLarge,
            color = if (sel == null) scheme.onSurfaceVariant else scheme.onSurface,
            modifier = Modifier.padding(top = 12.dp),
        )
        lanes.forEach { lane ->
            androidx.compose.material3.Text(
                lane.label,
                style = MaterialTheme.typography.labelMedium,
                color = if (sel != null && lane.active(sel)) scheme.onSurface else scheme.onSurfaceVariant,
                modifier = androidx.compose.ui.Modifier.padding(top = 10.dp, bottom = 4.dp),
            )
            Canvas(Modifier.fillMaxWidth().height(22.dp).touchSelection(timestamps, pick) { selected = it }) {
                val r = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                drawRoundRect(scheme.surfaceContainerHighest, Offset.Zero, size, r)
                if (timestamps.size < 2) return@Canvas
                val left = leftInset.toPx()
                val t0 = timestamps.first()
                val t1 = timestamps.last().coerceAtLeast(t0 + 1)
                fun px(t: Long) = left + (t - t0).toFloat() / (t1 - t0) * (size.width - left)
                var i = 0
                while (i < timestamps.size) {
                    if (!lane.active(i)) { i++; continue }
                    val start = timestamps[i]
                    var j = i
                    while (j + 1 < timestamps.size && lane.active(j + 1) && timestamps[j + 1] - timestamps[j] <= gapMs) j++
                    val end = if (j + 1 < timestamps.size) minOf(timestamps[j + 1], timestamps[j] + gapMs) else timestamps[j]
                    val x0 = px(start)
                    val w = (px(end) - x0).coerceAtLeast(3.dp.toPx())
                    drawRoundRect(lane.color, Offset(x0, 0f), Size(w, size.height), r)
                    i = j + 1
                }
                sel?.let { k ->
                    val x = px(timestamps[k])
                    drawLine(scheme.onSurface, Offset(x, -6.dp.toPx()), Offset(x, size.height + 6.dp.toPx()), 2.dp.toPx())
                }
            }
        }
    }
}
