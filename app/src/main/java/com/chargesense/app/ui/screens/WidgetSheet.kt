package com.chargesense.app.ui.screens

import com.chargesense.app.ui.theme.Ph
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chargesense.app.R
import com.chargesense.app.data.BatterySnapshot
import com.chargesense.app.data.HistoryDb
import com.chargesense.app.data.LiveState
import com.chargesense.app.data.Sample
import com.chargesense.app.service.BatteryWidget
import com.chargesense.app.service.GraphWidget
import com.chargesense.app.service.RingWidget
import com.chargesense.app.service.StatsWidget
import com.chargesense.app.service.Widgets
import com.chargesense.app.ui.theme.ChargingGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Widget picker: a panel sliding up from the bottom with live previews of each widget, drawn the
 * way they look on the home screen (same colours as the RemoteViews layouts), each with an Add button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val snapshot by LiveState.snapshot.collectAsState()
    var day by remember { mutableStateOf<List<Sample>>(emptyList()) }
    LaunchedEffect(Unit) { day = withContext(Dispatchers.IO) { HistoryDb.get(context).samplesSince(System.currentTimeMillis() - 24 * 60 * 60_000L) } }
    val pinSupported = remember { AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        val s = snapshot ?: return@ModalBottomSheet
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Widgets", style = MaterialTheme.typography.headlineSmall)
            Text(
                if (pinSupported) "Tap Add, then confirm where it goes on your home screen."
                else "Your launcher can't add widgets from apps. Long-press the home screen → Widgets → Moto Battery.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // A wallpaper-like backdrop behind the previews, like a launcher's widget picker.
            PreviewEntry("Ring", "2 × 2 · Level at a glance", pinSupported, { pin(context, RingWidget::class.java, onDismiss) }) {
                Box(Modifier.width(150.dp).aspectRatio(1f)) { RingPreview(s) }
            }
            PreviewEntry("Graph", "4 × 2 · Last 24 hours", pinSupported, { pin(context, GraphWidget::class.java, onDismiss) }) {
                GraphPreview(s, day)
            }
            PreviewEntry("Stats", "4 × 1 · Current, temp, power, time left", pinSupported, { pin(context, StatsWidget::class.java, onDismiss) }) {
                StatsPreview(s, Widgets.timeLeft(context, s))
            }
            PreviewEntry("Info", "3 × 1 · Level, current and temperature", pinSupported, { pin(context, BatteryWidget::class.java, onDismiss) }) {
                Box(Modifier.fillMaxWidth(0.78f)) { InfoPreview(s) }
            }
        }
    }
}

private fun pin(context: Context, cls: Class<*>, done: () -> Unit) {
    AppWidgetManager.getInstance(context).requestPinAppWidget(ComponentName(context, cls), null, null)
    done()
}

@Composable
private fun PreviewEntry(title: String, subtitle: String, canAdd: Boolean, onAdd: () -> Unit, preview: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(28.dp), color = scheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            // Soft "wallpaper" tile so the widget sits on something, as on a home screen.
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Brush.linearGradient(listOf(scheme.primary.copy(alpha = 0.35f), scheme.tertiary.copy(alpha = 0.25f), scheme.secondary.copy(alpha = 0.3f))))
                    .padding(18.dp),
                contentAlignment = Alignment.Center,
            ) { preview() }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
                FilledTonalButton(onClick = onAdd, enabled = canAdd) {
                    Icon(if (canAdd) Ph.Plus else Ph.HandTap, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add")
                }
            }
        }
    }
}

// --- Previews: Compose replicas of the RemoteViews widgets, using the same widget colours. ---

@Composable private fun wBg() = colorResource(R.color.widget_bg)
@Composable private fun wText() = colorResource(R.color.widget_text)
@Composable private fun wText2() = colorResource(R.color.widget_text_secondary)
@Composable private fun wAccent() = colorResource(R.color.widget_accent)
@Composable private fun wTrack() = colorResource(R.color.widget_track)

private fun status(s: BatterySnapshot) = Widgets.statusLine(s)

