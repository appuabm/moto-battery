package com.chargesense.app.ui.screens

import com.chargesense.app.ui.components.FitText

import com.chargesense.app.ui.theme.Space
import com.chargesense.app.ui.theme.Ph
import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.chargesense.app.data.AppDrain
import com.chargesense.app.data.AppUsage
import com.chargesense.app.data.BatteryReader
import com.chargesense.app.data.BatterySnapshot
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
import com.chargesense.app.ui.components.KeyValue
import com.chargesense.app.ui.components.LegendRow
import com.chargesense.app.ui.components.DetailRows
import com.chargesense.app.ui.components.DeviceCard
import com.chargesense.app.ui.components.HelpCard
import com.chargesense.app.ui.components.RingActionCard
import androidx.compose.ui.graphics.Color
import com.chargesense.app.ui.components.MotoFeatureCard
import com.chargesense.app.ui.components.MotoLink
import com.chargesense.app.ui.components.MotoSection
import com.chargesense.app.ui.components.MotoTile
import com.chargesense.app.ui.components.MotoTileRow
import com.chargesense.app.ui.components.DividedStats
import com.chargesense.app.ui.components.ValueBarChart
import com.chargesense.app.ui.components.fmtNum
import com.chargesense.app.ui.components.Sparkline
import com.chargesense.app.ui.components.DivergingBarChart
import com.chargesense.app.ui.components.InteractiveLineChart
import com.chargesense.app.ui.components.SplitBar
import com.chargesense.app.ui.components.StatusPill
import com.chargesense.app.ui.theme.ChargingGreen
import com.chargesense.app.ui.theme.currentColor
import com.chargesense.app.ui.theme.levelColor
import com.chargesense.app.ui.theme.tempColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private data class DashData(
    val hourlyLevels: List<Pair<String, Float>> = emptyList(),
    val levelSamples: List<Pair<Long, Float>> = emptyList(),
    val capacity: Float = 0f,
    val design: Int = 0,
    val estimatedCapacity: Float? = null,
    val lastDischarge: Session? = null,
)

