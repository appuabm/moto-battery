package com.chargesense.app.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Spacing scale, measured from Motorola's apps (Device Help, Moto Secure): ~20 dp screen edges,
 * ~22 dp card padding and 12 dp between cards.
 */
object Space {
    /** Left/right edge of every screen; titles, cards and list content all align to it. */
    val Gutter: androidx.compose.ui.unit.Dp
        @androidx.compose.runtime.Composable @androidx.compose.runtime.ReadOnlyComposable
        get() = if (LocalCompact.current) 16.dp else 20.dp
    /** Gap between stacked cards. */
    val Gap: androidx.compose.ui.unit.Dp
        @androidx.compose.runtime.Composable @androidx.compose.runtime.ReadOnlyComposable
        get() = if (LocalCompact.current) 10.dp else 12.dp
    /** Inner padding of cards. */
    val Card: androidx.compose.ui.unit.Dp
        @androidx.compose.runtime.Composable @androidx.compose.runtime.ReadOnlyComposable
        get() = if (LocalCompact.current) 18.dp else 20.dp
    /** Space above a section header and between it and its first card. */
    val SectionTop = 22.dp
    val SectionBottom = 8.dp
    /** Space left under the last item so it clears the navigation bar comfortably. */
    val Bottom = 28.dp
}

/**
 * Optional brand typeface for the phone's model name. Motorola's fonts are not part of this
 * repository: drop `moto_brand_regular.ttf` (and optionally `moto_brand_medium.ttf`) into
 * `res/font/` in your own build to use them. Without them the system font is used.
 */
@androidx.compose.runtime.Composable
fun motoBrandFont(): androidx.compose.ui.text.font.FontFamily {
    val context = androidx.compose.ui.platform.LocalContext.current
    return androidx.compose.runtime.remember {
        val res = context.resources
        val regular = res.getIdentifier("moto_brand_regular", "font", context.packageName)
        val medium = res.getIdentifier("moto_brand_medium", "font", context.packageName).takeIf { it != 0 } ?: regular
        if (regular == 0) androidx.compose.ui.text.font.FontFamily.Default
        else androidx.compose.ui.text.font.FontFamily(
            androidx.compose.ui.text.font.Font(regular, androidx.compose.ui.text.font.FontWeight.Normal),
            androidx.compose.ui.text.font.Font(medium, androidx.compose.ui.text.font.FontWeight.Medium),
            androidx.compose.ui.text.font.Font(medium, androidx.compose.ui.text.font.FontWeight.Bold),
        )
    }
}
