package com.chargesense.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/*
 * Chart kit in the style of the Mebius chart freebies: soft "ghost" tracks behind coloured values,
 * one highlighted item with a dark tooltip, glass cylinders, diverging bars, range tracks, stripe
 * breakdowns and dot heatmaps. All charts support press-and-drag selection.
 */

/** Dark rounded tooltip with an optional colour dot, clamped inside the canvas. */
internal fun DrawScope.darkTooltip(
    measurer: TextMeasurer,
    lines: List<String>,
    anchorX: Float,
    top: Float,
    bg: Color,
    style: TextStyle,
    dot: Color? = null,
) {
    val measured = lines.map { measurer.measure(it, style) }
    val padH = 10.dp.toPx()
    val padV = 7.dp.toPx()
    val dotW = if (dot != null) 12.dp.toPx() else 0f
    val w = measured.maxOf { it.size.width } + padH * 2 + dotW
    val h = measured.sumOf { it.size.height } + padV * 2
    val x = (anchorX - w / 2).coerceIn(0f, size.width - w)
    drawRoundRect(bg, Offset(x, top), Size(w, h.toFloat()), CornerRadius(10.dp.toPx()))
    var y = top + padV
    measured.forEachIndexed { i, m ->
        if (i == 0 && dot != null) drawCircle(dot, 4.dp.toPx(), Offset(x + padH + 4.dp.toPx(), y + m.size.height / 2f))
        drawText(m, topLeft = Offset(x + padH + dotW, y))
        y += m.size.height
    }
}

/** Shared press-and-drag selection over [count] equal slots. Returns the selected index or null. */
@Composable
internal fun Modifier.slotSelection(count: Int, onSelect: (Int?) -> Unit): Modifier =
    touchSelection(count, { p, s -> if (count == 0) null else (p.x / s.width * count).toInt().coerceIn(0, count - 1) }, onSelect = onSelect)

/**
 * Tap or press-and-drag selection where [pick] maps the touch position (and canvas size) to an
 * item. A tick of haptics marks each new item. The value stays on screen after the finger lifts
 * (a quick tap is the usual way to read a chart) and clears after [holdMs], or on a tap outside
 * any item. If the page scroll takes over a vertical drag, the selection clears at once.
 */
@Composable
internal fun <T> Modifier.touchSelection(key: Any?, pick: (Offset, Size) -> T?, holdMs: Long = 4_000L, onSelect: (T?) -> Unit): Modifier {
    val haptics = LocalHapticFeedback.current
    val latestPick by rememberUpdatedState(pick)
    val latestSelect by rememberUpdatedState(onSelect)
    return this
        .systemGestureExclusion()
        // Keyed on nothing: live charts get new data every few seconds, and restarting here would
        // cancel an in-progress gesture and its auto-clear. The latest pick/onSelect are read instead.
        .pointerInput(Unit) {
            coroutineScope {
                var clear: Job? = null
                awaitEachGesture {
                    val area = Size(size.width.toFloat(), size.height.toFloat())
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // Claim the press so a clickable parent card doesn't also open on a chart tap.
                    down.consume()
                    clear?.cancel()
                    var last = latestPick(down.position, area)
                    latestSelect(last)
                    if (last != null) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    var scrolled = false
                    while (true) {
                        val ev = awaitPointerEvent(PointerEventPass.Final)
                        val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!c.pressed) break
                        // The press itself comes back through this pass already consumed (by us).
                        if (c.changedToDownIgnoreConsumed()) continue
                        // The page's vertical scroll took the gesture: stop reading values.
                        if (c.isConsumed) { scrolled = true; break }
                        val i = latestPick(c.position, area)
                        if (i != last) {
                            last = i
                            latestSelect(i)
                            if (i != null) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        val d = c.position - down.position
                        if (abs(d.x) > abs(d.y)) c.consume()
                    }
                    if (scrolled || last == null) latestSelect(null)
                    else clear = launch { delay(holdMs); latestSelect(null) }
                }
            }
        }
}

