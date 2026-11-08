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
import androidx.compose.ui.graphics.luminance
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
fun SievrTheme(
    mode: String = "system",
    fontScale: Float = 1f,
    fontFamily: String = "system",
    skinColor: Long = 0L,
    skinTint: Int = 1,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        "dark", "black" -> true
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
        var scheme = if (dark) Dark else Light
        if (mode == "black") scheme = scheme.copy(
            background = Color.Black, surface = Color.Black, surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color(0xFF0A0A0A), surfaceContainer = Color(0xFF121212), surfaceContainerHigh = Color(0xFF1A1A1A),
            surfaceContainerHighest = Color(0xFF222222), surfaceVariant = Color(0xFF1A1A1A),
        )
        if (skinColor != 0L) scheme = skinned(scheme, Color(skinColor), skinTint)
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
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

/** App skins: an accent colour for the whole app. 0 is Sievr's own teal. */
val skinChoices: List<Pair<Long, String>> = listOf(
    0L to "Teal", 0xFF1565C0 to "Ocean", 0xFF6A1B9A to "Royal", 0xFFAD1457 to "Rose", 0xFFEF6C00 to "Sunset",
    0xFF2E7D32 to "Forest", 0xFFC62828 to "Ruby", 0xFF00838F to "Lagoon", 0xFF5D4037 to "Coffee", 0xFF455A64 to "Slate",
    0xFFF9A825 to "Gold", 0xFF283593 to "Indigo",
)

/** What each skin choice looks like as a swatch (teal for the default). */
fun skinSwatch(color: Long): Color = if (color == 0L) Color(0xFF0F766E) else Color(color)

/**
 * [base] recoloured around [c]: it becomes the accent (buttons, tabs, chips), and with [tint] 1 (light) or 2 (strong)
 * the backgrounds, bars and cards take on a wash of it. Gains and losses keep their own green and red.
 */
fun skinned(base: androidx.compose.material3.ColorScheme, c: Color, tint: Int): androidx.compose.material3.ColorScheme {
    val dark = base.surface.luminance() < 0.5f
    val lerp = { a: Color, b: Color, f: Float -> androidx.compose.ui.graphics.lerp(a, b, f) }
    // A dark colour on a dark page is lifted so it stays visible.
    val accent = if (dark) lerp(c, Color.White, 0.35f) else c
    val amount = when (tint) { 0 -> 0f; 1 -> if (dark) 0.10f else 0.07f; else -> if (dark) 0.20f else 0.16f }
    fun wash(x: Color, k: Float = 1f) = lerp(x, c, (amount * k).coerceAtMost(0.5f))
    return base.copy(
        primary = accent,
        onPrimary = if (accent.luminance() > 0.5f) Color.Black else Color.White,
        primaryContainer = lerp(base.surface, c, if (dark) 0.35f else 0.22f),
        onPrimaryContainer = base.onSurface,
        secondaryContainer = lerp(base.surface, c, if (dark) 0.32f else 0.20f),
        onSecondaryContainer = base.onSurface,
        background = wash(base.background),
        surface = wash(base.surface),
        surfaceVariant = wash(base.surfaceVariant, 1.3f),
        surfaceContainerLowest = wash(base.surfaceContainerLowest),
        surfaceContainerLow = wash(base.surfaceContainerLow, 1.3f),
        surfaceContainer = wash(base.surfaceContainer, 1.4f),
        surfaceContainerHigh = wash(base.surfaceContainerHigh, 1.5f),
        surfaceContainerHighest = wash(base.surfaceContainerHighest, 1.6f),
    )
}
