package com.chargesense.app.ui.components

import com.chargesense.app.ui.theme.Space
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Components after Motorola's Hello UI apps (Device Help, Moto Secure, Smart Connect, Family
 * Space): device card with an image and accent pill, a big ring card with a pill action, list
 * cards with a dark icon box, and pill-shaped buttons.
 */

private val HelloCardShape = RoundedCornerShape(32.dp)

/** Filled pill button (Hello UI primary action). */
@Composable
fun PillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val scheme = MaterialTheme.colorScheme
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = scheme.primary, contentColor = scheme.onPrimary),
        contentPadding = PaddingValues(start = if (icon != null) 16.dp else 20.dp, end = 20.dp),
    ) {
        if (icon != null) { Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
        FitText(text, MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp), fontWeight = FontWeight.Bold)
    }
}

/** Outlined pill button (secondary action, e.g. "Disconnect", "Join family"). */
@Composable
fun OutlinePillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        shape = CircleShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        contentPadding = PaddingValues(horizontal = 24.dp),
    ) {
        if (icon != null) { Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1)
    }
}

/**
 * Device Help's top card: a launch-style banner with the phone's model and a health status card,
 * and the battery details in one divided row beneath.
 */
@Composable
fun DeviceCard(details: List<Pair<String, String>>, health: Int?, rating: String, onHealth: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val healthColor = healthStatusColor(health)
    // Health joins the row as the last stat, coloured green / amber / red; tapping it opens Health.
    val items = details + ("Health" to (health?.let { "$it%" } ?: "—"))
    Surface(shape = HelloCardShape, color = scheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            ModelBanner()
            DividedStats(
                items,
                Modifier.padding(top = 18.dp, bottom = 4.dp),
                centered = false,
                valueColors = List(details.size) { null } + healthColor,
                onClick = { if (it == details.size) onHealth() },
                captions = List(details.size) { null } + rating,
            )
        }
    }
}

/**
 * Device Help's storage card: a big ring with a percentage and a small value under it, and a
 * light pill action beside it.
 */
@Composable
fun RingActionCard(
    percent: Float,
    sub: String,
    subIcon: ImageVector?,
    ringColor: Color,
    caption: String,
    action: String,
    onAction: () -> Unit,
    onRing: () -> Unit,
    actionIcon: ImageVector? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val animated by animateFloatAsState(percent.coerceIn(0f, 100f), tween(900, easing = FastOutSlowInEasing), label = "ring")
    Surface(shape = HelloCardShape, color = scheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(com.chargesense.app.ui.theme.Space.Card + 2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val ring = if (com.chargesense.app.ui.theme.LocalCompact.current) 136.dp else 160.dp
                val stroke = if (com.chargesense.app.ui.theme.LocalCompact.current) 10.dp else 12.dp
                Box(Modifier.size(ring).clip(CircleShape).clickable(onClick = onRing), contentAlignment = Alignment.Center) {
                    val track = scheme.surfaceContainerHighest
                    Canvas(Modifier.fillMaxSize()) {
                        val st = stroke.toPx()
                        val tl = Offset(st / 2, st / 2)
                        val sz = Size(size.width - st, size.height - st)
                        drawArc(track, 0f, 360f, false, tl, sz, style = Stroke(st))
                        drawArc(ringColor, -90f, 360f * animated / 100f, false, tl, sz, style = Stroke(st, cap = StrokeCap.Round))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        com.chargesense.app.ui.screens.RingNumber(percent.toInt().toString(), ring)
                        // Kept inside the ring's inner circle (diameter minus the stroke and a margin).
                        Row(Modifier.widthIn(max = ring - stroke * 2 - 20.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (subIcon != null) { Icon(subIcon, null, Modifier.size(14.dp), tint = scheme.onSurfaceVariant); Spacer(Modifier.width(3.dp)) }
                            FitText(sub, MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, modifier = Modifier.weight(1f, fill = false))
                        }
                    }
                }
                Spacer(Modifier.width(14.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    PillButton(action, onAction, icon = actionIcon)
                }
            }
            Spacer(Modifier.height(16.dp))
            FitText(caption, MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant, minScale = 0.75f)
        }
    }
}

/** Device Help list card: dark rounded icon box with a coloured icon, bold title and subtitle. */
@Composable
fun HelpCard(icon: ImageVector, iconTint: Color, title: String, subtitle: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = HelloCardShape,
        color = scheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().clip(HelloCardShape).clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = Space.Card, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Surface(color = scheme.surfaceContainerLowest, modifier = Modifier.fillMaxSize()) {}
                Icon(icon, null, tint = iconTint, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontSize = 21.sp)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
            }
        }
    }
}
