package io.github.akrishna87.sievr.ui.components

import io.github.akrishna87.sievr.core.metrics.Metrics
import io.github.akrishna87.sievr.core.metrics.MetricUnit
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/** Number formatting in the Indian style: ₹1,23,456.78, ₹12,345 Cr, 1.2 L shares. */
object Fmt {
    private val locale = Locale("en", "IN")

    fun indian(x: Double, decimals: Int = 2): String {
        val negative = x < 0
        val fixed = String.format(Locale.US, "%.${decimals}f", abs(x))
        val intPart = fixed.substringBefore('.')
        val frac = if (decimals > 0) "." + fixed.substringAfter('.') else ""
        val grouped = if (intPart.length <= 3) intPart else {
            val last3 = intPart.takeLast(3)
            val rest = intPart.dropLast(3).reversed().chunked(2).joinToString(",").reversed()
            "$rest,$last3"
        }
        return (if (negative) "-" else "") + grouped + frac
    }

    fun price(x: Double?): String = x?.let { "₹" + indian(it, if (abs(it) >= 10_000) 0 else 2) } ?: "—"

    fun crore(x: Double?): String = when {
        x == null -> "—"
        abs(x) >= 1_00_000 -> "₹" + indian(x / 1_00_000, 2) + " L Cr"
        abs(x) >= 100 -> "₹" + indian(x, 0) + " Cr"
        else -> "₹" + indian(x, 2) + " Cr"
    }

    fun percent(x: Double?, signed: Boolean = false, decimals: Int = 1): String = when (x) {
        null -> "—"
        else -> (if (signed && x > 0) "+" else "") + String.format(Locale.US, "%.${decimals}f", x) + "%"
    }

    fun number(x: Double?, decimals: Int = 2): String = when {
        x == null -> "—"
        abs(x) >= 1e5 -> indian(x, 0)
        else -> String.format(Locale.US, "%.${decimals}f", x)
    }

    fun volume(x: Double?): String = when {
        x == null -> "—"
        x >= 1e7 -> String.format(Locale.US, "%.2f Cr", x / 1e7)
        x >= 1e5 -> String.format(Locale.US, "%.2f L", x / 1e5)
        x >= 1e3 -> String.format(Locale.US, "%.1f K", x / 1e3)
        else -> String.format(Locale.US, "%.0f", x)
    }

    /** Formats a metric value for display according to its unit. */
    fun metric(id: String, x: Double?): String {
        if (x == null) return "—"
        val m = Metrics[id] ?: return number(x)
        return when (m.unit) {
            MetricUnit.RUPEE -> price(x)
            MetricUnit.CRORE -> crore(x)
            MetricUnit.PERCENT -> percent(x, signed = id.startsWith("ret") || id.startsWith("chg") || id.endsWith("Change") || id == "relStrength" || id.endsWith("Upside") || id.startsWith("priceVs"))
            MetricUnit.RATIO -> number(x)
            MetricUnit.TIMES -> if (abs(x) >= 1000) indian(x, 0) + "×" else number(x, if (abs(x) >= 100) 0 else 1) + "×"
            MetricUnit.SCORE -> String.format(Locale.US, "%.0f", x)
            MetricUnit.FLAG -> if (x >= 1) "Yes" else "No"
            MetricUnit.VOLUME -> volume(x)
            MetricUnit.YEARS -> number(x, 1) + " y"
            MetricUnit.NUMBER -> when (id) {
                "capCategory" -> capCategory(x)
                "piotroski", "magicRank", "analysts" -> String.format(Locale.US, "%.0f", x)
                else -> number(x)
            }
        }
    }

    fun capCategory(x: Double?): String = when (x?.toInt()) {
        1 -> "Large cap"; 2 -> "Mid cap"; 3 -> "Small cap"; 4 -> "Micro cap"; else -> "—"
    }

    fun epochDay(day: Int, pattern: String = "d MMM yyyy"): String =
        LocalDate.ofEpochDay(day.toLong()).format(DateTimeFormatter.ofPattern(pattern, locale))

    fun isoDate(iso: String): String = runCatching {
        LocalDate.parse(iso.take(10)).format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy", locale))
    }.getOrDefault(iso)
}
