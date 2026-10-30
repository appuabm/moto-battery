package com.chargesense.app.ui.screens

import com.chargesense.app.ui.components.FitText

import com.chargesense.app.ui.theme.Space
import com.chargesense.app.ui.theme.Ph
import android.annotation.SuppressLint
import android.app.StatusBarManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.rotate
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.chargesense.app.R
import com.chargesense.app.data.AppUsage
import com.chargesense.app.data.CurrentSign
import com.chargesense.app.data.CurrentUnit
import com.chargesense.app.data.DesignCapacity
import com.chargesense.app.data.LiveState
import com.chargesense.app.data.Prefs
import com.chargesense.app.service.BatteryTileService
import com.chargesense.app.service.BatteryWidget
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import com.chargesense.app.service.MonitorService
import com.chargesense.app.ui.components.CardShape
import com.chargesense.app.ui.components.PillTabs
import com.chargesense.app.ui.components.fmtNum
import com.chargesense.app.ui.components.SectionHeader
import kotlin.math.roundToInt

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val prefs = remember { Prefs.get(context) }
    prefs.version.collectAsState().value
    val running by LiveState.serviceRunning.collectAsState()

    // Permission states change in system screens; refresh them whenever we come back.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val pm = remember { context.getSystemService(PowerManager::class.java) }
    var unrestricted by remember { mutableStateOf(pm.isIgnoringBatteryOptimizations(context.packageName)) }
    var usageAccess by remember { mutableStateOf(AppUsage.hasAccess(context)) }
    LaunchedEffect(Unit) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            unrestricted = pm.isIgnoringBatteryOptimizations(context.packageName)
            usageAccess = AppUsage.hasAccess(context)
        }
    }

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Space.Gutter - 16.dp).padding(bottom = Space.Bottom)) {
        SectionHeader("Monitoring")
        Group {
            SwitchItem(Ph.Heartbeat, "Background monitoring",
                if (running) "Records sessions and live status" else "Off", prefs.monitoringEnabled) {
                prefs.monitoringEnabled = it
                if (it) MonitorService.start(context) else MonitorService.stop(context)
            }
            Divider()
            SwitchItem(Ph.Power, "Start on boot", "Resumes after a restart", prefs.startOnBoot, MaterialTheme.colorScheme.secondary) { prefs.startOnBoot = it }
            Divider()
            @SuppressLint("BatteryLife")
            val batteryIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
            PermissionItem(
                Ph.BatteryPlus, "Unrestricted battery use",
                if (unrestricted) "Monitoring won't be stopped" else "Keeps monitoring running",
                unrestricted, MaterialTheme.colorScheme.tertiary,
            ) { runCatching { context.startActivity(batteryIntent) } }
            Divider()
            PermissionItem(
                Ph.AppWindow, "Usage access",
                if (usageAccess) "Per-app usage is shown" else "Needed for the app usage card",
                usageAccess, MaterialTheme.colorScheme.secondary,
            ) { runCatching { context.startActivity(AppUsage.accessSettingsIntent(context)) } }
        }

        SectionHeader("Notification")
        Group {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HelloBadge(Ph.Bell)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        FitText("Status bar icon", MaterialTheme.typography.titleMedium.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize * 0.88f))
                        FitText("Shown next to the clock", MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Segmented(listOf("Icon", "Level", "Current", "Temp"), prefs.statusIcon) { prefs.statusIcon = it }
            }
            Divider()
            LinkItem(Ph.Sliders, "Notification settings", "Sounds, pop-ups and lock screen", MaterialTheme.colorScheme.secondary) {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    )
                }
            }
        }

        SectionHeader("Alarms")
        Group {
            SwitchItem(Ph.BatteryCharging, "Charge limit alarm",
                "Alert when charging reaches ${prefs.chargeAlarmLevel}%", prefs.chargeAlarmEnabled) { prefs.chargeAlarmEnabled = it }
            SliderItem(prefs.chargeAlarmLevel, 50..100, "%", prefs.chargeAlarmEnabled) { prefs.chargeAlarmLevel = it }
            Divider()
            SwitchItem(Ph.BatteryWarning, "Low battery alarm",
                "Alert at ${prefs.lowAlarmLevel}% when unplugged", prefs.lowAlarmEnabled, MaterialTheme.colorScheme.error) { prefs.lowAlarmEnabled = it }
            SliderItem(prefs.lowAlarmLevel, 5..50, "%", prefs.lowAlarmEnabled) { prefs.lowAlarmLevel = it }
            Divider()
            SwitchItem(Ph.Thermometer, "Temperature alarm",
                "Alert above ${prefs.tempAlarmC} °C", prefs.tempAlarmEnabled, MaterialTheme.colorScheme.tertiary) { prefs.tempAlarmEnabled = it }
            SliderItem(prefs.tempAlarmC, 35..55, " °C", prefs.tempAlarmEnabled) { prefs.tempAlarmC = it }
            Divider()
            SwitchItem(Ph.Vibrate, "Vibrate", "Ticks with the alarm sound", prefs.alarmVibrate, MaterialTheme.colorScheme.secondary) { prefs.alarmVibrate = it }
        }

        SectionHeader("Shortcuts")
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(Space.Gap)) {
            // Opens a panel from the bottom with live previews of each widget.
            var widgetSheet by remember { mutableStateOf(false) }
            ShortcutTile(ImageVector.vectorResource(R.drawable.ic_widgets), "Add widget", "Home screen", Modifier.weight(1f)) { widgetSheet = true }
            if (widgetSheet) WidgetSheet(onDismiss = { widgetSheet = false })
            if (Build.VERSION.SDK_INT >= 33) {
                ShortcutTile(Ph.BatteryFull, "Add tile", "Quick settings", Modifier.weight(1f)) {
                    context.getSystemService(StatusBarManager::class.java)?.requestAddTileService(
                        ComponentName(context, BatteryTileService::class.java),
                        context.getString(R.string.tile_label),
                        Icon.createWithResource(context, R.drawable.ic_stat_battery),
                        context.mainExecutor,
                    ) { }
                }
            }
        }

        SectionHeader("Advanced")
        Group {
            // Auto-detection covers most phones, so the manual overrides stay folded away.
            val detected = remember { DesignCapacity.detected(context) }
            var calibrationOpen by rememberSaveable { mutableStateOf(false) }
            val manual = prefs.designCapacityOverride > 0 || prefs.currentUnit != CurrentUnit.entries[0] || prefs.currentSign != CurrentSign.entries[0]
            ListItem(
                modifier = Modifier.clickable { calibrationOpen = !calibrationOpen },
                leadingContent = { HelloBadge(Ph.Lightning) },
                trailingContent = { Icon(Ph.CaretRight, null, Modifier.rotate(if (calibrationOpen) 90f else 0f)) },
                headlineContent = { FitText("Calibration", MaterialTheme.typography.titleMedium.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize * 0.88f)) },
                supportingContent = {
                    Text(
                        if (manual) "Manual overrides on"
                        else if (detected > 0) "Automatic · ${fmtNum(detected)} mAh"
                        else "Automatic"
                    )
                },
                colors = itemColors(),
            )
            if (calibrationOpen) Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                var text by remember { mutableStateOf(prefs.designCapacityOverride.takeIf { it > 0 }?.toString() ?: "") }
                Text("Design capacity (mAh)", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { v ->
                        text = v.filter(Char::isDigit).take(5)
                        prefs.designCapacityOverride = text.toIntOrNull() ?: 0
                    },
                    placeholder = { Text(if (detected > 0) "Detected: ${fmtNum(detected)}" else "e.g. 5000") },
                    supportingText = {
                        Text(if (detected > 0) "Leave empty to use the detected ${fmtNum(detected)} mAh." else "Not reported by this phone — see its spec sheet.")
                    },
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Current unit", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp))
                Segmented(listOf("Auto", "µA", "mA"), CurrentUnit.entries.indexOf(prefs.currentUnit)) {
                    prefs.currentUnit = CurrentUnit.entries[it]
                }
                Text("Current sign", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp))
                Segmented(listOf("Auto", "Normal", "Inverted"), CurrentSign.entries.indexOf(prefs.currentSign)) {
                    prefs.currentSign = CurrentSign.entries[it]
                }
                Text(
                    "If charging shows a negative current, choose Inverted. Auto learns the sign while unplugged.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        if (prefs.hiddenCards.isNotEmpty()) {
            Group {
                LinkItem(Ph.ArrowsClockwise, "Show hidden Home cards", prefs.hiddenCards.size.let { n -> if (n == 1) "1 Explore card is hidden" else "$n Explore cards are hidden" }) {
                    prefs.hiddenCards = emptySet()
                }
            }
        }

        SectionHeader("About")
        Group {
            val version = remember {
                runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
            }
            ListItem(
                leadingContent = { HelloBadge(Ph.Info) },
                headlineContent = { FitText("Moto Battery ${version ?: ""}", MaterialTheme.typography.titleMedium.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize * 0.88f)) },
                supportingContent = { FitText("Data stays on your phone · No ads", MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                colors = itemColors(),
            )
        }
    }
}

