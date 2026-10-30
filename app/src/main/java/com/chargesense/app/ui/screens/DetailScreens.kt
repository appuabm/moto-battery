package com.chargesense.app.ui.screens

import com.chargesense.app.ui.theme.Space
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.chargesense.app.data.HistoryDb
import com.chargesense.app.data.LiveState
import com.chargesense.app.data.Sample
import com.chargesense.app.data.Session
import com.chargesense.app.data.formatMinutes
import com.chargesense.app.ui.components.BigValue
import com.chargesense.app.ui.components.DashCard
import com.chargesense.app.ui.components.InteractiveLineChart
import com.chargesense.app.ui.components.KeyValue
import com.chargesense.app.ui.components.DividedStats
import com.chargesense.app.ui.components.fmtNum
import com.chargesense.app.ui.components.DivergingBarChart
import com.chargesense.app.ui.components.RangeTrack
import com.chargesense.app.ui.theme.currentColor
import com.chargesense.app.ui.theme.tempColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import kotlin.math.abs
import kotlin.math.roundToInt

enum class Metric(val title: String) {
    Level("Battery level"),
    Current("Electric current"),
    Temperature("Battery temperature"),
}

private enum class Range(val label: String, val ms: Long) {
    Live("Live", 15 * 60_000L),
    Hour("1 h", 60 * 60_000L),
    SixHours("6 h", 6 * 60 * 60_000L),
    Day("24 h", 24 * 60 * 60_000L),
    Week("7 d", 7 * 24 * 60 * 60_000L),
}

/** One reading, from either the live buffer or the stored samples. */
private data class Point(val ts: Long, val level: Float, val currentMa: Int?, val tempC: Float, val voltageMv: Int)

private fun Sample.toPoint() = Point(ts, level, currentMa, tempC, voltageMv)

/** Navigation key for a detail page: "metric:<name>" or "session:<id>" (-1 = the session in progress). */
object DetailKey {
    fun metric(m: Metric) = "metric:${m.name}"
    fun session(id: Long) = "session:$id"
    fun title(key: String): String = when {
        key.startsWith("metric:") -> Metric.valueOf(key.substringAfter(':')).title
        key == session(-1) -> "Current session"
        else -> "Session details"
    }
}

@Composable
fun DetailScreen(key: String) {
    when {
        key == DetailKey.metric(Metric.Level) -> LevelDetailScreen()
        key.startsWith("metric:") -> MetricDetailScreen(Metric.valueOf(key.substringAfter(':')))
        key.startsWith("session:") -> SessionDetailScreen(key.substringAfter(':').toLong())
    }
}

