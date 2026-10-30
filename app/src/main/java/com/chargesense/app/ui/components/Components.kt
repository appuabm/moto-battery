package com.chargesense.app.ui.components

import com.chargesense.app.ui.theme.Space
import com.chargesense.app.ui.theme.Ph
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val CardShape = RoundedCornerShape(28.dp)

/** Rounded tonal card with an optional title row and chevron, as used across stock Android settings. */
@Composable
fun DashCard(
    title: String? = null,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    trailing: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(
            Modifier
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(Space.Card)
        ) {
            if (title != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FitText(
                        title,
                        MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        minScale = 0.8f,
                    )
                    trailing()
                    if (onClick != null) {
                        Icon(
                            Ph.CaretRight, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            content()
        }
    }
}

/** Big number with a smaller unit, e.g. "975 mA". */
@Composable
fun BigValue(value: String, unit: String = "", color: Color = MaterialTheme.colorScheme.onSurface) {
    Row {
        FitText(value, MaterialTheme.typography.displaySmall, color = color, modifier = Modifier.alignByBaseline().weight(1f, fill = false))
        if (unit.isNotEmpty()) {
            Text(
                unit,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
                // "%" hugs the number; word units like "mA" get a small gap.
                modifier = Modifier.alignByBaseline().padding(start = if (unit == "%") 2.dp else 6.dp),
            )
        }
    }
}

/** Pill-shaped status label, filled for emphasis. */
@Composable
fun StatusPill(text: String, icon: ImageVector?, emphasized: Boolean) {
    val bg = if (emphasized) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
    val fg = if (emphasized) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
    Surface(shape = CircleShape, color = bg) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, tint = fg, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(text, style = MaterialTheme.typography.labelLarge, color = fg)
        }
    }
}

@Composable
fun KeyValue(key: String, value: String, valueColor: Color = Color.Unspecified) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(key, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(12.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = valueColor)
    }
}

/** Full-circle progress ring with the level in the centre. */
@Composable
fun LevelRing(level: Float, color: Color, size: Dp = 128.dp, centerText: String? = null) {
    val animated by animateFloatAsState(level.coerceIn(0f, 100f), tween(700), label = "level")
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 12.dp.toPx()
            val inset = stroke / 2
            val arc = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
            drawArc(color, -90f, 360f * animated / 100f, false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        if (centerText != null) {
            Text(centerText, fontSize = 40.sp)
        } else Row(verticalAlignment = Alignment.Bottom) {
            Text("${level.toInt()}", fontSize = 40.sp, fontWeight = FontWeight.Normal)
            Text("%", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
        }
    }
}

/** Rounded vertical bars (0–100), the last one highlighted. Used for hourly battery level. */
@Composable
fun LevelBars(values: List<Float>, highlight: Color, modifier: Modifier = Modifier, height: Dp = 128.dp) {
    val bar = MaterialTheme.colorScheme.surfaceContainerHighest
    Canvas(modifier.fillMaxWidth().height(height)) {
        if (values.isEmpty()) return@Canvas
        val slots = maxOf(values.size, 12)
        val slotW = size.width / slots
        val w = slotW * 0.62f
        val r = CornerRadius(w / 2, w / 2)
        val offset = slots - values.size
        values.forEachIndexed { i, v ->
            val h = (v.coerceIn(2f, 100f) / 100f) * size.height
            val x = (offset + i) * slotW + (slotW - w) / 2
            drawRoundRect(
                if (i == values.lastIndex) highlight else bar,
                Offset(x, size.height - h), Size(w, h), r,
            )
        }
    }
}

/** Compact line chart without axes. */
@Composable
fun Sparkline(
    values: List<Float>,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 44.dp,
    fill: Boolean = false,
    format: ((Float) -> String)? = null,
) {
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val scheme = MaterialTheme.colorScheme
    val tipStyle = MaterialTheme.typography.labelMedium.copy(color = scheme.inverseOnSurface)
    var selected by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Int?>(null) }
    val touch = if (format == null || values.size < 2) Modifier else Modifier.touchSelection(values, { p, s ->
        (p.x / s.width * (values.size - 1) + 0.5f).toInt().coerceIn(0, values.size - 1)
    }) { selected = it }
    Canvas(modifier.fillMaxWidth().height(height).then(touch)) {
        if (values.size < 2) return@Canvas
        var lo = values.min()
        var hi = values.max()
        if (hi - lo < 1e-3f) { lo -= 1f; hi += 1f }
        val stroke = 2.5.dp.toPx()
        fun px(i: Int) = i * size.width / (values.size - 1)
        fun py(v: Float) = stroke + (1f - (v - lo) / (hi - lo)) * (size.height - 2 * stroke)
        val path = Path()
        values.forEachIndexed { i, v -> if (i == 0) path.moveTo(px(i), py(v)) else path.lineTo(px(i), py(v)) }
        if (fill) {
            val area = Path().apply {
                addPath(path)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), Color.Transparent)))
        }
        drawPath(path, color, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        val sel = selected
        if (sel != null && format != null) {
            val x = px(sel)
            drawLine(scheme.onSurface.copy(alpha = 0.3f), Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
            drawCircle(scheme.surface, 5.dp.toPx(), Offset(x, py(values[sel])))
            drawCircle(scheme.onSurface, 3.5f.dp.toPx(), Offset(x, py(values[sel])))
            // Tooltip sits above the line so the finger doesn't cover it.
            darkTooltip(measurer, listOf(format(values[sel])), x, -34.dp.toPx(), scheme.inverseSurface, tipStyle)
        }
    }
}