@Composable
private fun Group(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth()) { content() }
}

@Composable
private fun itemColors() = ListItemDefaults.colors(containerColor = Color.Transparent)

@Composable
private fun Divider() = Spacer(Modifier.height(2.dp))

@Composable
private fun SwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    checked: Boolean,
    tint: Color = MaterialTheme.colorScheme.primary,
    onChange: (Boolean) -> Unit,
) {
    ListItem(
        modifier = Modifier.clickable { onChange(!checked) },
        leadingContent = { HelloBadge(icon) },
        headlineContent = { FitText(title, MaterialTheme.typography.titleMedium.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize * 0.88f), minScale = 0.8f) },
        supportingContent = subtitle?.let { { FitText(it, MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, minScale = 0.8f) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
        colors = itemColors(),
    )
}

@Composable
private fun LinkItem(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    tint: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit,
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { HelloBadge(icon) },
        trailingContent = { Icon(Ph.CaretRight, null) },
        headlineContent = { FitText(title, MaterialTheme.typography.titleMedium.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize * 0.88f), minScale = 0.8f) },
        supportingContent = subtitle?.let { { FitText(it, MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, minScale = 0.8f) } },
        colors = itemColors(),
    )
}

/** A permission row: a check mark when granted, an "Allow" button when not. */
@Composable
private fun PermissionItem(icon: ImageVector, title: String, subtitle: String, granted: Boolean, tint: Color, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { HelloBadge(icon) },
        headlineContent = { FitText(title, MaterialTheme.typography.titleMedium.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize * 0.88f), minScale = 0.8f) },
        supportingContent = { FitText(subtitle, MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, minScale = 0.8f) },
        trailingContent = {
            if (granted) Icon(Ph.ArrowSquareOut, "Allowed — open settings", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            else FilledTonalButton(onClick = onClick) { Text("Allow") }
        },
        colors = itemColors(),
    )
}

