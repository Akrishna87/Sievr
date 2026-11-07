package io.github.akrishna87.sievr.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.akrishna87.sievr.ui.theme.LocalMarketColors
import kotlin.math.abs

/** "+₹1,706" or "−₹120": money with its sign, for gains and losses. */
fun signedMoney(x: Double): String = (if (x > 0.005) "+" else if (x < -0.005) "−" else "") + Fmt.price(abs(x))

/**
 * The portfolio's headline numbers: current value large, then invested, total returns and 1-day returns
 * side by side, and a bar showing how much of today's value is money put in versus gains.
 * [dayChange] null means there's no previous price yet, shown as "—" rather than a misleading ₹0.
 */
@Composable
fun ReturnsCard(
    value: Double,
    invested: Double,
    gain: Double,
    gainPercent: Double?,
    dayChange: Double?,
    dayPercent: Double?,
    footnote: String? = null,
    modifier: Modifier = Modifier,
    label: String = "Current value",
    /** Annualised return; pass [showCagr] true with null to explain how to get one. */
    cagr: io.github.akrishna87.sievr.core.portfolio.Growth.Summary? = null,
    showCagr: Boolean = false,
) {
    SievrCard(modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(Fmt.price(value), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("Invested", Fmt.price(invested), null, Modifier.weight(1f))
                Stat("Total returns", signedMoney(gain), gainPercent?.let { Fmt.percent(it, true, 2) }, Modifier.weight(1f), colorFor = gain)
                Stat("1-day returns", dayChange?.let { signedMoney(it) } ?: "—", dayPercent?.takeIf { dayChange != null }?.let { Fmt.percent(it, true, 2) },
                    Modifier.weight(1f), colorFor = dayChange)
            }
            if (showCagr) {
                Spacer(Modifier.height(12.dp))
                CagrLine(cagr)
            }
            if (value > 0 && invested > 0) {
                Spacer(Modifier.height(14.dp))
                InvestedBar(invested, value)
            }
            if (footnote != null) {
                Spacer(Modifier.height(10.dp))
                Text(footnote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun CagrLine(s: io.github.akrishna87.sievr.core.portfolio.Growth.Summary?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("CAGR", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 10.dp))
        when {
            s == null -> Text("Add the date you bought each holding (tap Edit) to see yearly growth",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            s.cagr == null -> Text("Shows once your money has been invested about a year (since ${UserDate.monthYear(s.earliest)})",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> Column {
                val c = s.cagr ?: 0.0
                Text("${Fmt.percent(c, true, 2)} a year", style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold, color = changeColor(c))
                Text("since ${UserDate.monthYear(s.earliest)}" +
                    (if (s.coverage < 99.5) " · counts ${Fmt.percent(s.coverage, decimals = 0)} of invested (holdings with a buy date)" else ""),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Buy dates typed as DD-MM-YYYY (or with / or .), stored as yyyy-MM-dd. */
object UserDate {
    private val shown = java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy")
    private val month = java.time.format.DateTimeFormatter.ofPattern("MMM yyyy")
    fun show(iso: String?): String = io.github.akrishna87.sievr.core.portfolio.Growth.parse(iso)?.format(shown) ?: ""
    fun monthYear(d: java.time.LocalDate): String = d.format(month)
    /** "" → null (no date); otherwise the ISO date, or [INVALID] if it can't be read or is in the future. */
    fun parse(text: String): String? {
        val t = text.trim()
        if (t.isEmpty()) return null
        val p = t.split('-', '/', '.', ' ').filter { it.isNotBlank() }.mapNotNull { it.toIntOrNull() }
        if (p.size != 3) return INVALID
        val year = if (p[2] < 100) 2000 + p[2] else p[2]
        val d = runCatching { java.time.LocalDate.of(year, p[1], p[0]) }.getOrNull() ?: return INVALID
        return if (d.isAfter(java.time.LocalDate.now()) || d.year < 1980) INVALID else d.toString()
    }
    const val INVALID = "invalid"
}

@Composable
private fun Stat(label: String, value: String, sub: String?, modifier: Modifier, colorFor: Double? = null) {
    val color = if (colorFor == null) MaterialTheme.colorScheme.onSurface else changeColor(colorFor)
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = color, maxLines = 2)
        if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = color, maxLines = 1)
    }
}

/** One bar: the invested part, then the gain in green (or the loss eaten out of it, in red). */
@Composable
private fun InvestedBar(invested: Double, value: Double) {
    val up = LocalMarketColors.current.up
    val down = LocalMarketColors.current.down
    val total = maxOf(invested, value)
    val base = (minOf(invested, value) / total).toFloat().coerceIn(0.01f, 1f)
    val rest = 1f - base
    Column {
        Row(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp))) {
            Box(Modifier.weight(base).height(10.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)))
            if (rest > 0.001f) Box(Modifier.weight(rest).height(10.dp).background(if (value >= invested) up else down.copy(alpha = 0.6f)))
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth()) {
            Text(if (value >= invested) "Invested" else "Current value", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(if (value >= invested) "Gains" else "Loss", style = MaterialTheme.typography.labelSmall,
                color = if (value >= invested) up else down)
        }
    }
}

/**
 * One holding: name, a detail line (units, prices), then invested → current value and the returns.
 */
@Composable
fun HoldingRow(
    title: String,
    detail: String,
    invested: Double,
    value: Double,
    gain: Double,
    gainPercent: Double?,
    dayPercent: Double?,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
    cagr: Double? = null,
) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
            if (trailing != null) trailing()
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text("Invested", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(Fmt.price(invested), style = MaterialTheme.typography.bodyMedium)
            }
            Column(Modifier.weight(1f)) {
                Text("Current", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(Fmt.price(value), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }
            Column(Modifier.weight(1.1f), horizontalAlignment = Alignment.End) {
                Text("Returns", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(signedMoney(gain), style = MaterialTheme.typography.bodyMedium, color = changeColor(gain), fontWeight = FontWeight.Medium)
                gainPercent?.let { Text(Fmt.percent(it, true, 2), style = MaterialTheme.typography.bodySmall, color = changeColor(gain)) }
                if (cagr != null) Text("CAGR ${Fmt.percent(cagr, true, 2)}", style = MaterialTheme.typography.labelSmall, color = changeColor(cagr))
                if (dayPercent != null) Text("1D ${Fmt.percent(dayPercent, true, 2)}", style = MaterialTheme.typography.labelSmall, color = changeColor(dayPercent))
            }
        }
    }
    HorizontalDivider()
}