/** Two-segment bar, e.g. screen-on vs screen-off share. */
@Composable
fun SplitBar(fraction: Float, first: Color, second: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(10.dp)) {
        val r = CornerRadius(size.height / 2, size.height / 2)
        val gap = 4.dp.toPx()
        val f = fraction.coerceIn(0f, 1f)
        val w1 = (size.width - gap) * f
        if (w1 > 0f) drawRoundRect(first, Offset.Zero, Size(w1, size.height), r)
        val w2 = size.width - gap - w1
        if (w2 > 0f) drawRoundRect(second, Offset(if (w1 > 0f) w1 + gap else 0f, 0f), Size(if (w1 > 0f) w2 else size.width, size.height), r)
    }
}

/** Small coloured dot followed by a label and value, for legends. */
@Composable
fun LegendRow(color: Color, label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(10.dp)) { drawCircle(color) }
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Section heading in the stock Settings style. */
@Composable
fun SectionHeader(text: String) {
    // Moto style: bold label with a hairline rule running to the edge.
    MotoSection(text, Modifier.padding(horizontal = 16.dp))
}

/**
 * Single-line text that steps its font size down (to [minScale] of the style) until it fits, so
 * values aren't cut off on narrow screens or with a large system font size. Hidden until settled.
 */
@Composable
fun FitText(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    minScale: Float = 0.6f,
    role: String? = null,
) {
    val group = role?.let { LocalFitGroup.current?.get(it) }
    var own by androidx.compose.runtime.remember(text, style) { androidx.compose.runtime.mutableFloatStateOf(1f) }
    var ready by androidx.compose.runtime.remember(text, style) { androidx.compose.runtime.mutableStateOf(false) }
    val scale = if (group != null) minOf(own, group.scale) else own
    Text(
        text,
        style = style.copy(fontSize = style.fontSize * scale, lineHeight = style.lineHeight * scale),
        color = color,
        fontWeight = fontWeight,
        maxLines = 1,
        softWrap = false,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        onTextLayout = {
            if (it.hasVisualOverflow && scale > minScale) {
                val next = (scale - 0.06f).coerceAtLeast(minScale)
                own = next
                group?.let { g -> if (next < g.scale) g.scale = next }
            } else ready = true
        },
        modifier = modifier.drawWithContent { if (ready) drawContent() },
    )
}

/** Shared shrink factor for [FitText]s of one role in one row, so they stay one size. */
class FitGroup { var scale by androidx.compose.runtime.mutableFloatStateOf(1f) }

/** One [FitGroup] per role ("value", "label", ...) inside a [FitTogether] block. */
class FitGroups {
    private val groups = mutableMapOf<String, FitGroup>()
    operator fun get(role: String): FitGroup = groups.getOrPut(role) { FitGroup() }
}

val LocalFitGroup = androidx.compose.runtime.compositionLocalOf<FitGroups?> { null }

/** Runs [content] so FitTexts with the same role shrink together (e.g. all values in a row). */
@Composable
fun FitTogether(key: Any? = null, content: @Composable () -> Unit) {
    val g = androidx.compose.runtime.remember(key) { FitGroups() }
    androidx.compose.runtime.CompositionLocalProvider(LocalFitGroup provides g, content = content)
}
