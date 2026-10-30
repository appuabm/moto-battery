package com.chargesense.app.ui.components

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chargesense.app.ui.theme.ChargingGreen
import com.chargesense.app.ui.theme.Ph

/**
 * A phone name split the way Motorola writes it on its banners: an optional brand line, the series
 * as the big lowercase word ("edge", "razr", "moto g") and the generation/variant stacked beside it
 * ("50" over "NEO").
 */
data class ModelName(val brand: String?, val series: String, val top: String?, val bottom: String?)

fun modelName(model: String = Build.MODEL, maker: String = Build.MANUFACTURER): ModelName {
    val isMoto = maker.equals("motorola", true) || model.lowercase().startsWith("moto")
    var rest = model.trim()
    if (rest.lowercase().startsWith("motorola ")) rest = rest.substring(9).trim()
    else if (!isMoto && rest.lowercase().startsWith(maker.lowercase() + " ")) rest = rest.substring(maker.length).trim()
    val words = rest.split(Regex("\\s+")).filter { it.isNotEmpty() }.toMutableList()
    if (words.isEmpty()) return ModelName(maker.lowercase(), model, null, null)
    var series = words.removeAt(0)
    // "moto g84 5G" → series "moto g", then "84" over "5G".
    if (series.equals("moto", true) && words.isNotEmpty()) {
        val m = Regex("^([a-zA-Z]+)(\\d.*)?$").find(words[0])
        if (m != null) {
            words.removeAt(0)
            series = "moto ${m.groupValues[1].lowercase()}"
            m.groupValues[2].takeIf { it.isNotEmpty() }?.let { words.add(0, it) }
        }
    }
    val brand = if (isMoto) "motorola" else maker.lowercase()
    return ModelName(
        brand = brand,
        series = if (isMoto) series.lowercase() else series,
        top = words.getOrNull(0),
        bottom = words.drop(1).joinToString(" ").uppercase().ifEmpty { null },
    )
}

/**
 * Card header on the plain card surface: the Moto logo, the model in Motorola's typeface (variant
 * stacked beside the series).
 */
@Composable
fun ModelBanner(modifier: Modifier = Modifier) {
    val name = remember { modelName() }
    val font = com.chargesense.app.ui.theme.motoBrandFont()
    // The Motorola logo is optional too (`res/drawable/moto_logo.xml` in your own build only).
    val context = androidx.compose.ui.platform.LocalContext.current
    val logo = remember { context.resources.getIdentifier("moto_logo", "drawable", context.packageName) }
    val isMoto = name.brand == "motorola"
    val ink = MaterialTheme.colorScheme.onSurface
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isMoto && logo != 0) {
            androidx.compose.foundation.Image(
                androidx.compose.ui.res.painterResource(logo),
                contentDescription = "Motorola",
                // Tinted so the logo reads in both light and dark themes.
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(ink),
                modifier = Modifier.size(42.dp),
            )
            Spacer(Modifier.width(12.dp))
        } else if (!isMoto && name.brand != null) {
            Text(name.brand, color = ink, fontFamily = font, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(8.dp))
        }
        Text(name.series, color = ink, fontFamily = font, fontSize = 36.sp, lineHeight = 40.sp, maxLines = 1)
        if (name.top != null) {
            Spacer(Modifier.width(5.dp))
            Column {
                Text(name.top, color = ink, fontFamily = font, fontSize = 15.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold)
                if (name.bottom != null) {
                    Text(name.bottom, color = ink, fontFamily = font, fontSize = 12.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
                }
            }
        }
    }
}

/** Green / amber / red for a health percentage; null means not measured yet. */
@Composable
fun healthStatusColor(health: Int?): Color {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return when {
        health == null -> MaterialTheme.colorScheme.primary
        health >= 80 -> ChargingGreen
        health >= 70 -> if (dark) Color(0xFFF2B33D) else Color(0xFFA86A00)
        else -> if (dark) Color(0xFFFF6B5E) else Color(0xFFC62828)
    }
}

private fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

/**
 * Health tag: a semi-transparent card tinted by status (green, amber, red) with an icon, the
 * health figure and rating, and a chevron to the details.
 */
@Composable
fun HealthTag(health: Int?, rating: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = healthStatusColor(health)
    val shape = RoundedCornerShape(18.dp)
    Surface(
        shape = shape,
        color = c.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, c.copy(alpha = 0.35f)),
        modifier = modifier.fillMaxWidth().clip(shape).clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) { drawCircle(c.copy(alpha = 0.22f)) }
                Icon(if (health != null && health < 70) Ph.BatteryWarning else Ph.ShieldCheck, null, tint = c, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (health != null) "Health $health% · $rating" else "Battery health",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = c,
                )
                Text("Tap to see details", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Ph.CaretRight, null, tint = c, modifier = Modifier.size(18.dp))
        }
    }
}
