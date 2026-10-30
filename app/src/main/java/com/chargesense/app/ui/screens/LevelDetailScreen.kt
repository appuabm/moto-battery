package com.chargesense.app.ui.screens

import com.chargesense.app.ui.theme.Space
import com.chargesense.app.ui.theme.Ph
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.chargesense.app.data.DesignCapacity
import com.chargesense.app.data.Estimates
import com.chargesense.app.data.HistoryDb
import com.chargesense.app.data.LiveState
import com.chargesense.app.data.Prefs
import com.chargesense.app.data.Sample
import com.chargesense.app.data.Session
import com.chargesense.app.data.formatMinutes
import com.chargesense.app.ui.components.BigValue
import com.chargesense.app.ui.components.DashCard
import com.chargesense.app.ui.components.InteractiveBarChart
import com.chargesense.app.ui.components.InteractiveLineChart
import com.chargesense.app.ui.components.Lane
import com.chargesense.app.ui.components.ValueBarChart
import com.chargesense.app.ui.components.PillTabs
import com.chargesense.app.ui.components.StatusPill
import com.chargesense.app.ui.components.TimelineLanes
import com.chargesense.app.ui.theme.ChargingGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt


/**
 * Battery level page: level and change over the last hour, remaining-time estimates split by
 * screen state, a state-coloured level graph (or hourly bars) and timeline lanes underneath.
 */