@Composable
private fun MetricDetailScreen(metric: Metric) {
    val context = LocalContext.current
    val db = remember { HistoryDb.get(context) }
    var range by rememberSaveable(metric) { mutableStateOf(if (metric == Metric.Level) Range.Day else Range.Live) }
    val recent by LiveState.recent.collectAsState()
    val snapshot by LiveState.snapshot.collectAsState()
    var samples by remember { mutableStateOf<List<Sample>>(emptyList()) }

    LaunchedEffect(range) {
        if (range == Range.Live) return@LaunchedEffect
        while (true) {
            samples = withContext(Dispatchers.IO) { db.samplesSince(System.currentTimeMillis() - range.ms) }
            delay(60_000)
        }
    }

    val pts = if (range == Range.Live) {
        recent.map { Point(it.timestamp, it.levelPct, it.currentMa, it.temperatureC, it.voltageMv) }
    } else samples.map { it.toPoint() }

    val scheme = MaterialTheme.colorScheme
    val (series, color, unit, fmt) = when (metric) {
        Metric.Level -> Quad(pts.map { it.ts to it.level }, scheme.primary, "%", { v: Float -> "${v.roundToInt()}%" })
        Metric.Current -> Quad(
            pts.mapNotNull { p -> p.currentMa?.let { p.ts to it.toFloat() } },
            currentColor(snapshot?.currentMa), "mA", { v: Float -> "${v.roundToInt()} mA" },
        )
        Metric.Temperature -> Quad(pts.map { it.ts to it.tempC }, scheme.secondary, "°C", { v: Float -> "%.1f °C".format(v) })
    }
    val now: Float? = snapshot?.let {
        when (metric) {
            Metric.Level -> it.levelPct
            Metric.Current -> it.currentMa?.toFloat()
            Metric.Temperature -> it.temperatureC
        }
    }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Space.Gutter).padding(top = 4.dp, bottom = Space.Bottom),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Same pill tabs as the rest of the app.
        com.chargesense.app.ui.components.PillTabs(
            Range.entries.map { it.label }, range.ordinal, { range = Range.entries[it] }, compact = true,
        )

        DashCard(null) {
            Text("Now", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
            BigValue(
                now?.let {
                    when (metric) {
                        Metric.Level -> "${it.roundToInt()}"
                        Metric.Current -> (if (it > 0) "+" else "") + it.roundToInt()
                        Metric.Temperature -> "%.1f".format(it)
                    }
                } ?: "—",
                unit,
                if (metric == Metric.Temperature && now != null) tempColor(now) else color,
            )
            Spacer(Modifier.height(12.dp))
            InteractiveLineChart(
                series, color, fmt, height = 240.dp,
                minY = if (metric == Metric.Level) 0f else null,
                maxY = if (metric == Metric.Level) 100f else null,
            )
            Text(
                "Press and drag on the graph to see exact values",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        val vals = series.map { it.second }
        if (vals.isNotEmpty()) {
            DashCard(null) {
                Row {
                    StatCol("Minimum", fmt(vals.min()), Modifier.weight(1f))
                    StatCol("Average", fmt(vals.average().toFloat()), Modifier.weight(1f))
                    StatCol("Maximum", fmt(vals.max()), Modifier.weight(1f))
                }
            }
        }

        when (metric) {
            Metric.Level -> SecondaryChart(
                "Voltage", pts.filter { it.voltageMv > 0 }.map { it.ts to it.voltageMv / 1000f }, scheme.tertiary,
            ) { "%.2f V".format(it) }
            Metric.Current -> {
                if (series.size >= 2) {
                    DashCard("Charge vs drain") {
                        DivergingBarChart(bucketed(series, 24), scheme.primary, scheme.tertiary, { "${it.roundToInt()} mA" }, height = 170.dp, sideLabels = true)
                    }
                }
                SecondaryChart(
                    "Power",
                    pts.mapNotNull { p -> p.currentMa?.let { p.ts to it * p.voltageMv / 1_000_000f } },
                    scheme.tertiary,
                ) { "%.2f W".format(it) }
            }
            Metric.Temperature -> if (series.isNotEmpty() && now != null) {
                DashCard("Temperature range") {
                    RangeTrack(series.minOf { it.second }, series.maxOf { it.second }, now, scheme.secondary, { "%.1f °C".format(it) }, title = range.label)
                }
            }
        }

        DashCard("About this reading") {
            Text(
                when (metric) {
                    Metric.Level -> "The charge level reported by the battery fuel gauge. Keeping it between 20% and 80% slows down wear."
                    Metric.Current -> "Positive values mean current flowing into the battery, negative values mean the phone is draining it. " +
                        "Live samples every 3 seconds while the screen is on; history is saved once a minute."
                    Metric.Temperature -> "Lithium batteries age fastest when hot. Above 40 °C while charging, consider unplugging or removing the case."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

@Composable
private fun SecondaryChart(title: String, series: List<Pair<Long, Float>>, color: Color, fmt: (Float) -> String) {
    DashCard(title) {
        InteractiveLineChart(series, color, fmt, height = 180.dp)
    }
}

@Composable
private fun StatCol(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun SessionDetailScreen(id: Long) {
    val context = LocalContext.current
    val db = remember { HistoryDb.get(context) }
    val live by LiveState.activeSession.collectAsState()
    var stored by remember { mutableStateOf<Session?>(null) }
    var samples by remember { mutableStateOf<List<Sample>>(emptyList()) }

    LaunchedEffect(id) {
        if (id >= 0) stored = withContext(Dispatchers.IO) { db.sessions(500).firstOrNull { it.id == id } }
    }
    val session = if (id < 0) live else stored
    LaunchedEffect(session?.startTs) {
        val s = session ?: return@LaunchedEffect
        while (true) {
            val end = if (id < 0) System.currentTimeMillis() else s.endTs
            samples = withContext(Dispatchers.IO) { db.samplesSince(s.startTs).filter { it.ts <= end } }
            if (id >= 0) break
            delay(60_000)
        }
    }

    val s = session
    if (s == null) {
        Text("Session not found.", modifier = Modifier.padding(24.dp))
        return
    }
    val scheme = MaterialTheme.colorScheme
    val delta = s.endLevel - s.startLevel
    val fmt = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    val hours = s.durationMs / 3_600_000f

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Space.Gutter).padding(top = 4.dp, bottom = Space.Bottom),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DashCard(if (s.charging) "Charged" else "Used") {
            val day = remember { java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.getDefault()) }
            val time = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }
            Row(verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
                BigValue(
                    "${if (delta >= 0) "+" else "−"}${abs(delta).roundToInt()}", "%",
                    if (s.charging) scheme.primary else scheme.tertiary,
                )
                Text(
                    "   ${fmtNum(s.mah)} mAh ${if (s.charging) "charged" else "used"}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            Text(
                "${day.format(Date(s.startTs))} · ${time.format(Date(s.startTs))} – ${if (id < 0) "now" else time.format(Date(s.endTs))}" +
                    if (s.charging) " · ${s.plugType}" else "",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            DividedStats(
                listOf(
                    "Total time" to formatMinutes((s.durationMs / 60_000).toInt()),
                    (if (s.charging) "Charge speed" else "Drain rate") to (if (hours > 0.02f) "%.1f%%/h".format(abs(delta) / hours) else "—"),
                    "Average current" to "${s.avgCurrentMa.roundToInt()} mA",
                )
            )
        }

        DashCard("Battery level") {
            InteractiveLineChart(samples.map { it.ts to it.level }, scheme.primary, { "${it.roundToInt()}%" }, height = 200.dp, minY = 0f, maxY = 100f)
        }
        DashCard("Electric current") {
            InteractiveLineChart(
                samples.mapNotNull { p -> p.currentMa?.let { p.ts to it.toFloat() } },
                if (s.charging) scheme.primary else scheme.tertiary,
                { "${it.roundToInt()} mA" }, height = 200.dp,
            )
        }
        DashCard("Temperature") {
            InteractiveLineChart(samples.map { it.ts to it.tempC }, scheme.secondary, { "%.1f °C".format(it) }, height = 180.dp)
        }

        DashCard("Details") {
            if (s.charging) KeyValue("Charger", s.plugType)
            KeyValue("Level", "${s.startLevel.roundToInt()}% → ${s.endLevel.roundToInt()}%")
            KeyValue("Temperature min / avg / max", "%.1f / %.1f / %.1f °C".format(s.minTempC, s.avgTempC, s.maxTempC), tempColor(s.maxTempC))
            if (!s.charging) {
                KeyValue("Screen on", formatMinutes((s.screenOnMs / 60_000).toInt()) + (s.screenOnRatePerHour?.let { " · %.1f%%/h".format(it) } ?: ""))
                KeyValue("Screen off", formatMinutes((s.screenOffMs / 60_000).toInt()) + (s.screenOffRatePerHour?.let { " · %.1f%%/h".format(it) } ?: ""))
                s.deepSleepPct?.let { KeyValue("Deep sleep", "%.0f%% of screen off".format(it)) }
            }
            s.capacityEstimate?.let { KeyValue("Capacity estimate", "${it.roundToInt()} mAh") }
        }
        Spacer(Modifier.height(8.dp))
    }
}
