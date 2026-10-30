package com.chargesense.app.ui.components

import com.chargesense.app.ui.theme.Space
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Building blocks modelled on Motorola's own apps (Moto, Moto Secure, Battery settings):
 * bold section labels with a hairline rule, square action tiles, feature cards with two quick
 * links, and the big-number battery header with a thick bar.
 */

/** "Data security ———" style label: bold text followed by a hairline that runs to the edge. */
@Composable
fun MotoSection(text: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(top = Space.SectionTop, bottom = Space.SectionBottom), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.width(12.dp))
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** Square tile: outline icon top-left, bold label at the bottom, optional value above it. */
@Composable
fun MotoTile(icon: ImageVector, label: String, modifier: Modifier = Modifier, value: String? = null, tint: Color = MaterialTheme.colorScheme.onSurface, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        // Fixed height (not square) so tiles stay compact on wide landscape screens too.
        modifier = modifier.height(124.dp).clip(RoundedCornerShape(24.dp)).clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(if (com.chargesense.app.ui.theme.LocalCompact.current) 14.dp else 18.dp)) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
            Spacer(Modifier.weight(1f))
            if (value != null) FitText(value, MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, role = "value", minScale = 0.7f)
            FitText(label, MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, role = "label", minScale = 0.62f, color = if (value != null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
        }
    }
}

/** A quick link inside a feature card: small accent icon and label. */
data class MotoLink(val icon: ImageVector, val label: String, val onClick: () -> Unit)

/**
 * Feature card as on the Moto app's home: outline icon on the left, bold title and subtitle, a
 * divider, then up to two accent-coloured quick links.
 */
@Composable
fun MotoFeatureCard(icon: ImageVector, title: String, subtitle: String, links: List<MotoLink>, onClose: (() -> Unit)? = null, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = scheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(start = 18.dp, end = 16.dp, top = 16.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = scheme.primary, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FitText(title, MaterialTheme.typography.titleMedium.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize * 0.9f), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), minScale = 0.8f)
                    if (onClose != null) {
                        Icon(
                            ImageVector.vectorResource(com.chargesense.app.R.drawable.ic_close),
                            "Hide $title",
                            tint = scheme.onSurfaceVariant,
                            modifier = Modifier.size(32.dp).clip(CircleShape).clickable(onClick = onClose).padding(7.dp),
                        )
                    }
                }
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                HorizontalDivider(Modifier.padding(vertical = 12.dp), color = scheme.outlineVariant)
                FitTogether(links.size) { Row {
                    links.take(2).forEach { link ->
                        Row(
                            Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable(onClick = link.onClick).padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(link.icon, null, tint = scheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            FitText(link.label, MaterialTheme.typography.labelLarge, color = scheme.primary, modifier = Modifier.padding(end = 6.dp), role = "link", minScale = 0.75f)
                        }
                    }
                } }
            }
        }
    }
}

/** Moto Battery-settings header: huge thin level, small %, a thick rounded bar and a status line. */
@Composable
fun MotoBatteryHeader(level: Float, status: String, barColor: Color, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val animated by animateFloatAsState(level.coerceIn(0f, 100f), tween(800), label = "header")
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${level.toInt()}", fontSize = 88.sp, fontWeight = FontWeight.Light, letterSpacing = (-3).sp, lineHeight = 88.sp)
            Text("%", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 4.dp, bottom = 16.dp))
        }
        Spacer(Modifier.height(10.dp))
        Canvas(Modifier.fillMaxWidth().height(14.dp)) {
            val r = CornerRadius(size.height / 2)
            drawRoundRect(scheme.surfaceContainerHighest, Offset.Zero, size, r)
            drawRoundRect(barColor, Offset.Zero, Size(size.width * animated / 100f, size.height), r)
        }
        Spacer(Modifier.height(12.dp))
        Text(status, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
    }
}

/** Row of equal-width items with a gap, for tiles. */
@Composable
fun MotoTileRow(content: @Composable (Modifier) -> Unit) {
    FitTogether { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) { content(Modifier.weight(1f)) } }
}