@Composable
fun LevelDetailScreen() {
    val context = LocalContext.current
    val db = remember { HistoryDb.get(context) }
    val prefs = remember { Prefs.get(context) }
    val snapshot by LiveState.snapshot.collectAsState()
    val smoothed by LiveState.smoothedCurrentMa.collectAsState()
    var sinceUnplug by rememberSaveable { mutableStateOf(false) }
    var lineMode by rememberSaveable { mutableStateOf(true) }
    var samples by remember { mutableStateOf<List<Sample>>(emptyList()) }
    var sessions by remember { mutableStateOf<List<Session>>(emptyList()) }

    LaunchedEffect(Unit) {
        while (true) {
            withContext(Dispatchers.IO) {
                samples = db.samplesSince(System.currentTimeMillis() - 24 * 60 * 60_000L)
                sessions = db.sessions(30)
            }
            delay(60_000)
        }
    }

    val s = snapshot ?: return
    val scheme = MaterialTheme.colorScheme
    val now = System.currentTimeMillis()

    // "Since unplug" = since the plug state last changed (or "since plugged in" while charging).
    val window = if (sinceUnplug) {
        val lastOther = samples.indexOfLast { it.plugged != s.isPlugged }
        samples.drop(lastOther + 1)
    } else samples
    val hourAgo = samples.firstOrNull { it.ts >= now - 60 * 60_000L }
    val lastHourDelta = s.levelPct - (hourAgo?.level ?: s.levelPct)

    // Average drain per screen state over recent discharges.
    val discharges = sessions.filter { !it.charging }
    val onMs = discharges.sumOf { it.screenOnMs }
    val offMs = discharges.sumOf { it.screenOffMs }
    val onRate = if (onMs > 10 * 60_000) discharges.sumOf { it.screenOnDrop.toDouble() } / (onMs / 3_600_000.0) else null
    val offRate = if (offMs > 30 * 60_000) discharges.sumOf { it.screenOffDrop.toDouble() } / (offMs / 3_600_000.0) else null
    val capacity = Estimates.capacity(s, sessions)?.mah ?: DesignCapacity.get(context).toFloat()
    val totalMins = Estimates.minutesRemaining(s, smoothed, capacity, prefs.chargeAlarmLevel)

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Space.Gutter).padding(top = 4.dp, bottom = Space.Bottom),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                BigValue("${s.levelPct.roundToInt()}", "%")
                if (hourAgo != null) {
                    val d = lastHourDelta.roundToInt()
                    Text(
                        "${if (d >= 0) "+" else "−"}${abs(d)}% in the last hour",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (d >= 0) ChargingGreen else scheme.error,
                    )
                }
            }
            StatusPill(s.statusText, if (s.isPlugged) Ph.Lightning else Ph.BatteryFull, emphasized = s.isPlugged)
        }

        PillTabs(
            listOf("Last 24h", if (s.isPlugged) "Since plugged in" else "Since unplug"),
            if (sinceUnplug) 1 else 0,
            { sinceUnplug = it == 1 },
        )

        DashCard("Estimated remaining time") {
            Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
                EstimateCol(
                    if (s.isPlugged) "Time to ${Estimates.chargeTargetFor(s, prefs.chargeAlarmLevel)}%" else "Total left",
                    totalMins?.let { formatMinutes(it) } ?: "—",
                    Modifier.weight(1f),
                )
                VerticalDivider()
                EstimateCol("Screen on left", onRate?.takeIf { it > 0 }?.let { formatMinutes((s.levelPct / it * 60).roundToInt()) } ?: "—", Modifier.weight(1f))
                VerticalDivider()
                EstimateCol("Screen off left", offRate?.takeIf { it > 0 }?.let { formatMinutes((s.levelPct / it * 60).roundToInt()) } ?: "—", Modifier.weight(1f))
            }
        }

        DashCard(null) {
            Box(Modifier.fillMaxWidth()) {
                if (lineMode) {
                    val pts = window.map { it.ts to it.level }.ifEmpty { listOf(s.timestamp to s.levelPct) }
                    val colors = window.map {
                        when {
                            it.plugged -> ChargingGreen
                            it.level <= 15f -> scheme.error
                            else -> scheme.primary
                        }
                    }.ifEmpty { listOf(scheme.primary) }
                    InteractiveLineChart(pts, scheme.primary, { "${it.roundToInt()}%" }, height = 260.dp, minY = 0f, maxY = 100f, pointColors = colors)
                } else {
                    val fmt = remember { SimpleDateFormat("HH:00", Locale.getDefault()) }
                    val bars = window.groupBy { it.ts / 3_600_000L }.toSortedMap().values
                        .map { fmt.format(Date(it.last().ts)) to it.last().level }
                        .ifEmpty { listOf("Now" to s.levelPct) }
                    Column {
                        Spacer(Modifier.height(36.dp))
                        ValueBarChart(bars.takeLast(8), scheme.primary, { "${it.roundToInt()}%" }, height = 224.dp)
                    }
                }
                Row(Modifier.align(Alignment.TopEnd)) {
                    Toggle(Ph.ChartLine, lineMode, "Line chart") { lineMode = true }
                    Spacer(Modifier.width(4.dp))
                    Toggle(Ph.ChartBar, !lineMode, "Bar chart") { lineMode = false }
                }
            }
            if (window.size >= 2) {
                TimelineLanes(
                    timestamps = window.map { it.ts },
                    lanes = listOf(
                        Lane("Screen on", scheme.primary) { window[it].screenOn },
                        Lane("Screen off", scheme.outline) { !window[it].screenOn },
                        Lane("Deep sleep", scheme.tertiary) { !window[it].screenOn && window[it].sleepFrac >= 0.5f },
                        Lane("Awake", scheme.secondary) { !window[it].screenOn && window[it].sleepFrac < 0.5f },
                        Lane("Charging", ChargingGreen) { window[it].plugged },
                    ),
                    leftInset = if (lineMode) 38.dp else 0.dp,
                )
            }
        }

        Text(
            "Press and drag on the graph for exact values. Green marks charging; the lanes show what the phone was doing at each moment.",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun EstimateCol(label: String, value: String, modifier: Modifier) {
    Column(modifier.padding(horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun Toggle(icon: ImageVector, selected: Boolean, description: String, onClick: () -> Unit) {
    FilledIconToggleButton(
        checked = selected,
        onCheckedChange = { onClick() },
        modifier = Modifier.size(34.dp),
        colors = IconButtonDefaults.filledIconToggleButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
    ) { Icon(icon, description, Modifier.size(18.dp)) }
}
