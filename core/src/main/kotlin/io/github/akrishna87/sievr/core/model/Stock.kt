package io.github.akrishna87.sievr.core.model

/** A stock with every metric worked out, ready to screen, sort and show. */
class Stock(
    val data: StockData,
    /** Metric id → value. Missing or meaningless metrics are absent, never NaN. */
    val metrics: Map<String, Double>,
    /** Candlestick patterns on the last bar, by [io.github.akrishna87.sievr.core.technical.CandlePattern] id. */
    val patterns: List<String>,
) {
    val symbol: String get() = data.symbol
    val name: String get() = data.name
    val sector: String get() = data.sector ?: "Other"
    val industry: String get() = data.industry ?: sector

    operator fun get(metricId: String): Double? = metrics[metricId]

    override fun toString(): String = "Stock($symbol)"
}

/** The whole market after enrichment: stocks plus lookups the screens and screens' UI need. */
class Market(
    val snapshot: Snapshot,
    val stocks: List<Stock>,
) {
    private val bySymbol = stocks.associateBy { it.symbol }

    operator fun get(symbol: String): Stock? = bySymbol[symbol]

    val sectors: List<String> by lazy { stocks.map { it.sector }.distinct().sorted() }
    val industries: List<String> by lazy { stocks.map { it.industry }.distinct().sorted() }
    val indexNames: List<String> by lazy { stocks.flatMap { it.data.indices }.distinct().sorted() }

    fun peers(stock: Stock, limit: Int = 10): List<Stock> =
        stocks.filter { it.industry == stock.industry && it.symbol != stock.symbol }
            .sortedByDescending { it["mcap"] ?: 0.0 }
            .take(limit)
}
