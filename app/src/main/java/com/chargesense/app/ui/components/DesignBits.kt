package com.chargesense.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import kotlin.math.roundToInt

/** Whole number with the locale's thousands separator, e.g. 3,668. */
fun fmtNum(v: Number): String = NumberFormat.getIntegerInstance().format(v.toFloat().roundToInt())

/** A row of label-over-value stats separated by thin vertical dividers. */
@Composable
fun DividedStats(
    items: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
    centered: Boolean = false,
    valueColors: List<Color?> = emptyList(),
    onClick: ((Int) -> Unit)? = null,
    captions: List<String?> = emptyList(),
) {
    val scheme = MaterialTheme.colorScheme
    FitTogether(items.size) { Row(modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        items.forEachIndexed { i, (label, value) ->
            if (i > 0) VerticalDivider(Modifier.fillMaxHeight().padding(vertical = 2.dp), color = scheme.outlineVariant.copy(alpha = 0.6f))
            // Columns share the width by content length, so a long value ("7,000 mAh") gets more room.
            val weight = maxOf(label.length.toFloat(), value.length * 1.25f, (captions.getOrNull(i)?.length ?: 0).toFloat()).coerceAtLeast(5f)
            val cell = Modifier.weight(weight)
                // No clip here: a rounded clip trims glyphs that overhang their box (the "B" in "Battery").
                .then(if (onClick != null) Modifier.clickable { onClick(i) } else Modifier)
                .padding(start = if (i == 0 || centered) 0.dp else 10.dp, end = if (centered) 0.dp else 4.dp)
            Column(cell, horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start) {
                FitText(label, MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant, role = "label", minScale = 0.62f)
                FitText(
                    value,
                    MaterialTheme.typography.titleMedium,
                    color = valueColors.getOrNull(i) ?: Color.Unspecified,
                    modifier = Modifier.padding(top = 4.dp),
                    role = "value",
                    minScale = 0.68f,
                )
                captions.getOrNull(i)?.let {
                    FitText(it, MaterialTheme.typography.labelSmall, color = valueColors.getOrNull(i) ?: scheme.onSurfaceVariant, role = "caption")
                }
            }
        }
    } }
}

/** Key–value rows separated by hairline dividers; a row's value can carry its own colour. */
@Composable
fun DetailRows(rows: List<Triple<String, String, Color?>>) {
    val scheme = MaterialTheme.colorScheme
    Column {
        rows.forEachIndexed { i, (k, v, c) ->
            if (i > 0) HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.35f))
            Row(Modifier.fillMaxWidth().padding(vertical = 11.dp)) {
                Text(k, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text(v, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = c ?: Color.Unspecified)
            }
        }
    }
}

/**
 * Rounded bars with their value printed above, dashed 0/50/100 gridlines, and labels beneath.
 * The latest (or pressed) bar is highlighted and its value shown in a chip.
 */
@Composable
fun ValueBarChart(
    bars: List<Pair<String, Float>>,
    color: Color,
    valueLabel: (Float) -> String,
    modifier: Modifier = Modifier,
    height: Dp = 190.dp,
    max: Float = 100f,
    axis: Boolean = true,
    axisLabel: (Float) -> String = { "${it.roundToInt()}%" },
) {
    val measurer = rememberTextMeasurer()
    val scheme = MaterialTheme.colorScheme
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = scheme.onSurfaceVariant)
    val valueStyle = MaterialTheme.typography.labelMedium.copy(color = scheme.onSurfaceVariant)
    val chipStyle = MaterialTheme.typography.labelMedium.copy(color = scheme.onPrimaryContainer, fontWeight = FontWeight.SemiBold)
    val chipBg = scheme.primaryContainer
    val idle = scheme.onSurface.copy(alpha = 0.22f)
    val grid = scheme.outlineVariant.copy(alpha = 0.5f)
    var selected by remember(bars.size) { mutableStateOf<Int?>(null) }
    val slots = maxOf(bars.size, 3)
    val offset = (slots - bars.size) / 2 // few bars sit centred
    Canvas(
        modifier.fillMaxWidth().height(height)
            .slotSelection(slots) { selected = it?.minus(offset)?.takeIf { i -> i in bars.indices } }
    ) {
        if (bars.isEmpty()) return@Canvas
        val axisW = if (axis) measurer.measure("100%", labelStyle).size.width + 10.dp.toPx() else 0f
        val top = 30.dp.toPx()
        val bottom = size.height - 22.dp.toPx()
        val plotW = size.width - axisW
        if (axis) {
            listOf(max, max / 2, 0f).forEach { v ->
                val y = bottom - v / max * (bottom - top)
                drawLine(grid, Offset(axisW, y), Offset(size.width, y), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 8f)))
                val m = measurer.measure(axisLabel(v), labelStyle)
                drawText(m, topLeft = Offset(0f, y - m.size.height / 2f))
            }
        }
        val slot = plotW / slots
        val w = (slot * 0.42f).coerceAtMost(44.dp.toPx())
        val active = selected ?: bars.lastIndex
        bars.forEachIndexed { i, (label, v) ->
            val cx = axisW + (i + offset) * slot + slot / 2
            val h = (v.coerceIn(0f, max) / max * (bottom - top)).coerceAtLeast(6.dp.toPx())
            drawRoundRect(if (i == active) color else idle, Offset(cx - w / 2, bottom - h), Size(w, h), CornerRadius(10.dp.toPx()))
            // Value above the bar: a filled chip for the active bar, plain text otherwise.
            val text = valueLabel(v)
            if (i == active) {
                val m = measurer.measure(text, chipStyle)
                val pw = m.size.width + 14.dp.toPx()
                val ph = m.size.height + 6.dp.toPx()
                val y = bottom - h - ph - 6.dp.toPx()
                drawRoundRect(chipBg, Offset(cx - pw / 2, y), Size(pw, ph), CornerRadius(8.dp.toPx()))
                drawText(m, topLeft = Offset(cx - m.size.width / 2f, y + 3.dp.toPx()))
            } else {
                val m = measurer.measure(text, valueStyle)
                drawText(m, topLeft = Offset(cx - m.size.width / 2f, bottom - h - m.size.height - 6.dp.toPx()))
            }
            val lm = measurer.measure(label, if (i == active) labelStyle.copy(color = scheme.onSurface) else labelStyle)
            drawText(lm, topLeft = Offset(cx - lm.size.width / 2f, bottom + 6.dp.toPx()))
        }
    }
}
