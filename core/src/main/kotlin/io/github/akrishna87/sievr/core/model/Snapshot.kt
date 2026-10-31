package io.github.akrishna87.sievr.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The market data file built every evening by the data pipeline (pipeline/build_snapshot.py)
 * and downloaded by the app. Price histories are stored column-wise against the shared
 * [dailyDates] and [weeklyDates] so every stock's series lines up and the file stays small.
 */
@Serializable
data class Snapshot(
    val version: Int = 1,
    /** Last trading day in the data, ISO date (yyyy-MM-dd). */
    val asOf: String,
    /** When the file was built, ISO date-time in UTC. */
    val generatedAt: String = "",
    val source: String = "",
    /** Trading days of the daily closes, as epoch days, oldest first. */
    val dailyDates: List<Int> = emptyList(),
    /** Week-ending dates of the weekly closes, as epoch days, oldest first. */
    val weeklyDates: List<Int> = emptyList(),
    val stocks: List<StockData> = emptyList(),
    /** Benchmark indices (Nifty 50 and others), keyed by display name. */
    val indices: List<IndexData> = emptyList(),
)

@Serializable
data class IndexData(
    val name: String,
    /** Daily closes aligned to [Snapshot.dailyDates]. */
    val c: List<Double?> = emptyList(),
    /** Weekly closes aligned to [Snapshot.weeklyDates]. */
    val w: List<Double?> = emptyList(),
)

@Serializable
data class StockData(
    val symbol: String,
    val name: String,
    val isin: String? = null,
    val sector: String? = null,
    val industry: String? = null,
    /** Index memberships, e.g. "NIFTY 50", "NIFTY 500". */
    val indices: List<String> = emptyList(),
    /** Raw fundamentals from the data source; keys are [io.github.akrishna87.sievr.core.metrics.Raw] ids. */
    val f: Map<String, Double> = emptyMap(),
    /** Daily closes aligned to [Snapshot.dailyDates]; null on days without a trade. */
    val c: List<Double?> = emptyList(),
    /** Daily volumes aligned to the *last* `v.size` entries of [Snapshot.dailyDates]. */
    val v: List<Double?> = emptyList(),
    /** Weekly closes aligned to [Snapshot.weeklyDates]. */
    val w: List<Double?> = emptyList(),
    /** The last few daily bars, oldest first, as [open, high, low, close]. */
    val ohlc: List<List<Double>> = emptyList(),
    /** Annual financial statements, oldest first. */
    val fin: List<AnnualFinancials> = emptyList(),
    /** Shareholding pattern, when the source has it. */
    val holding: Shareholding? = null,
)

/** One financial year's figures. Amounts are in ₹ crore; [eps] is in ₹ per share. */
@Serializable
data class AnnualFinancials(
    val year: Int,
    val revenue: Double? = null,
    val grossProfit: Double? = null,
    val ebit: Double? = null,
    val ebitda: Double? = null,
    val interest: Double? = null,
    val netIncome: Double? = null,
    val eps: Double? = null,
    val totalAssets: Double? = null,
    val currentAssets: Double? = null,
    val currentLiabilities: Double? = null,
    val totalLiabilities: Double? = null,
    val equity: Double? = null,
    val debt: Double? = null,
    val cash: Double? = null,
    val retainedEarnings: Double? = null,
    @SerialName("ocf") val operatingCashFlow: Double? = null,
    val capex: Double? = null,
    val dividendsPaid: Double? = null,
    /** Shares outstanding, in crore. */
    val shares: Double? = null,
)

/** Shareholding pattern in percent of equity. */
@Serializable
data class Shareholding(
    val promoter: Double? = null,
    val pledged: Double? = null,
    val fii: Double? = null,
    val dii: Double? = null,
    val public: Double? = null,
    /** Change in promoter holding over the last quarter, percentage points. */
    val promoterChange: Double? = null,
    val fiiChange: Double? = null,
    val diiChange: Double? = null,
)