/**
 * Bars on soft full-height "ghost" tracks. With nothing pressed the last bar is highlighted; the
 * pressed bar gets a gradient fill, a highlight band and a dark tooltip. Values are 0..[max].
 */
@Composable
fun GhostBarChart(
    bars: List<Pair<String, Float>>,
    color: Color,
    format: (Float) -> String,
    modifier: Modifier = Modifier,
    height: Dp = 140.dp,
    max: Float = 100f,
) {
    val measurer = rememberTextMeasurer()
    var selected by remember(bars.size) { mutableStateOf<Int?>(null) }
    val scheme = MaterialTheme.colorScheme
    val tipStyle = MaterialTheme.typography.labelMedium.copy(color = scheme.inverseOnSurface)
    val ghost = scheme.onSurface.copy(alpha = 0.07f)
    val band = color.copy(alpha = 0.12f)
    val idle = color.copy(alpha = 0.45f)
    val anim by animateFloatAsState(1f, tween(600), label = "bars")
    // Few bars sit right-aligned in at least 7 slots instead of stretching across the card.
    val slots = maxOf(bars.size, 7)
    val offset = slots - bars.size
    Canvas(modifier.fillMaxWidth().height(height).slotSelection(slots) { selected = it?.minus(offset)?.takeIf { i -> i >= 0 } }) {
        if (bars.isEmpty()) return@Canvas
        val tipSpace = 40.dp.toPx()
        val chartTop = tipSpace
        val chartH = size.height - chartTop
        val slot = size.width / slots
        val w = (slot * 0.62f).coerceAtMost(28.dp.toPx())
        val r = CornerRadius(w / 2.2f)
        val active = selected ?: bars.lastIndex
        bars.forEachIndexed { i, (_, v) ->
            val x = (i + offset) * slot + (slot - w) / 2
            if (i == selected) drawRoundRect(band, Offset((i + offset) * slot + 2f, chartTop - 6.dp.toPx()), Size(slot - 4f, chartH + 6.dp.toPx()), CornerRadius(8.dp.toPx()))
            drawRoundRect(ghost, Offset(x, chartTop), Size(w, chartH), r)
            val h = (v.coerceIn(0f, max) / max * chartH * anim).coerceAtLeast(w * 0.6f)
            val brush = if (i == active) Brush.verticalGradient(listOf(color.copy(alpha = 0.75f), color), startY = size.height - h, endY = size.height)
            else Brush.verticalGradient(listOf(idle, idle))
            drawRoundRect(brush, Offset(x, size.height - h), Size(w, h), r)
        }
        selected?.let { i ->
            val (label, v) = bars[i]
            darkTooltip(measurer, listOf(format(v), label), (i + offset) * slot + slot / 2, 0f, scheme.inverseSurface, tipStyle, dot = color)
        }
    }
}

/**
 * Bars growing up (positive) and down (negative) from a centre line, pastel with the selected or
 * latest bar in full colour. Ideal for charge (+) vs drain (−) current.
 */
