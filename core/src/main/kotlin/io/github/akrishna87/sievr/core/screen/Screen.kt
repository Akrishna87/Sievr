package io.github.akrishna87.sievr.core.screen

import io.github.akrishna87.sievr.core.formula.Formula
import io.github.akrishna87.sievr.core.metrics.Metrics
import io.github.akrishna87.sievr.core.model.Stock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class Op(val symbol: String) { GT(">"), GTE("≥"), LT("<"), LTE("≤"), BETWEEN("between"), EQ("=") }

@Serializable
sealed interface Filter {
    fun matches(stock: Stock): Boolean
    /** Metric ids the filter reads, for the results table and the backtester. */
    fun metricIds(): Set<String>
    fun describe(): String
}

/** A metric compared with a number, e.g. ROE ≥ 15, or Market cap between 500 and 5000. */
@Serializable
@SerialName("metric")
data class MetricFilter(
    val metric: String,
    val op: Op,
    val value: Double,
    val value2: Double? = null,
) : Filter {
    override fun matches(stock: Stock): Boolean = test(stock[metric])

    fun test(x: Double?): Boolean {
        if (x == null) return false
        return when (op) {
            Op.GT -> x > value
            Op.GTE -> x >= value
            Op.LT -> x < value
            Op.LTE -> x <= value
            Op.EQ -> x == value
            Op.BETWEEN -> x >= minOf(value, value2 ?: value) && x <= maxOf(value, value2 ?: value)
        }
    }

    override fun metricIds() = setOf(metric)

    override fun describe(): String {
        val label = Metrics[metric]?.label ?: metric
        val m = Metrics[metric]
        if (m?.isFlag == true) return if (value >= 1) label else "Not $label"
        return when (op) {
            Op.BETWEEN -> "$label ${fmt(value)}–${fmt(value2 ?: value)}"
            else -> "$label ${op.symbol} ${fmt(value)}"
        }
    }
}

/** A free-form formula, e.g. `PE < Industry_PE AND ROCE > 20`. */
@Serializable
@SerialName("formula")
data class FormulaFilter(val expression: String) : Filter {
    @kotlinx.serialization.Transient
    private val parsed: Formula? = Formula.check(expression).getOrNull()

    val isValid: Boolean get() = parsed != null

    override fun matches(stock: Stock): Boolean = parsed?.matches { stock[it] } ?: false

    /** Matches against any metric source, e.g. past values in the backtester. */
    fun matches(values: (String) -> Double?): Boolean = parsed?.matches(values) ?: false
    override fun metricIds(): Set<String> = parsed?.metricIds ?: emptySet()
    override fun describe() = expression
}

/** Stocks in any of the chosen sectors. */
@Serializable
@SerialName("sector")
data class SectorFilter(val sectors: Set<String>, val exclude: Boolean = false) : Filter {
    override fun matches(stock: Stock): Boolean = (stock.sector in sectors) != exclude
    override fun metricIds() = emptySet<String>()
    override fun describe() = (if (exclude) "Not in " else "Sector: ") + sectors.sorted().joinToString(", ")
}

/** Stocks in any of the chosen industries. */
@Serializable
@SerialName("industry")
data class IndustryFilter(val industries: Set<String>) : Filter {
    override fun matches(stock: Stock): Boolean = stock.industry in industries
    override fun metricIds() = emptySet<String>()
    override fun describe() = "Industry: " + industries.sorted().joinToString(", ")
}

/** Members of any of the chosen indices, e.g. NIFTY 50. */
@Serializable
@SerialName("index")
data class IndexFilter(val indices: Set<String>) : Filter {
    override fun matches(stock: Stock): Boolean = stock.data.indices.any { it in indices }
    override fun metricIds() = emptySet<String>()
    override fun describe() = "In " + indices.sorted().joinToString(" or ")
}

/** Where the stock is listed: "NSE", "BSE" (listed on BSE, whatever else), or "BSE_ONLY". */
@Serializable
@SerialName("exchange")
data class ExchangeFilter(val exchange: String) : Filter {
    override fun matches(stock: Stock): Boolean = when (exchange) {
        "BSE_ONLY" -> stock.bseOnly
        else -> exchange in stock.exchanges
    }
    override fun metricIds() = emptySet<String>()
    override fun describe() = when (exchange) {
        "BSE_ONLY" -> "Only on BSE"
        else -> "Listed on $exchange"
    }
}

/** Any of the chosen candlestick patterns on the last bar. */
@Serializable
@SerialName("pattern")
data class PatternFilter(val patterns: Set<String>) : Filter {
    override fun matches(stock: Stock): Boolean = stock.patterns.any { it in patterns }
    override fun metricIds() = patterns
    override fun describe() = "Pattern: " + patterns.mapNotNull { Metrics[it]?.label }.joinToString(" or ")
}

@Serializable
data class Screen(
    val id: String,
    val name: String,
    val description: String = "",
    val filters: List<Filter> = emptyList(),
    val sortBy: String = "mcap",
    val sortDescending: Boolean = true,
    /** Extra metric columns to show in the results, besides the ones the filters use. */
    val columns: List<String> = emptyList(),
    /** Notify when stocks enter or leave the results. */
    val alerts: Boolean = false,
    /** Show only the top N results (0 = all). */
    val limit: Int = 0,
    val category: String = "",
) {
    /** Columns to show: price, the filters' metrics, the sort metric, then the chosen extras. */
    fun displayColumns(): List<String> =
        (listOf("price", "chg1d", "mcap") + filters.flatMap { it.metricIds() }.filterNot { it.startsWith("pat_") } + sortBy + columns)
            .distinct()
            .filter { Metrics[it] != null }
}

object ScreenEngine {
    fun run(screen: Screen, stocks: List<Stock>): List<Stock> {
        val matched = stocks.filter { s -> !s.isEtf && screen.filters.all { it.matches(s) } }
        val sorted = sort(matched, screen.sortBy, screen.sortDescending)
        return if (screen.limit > 0) sorted.take(screen.limit) else sorted
    }

    fun count(filters: List<Filter>, stocks: List<Stock>): Int = stocks.count { s -> !s.isEtf && filters.all { it.matches(s) } }

    /** Sorts with missing values always last. */
    fun sort(stocks: List<Stock>, metric: String, descending: Boolean): List<Stock> {
        val (has, missing) = stocks.partition { it[metric] != null }
        val sorted = if (descending) has.sortedByDescending { it[metric] } else has.sortedBy { it[metric] }
        return sorted + missing
    }
}

internal fun fmt(x: Double): String =
    if (x == x.toLong().toDouble() && kotlin.math.abs(x) < 1e15) x.toLong().toString()
    else "%.2f".format(x).trimEnd('0').trimEnd('.')
