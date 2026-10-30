package com.chargesense.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class NavItem(val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

/**
 * Bottom navigation in the style of Motorola's apps (Smart Connect, Family Space): full width on
 * the page background with a hairline above, icon over label, and a short accent indicator line
 * on top of the selected item. Selected items use the filled icon and a bold label.
 */
@Composable
fun MotoNavBar(items: List<NavItem>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth().background(scheme.surface).navigationBarsPadding()) {
        HorizontalDivider(color = scheme.outlineVariant)
        Row(Modifier.fillMaxWidth().height(72.dp)) {
            items.forEachIndexed { i, item ->
                val isSelected = i == selected
                val tint by animateColorAsState(if (isSelected) scheme.onSurface else scheme.onSurfaceVariant, label = "navTint")
                val indicator by animateDpAsState(if (isSelected) 40.dp else 0.dp, label = "navIndicator")
                Box(
                    Modifier
                        .weight(1f)
                        .height(72.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = false, radius = 36.dp),
                            role = Role.Tab,
                        ) {
                            if (!isSelected) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelect(i)
                        },
                    contentAlignment = Alignment.TopCenter,
                ) {
                    // Indicator line sitting on the top edge, as in Moto's apps.
                    Box(Modifier.width(indicator).height(3.dp).background(scheme.primary, RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp)))
                    Column(Modifier.padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(if (isSelected) item.selectedIcon else item.icon, item.label, tint = tint, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.height(6.dp))
                        Text(
                            item.label,
                            color = tint,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
