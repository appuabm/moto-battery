package com.chargesense.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import android.os.Build
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/*
 * Motorola "Hello UI" foundations: Moto blue on the "Vellum" neutral range (warm off-whites in
 * light mode, cool blue-blacks in dark mode), plus a success green for charging. Token values are
 * taken from the published Hello UI theme swatches.
 */

private val HelloDark = darkColorScheme(
    primary = Color(0xFFA6C8FF),
    onPrimary = Color(0xFF003060),
    primaryContainer = Color(0xFF004787),
    onPrimaryContainer = Color(0xFFD5E3FF),
    inversePrimary = Color(0xFF0E77DA),
    secondary = Color(0xFFBDC7DC),
    onSecondary = Color(0xFF273141),
    secondaryContainer = Color(0xFF3D4758),
    onSecondaryContainer = Color(0xFFD9E3F8),
    tertiary = Color(0xFFDBBDE2),
    onTertiary = Color(0xFF3E2846),
    tertiaryContainer = Color(0xFF553F5D),
    onTertiaryContainer = Color(0xFFF8D8FF),
    error = Color(0xFFFFB4AB),
    background = Color(0xFF09111B),
    onBackground = Color(0xFFE7E2DD),
    surface = Color(0xFF070E18),
    onSurface = Color(0xFFF1EEEB),
    surfaceVariant = Color(0xFF5F5F60),
    onSurfaceVariant = Color(0xFFDED9D4),
    surfaceDim = Color(0xFF000000),
    surfaceBright = Color(0xFF363940),
    surfaceContainerLowest = Color(0xFF000611),
    surfaceContainerLow = Color(0xFF161C27),
    surfaceContainer = Color(0xFF1B2029),
    surfaceContainerHigh = Color(0xFF262A32),
    surfaceContainerHighest = Color(0xFF2C3038),
    outline = Color(0xFF787777),
    outlineVariant = Color(0xFF313337),
    inverseSurface = Color(0xFFF1EEEB),
    inverseOnSurface = Color(0xFF161C27),
)

private val HelloLight = lightColorScheme(
    primary = Color(0xFF0E77DA),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD5E3FF),
    onPrimaryContainer = Color(0xFF001C3B),
    inversePrimary = Color(0xFFA6C8FF),
    secondary = Color(0xFF6D778A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD9E3F8),
    onSecondaryContainer = Color(0xFF121C2B),
    tertiary = Color(0xFF886E90),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF8D8FF),
    onTertiaryContainer = Color(0xFF27132F),
    error = Color(0xFFDE3730),
    background = Color(0xFFFBF9F7),
    onBackground = Color(0xFF161C27),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF161C27),
    surfaceVariant = Color(0xFFCACACB),
    onSurfaceVariant = Color(0xFF454649),
    surfaceDim = Color(0xFFE7E2DD),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFBF9F7),
    surfaceContainer = Color(0xFFF5F3F1),
    surfaceContainerHigh = Color(0xFFF1EEEB),
    surfaceContainerHighest = Color(0xFFEBE8E5),
    outline = Color(0xFF787777),
    outlineVariant = Color(0xFFE3E1DB),
    inverseSurface = Color(0xFF0E131E),
    inverseOnSurface = Color(0xFFFBF9F7),
)

/**
 * Accent colours follow the system (wallpaper / Moto "Colours" setting) on Android 12+, while the
 * neutral surfaces stay on Hello UI's Vellum range — the same split Motorola uses, so the app
 * matches your chosen accent and still feels like Moto. Older Android versions use Moto blue.
 */
@Composable
fun ChargeSenseTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val base = if (dark) HelloDark else HelloLight
    val scheme = if (Build.VERSION.SDK_INT >= 31) {
        val context = LocalContext.current
        val dyn = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        base.copy(
            primary = dyn.primary, onPrimary = dyn.onPrimary,
            primaryContainer = dyn.primaryContainer, onPrimaryContainer = dyn.onPrimaryContainer,
            inversePrimary = dyn.inversePrimary, surfaceTint = dyn.surfaceTint,
            secondary = dyn.secondary, onSecondary = dyn.onSecondary,
            secondaryContainer = dyn.secondaryContainer, onSecondaryContainer = dyn.onSecondaryContainer,
            tertiary = dyn.tertiary, onTertiary = dyn.onTertiary,
            tertiaryContainer = dyn.tertiaryContainer, onTertiaryContainer = dyn.onTertiaryContainer,
        )
    } else base
    val cfg = androidx.compose.ui.platform.LocalConfiguration.current
    val k = typeScaleFor(cfg.screenWidthDp, cfg.fontScale)
    val typography = androidx.compose.runtime.remember(k) { scaledTypography(k) }
    androidx.compose.runtime.CompositionLocalProvider(LocalCompact provides (k < 1f)) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}

