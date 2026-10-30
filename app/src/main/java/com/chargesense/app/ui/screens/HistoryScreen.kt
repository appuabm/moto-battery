package com.chargesense.app.ui.screens

import com.chargesense.app.ui.components.FitText

import com.chargesense.app.ui.theme.Space
import com.chargesense.app.ui.theme.Ph
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chargesense.app.data.HistoryDb
import com.chargesense.app.data.IdleLog
import com.chargesense.app.data.IdlePeriod
import com.chargesense.app.data.LiveState
import com.chargesense.app.data.Sample
import com.chargesense.app.data.Session
import com.chargesense.app.data.formatMinutes
import com.chargesense.app.ui.components.DotHeatmap
import com.chargesense.app.ui.components.GhostBarChart
import com.chargesense.app.ui.components.ValueBarChart
import com.chargesense.app.ui.components.DashCard
import com.chargesense.app.ui.components.PillTabs
import com.chargesense.app.ui.components.DividedStats
import com.chargesense.app.ui.components.BigValue
import com.chargesense.app.ui.components.OutlinePillButton
import com.chargesense.app.ui.components.StatusPill
import com.chargesense.app.ui.theme.tempColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class HistoryTab(val label: String, val icon: ImageVector) {
    Charging("Charging", Ph.Lightning),
    Discharging("Discharging", Ph.TrendDown),
    Idle("Idle log", Ph.Moon),
}

@Composable
fun HistoryScreen(onOpenSession: (Long) -> Unit, startSection: String? = null) {
    val context = LocalContext.current
    val db = remember { HistoryDb.get(context) }
    val version by db.changes.collectAsState()
    val active by LiveState.activeSession.collectAsState()
    var sessions by remember { mutableStateOf<List<Session>>(emptyList()) }
    var samples by remember { mutableStateOf<List<Sample>>(emptyList()) }
    var confirmClear by remember { mutableStateOf(false) }
    var tab by rememberSaveable { mutableStateOf(HistoryTab.entries.firstOrNull { it.name.equals(startSection, true) } ?: HistoryTab.Charging) }

    LaunchedEffect(version) {
        sessions = withContext(Dispatchers.IO) { db.sessions() }
    }
    LaunchedEffect(tab, version) {
        if (tab != HistoryTab.Discharging) {
            samples = withContext(Dispatchers.IO) { db.samplesSince(System.currentTimeMillis() - 7 * 24 * 60 * 60_000L) }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            icon = { Icon(Ph.Trash, null) },
            title = { Text("Clear all sessions?") },
            text = { Text("All recorded sessions, samples and capacity measurements will be removed.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    db.clearAll()
                }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }

    val charging = tab == HistoryTab.Charging
    val stored = sessions.filter { it.charging == charging }
    val inProgress = active?.takeIf { tab != HistoryTab.Idle && it.charging == charging }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Space.Gutter, end = Space.Gutter, top = 4.dp, bottom = Space.Bottom),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            PillTabs(
                HistoryTab.entries.map { it.label }, tab.ordinal, { tab = HistoryTab.entries[it] },
                icons = HistoryTab.entries.map { it.icon },
            )
        }

        when (tab) {
            HistoryTab.Idle -> idleItems(IdleLog.periods(samples))
            else -> {
                item { SessionsSummary(charging, listOfNotNull(inProgress) + stored) }
                if (charging) item { ChargingHeatmapCard(samples) }
                inProgress?.let { s ->
                    item(key = "active") {
                        DayHeader("Now")
                        SessionTile(s, inProgress = true) { onOpenSession(-1) }
                    }
                }
                if (stored.isEmpty() && inProgress == null) {
                    item { EmptyNote(if (charging) "Each charge of a few minutes or more appears here." else "Each time you unplug, a discharge session starts.", if (charging) Ph.Lightning else Ph.TrendDown) }
                }
                stored.groupBy { dayLabel(it.startTs) }.forEach { (day, list) ->
                    item(key = "h-$day-${list.first().id}") { DayHeader(day) }
                    items(list, key = { it.id }) { s -> SessionTile(s) { onOpenSession(s.id) } }
                }
            }
        }

        if (sessions.isNotEmpty()) {
            item {
                OutlinePillButton("Clear sessions", { confirmClear = true }, Modifier.fillMaxWidth().padding(top = 8.dp), icon = Ph.Trash)
            }
        }
    }
}

