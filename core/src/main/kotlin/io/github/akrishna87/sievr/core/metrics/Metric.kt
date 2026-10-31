package io.github.akrishna87.sievr.core.metrics

enum class MetricUnit { RUPEE, CRORE, PERCENT, RATIO, TIMES, NUMBER, SCORE, FLAG, VOLUME, YEARS }

enum class Category(val label: String) {
    PRICE("Price & returns"),
    SIZE("Size"),
    VALUATION("Valuation"),
    PROFITABILITY("Profitability"),
    GROWTH("Growth"),
    HEALTH("Financial health"),
    CASH_FLOW("Cash flow"),
    DIVIDENDS("Dividends"),
    OWNERSHIP("Ownership"),
    ANALYSTS("Analysts"),
    TECHNICAL("Technicals"),
    PATTERNS("Candlestick patterns"),
    SCORES("Sievr scores"),
}

/**
 * A screenable number. [id] is what screens, formulas and the snapshot use; [aliases] are
 * other names the formula language and the plain-English screen builder accept.
 */
data class Metric(
    val id: String,
    val label: String,
    val category: Category,
    val unit: MetricUnit,
    val description: String,
    /** True when a bigger value is usually better (used by scores and default sorting). */
    val higherIsBetter: Boolean? = null,
    val aliases: List<String> = emptyList(),
    /** True when the backtester can work it out at past dates from weekly prices. */
    val historical: Boolean = false,
) {
    val isFlag: Boolean get() = unit == MetricUnit.FLAG
}