@Composable
fun DashboardScreen(
    onOpenHealth: () -> Unit,
    onOpenDetail: (String) -> Unit,
    onOpenHistory: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
) {
    val context = LocalContext.current
    val prefs = remember { Prefs.get(context) }
    val db = remember { HistoryDb.get(context) }
    val serviceRunning by LiveState.serviceRunning.collectAsState()
    val snapshot by LiveState.snapshot.collectAsState()
    val smoothed by LiveState.smoothedCurrentMa.collectAsState()
    val recent by LiveState.recent.collectAsState()
    val session by LiveState.activeSession.collectAsState()
    val dbVersion by db.changes.collectAsState()
    prefs.version.collectAsState().value

    // Without the service, poll directly so the dashboard still works.
    LaunchedEffect(serviceRunning) {
        if (serviceRunning) return@LaunchedEffect
        val reader = BatteryReader(context)
        while (true) {
            reader.read()?.let(LiveState::publish)
            delay(2_000)
        }
    }

    var data by remember { mutableStateOf(DashData()) }
    // Also re-run once the first reading arrives, so the capacity fallback has a snapshot to use.
    LaunchedEffect(dbVersion, snapshot == null) {
        while (true) {
            data = withContext(Dispatchers.IO) { loadDashData(context, db) }
            delay(60_000)
        }
    }

    val s = snapshot
    if (s == null) {
        Text("Reading battery…", modifier = Modifier.padding(32.dp))
        return
    }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Space.Gutter).padding(top = 4.dp, bottom = Space.Bottom),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Moto Battery-settings style header: big number, thick bar, plain status line.
        val mins = Estimates.minutesRemaining(s, smoothed, data.capacity, prefs.chargeAlarmLevel)
        // Device Help-style device card.
        val health = Estimates.healthPct(data.estimatedCapacity?.let { com.chargesense.app.data.CapacityEstimate(it, "", 1) }, data.design)
        DeviceCard(
            details = listOfNotNull(
                "Battery" to s.technology,
                data.design.takeIf { it > 0 }?.let { "Capacity" to "${fmtNum(it)} mAh" },
                s.cycleCount?.let { "Cycles" to fmtNum(it) },
            ),
            health = health,
            rating = healthRating(health),
            onHealth = onOpenHealth,
        )

        // Device Help-style ring card with a pill action.
        RingActionCard(
            percent = s.levelPct,
            sub = s.chargeCounterMah?.let { "${fmtNum(it)} mAh" } ?: s.plugText,
            subIcon = if (s.isPlugged) Ph.Lightning else Ph.BatteryCharging,
            ringColor = if (s.isPlugged && s.isCharging) ChargingGreen else levelColor(s.levelPct),
            caption = when {
                s.isFull -> "Fully charged"
                s.isPlugged && !s.isCharging -> "Connected, but not charging"
                s.isPlugged && mins != null -> "Charging · ${formatMinutes(mins)} until ${Estimates.chargeTargetFor(s, prefs.chargeAlarmLevel)}%"
                s.isPlugged -> "Charging"
                mins != null -> "About ${formatMinutes(mins)} left at this usage"
                else -> "On battery"
            },
            action = "Battery usage",
            onAction = {
                runCatching {
                    context.startActivity(android.content.Intent(android.content.Intent.ACTION_POWER_USAGE_SUMMARY).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            },
            onRing = { onOpenDetail(DetailKey.metric(Metric.Level)) },
        )

        // Moto Secure style action tiles.
        MotoTileRow { m ->
            MotoTile(Ph.Lightning, "Current", m, value = s.currentMa?.let { (if (it > 0) "+" else "") + "$it mA" } ?: "—", tint = currentColor(s.currentMa)) {
                onOpenDetail(DetailKey.metric(Metric.Current))
            }
            MotoTile(Ph.Thermometer, "Temperature", m, value = "%.1f °C".format(s.temperatureC), tint = tempColor(s.temperatureC)) {
                onOpenDetail(DetailKey.metric(Metric.Temperature))
            }
            MotoTile(Ph.Plug, "Voltage", m, value = "%.2f V".format(s.voltageMv / 1000f)) {
                onOpenDetail(DetailKey.metric(Metric.Level))
            }
        }

        MotoSection("Battery")
        PairRow {
            HealthCard(data, onOpenHealth, it)
            CyclesCard(s, data, it)
        }

        LevelHistoryCard(s, data.hourlyLevels, data.levelSamples) { onOpenDetail(DetailKey.metric(Metric.Level)) }

        session?.let { SessionCard(it) { onOpenDetail(DetailKey.session(-1)) } }

        MotoSection("Live readings")
        PairRow {
            CurrentCard(s, smoothed, recent, it) { onOpenDetail(DetailKey.metric(Metric.Current)) }
            TemperatureCard(s, session, recent, it) { onOpenDetail(DetailKey.metric(Metric.Temperature)) }
        }

        val discharge = session?.takeIf { !it.charging } ?: data.lastDischarge
        MotoSection("App usage")
        AppUsageCard(discharge, isPrevious = discharge != null && discharge !== session, capacity = data.capacity)

        val hidden = prefs.hiddenCards
        fun hide(key: String) { prefs.hiddenCards = prefs.hiddenCards + key }
        val widgetIcon = ImageVector.vectorResource(com.chargesense.app.R.drawable.ic_widgets)
        val explore = listOf<Pair<String, @Composable () -> Unit>>(
            "health" to {
                MotoFeatureCard(
                    Ph.Heartbeat, "Battery health check", "Capacity, wear and charging habits",
                    listOf(MotoLink(Ph.ShieldCheck, "Health details", onOpenHealth), MotoLink(Ph.Lightbulb, "Care tips", onOpenHealth)),
                    onClose = { hide("health") }, onClick = onOpenHealth,
                )
            },
            "sessions" to {
                MotoFeatureCard(
                    ImageVector.vectorResource(com.chargesense.app.R.drawable.ic_hourglass), "Sessions", "Every charge, discharge and idle period",
                    listOf(MotoLink(Ph.Lightning, "Charging", onOpenHistory), MotoLink(Ph.Moon, "Idle log", onOpenHistory)),
                    onClose = { hide("sessions") }, onClick = onOpenHistory,
                )
            },
            "widgets" to {
                MotoFeatureCard(
                    widgetIcon, "Widgets & quick tile", "Battery at a glance on your home screen",
                    listOf(MotoLink(widgetIcon, "Add widget", onOpenSettings), MotoLink(Ph.BatteryFull, "Add tile", onOpenSettings)),
                    onClose = { hide("widgets") }, onClick = onOpenSettings,
                )
            },
            "alarms" to {
                MotoFeatureCard(
                    Ph.BellRinging, "Alarms", "Charge limit, low battery and heat alerts",
                    listOf(MotoLink(Ph.BatteryCharging, "Charge limit", onOpenSettings), MotoLink(Ph.Thermometer, "Temperature", onOpenSettings)),
                    onClose = { hide("alarms") }, onClick = onOpenSettings,
                )
            },
        ).filter { it.first !in hidden }
        if (explore.isNotEmpty()) {
            MotoSection("Explore")
            explore.forEach { it.second() }
        }

        MotoSection("Details")
        DashCard(null) {
            DetailRows(
                listOfNotNull(
                    Triple("Status", s.statusText, null),
                    Triple("Power source", s.plugText, null),
                    Triple("Voltage", "%.3f V".format(s.voltageMv / 1000f), null),
                    Triple("Technology", s.technology, null),
                    s.chargeCounterMah?.let { Triple("Charge counter", "${fmtNum(it)} mAh", null) },
                    s.cycleCount?.let { Triple("Charge cycles", fmtNum(it), null) },
                    Triple("Reported health", s.healthText, null),
                    Triple("Monitoring", if (serviceRunning) "Active" else "Off", if (serviceRunning) ChargingGreen else null),
                )
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

private fun loadDashData(context: Context, db: HistoryDb): DashData {
    val now = System.currentTimeMillis()
    val samples = db.samplesSince(now - 24 * 60 * 60_000L)
    val sessions = db.sessions(30)
    val estimate = Estimates.capacity(LiveState.snapshot.value, sessions)
    val design = DesignCapacity.get(context)
    return DashData(
        hourlyLevels = hourly(samples),
        levelSamples = samples.map { it.ts to it.level },
        capacity = estimate?.mah ?: design.toFloat(),
        design = design,
        estimatedCapacity = estimate?.mah,
        lastDischarge = sessions.firstOrNull { !it.charging },
    )
}

/** Last recorded level in each of the past 24 hours, labelled with the hour. */
private fun hourly(samples: List<Sample>): List<Pair<String, Float>> {
    if (samples.isEmpty()) return emptyList()
    val cal = Calendar.getInstance()
    val fmt = SimpleDateFormat("HH:00", Locale.getDefault())
    return samples.groupBy {
        cal.timeInMillis = it.ts
        cal.get(Calendar.DAY_OF_YEAR) * 24 + cal.get(Calendar.HOUR_OF_DAY)
    }.toSortedMap().values.map { fmt.format(Date(it.last().ts)) to it.last().level }.takeLast(24)
}

/** Two cards side by side with equal height. */
@Composable
private fun PairRow(content: @Composable (Modifier) -> Unit) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        content(Modifier.weight(1f).fillMaxHeight())
    }
}

@Composable
private fun LevelHistoryCard(
    s: BatterySnapshot,
    hourly: List<Pair<String, Float>>,
    levelSamples: List<Pair<Long, Float>>,
    onClick: () -> Unit,
) {
    val color = levelColor(s.levelPct)
    var lineMode by rememberSaveable { mutableStateOf(false) }
    DashCard("Battery level · 24 h", onClick = onClick, trailing = {
        ChartToggle(Ph.ChartBar, !lineMode, "Bar chart") { lineMode = false }
        Spacer(Modifier.width(4.dp))
        ChartToggle(Ph.ChartLine, lineMode, "Line chart") { lineMode = true }
        Spacer(Modifier.width(4.dp))
    }) {
        if (lineMode) {
            InteractiveLineChart(
                levelSamples.ifEmpty { listOf(s.timestamp to s.levelPct) }, color, { "${it.roundToInt()}%" },
                height = 170.dp, minY = 0f, maxY = 100f,
            )
        } else {
            ValueBarChart(
                hourly.ifEmpty { listOf("Now" to s.levelPct) }.takeLast(8),
                color, { "${it.roundToInt()}%" }, height = 190.dp,
            )
        }
    }
}

/** Small icon toggle used for switching the chart style. */
@Composable
private fun ChartToggle(icon: ImageVector, selected: Boolean, description: String, onClick: () -> Unit) {
    FilledIconToggleButton(
        checked = selected,
        onCheckedChange = { onClick() },
        modifier = Modifier.size(32.dp),
        colors = IconButtonDefaults.filledIconToggleButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
    ) {
        Icon(icon, description, Modifier.size(18.dp))
    }
}

@Composable
private fun CyclesCard(s: BatterySnapshot, data: DashData, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    DashCard("Charge cycles", modifier, containerColor = scheme.tertiaryContainer) {
        Text(
            s.cycleCount?.let { fmtNum(it) } ?: "—",
            style = MaterialTheme.typography.displaySmall,
            color = scheme.onTertiaryContainer,
        )
        Spacer(Modifier.height(6.dp))
        val est = data.estimatedCapacity
        Text(
            if (est != null && data.design > 0) "Wear level · ${(100 - est / data.design * 100).roundToInt().coerceAtLeast(0)}%"
            else "Reported by the battery",
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onTertiaryContainer,
        )
        FitText("Lifetime cycles", MaterialTheme.typography.bodySmall, color = scheme.onTertiaryContainer.copy(alpha = 0.75f))
    }
}

@Composable
private fun HealthCard(data: DashData, onClick: () -> Unit, modifier: Modifier) {
    val health = Estimates.healthPct(
        data.estimatedCapacity?.let { com.chargesense.app.data.CapacityEstimate(it, "", 1) }, data.design
    )
    val scheme = MaterialTheme.colorScheme
    DashCard("Battery health", modifier, onClick = onClick, containerColor = scheme.secondaryContainer) {
        if (health != null) BigValue("$health", "%", scheme.onSecondaryContainer)
        else Text("—", style = MaterialTheme.typography.displaySmall, color = scheme.onSecondaryContainer)
        Spacer(Modifier.height(4.dp))
        Text(healthRating(health), style = MaterialTheme.typography.bodyMedium, color = scheme.onSecondaryContainer)
        val est = data.estimatedCapacity
        if (est != null && data.design > 0) {
            FitText(
                "${fmtNum(est.coerceAtMost(data.design.toFloat()))} of ${fmtNum(data.design)} mAh",
                MaterialTheme.typography.bodySmall,
                color = scheme.onSecondaryContainer.copy(alpha = 0.75f),
            )
        }
    }
}

fun healthRating(health: Int?): String = when {
    health == null -> "Not measured yet"
    health >= 90 -> "Excellent"
    health >= 80 -> "Good"
    health >= 70 -> "Fair"
    else -> "Poor"
}

@Composable
private fun SessionCard(a: Session, onClick: () -> Unit) {
    val delta = a.endLevel - a.startLevel
    val minutes = (a.durationMs / 60_000).toInt()
    val hours = a.durationMs / 3_600_000f
    DashCard(if (a.charging) "Charging session" else "Current session", onClick = onClick) {
        Row(verticalAlignment = Alignment.Bottom) {
            BigValue(
                "${if (delta >= 0) "+" else "−"}${abs(delta).roundToInt()}", "%",
                color = if (a.charging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
            )
            Spacer(Modifier.width(16.dp))
            Text(
                "${fmtNum(a.mah)} mAh ${if (a.charging) "charged" else "used"}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        Spacer(Modifier.height(14.dp))
        DividedStats(
            listOf(
                "Total time" to formatMinutes(minutes),
                (if (a.charging) "Charge speed" else "Drain rate") to (if (hours > 0.02f) "%.1f%%/h".format(abs(delta) / hours) else "—"),
                "Average current" to (if (minutes > 0) "${a.avgCurrentMa.roundToInt()} mA" else "—"),
            )
        )
        if (!a.charging) {
            val total = (a.screenOnMs + a.screenOffMs).coerceAtLeast(1)
            Spacer(Modifier.height(16.dp))
            SplitBar(a.screenOnMs.toFloat() / total, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondaryContainer)
            Spacer(Modifier.height(8.dp))
            LegendRow(
                MaterialTheme.colorScheme.primary, "Screen on",
                formatMinutes((a.screenOnMs / 60_000).toInt()) + (a.screenOnRatePerHour?.let { " · %.1f%%/h".format(it) } ?: ""),
            )
            LegendRow(
                MaterialTheme.colorScheme.secondaryContainer, "Screen off",
                formatMinutes((a.screenOffMs / 60_000).toInt()) + (a.screenOffRatePerHour?.let { " · %.1f%%/h".format(it) } ?: ""),
            )
            a.deepSleepPct?.let { LegendRow(MaterialTheme.colorScheme.outline, "Deep sleep", "%.0f%% of screen off".format(it)) }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun CurrentCard(s: BatterySnapshot, smoothed: Float?, recent: List<BatterySnapshot>, modifier: Modifier, onClick: () -> Unit) {
    val color = currentColor(s.currentMa)
    DashCard("Current", modifier, onClick = onClick) {
        BigValue(s.currentMa?.let { (if (it > 0) "+" else "") + it } ?: "—", "mA", color)
        Text(
            listOfNotNull(s.powerW?.let { "%.2f W".format(abs(it)) }, "%.2f V".format(s.voltageMv / 1000f)).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        smoothed?.let {
            Text("Average ${it.roundToInt()} mA", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(10.dp))
        DivergingBarChart(
            bucketed(recent.mapNotNull { r -> r.currentMa?.let { r.timestamp to it.toFloat() } }, 16),
            MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary,
            { "${it.roundToInt()} mA" }, height = 100.dp,
        )
    }
}

@Composable
private fun TemperatureCard(s: BatterySnapshot, session: Session?, recent: List<BatterySnapshot>, modifier: Modifier, onClick: () -> Unit) {
    val temps = recent.map { it.temperatureC }
    val max = session?.maxTempC ?: temps.maxOrNull() ?: s.temperatureC
    val min = session?.minTempC ?: temps.minOrNull() ?: s.temperatureC
    DashCard("Temperature", modifier, onClick = onClick) {
        BigValue("%.1f".format(s.temperatureC), "°C", tempColor(s.temperatureC))
        Text(
            if (s.temperatureC >= 40f) "Hot, let it cool" else if (s.temperatureC >= 37f) "Warm" else "Normal",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text("Current session", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(12.dp))
        Sparkline(temps.ifEmpty { listOf(s.temperatureC, s.temperatureC) }, MaterialTheme.colorScheme.onSurfaceVariant, height = 36.dp, format = { "%.1f °C".format(it) })
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("%.1f°".format(min), style = MaterialTheme.typography.labelSmall, maxLines = 1)
            Text(
                "Session",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
            )
            Text("%.1f°".format(max), style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

@Composable
private fun AppUsageCard(session: Session?, isPrevious: Boolean, capacity: Float) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var hasAccess by remember { mutableStateOf(AppUsage.hasAccess(context)) }
    var apps by remember { mutableStateOf<List<AppDrain>>(emptyList()) }

    // Re-check access when returning from the system settings screen, and refresh periodically.
    LaunchedEffect(session?.startTs, capacity) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                hasAccess = AppUsage.hasAccess(context)
                apps = if (hasAccess && session != null) {
                    withContext(Dispatchers.IO) { AppUsage.forSession(context, session, capacity, limit = 6) }
                } else emptyList()
                delay(60_000)
            }
        }
    }

    DashCard("App usage", trailing = {
        if (isPrevious) StatusPill("Previous session", Ph.ClockHistory, emphasized = false)
    }) {
        when {
            !hasAccess -> {
                Text(
                    "See which apps used the most battery. Android needs you to allow usage access for this.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                FilledTonalButton(onClick = { runCatching { context.startActivity(AppUsage.accessSettingsIntent(context)) } }) {
                    Text("Allow usage access")
                }
            }
            apps.isEmpty() -> Text(
                if (session == null) "Shown after the phone has run on battery for a while."
                else "No app activity recorded in this session yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> {
                BigValue(apps.sumOf { it.mah.toDouble() }.roundToInt().toString(), "mAh")
                Spacer(Modifier.height(8.dp))
                val top = apps.first().mah.coerceAtLeast(0.01f)
                apps.forEach { AppRow(it, it.mah / top) }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Ph.Info, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(6.dp))
                    FitText(
                        "Estimated from screen-on drain and foreground time",
                        MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        minScale = 0.8f,
                    )
                }
            }
        }
    }
}

@Composable
private fun AppRow(app: AppDrain, fraction: Float) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        val bitmap = remember(app.packageName) { runCatching { app.icon?.toBitmap(96, 96)?.asImageBitmap() }.getOrNull() }
        if (bitmap != null) {
            Image(bitmap, null, Modifier.size(36.dp))
        } else {
            Surface(Modifier.size(36.dp), shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                Icon(Ph.AppWindow, null, Modifier.padding(8.dp))
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row {
                FitText(app.label, MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(end = 8.dp), minScale = 0.85f)
                Text(
                    "${app.mah.roundToInt()} mAh · %.1f%%".format(app.pctOfBattery),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                )
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth(), drawStopIndicator = {})
        }
    }
}

/** Averages (timestamp, value) readings into at most [n] equal time buckets, labelled HH:mm. */
fun bucketed(points: List<Pair<Long, Float>>, n: Int): List<Pair<String, Float>> {
    if (points.isEmpty()) return emptyList()
    val t0 = points.first().first
    val t1 = points.last().first.coerceAtLeast(t0 + 1)
    val step = (t1 - t0).toDouble() / n
    val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    return points.groupBy { ((it.first - t0) / step).toInt().coerceIn(0, n - 1) }
        .toSortedMap()
        .map { (_, list) -> fmt.format(Date(list.last().first)) to list.map { it.second }.average().toFloat() }
}
