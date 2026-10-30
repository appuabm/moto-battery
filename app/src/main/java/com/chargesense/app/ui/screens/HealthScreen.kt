package com.chargesense.app.ui.screens

import com.chargesense.app.ui.components.FitText

import com.chargesense.app.ui.theme.Space
import com.chargesense.app.ui.theme.Ph
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.unit.dp
import com.chargesense.app.data.DesignCapacity
import com.chargesense.app.data.Estimates
import com.chargesense.app.data.HistoryDb
import com.chargesense.app.data.LiveState
import com.chargesense.app.data.Prefs
import com.chargesense.app.data.Sample
import com.chargesense.app.data.Session
import com.chargesense.app.ui.components.DashCard
import com.chargesense.app.ui.components.InteractiveLineChart
import com.chargesense.app.ui.components.LegendRow
import com.chargesense.app.ui.components.DividedStats
import com.chargesense.app.ui.components.fmtNum
import com.chargesense.app.ui.components.StatusPill
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private data class HealthData(
    val sessions: List<Session> = emptyList(),
    val weekSamples: List<Sample> = emptyList(),
)

@Composable
fun HealthScreen() {
    val context = LocalContext.current
    val db = remember { HistoryDb.get(context) }
    val prefs = remember { Prefs.get(context) }
    val snapshot by LiveState.snapshot.collectAsState()
    val dbVersion by db.changes.collectAsState()
    val prefsVersion by prefs.version.collectAsState()

    var data by remember { mutableStateOf(HealthData()) }
    LaunchedEffect(dbVersion) {
        data = withContext(Dispatchers.IO) {
            HealthData(db.sessions(100), db.samplesSince(System.currentTimeMillis() - 7 * 24 * 60 * 60_000L))
        }
    }

    val design = remember(prefsVersion) { DesignCapacity.get(context) }
    val estimate = Estimates.capacity(snapshot, data.sessions)
    val health = Estimates.healthPct(estimate, design)
    val scheme = MaterialTheme.colorScheme
    val healthColor = when {
        health == null -> scheme.outline
        health >= 80 -> scheme.primary
        health >= 70 -> scheme.tertiary
        else -> scheme.error
    }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.padding(horizontal = Space.Gutter), verticalArrangement = Arrangement.spacedBy(Space.Gap)) {
            // Hero: health gauge
            GradientCard(healthColor) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlowRing((health ?: 0).toFloat(), healthColor, breathing = false, size = if (com.chargesense.app.ui.theme.LocalCompact.current) 128.dp else 150.dp, centerText = if (health == null) "—" else null, caption = "Battery health")
                    Spacer(Modifier.width(20.dp))
                    Column(Modifier.weight(1f)) {
                        Text(healthRating(health), style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            when {
                                estimate != null && design > 0 -> "${fmtNum(estimate.mah.coerceAtMost(design.toFloat()))} of ${fmtNum(design)} mAh"
                                design <= 0 -> "Set the design capacity in Settings"
                                else -> "Charge from below 50% to above 80% to measure"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(10.dp))
                        StatusPill("Estimated", Ph.ShieldCheck, emphasized = false)
                    }
                }
            }

            // Stat grid
            val week = data.weekSamples
            val avgTemp = week.takeIf { it.isNotEmpty() }?.map { it.tempC }?.average()?.toFloat()
            StatGrid(
                listOf(
                    Stat(Ph.BatteryFull, "Design capacity", if (design > 0) "${fmtNum(design)} mAh" else "—", scheme.primary),
                    Stat(Ph.BatteryFull, "Estimated capacity", estimate?.let { "${fmtNum(if (design > 0) it.mah.coerceAtMost(design.toFloat()) else it.mah)} mAh" } ?: "—", scheme.secondary),
                    Stat(Ph.TrendDown, "Wear level", health?.let { "${100 - it}%" } ?: "—", scheme.tertiary),
                    Stat(Ph.ArrowsClockwise, "Charge cycles", snapshot?.cycleCount?.toString() ?: "—", scheme.primary),
                    Stat(Ph.Thermometer, "Avg temp · 7 days", avgTemp?.let { "%.1f °C".format(it) } ?: "—", scheme.secondary),
                    Stat(Ph.ShieldCheck, "System status", snapshot?.healthText ?: "—", scheme.tertiary),
                )
            )

            // Capacity trend
            val measured = data.sessions.mapNotNull { s -> s.capacityEstimate?.let { s.endTs to it } }.sortedBy { it.first }
            DashCard("Capacity over time") {
                if (measured.size >= 2) {
                    InteractiveLineChart(
                        measured, scheme.primary, { "${it.roundToInt()} mAh" }, height = 200.dp,
                        gapMs = Long.MAX_VALUE,
                    )
                } else {
                    Text(
                        "Each charge or discharge covering at least 15% adds a measurement. The trend appears after two.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
                if (design > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text("Design capacity: ${fmtNum(design)} mAh", style = MaterialTheme.typography.labelMedium, color = scheme.primary)
                }
            }

            val active by LiveState.activeSession.collectAsState()
            ChargingHabitsCard((listOfNotNull(active) + data.sessions).filter { it.charging && it.levelDelta >= 1f })
            TemperatureExposureCard(week)
        }

        Text(
            "Tips for a longer battery life",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = Space.Gutter, top = 12.dp),
        )
        TipsRow()
        Spacer(Modifier.height(8.dp))
    }
}

