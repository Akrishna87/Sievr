package io.github.akrishna87.sievr.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Teal = Color(0xFF0F766E)
private val TealLight = Color(0xFF5EEAD4)

private val Light = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCCFBF1),
    onPrimaryContainer = Color(0xFF042F2E),
    secondary = Color(0xFF334155),
    secondaryContainer = Color(0xFFE2E8F0),
    onSecondaryContainer = Color(0xFF0F172A),
    tertiary = Color(0xFFB45309),
    background = Color(0xFFF8FAFC),
    surface = Color.White,
    surfaceVariant = Color(0xFFF1F5F9),
    surfaceContainer = Color(0xFFF1F5F9),
    surfaceContainerHigh = Color(0xFFE9EEF3),
    surfaceContainerLow = Color(0xFFF6F8FA),
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE2E8F0),
)

private val Dark = darkColorScheme(
    primary = TealLight,
    onPrimary = Color(0xFF042F2E),
    primaryContainer = Color(0xFF134E4A),
    onPrimaryContainer = Color(0xFFCCFBF1),
    secondary = Color(0xFFCBD5E1),
    secondaryContainer = Color(0xFF1E293B),
    onSecondaryContainer = Color(0xFFE2E8F0),
    tertiary = Color(0xFFFBBF24),
    background = Color(0xFF0B1220),
    surface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFF1E293B),
    surfaceContainer = Color(0xFF152033),
    surfaceContainerHigh = Color(0xFF1E293B),
    surfaceContainerLow = Color(0xFF111A2B),
    outline = Color(0xFF334155),
    outlineVariant = Color(0xFF1E293B),
)

/** Colours for gains and losses, which Material's scheme doesn't have. */
@Immutable
data class MarketColors(val up: Color, val down: Color, val neutral: Color)

val LocalMarketColors = staticCompositionLocalOf { MarketColors(Color(0xFF15803D), Color(0xFFDC2626), Color.Gray) }

@Composable
fun SievrTheme(mode: String = "system", fontScale: Float = 1f, fontFamily: String = "system", content: @Composable () -> Unit) {
    val dark = when (mode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val colors = if (dark) MarketColors(Color(0xFF4ADE80), Color(0xFFF87171), Color(0xFF94A3B8))
    else MarketColors(Color(0xFF15803D), Color(0xFFDC2626), Color(0xFF64748B))
    val base = Typography()
    val family = fontFamilyFor(fontFamily)
    fun TextStyle.f() = copy(fontFamily = family)
    val typography = base.copy(
        displayLarge = base.displayLarge.f(), displayMedium = base.displayMedium.f(), displaySmall = base.displaySmall.f(),
        headlineLarge = base.headlineLarge.f(), headlineMedium = base.headlineMedium.f(), headlineSmall = base.headlineSmall.f(),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold).f(),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold).f(),
        titleSmall = base.titleSmall.f(),
        bodyLarge = base.bodyLarge.f(), bodyMedium = base.bodyMedium.f(), bodySmall = base.bodySmall.f(),
        labelLarge = base.labelLarge.f(), labelMedium = base.labelMedium.f(),
        labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp).f(),
    )
    // Text size: multiply the phone's own font scale, so it also respects Android's accessibility setting.
    val density = androidx.compose.ui.platform.LocalDensity.current
    val scaled = androidx.compose.ui.unit.Density(density.density, density.fontScale * fontScale.coerceIn(0.8f, 1.5f))
    androidx.compose.runtime.CompositionLocalProvider(LocalMarketColors provides colors, androidx.compose.ui.platform.LocalDensity provides scaled) {
        MaterialTheme(colorScheme = if (dark) Dark else Light, typography = typography, content = content)
    }
}

/** The fonts users can pick: all ship with Android, so nothing is downloaded. */
val fontChoices = listOf(
    "system" to "System",
    "serif" to "Serif",
    "condensed" to "Condensed",
    "mono" to "Monospace",
    "handwritten" to "Handwritten",
)

fun fontFamilyFor(id: String): androidx.compose.ui.text.font.FontFamily = when (id) {
    "serif" -> androidx.compose.ui.text.font.FontFamily.Serif
    "mono" -> androidx.compose.ui.text.font.FontFamily.Monospace
    "handwritten" -> androidx.compose.ui.text.font.FontFamily.Cursive
    "condensed" -> androidx.compose.ui.text.font.FontFamily(
        androidx.compose.ui.text.font.Typeface(android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.NORMAL)),
    )
    else -> androidx.compose.ui.text.font.FontFamily.Default
}

/** Text size choices: label to multiplier. */
val textSizeChoices = listOf("Small" to 0.9f, "Default" to 1f, "Large" to 1.15f, "Extra large" to 1.3f)
