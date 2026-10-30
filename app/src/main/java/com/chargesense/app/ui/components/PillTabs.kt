package com.chargesense.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * Pill-shaped tab switcher on a soft track: the selected tab fills with the primary colour.
 * Replaces outlined segmented buttons throughout the app.
 */
@Composable
fun PillTabs(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    icons: List<ImageVector>? = null,
    compact: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    FitTogether(labels.size) { Surface(shape = RoundedCornerShape(28.dp), color = scheme.surfaceContainerHighest, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            labels.forEachIndexed { i, label ->
                val isSel = i == selected
                val bg by animateColorAsState(if (isSel) scheme.primary else Color.Transparent, label = "pillBg")
                val fg by animateColorAsState(if (isSel) scheme.onPrimary else scheme.onSurfaceVariant, label = "pillFg")
                Row(
                    Modifier
                        .weight(1f)
                        .clip(CircleShape)
                        .background(bg)
                        .clickable(role = Role.Tab) { onSelect(i) }
                        .padding(horizontal = 8.dp, vertical = if (compact) 9.dp else 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    icons?.getOrNull(i)?.let {
                        Icon(it, null, tint = fg, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                    }
                    FitText(label, MaterialTheme.typography.labelLarge, color = fg, role = "tab")
                }
            }
        }
    } }
}