@Composable
private fun SliderItem(value: Int, range: IntRange, suffix: String, enabled: Boolean, onChange: (Int) -> Unit) {
    // Keep the drag position locally and only persist when the drag ends.
    var pos by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Row(Modifier.padding(start = 56.dp, end = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Slider(
            value = pos,
            onValueChange = { pos = it },
            onValueChangeFinished = { onChange(pos.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            enabled = enabled,
            modifier = Modifier.weight(1f),
        )
        Text(
            "${pos.roundToInt()}$suffix",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

@Composable
private fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    PillTabs(options, selected, onSelect, modifier = Modifier.padding(top = 10.dp), compact = true)
}

/** Hello UI "Configure" icon: a small light-blue rounded square behind the glyph. */
@Composable
private fun HelloBadge(icon: ImageVector) {
    // Moto Device Help list style: a quiet dark tile with an accent-coloured outline icon.
    Surface(Modifier.size(44.dp), shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        }
    }
}

/** Compact Moto-style shortcut: accent icon, bold title with a short subtitle, and a round "+". */
@Composable
private fun ShortcutTile(icon: ImageVector, title: String, subtitle: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = scheme.surfaceContainer,
        modifier = modifier.height(64.dp).clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(start = 12.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = scheme.primary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                FitText(title, MaterialTheme.typography.titleSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                FitText(subtitle, MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            }
            Surface(Modifier.size(24.dp), shape = androidx.compose.foundation.shape.CircleShape, color = scheme.primary) {
                androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                    Icon(Ph.Plus, null, tint = scheme.onPrimary, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}