private data class Stat(val icon: ImageVector, val label: String, val value: String, val tint: Color)

@Composable
private fun StatGrid(stats: List<Stat>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        stats.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { s ->
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(s.icon, s.tint, size = if (com.chargesense.app.ui.theme.LocalCompact.current) 34 else 40)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                FitText(s.value, MaterialTheme.typography.titleMedium)
                                FitText(s.label, MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Icon inside a softly tinted circle. */
@Composable
fun IconBadge(icon: ImageVector, tint: Color, size: Int = 40) {
    Surface(Modifier.size(size.dp), shape = RoundedCornerShape((size * 0.3f).dp), color = tint.copy(alpha = 0.18f)) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = tint, modifier = Modifier.size((size * 0.5).dp)) }
    }
}

@Composable
private fun ChargingHabitsCard(charges: List<Session>) {
    val scheme = MaterialTheme.colorScheme
    DashCard("Charging habits") {
        val recent = charges.take(30)
        if (recent.isEmpty()) {
            Text("Appears after your first recorded charge.", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            return@DashCard
        }
        val avgStart = recent.map { it.startLevel }.average().toFloat()
        val avgEnd = recent.map { it.endLevel }.average().toFloat()
        val over90 = recent.count { it.endLevel >= 90f } * 100 / recent.size
        val avgTemp = recent.map { it.avgTempC }.average().toFloat()

        Text(
            buildAnnotatedString {
                append("You usually charge from ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("${avgStart.roundToInt()}% to ${avgEnd.roundToInt()}%") }
            },
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(14.dp))
        // 0–100 track: shaded 20–80 sweet spot, filled bar for the usual charge range.
        val sweet = scheme.onSurface.copy(alpha = 0.16f)
        val track = scheme.surfaceContainerHighest
        val bar = scheme.primary
        Canvas(Modifier.fillMaxWidth().height(16.dp)) {
            val r = CornerRadius(size.height / 2, size.height / 2)
            drawRoundRect(track, Offset.Zero, size, r)
            drawRoundRect(sweet, Offset(size.width * 0.2f, 0f), Size(size.width * 0.6f, size.height), r)
            val x0 = size.width * avgStart / 100f
            val x1 = size.width * avgEnd / 100f
            drawRoundRect(bar, Offset(x0, 0f), Size((x1 - x0).coerceAtLeast(size.height), size.height), r)
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text("0%", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text("Ideal · 20–80%", style = MaterialTheme.typography.labelSmall, color = scheme.primary)
            Text("100%", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
        }
        Spacer(Modifier.height(14.dp))
        DividedStats(listOf("Charges" to recent.size.toString(), "Past 90%" to "$over90%", "Average temp" to "%.1f °C".format(avgTemp)))
        Spacer(Modifier.height(10.dp))
        Text(
            when {
                over90 > 50 -> "Most charges go past 90%. Setting the charge alarm to 80% would reduce wear."
                avgStart < 15f -> "You often charge from very low. Plugging in a little earlier is gentler on the battery."
                else -> "Your charging stays mostly in the healthy range."
            },
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HabitStat(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun TemperatureExposureCard(week: List<Sample>) {
    val scheme = MaterialTheme.colorScheme
    DashCard("Heat exposure · last 7 days") {
        if (week.size < 10) {
            Text("Builds up as monitoring runs.", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            return@DashCard
        }
        val cool = week.count { it.tempC < 35f } / week.size.toFloat()
        val warm = week.count { it.tempC in 35f..40f } / week.size.toFloat()
        val hot = 1f - cool - warm
        // Fixed cool / warm / hot hues so the three bands never blend with the accent colour.
        val dark = androidx.compose.foundation.isSystemInDarkTheme()
        val colors = if (dark) listOf(Color(0xFF6CB8FF), Color(0xFFF2B33D), Color(0xFFFF6B5E))
        else listOf(Color(0xFF1E6FD9), Color(0xFFA86A00), Color(0xFFC62828))
        Row {
            Column(Modifier.weight(1f)) {
                Text("Below 35°", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                Text("${(cool * 100).roundToInt()}%", style = MaterialTheme.typography.titleMedium)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(if (hot > 0.005f) "Above 40°" else "35–40°", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                Text("${((if (hot > 0.005f) hot else warm) * 100).roundToInt()}%", style = MaterialTheme.typography.titleMedium)
            }
        }
        Spacer(Modifier.height(10.dp))
        StripeBar(listOf(cool to colors[0], warm to colors[1], hot to colors[2]))
        Spacer(Modifier.height(12.dp))
        LegendRow(colors[0], "Below 35 °C", "${(cool * 100).roundToInt()}%")
        LegendRow(colors[1], "35 – 40 °C", "${(warm * 100).roundToInt()}%")
        LegendRow(colors[2], "Above 40 °C", "${(hot * 100).roundToInt()}%")
    }
}

private data class Tip(val icon: ImageVector, val title: String, val body: String)

@Composable
private fun TipsRow() {
    val tips = listOf(
        Tip(Ph.BatteryCharging, "Stop at 80%", "Lithium cells age fastest when kept full. Use the charge alarm to unplug around 80%."),
        Tip(Ph.Thermometer, "Keep it cool", "Heat is the biggest cause of wear. Avoid charging above 40 °C, in the sun or under a pillow."),
        Tip(Ph.Lightning, "Charge gently", "Fast charging is fine, but it heats the battery. Prefer a slow charger overnight."),
        Tip(Ph.MoonStars, "Avoid 0%", "Deep discharges stress the cell. Plug in before the battery drops below 15–20%."),
        Tip(Ph.Snowflake, "Mind the cold", "Below 0 °C capacity drops temporarily. Warm the phone up before charging."),
        Tip(Ph.Package, "Storing it away", "Leave it at around 50% and power it off if you won't use it for weeks."),
    )
    val scheme = MaterialTheme.colorScheme
    val tints = listOf(scheme.primary, scheme.error, scheme.tertiary, scheme.secondary, scheme.primary, scheme.tertiary)
    // All tip titles share one size.
    com.chargesense.app.ui.components.FitTogether(tips.size) { Column(Modifier.padding(horizontal = Space.Gutter), verticalArrangement = Arrangement.spacedBy(Space.Gap)) {
        tips.zip(tints).chunked(2).forEach { row ->
            Row(Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(Space.Gap)) {
                row.forEach { (tip, tint) ->
                    Card(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        shape = RoundedCornerShape(28.dp),
                        colors = CardDefaults.cardColors(containerColor = tint.copy(alpha = 0.12f)),
                    ) {
                        Column(Modifier.padding(18.dp)) {
                            IconBadge(tip.icon, tint, size = 44)
                            Spacer(Modifier.height(14.dp))
                            FitText(tip.title, MaterialTheme.typography.titleMedium, minScale = 0.75f, role = "tip")
                            Spacer(Modifier.height(6.dp))
                            Text(tip.body, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    } }
}

/** One bar made of thin vertical stripes, coloured by each part's share from left to right. */
@Composable
private fun StripeBar(parts: List<Pair<Float, Color>>) {
    val total = parts.sumOf { it.first.toDouble() }.toFloat().takeIf { it > 0f } ?: return
    Canvas(Modifier.fillMaxWidth().height(48.dp)) {
        val stripe = 3.dp.toPx()
        val gap = 2.dp.toPx()
        val count = ((size.width + gap) / (stripe + gap)).toInt()
        for (i in 0 until count) {
            val f = (i + 0.5f) / count
            var acc = 0f
            val c = parts.firstOrNull { acc += it.first / total; f <= acc }?.second ?: parts.last().second
            drawRoundRect(c, Offset(i * (stripe + gap), 0f), Size(stripe, size.height), CornerRadius(stripe / 2))
        }
    }
}