@Composable
private fun RingPreview(s: BatterySnapshot) {
    val accent = if (s.isPlugged && s.isCharging) ChargingGreen else wAccent()
    val track = wTrack()
    Surface(shape = RoundedCornerShape(24.dp), color = wBg(), modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                Canvas(Modifier.matchParentSize()) {
                    val st = size.minDimension * 0.09f
                    val tl = Offset(st, st)
                    val sz = Size(size.minDimension - st * 2, size.minDimension - st * 2)
                    drawArc(track, 0f, 360f, false, tl, sz, style = Stroke(st))
                    drawArc(accent, -90f, 360f * s.levelPct / 100f, false, tl, sz, style = Stroke(st, cap = StrokeCap.Round))
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${s.levelPct.roundToInt()}", color = wText(), fontSize = 30.sp, fontWeight = FontWeight.Light)
                    Text("%", color = wText(), fontSize = 12.sp, modifier = Modifier.padding(bottom = 5.dp))
                }
            }
            Text(status(s), color = wText2(), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun GraphPreview(s: BatterySnapshot, day: List<Sample>) {
    val accent = wAccent()
    Surface(shape = RoundedCornerShape(24.dp), color = wBg(), modifier = Modifier.fillMaxWidth().aspectRatio(2f)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${s.levelPct.roundToInt()}%", color = accent, fontSize = 26.sp, fontWeight = FontWeight.Light)
                Text(status(s), color = wText(), fontSize = 13.sp, modifier = Modifier.weight(1f).padding(start = 8.dp, bottom = 3.dp))
                Text(s.currentMa?.let { (if (it > 0) "+" else "") + "$it mA" } ?: "", color = wText2(), fontSize = 12.sp, modifier = Modifier.padding(bottom = 3.dp))
            }
            Spacer(Modifier.height(8.dp))
            val pts = day.map { Triple(it.ts, it.level, it.plugged) } + Triple(s.timestamp, s.levelPct, s.isPlugged)
            val green = ChargingGreen
            Canvas(Modifier.fillMaxWidth().weight(1f)) {
                if (pts.size < 2) return@Canvas
                val t0 = System.currentTimeMillis() - 24 * 60 * 60_000L
                val span = (System.currentTimeMillis() - t0).toFloat()
                fun x(t: Long) = ((t - t0) / span) * size.width
                fun y(v: Float) = (1f - v / 100f) * size.height
                for (i in 1 until pts.size) {
                    val a = pts[i - 1]
                    val b = pts[i]
                    if (b.first - a.first > 20 * 60_000L) continue
                    val c = if (b.third) green else accent
                    val area = Path().apply {
                        moveTo(x(a.first), size.height); lineTo(x(a.first), y(a.second)); lineTo(x(b.first), y(b.second)); lineTo(x(b.first), size.height); close()
                    }
                    drawPath(area, c.copy(alpha = 0.2f))
                    drawLine(c, Offset(x(a.first), y(a.second)), Offset(x(b.first), y(b.second)), 3.dp.toPx(), cap = StrokeCap.Round)
                }
            }
            Text("Last 24 hours", color = wText2(), fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun StatsPreview(s: BatterySnapshot, timeLeft: String) {
    Surface(shape = RoundedCornerShape(24.dp), color = wBg(), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
            listOf(
                (s.currentMa?.let { (if (it > 0) "+" else "") + "$it" } ?: "—") to "mA",
                "%.1f°".format(s.temperatureC) to "Temp",
                (s.powerW?.let { "%.1f W".format(abs(it)) } ?: "—") to "Power",
                timeLeft to if (s.isPlugged) "To full" else "Left",
            ).forEach { (v, l) ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(v, color = wAccent(), fontSize = 17.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                    Text(l, color = wText2(), fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun InfoPreview(s: BatterySnapshot) {
    val track = wTrack()
    val accent = wAccent()
    Surface(shape = RoundedCornerShape(24.dp), color = wBg(), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${s.levelPct.roundToInt()}%", color = accent, fontSize = 30.sp)
                    Text(if (s.isPlugged) "${s.statusText} · ${s.plugText}" else s.statusText, color = wText2(), fontSize = 12.sp, maxLines = 1)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(s.currentMa?.let { (if (it > 0) "+" else "") + "$it mA" } ?: "—", color = wText(), fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text("%.1f °C".format(s.temperatureC), color = wText2(), fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            Canvas(Modifier.fillMaxWidth().height(6.dp)) {
                val r = androidx.compose.ui.geometry.CornerRadius(size.height / 2)
                drawRoundRect(track, Offset.Zero, size, r)
                drawRoundRect(accent, Offset.Zero, Size(size.width * s.levelPct / 100f, size.height), r)
            }
        }
    }
}
