package com.chargesense.app.ui.screens

import com.chargesense.app.ui.theme.Ph
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chargesense.app.data.BatterySnapshot
import com.chargesense.app.data.Estimates
import com.chargesense.app.data.formatMinutes
import com.chargesense.app.ui.components.StatusPill
import com.chargesense.app.ui.theme.currentColor
import com.chargesense.app.ui.theme.levelColor
import com.chargesense.app.ui.theme.tempColor
import kotlin.math.abs

/**
 * The dashboard's headline card: a gradient tinted by battery state, a glowing level ring that
 * breathes while charging, live current / temperature / voltage, and the time estimate.
 */
@Composable
fun HeroCard(
    s: BatterySnapshot,
    smoothed: Float?,
    capacity: Float,
    chargeTarget: Int,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val accent = levelColor(s.levelPct)
    val charging = s.isPlugged && s.isCharging
    val shape = RoundedCornerShape(32.dp)

    Box(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(accent.copy(alpha = 0.32f), scheme.surfaceContainer, scheme.surfaceContainer),
                    start = Offset.Zero,
                    end = Offset(900f, 1400f),
                )
            )
            .clickable(onClick = onClick)
            .padding(20.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusPill(
                    when {
                        s.isPlugged && !s.isCharging && !s.isFull -> "Charging paused"
                        else -> s.statusText
                    },
                    if (s.isPlugged) Ph.Lightning else null,
                    emphasized = s.isPlugged,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    if (s.isPlugged) "${s.plugText} connected" else "On battery",
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlowRing(s.levelPct, accent, breathing = charging, size = 172.dp, caption = "Battery level")
                Spacer(Modifier.width(20.dp))
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    HeroStat(
                        Ph.Lightning,
                        s.currentMa?.let { (if (it > 0) "+" else "") + "$it mA" } ?: "—",
                        "Current",
                        currentColor(s.currentMa),
                    )
                    HeroStat(Ph.Thermometer, "%.1f °C".format(s.temperatureC), "Temperature", tempColor(s.temperatureC))
                    HeroStat(
                        Ph.Plug,
                        "%.2f V".format(s.voltageMv / 1000f),
                        (s.powerW?.let { "%.1f W · ".format(abs(it)) } ?: "") + "Power",
                        scheme.secondary,
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            val mins = Estimates.minutesRemaining(s, smoothed, capacity, chargeTarget)
            Surface(shape = RoundedCornerShape(20.dp), color = scheme.surface.copy(alpha = 0.55f), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Ph.Clock, null, tint = accent, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        when {
                            s.isFull -> "Fully charged — unplug to reduce wear"
                            s.isPlugged && !s.isCharging -> "Charging paused by the system at ${s.levelPct.toInt()}%"
                            mins == null -> if (s.isPlugged) "Working out charging time…" else "Working out time left…"
                            s.isPlugged -> "${formatMinutes(mins)} until ${Estimates.chargeTargetFor(s, chargeTarget)}%"
                            else -> "About ${formatMinutes(mins)} left at this usage"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

/** Rounded card with a soft diagonal gradient from [accent] into the surface colour. */
@Composable
fun GradientCard(accent: Color, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(32.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(accent.copy(alpha = 0.32f), scheme.surfaceContainer, scheme.surfaceContainer),
                    start = Offset.Zero,
                    end = Offset(900f, 1400f),
                )
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(20.dp)
    ) { content() }
}

@Composable
private fun HeroStat(icon: ImageVector, value: String, label: String, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon, tint, size = 36)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

/** Level ring with a gradient sweep and, while charging, a slow breathing glow behind it. */
@Composable
fun GlowRing(level: Float, color: Color, breathing: Boolean, size: Dp, centerText: String? = null, caption: String? = null) {
    val animated by animateFloatAsState(level.coerceIn(0f, 100f), tween(900, easing = FastOutSlowInEasing), label = "ring")
    val transition = rememberInfiniteTransition(label = "glow")
    val glow by transition.animateFloat(
        initialValue = 0.10f,
        targetValue = 0.38f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glowAlpha",
    )
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            if (breathing) {
                drawCircle(
                    Brush.radialGradient(listOf(color.copy(alpha = glow), Color.Transparent)),
                    radius = this.size.minDimension / 2,
                )
            }
            // Stroke and padding scale with the ring so small and large rings look alike.
            val stroke = this.size.minDimension * 0.08f
            val inset = stroke / 2 + this.size.minDimension * 0.035f
            val arc = Size(this.size.width - inset * 2, this.size.height - inset * 2)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
        }
        // Rotated so the sweep gradient starts at 12 o'clock with the arc.
        Canvas(Modifier.fillMaxSize().rotate(-90f)) {
            // Stroke and padding scale with the ring so small and large rings look alike.
            val stroke = this.size.minDimension * 0.08f
            val inset = stroke / 2 + this.size.minDimension * 0.035f
            val arc = Size(this.size.width - inset * 2, this.size.height - inset * 2)
            drawArc(
                color,
                0f, 360f * animated / 100f, false, Offset(inset, inset), arc,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (centerText != null) Text(centerText, fontSize = (size.value * 0.26f).sp, fontWeight = FontWeight.SemiBold)
            else RingNumber(level.toInt().toString(), size)
            if (caption != null) {
                com.chargesense.app.ui.components.FitText(
                    caption,
                    MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp).widthIn(max = size * 0.6f),
                )
            }
        }
    }
}

/** Number + "%" for the centre of a ring: same baseline, "%" smaller and tucked close. */
@Composable
fun RingNumber(value: String, ringSize: Dp, color: Color = Color.Unspecified) {
    Row {
        Text(
            value,
            fontSize = (ringSize.value * 0.26f).sp,
            lineHeight = (ringSize.value * 0.3f).sp,
            fontWeight = FontWeight.SemiBold,
            color = color,
            modifier = Modifier.alignByBaseline(),
        )
        Text(
            "%",
            fontSize = (ringSize.value * 0.12f).sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.alignByBaseline().padding(start = 2.dp),
        )
    }
}