/**
 * How much room text really has is the screen width divided by the user's font size. Below about
 * 400dp of that, headings and titles step down so layouts keep their shape on small phones and
 * with large font settings, without ignoring the user's font size for body text.
 */
fun typeScaleFor(widthDp: Int, fontScale: Float): Float {
    val effective = widthDp / fontScale.coerceAtLeast(0.5f)
    return when {
        effective < 320 -> 0.82f
        effective < 350 -> 0.88f
        effective < 380 -> 0.93f
        effective < 400 -> 0.97f
        else -> 1f
    }
}

/** True on narrow effective widths, for components that also tighten their own geometry. */
val LocalCompact = androidx.compose.runtime.staticCompositionLocalOf { false }

private fun androidx.compose.ui.text.TextStyle.scaled(k: Float) =
    if (k == 1f) this else copy(fontSize = fontSize * k, lineHeight = lineHeight * k)

/** [AppTypography] with display, headline and title sizes scaled by [k]; body and labels keep size. */
fun scaledTypography(k: Float): Typography = AppTypography.let { t ->
    t.copy(
        displayLarge = t.displayLarge.scaled(k), displayMedium = t.displayMedium.scaled(k), displaySmall = t.displaySmall.scaled(k),
        headlineLarge = t.headlineLarge.scaled(k), headlineMedium = t.headlineMedium.scaled(k), headlineSmall = t.headlineSmall.scaled(k),
        titleLarge = t.titleLarge.scaled(k), titleMedium = t.titleMedium.scaled(k), titleSmall = t.titleSmall.scaled((1f + k) / 2),
    )
}

/** Hello UI "success" token, used for charging. */
val ChargingGreen: Color
    @Composable @ReadOnlyComposable
    get() = if (isSystemInDarkTheme()) Color(0xFF8DD88C) else Color(0xFF3E8643)

/** Battery level colour: theme primary normally, tertiary when getting low, error when critical. */
@Composable
fun levelColor(level: Float): Color = when {
    level <= 15f -> MaterialTheme.colorScheme.error
    level <= 30f -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.primary
}

@Composable
fun tempColor(c: Float): Color = when {
    c >= 42f -> MaterialTheme.colorScheme.error
    c >= 38f -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.onSurface
}

/** Colour for a signed current: primary while charging, tertiary while draining. */
@Composable
fun currentColor(ma: Int?): Color = when {
    ma == null -> MaterialTheme.colorScheme.onSurfaceVariant
    ma > 0 -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.tertiary
}

/**
 * Type uses the system font, so it follows the font chosen in the phone's settings (on Motorola:
 * Personalise → Fonts). Hello UI is "confident": bold, tight headlines; big readings stay light.
 */
private val base = Typography()
val AppTypography = Typography(
    // Big readings: light and calm.
    displayLarge = base.displayLarge.copy(fontSize = 52.sp, lineHeight = 58.sp, fontWeight = FontWeight.Light, letterSpacing = (-0.5).sp),
    displayMedium = base.displayMedium.copy(fontSize = 42.sp, lineHeight = 48.sp, fontWeight = FontWeight.Light, letterSpacing = (-0.25).sp),
    displaySmall = base.displaySmall.copy(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.sp),
    // Page and hero titles: confident, slightly tight.
    headlineLarge = base.headlineLarge.copy(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
    headlineMedium = base.headlineMedium.copy(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
    headlineSmall = base.headlineSmall.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    // Card, list and tile titles.
    titleLarge = base.titleLarge.copy(fontSize = 19.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    titleMedium = base.titleMedium.copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    titleSmall = base.titleSmall.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.1.sp),
    // Reading text.
    bodyLarge = base.bodyLarge.copy(fontSize = 16.sp, lineHeight = 23.sp, letterSpacing = 0.sp),
    bodyMedium = base.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.sp),
    bodySmall = base.bodySmall.copy(fontSize = 12.5.sp, lineHeight = 17.sp, letterSpacing = 0.1.sp),
    // Labels: small but never cramped.
    labelLarge = base.labelLarge.copy(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp),
    labelMedium = base.labelMedium.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp),
    labelSmall = base.labelSmall.copy(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp),
)