@Composable
private fun SessionsSummary(charging: Boolean, list: List<Session>) {
    val scheme = MaterialTheme.colorScheme
    val accent = if (charging) scheme.primary else scheme.tertiary
    GradientCard(accent) {
        Column {
            Text(
                if (charging) "Charging overview" else "Discharging overview",
                style = MaterialTheme.typography.titleSmall,
                color = scheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            if (list.isEmpty()) {
                Text("Nothing recorded yet.", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                return@Column
            }
            val totalMah = list.sumOf { it.mah.toDouble() }.roundToInt()
            val hours = list.sumOf { it.durationMs } / 3_600_000.0
            val pct = list.sumOf { it.levelDelta.toDouble() }
            Row {
                SummaryStat(if (charging) "Charged" else "Used", "$totalMah mAh", Modifier.weight(1f))
                SummaryStat(if (charging) "Avg speed" else "Avg drain", if (hours > 0.02) "%.1f%%/h".format(pct / hours) else "—", Modifier.weight(1f))
                SummaryStat("Sessions", list.size.toString(), Modifier.weight(1f))
            }
            if (!charging) {
                val onMs = list.sumOf { it.screenOnMs }
                val offMs = list.sumOf { it.screenOffMs }
                Spacer(Modifier.height(12.dp))
                Row {
                    SummaryStat(
                        "Screen on",
                        if (onMs > 10 * 60_000) "%.1f%%/h".format(list.sumOf { it.screenOnDrop.toDouble() } / (onMs / 3_600_000.0)) else "—",
                        Modifier.weight(1f),
                    )
                    SummaryStat(
                        "Screen off",
                        if (offMs > 30 * 60_000) "%.1f%%/h".format(list.sumOf { it.screenOffDrop.toDouble() } / (offMs / 3_600_000.0)) else "—",
                        Modifier.weight(1f),
                    )
                    Spacer(Modifier.weight(1f))
                }
            }
            if (list.size >= 2) {
                Spacer(Modifier.height(16.dp))
                val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
                val bars = list.take(8).reversed().map { s ->
                    fmt.format(Date(s.startTs)) to s.levelDelta.coerceIn(0f, 100f)
                }
                val sign = if (charging) "+" else "−"
                ValueBarChart(
                    bars, accent, { "$sign${it.roundToInt()}%" }, height = 170.dp,
                    max = (bars.maxOf { it.second } * 1.3f).coerceIn(10f, 100f), axis = false,
                )
                Text(
                    if (charging) "Charge gained per session" else "Battery used per session",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun SummaryStat(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DayHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 6.dp, top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun EmptyNote(text: String, icon: ImageVector = Ph.Clock) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconBadge(icon, MaterialTheme.colorScheme.primary, size = 72)
        Spacer(Modifier.height(16.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

private fun dayLabel(ts: Long): String {
    val day = Calendar.getInstance().apply { timeInMillis = ts }
    val today = Calendar.getInstance()
    fun same(a: Calendar, b: Calendar) = a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    if (same(day, today)) return "Today"
    today.add(Calendar.DAY_OF_YEAR, -1)
    if (same(day, today)) return "Yesterday"
    return SimpleDateFormat("EEEE, d MMM", Locale.getDefault()).format(Date(ts))
}

/** Session as a tinted tile: big signed %, a start→end level bar and stat chips. */
@Composable
private fun SessionTile(s: Session, inProgress: Boolean = false, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val accent = if (s.charging) scheme.primary else scheme.tertiary
    val time = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }
    val delta = s.endLevel - s.startLevel
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = scheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(if (s.charging) Ph.Lightning else Ph.TrendDown, accent, size = 44)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "${if (delta >= 0) "+" else "−"}${abs(delta).roundToInt()}%",
                        style = MaterialTheme.typography.headlineSmall,
                        color = accent,
                        fontWeight = FontWeight.SemiBold,
                    )
                    FitText(
                        "${time.format(Date(s.startTs))} – ${if (inProgress) "now" else time.format(Date(s.endTs))}" +
                            if (s.charging) " · ${s.plugType}" else "",
                        MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
                if (inProgress) StatusPill("Live", Ph.Broadcast, emphasized = false)
            }
            Spacer(Modifier.height(14.dp))
            LevelRangeBar(s.startLevel, s.endLevel, accent)
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Text("${s.startLevel.roundToInt()}%", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text("${s.endLevel.roundToInt()}%", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(Ph.Clock, formatMinutes((s.durationMs / 60_000).toInt()))
                Chip(Ph.Lightning, "${s.mah.roundToInt()} mAh")
                Chip(Ph.Thermometer, "%.0f°".format(s.maxTempC), tempColor(s.maxTempC))
            }
            if (!s.charging && (s.screenOnRatePerHour != null || s.screenOffRatePerHour != null)) {
                Spacer(Modifier.height(10.dp))
                Text(
                    listOfNotNull(
                        s.screenOnRatePerHour?.let { "Screen on · %.1f%%/h".format(it) },
                        s.screenOffRatePerHour?.let { "Screen off · %.1f%%/h".format(it) },
                        s.deepSleepPct?.let { "Deep sleep · %.0f%%".format(it) },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 0–100 track with the session's covered range filled and dots at both ends. */
@Composable
private fun LevelRangeBar(start: Float, end: Float, color: Color) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Canvas(Modifier.fillMaxWidth().height(10.dp)) {
        val barH = 6.dp.toPx()
        val y = (size.height - barH) / 2
        val r = CornerRadius(barH / 2, barH / 2)
        drawRoundRect(track, Offset(0f, y), Size(size.width, barH), r)
        val lo = minOf(start, end).coerceIn(0f, 100f) / 100f * size.width
        val hi = maxOf(start, end).coerceIn(0f, 100f) / 100f * size.width
        if (hi - lo < size.height * 1.5f) drawCircle(color, size.height / 2, Offset((lo + hi) / 2, size.height / 2))
        else drawRoundRect(color, Offset(lo, y), Size(hi - lo, barH), r)
    }
}

@Composable
private fun Chip(icon: ImageVector, text: String, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

private fun LazyListScope.idleItems(periods: List<IdlePeriod>) {
    item { IdleSummary(periods) }
    if (periods.isEmpty()) {
        item { EmptyNote("Each time the screen stays off on battery for 15 minutes or more, it's logged here with its drain and deep sleep.", Ph.Moon) }
    }
    periods.groupBy { dayLabel(it.startTs) }.forEach { (day, list) ->
        item(key = "ih-$day-${list.first().startTs}") { DayHeader(day) }
        items(list, key = { it.startTs }) { IdleTile(it) }
    }
}

@Composable
private fun IdleSummary(periods: List<IdlePeriod>) {
    val scheme = MaterialTheme.colorScheme
    GradientCard(scheme.tertiary) {
        Column {
            Text("Idle overview · last 7 days", style = MaterialTheme.typography.titleSmall, color = scheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            if (periods.isEmpty()) {
                Text("Nothing logged yet.", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                return@Column
            }
            val totalMs = periods.sumOf { it.durationMs }
            val drop = periods.sumOf { it.drop.toDouble() }
            val sleep = (periods.sumOf { it.deepSleep.toDouble() * it.durationMs } / totalMs).toFloat()
            BigValue("%.0f".format(sleep * 100), "%", scheme.tertiary)
            Text("Average deep sleep while idle", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            SleepBar(sleep, scheme.tertiary, 10.dp)
            Spacer(Modifier.height(18.dp))
            DividedStats(
                listOf(
                    "Idle drain" to "%.2f%%/h".format(drop / (totalMs / 3_600_000.0)),
                    "Time idle" to formatMinutes((totalMs / 60_000).toInt()),
                    "Periods" to periods.size.toString(),
                )
            )
            if (periods.size >= 2) {
                Spacer(Modifier.height(16.dp))
                val fmt = SimpleDateFormat("d MMM HH:mm", Locale.getDefault())
                // Bars show drain per hour, scaled so 5 %/h fills the bar.
                GhostBarChart(
                    periods.take(14).reversed().map { fmt.format(Date(it.startTs)) to (it.ratePerHour * 20f).coerceAtMost(100f) },
                    scheme.tertiary,
                    { "%.2f%%/h".format(it / 20f) },
                    height = 140.dp,
                )
                Text("Drain per idle period", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun IdleTile(p: IdlePeriod) {
    val scheme = MaterialTheme.colorScheme
    val time = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }
    Surface(shape = RoundedCornerShape(28.dp), color = scheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Ph.Moon, scheme.tertiary, size = 44)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("${time.format(Date(p.startTs))} – ${time.format(Date(p.endTs))}", style = MaterialTheme.typography.titleMedium)
                Text(
                    "${formatMinutes((p.durationMs / 60_000).toInt())} · deep sleep %.0f%%".format(p.deepSleep * 100),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                SleepBar(p.deepSleep, scheme.tertiary, 6.dp)
            }
            Spacer(Modifier.width(14.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text("−%.0f%%".format(p.drop), style = MaterialTheme.typography.titleLarge, color = scheme.tertiary)
                Text("%.2f%%/h".format(p.ratePerHour), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            }
        }
    }
}

/** 7 days × 24 hours: how often the phone was plugged in during each hour. */
@Composable
private fun ChargingHeatmapCard(samples: List<Sample>) {
    val scheme = MaterialTheme.colorScheme
    DashCard("When you charge · 7 days") {
        if (samples.size < 30) {
            Text("Fills in as monitoring runs through the week.", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            return@DashCard
        }
        val cal = Calendar.getInstance()
        val dayFmt = SimpleDateFormat("EEE", Locale.getDefault())
        val today = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        val dayStarts = (6 downTo 0).map { d -> today.timeInMillis - d * 24 * 60 * 60_000L }
        val plugged = Array(7) { IntArray(24) }
        val total = Array(7) { IntArray(24) }
        samples.forEach { s ->
            val d = dayStarts.indexOfLast { s.ts >= it }
            if (d < 0) return@forEach
            cal.timeInMillis = s.ts
            val h = cal.get(Calendar.HOUR_OF_DAY)
            total[d][h]++
            if (s.plugged) plugged[d][h]++
        }
        DotHeatmap(
            rows = dayStarts.map { dayFmt.format(Date(it)) },
            columnsLabels = listOf("0h", "6h", "12h", "18h", "23h"),
            values = (0 until 7).map { d -> (0 until 24).map { h -> if (total[d][h] == 0) 0f else plugged[d][h].toFloat() / total[d][h] } },
            color = scheme.primary,
            describe = { r, c, v ->
                listOf(if (total[r][c] == 0) "No data" else "Plugged in ${(v * 100).toInt()}% of the time", "${dayFmt.format(Date(dayStarts[r]))} · %02d:00".format(c))
            },
        )
    }
}

/** Rounded bar showing a 0..1 share (deep sleep). */
@Composable
private fun SleepBar(fraction: Float, color: Color, height: androidx.compose.ui.unit.Dp) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Canvas(Modifier.fillMaxWidth().height(height)) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(track, Offset.Zero, size, r)
        if (fraction > 0f) drawRoundRect(color, Offset.Zero, Size((size.width * fraction.coerceIn(0f, 1f)).coerceAtLeast(size.height), size.height), r)
    }
}