@Composable
fun DivergingBarChart(
    values: List<Pair<String, Float>>,
    up: Color,
    down: Color,
    format: (Float) -> String,
    modifier: Modifier = Modifier,
    height: Dp = 140.dp,
    sideLabels: Boolean = false,
) {
    val measurer = rememberTextMeasurer()
    var selected by remember(values.size) { mutableStateOf<Int?>(null) }
    val scheme = MaterialTheme.colorScheme
    val tipStyle = MaterialTheme.typography.labelMedium.copy(color = scheme.inverseOnSurface)
    val sideStyle = MaterialTheme.typography.labelSmall.copy(color = scheme.onSurfaceVariant)
    val axis = scheme.outlineVariant
    // With side labels, bars start after the widest label so they never draw over it.
    val inset = if (sideLabels) remember(sideStyle) {
        maxOf(measurer.measure("Charge", sideStyle).size.width, measurer.measure("Drain", sideStyle).size.width).toFloat()
    } else 0f
    val gapPx = with(androidx.compose.ui.platform.LocalDensity.current) { 8.dp.toPx() }
    Canvas(
        modifier.fillMaxWidth().height(height).touchSelection(values.size, { p, s ->
            val left = if (inset > 0f) inset + gapPx else 0f
            if (values.isEmpty()) null else ((p.x - left) / (s.width - left) * values.size).toInt().coerceIn(0, values.size - 1)
        }) { selected = it }
    ) {
        if (values.isEmpty()) return@Canvas
        val left = if (inset > 0f) inset + 8.dp.toPx() else 0f
        val tipSpace = 40.dp.toPx()
        val mid = tipSpace + (size.height - tipSpace) / 2
        val half = (size.height - tipSpace) / 2 - 2.dp.toPx()
        val peak = values.maxOf { abs(it.second) }.coerceAtLeast(1f)
        val slot = (size.width - left) / values.size
        val w = (slot * 0.6f).coerceAtMost(14.dp.toPx())
        val r = CornerRadius(w / 2)
        val active = selected ?: values.lastIndex
        drawLine(axis, Offset(0f, mid), Offset(size.width, mid), 1.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6f, 8f)))
        if (sideLabels) {
            drawText(measurer.measure("Charge", sideStyle), topLeft = Offset(0f, tipSpace))
            val d = measurer.measure("Drain", sideStyle)
            drawText(d, topLeft = Offset(0f, size.height - d.size.height))
        }
        values.forEachIndexed { i, (_, v) ->
            val h = (abs(v) / peak * half).coerceAtLeast(w * 0.6f)
            val c = if (v >= 0) up else down
            val x = left + i * slot + (slot - w) / 2
            val fill = if (i == active) c else c.copy(alpha = 0.35f)
            if (v >= 0) drawRoundRect(fill, Offset(x, mid - h), Size(w, h), r)
            else drawRoundRect(fill, Offset(x, mid), Size(w, h), r)
        }
        selected?.let { i ->
            val (label, v) = values[i]
            darkTooltip(measurer, listOf(format(v), label), left + i * slot + slot / 2, 0f, scheme.inverseSurface, tipStyle, dot = if (v >= 0) up else down)
        }
    }
}

/**
 * Glass cylinders filled with gradient "liquid" to each value (0–100). The selected/latest
 * cylinder glows in full colour; the rest are frosted grey.
 */
@Composable
fun CylinderChart(
    values: List<Pair<String, Float>>,
    color: Color,
    accent2: Color,
    format: (Float) -> String,
    modifier: Modifier = Modifier,
    height: Dp = 170.dp,
) {
    val measurer = rememberTextMeasurer()
    var selected by remember(values.size) { mutableStateOf<Int?>(null) }
    val scheme = MaterialTheme.colorScheme
    val tipStyle = MaterialTheme.typography.labelMedium.copy(color = scheme.inverseOnSurface)
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = scheme.onSurfaceVariant)
    val glass = scheme.onSurface.copy(alpha = 0.06f)
    val rim = scheme.onSurface.copy(alpha = 0.12f)
    val liquidIdle = scheme.onSurface.copy(alpha = 0.18f)
    Canvas(modifier.fillMaxWidth().height(height).slotSelection(values.size) { selected = it }) {
        if (values.isEmpty()) return@Canvas
        val tipSpace = 40.dp.toPx()
        val labelH = 16.dp.toPx()
        val top = tipSpace
        val bottom = size.height - labelH
        val slot = size.width / values.size
        val w = (slot * 0.74f).coerceAtMost(40.dp.toPx())
        val ell = w * 0.32f // ellipse height of the cylinder caps
        val active = selected ?: values.lastIndex
        values.forEachIndexed { i, (label, v) ->
            val x = i * slot + (slot - w) / 2
            // Glass body with rounded caps.
            drawRoundRect(glass, Offset(x, top), Size(w, bottom - top), CornerRadius(w / 2, ell / 2))
            drawOval(rim, Offset(x, top), Size(w, ell))
            // Liquid.
            val lvl = v.coerceIn(0f, 100f) / 100f
            val liquidTop = bottom - (bottom - top - ell / 2) * lvl
            val liquid = if (i == active) Brush.verticalGradient(listOf(accent2, color), startY = liquidTop, endY = bottom)
            else Brush.verticalGradient(listOf(liquidIdle, liquidIdle))
            drawRoundRect(liquid, Offset(x, liquidTop), Size(w, bottom - liquidTop), CornerRadius(w / 2, ell / 2))
            drawOval(if (i == active) accent2.copy(alpha = 0.9f) else rim, Offset(x, liquidTop - ell / 2), Size(w, ell))
            if (values.size <= 12 || i % 2 == values.size % 2) {
                val m = measurer.measure(label, if (i == active) labelStyle.copy(color = color) else labelStyle)
                drawText(m, topLeft = Offset(x + w / 2 - m.size.width / 2, bottom + 2.dp.toPx()))
            }
        }
        selected?.let { i ->
            val (label, v) = values[i]
            darkTooltip(measurer, listOf(format(v), label), i * slot + slot / 2, 0f, scheme.inverseSurface, tipStyle, dot = color)
        }
    }
}

