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
fun SievrTheme(mode: String = "system", content: @Composable () -> Unit) {
    val dark = when (mode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val colors = if (dark) MarketColors(Color(0xFF4ADE80), Color(0xFFF87171), Color(0xFF94A3B8))
    else MarketColors(Color(0xFF15803D), Color(0xFFDC2626), Color(0xFF64748B))
    val base = Typography()
    val typography = base.copy(
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp),
    )
    androidx.compose.runtime.CompositionLocalProvider(LocalMarketColors provides colors) {
        MaterialTheme(colorScheme = if (dark) Dark else Light, typography = typography, content = content)
    }
}