/**
 * "Day's range" track: min and max labels, a gradient fill between them, and a marker at the
 * current value.
 */
@Composable
fun RangeTrack(
    min: Float,
    max: Float,
    now: Float,
    color: Color,
    format: (Float) -> String,
    modifier: Modifier = Modifier,
    title: String = "Range",
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth()) {
        Row {
            Text(format(min), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            Text(format(max), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
        }
        Spacer(Modifier.height(8.dp))
        val track = scheme.onSurface.copy(alpha = 0.08f)
        val marker = scheme.onSurface
        Canvas(Modifier.fillMaxWidth().height(18.dp)) {
            val barH = 8.dp.toPx()
            val y = (size.height - barH) / 2
            drawRoundRect(track, Offset(0f, y), Size(size.width, barH), CornerRadius(barH / 2))
            val span = (max - min).takeIf { it > 1e-3f } ?: 1f
            val f = ((now - min) / span).coerceIn(0f, 1f)
            val x = size.width * f
            drawRoundRect(
                Brush.horizontalGradient(listOf(color.copy(alpha = 0.15f), color), startX = 0f, endX = x.coerceAtLeast(1f)),
                Offset(0f, y), Size(x.coerceAtLeast(barH), barH), CornerRadius(barH / 2),
            )
            drawLine(marker, Offset(x, 0f), Offset(x, size.height), 2.dp.toPx())
        }
    }
}

/** Category breakdown drawn as thin vertical stripes, grouped with a label and share above each group. */
@Composable
fun StripeBreakdown(parts: List<Triple<String, Float, Color>>, modifier: Modifier = Modifier, height: Dp = 56.dp) {
    val total = parts.sumOf { it.second.toDouble() }.toFloat().takeIf { it > 0f } ?: return
    val scheme = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth()) {
        parts.filter { it.second > 0f }.forEachIndexed { idx, (label, v, c) ->
            val share = v / total
            Column(Modifier.weight(share.coerceAtLeast(0.08f)).padding(end = if (idx < parts.size - 1) 6.dp else 0.dp)) {
                Row {
                    Canvas(Modifier.width(2.dp).height(28.dp)) { drawRect(c) }
                    Column(Modifier.padding(start = 6.dp)) {
                        Text(label, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant, maxLines = 1)
                        Text("${(share * 100).toInt()}%", style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Canvas(Modifier.fillMaxWidth().height(height)) {
                    val stripe = 3.dp.toPx()
                    val gap = 2.5.dp.toPx()
                    var x = 0f
                    var k = 0
                    while (x + stripe <= size.width) {
                        // Stripes fade in from left to right within the group, like the reference.
                        val a = 0.35f + 0.65f * (x / size.width)
                        drawRoundRect(c.copy(alpha = a), Offset(x, 0f), Size(stripe, size.height), CornerRadius(stripe / 2))
                        x += stripe + gap
                        k++
                    }
                }
            }
        }
    }
}

/**
 * Dot heatmap: rows × columns of rounded squares whose intensity is the value (0..1). Used for
 * "when you charge" across days × hours.
 */
@Composable
fun DotHeatmap(
    rows: List<String>,
    columnsLabels: List<String>,
    values: List<List<Float>>,
    color: Color,
    modifier: Modifier = Modifier,
    describe: (row: Int, col: Int, value: Float) -> List<String> = { r, c, v -> listOf("${(v * 100).toInt()}%", "${rows[r]} · ${c}h") },
) {
    val measurer = rememberTextMeasurer()
    val scheme = MaterialTheme.colorScheme
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = scheme.onSurfaceVariant)
    val tipStyle = MaterialTheme.typography.labelMedium.copy(color = scheme.inverseOnSurface)
    val empty = scheme.onSurface.copy(alpha = 0.06f)
    val cols = values.firstOrNull()?.size ?: 0
    val density = androidx.compose.ui.platform.LocalDensity.current
    val labelW = remember(rows, labelStyle) {
        (rows.maxOfOrNull { measurer.measure(it, labelStyle).size.width } ?: 0) + with(density) { 6.dp.toPx() }
    }
    val rowHpx = with(density) { 16.dp.toPx() }
    var selected by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    Canvas(
        modifier.fillMaxWidth().height((rows.size * 16 + 18).dp).touchSelection(values, { p, s ->
            val r = (p.y / rowHpx).toInt()
            val c = ((p.x - labelW) / ((s.width - labelW) / cols.coerceAtLeast(1))).toInt()
            if (cols == 0 || r !in rows.indices || c !in 0 until cols || p.x < labelW) null else r to c
        }) { selected = it }
    ) {
        if (cols == 0) return@Canvas
        val cell = (size.width - labelW) / cols
        val dot = cell * 0.78f
        val rowH = rowHpx
        rows.forEachIndexed { r, name ->
            val m = measurer.measure(name, labelStyle)
            drawText(m, topLeft = Offset(0f, r * rowH + (rowH - m.size.height) / 2))
            for (c in 0 until cols) {
                val v = values[r][c].coerceIn(0f, 1f)
                val x = labelW + c * cell + (cell - dot) / 2
                val y = r * rowH + (rowH - dot) / 2
                drawRoundRect(if (v <= 0f) empty else color.copy(alpha = 0.25f + 0.75f * v), Offset(x, y), Size(dot, dot), CornerRadius(dot * 0.3f))
                if (selected == r to c) {
                    drawRoundRect(scheme.onSurface, Offset(x - 1.5f.dp.toPx(), y - 1.5f.dp.toPx()), Size(dot + 3.dp.toPx(), dot + 3.dp.toPx()), CornerRadius(dot * 0.35f), style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f.dp.toPx()))
                }
            }
        }
        columnsLabels.forEachIndexed { i, label ->
            if (label.isEmpty()) return@forEachIndexed
            val m = measurer.measure(label, labelStyle)
            val c = i * (cols - 1f) / (columnsLabels.size - 1).coerceAtLeast(1)
            val x = (labelW + c * cell + cell / 2 - m.size.width / 2).coerceIn(labelW, size.width - m.size.width)
            drawText(m, topLeft = Offset(x, rows.size * rowH + 2.dp.toPx()))
        }
        selected?.let { (r, c) ->
            val lines = describe(r, c, values[r][c])
            val tipH = lines.size * 18.dp.toPx() + 14.dp.toPx()
            // Above the pressed dot, or below it on the top rows.
            val top = if (r * rowH - tipH - 4.dp.toPx() >= 0f) r * rowH - tipH - 4.dp.toPx() else (r + 1) * rowH + 4.dp.toPx()
            darkTooltip(measurer, lines, labelW + c * cell + cell / 2, top, scheme.inverseSurface, tipStyle, dot = color)
        }
    }
}
