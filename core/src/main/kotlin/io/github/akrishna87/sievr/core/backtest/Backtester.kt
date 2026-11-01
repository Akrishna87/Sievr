package io.github.akrishna87.sievr.core.backtest

import io.github.akrishna87.sievr.core.metrics.Metrics
import io.github.akrishna87.sievr.core.model.Market
import io.github.akrishna87.sievr.core.model.Stock
import io.github.akrishna87.sievr.core.screen.ExchangeFilter
import io.github.akrishna87.sievr.core.screen.Filter
import io.github.akrishna87.sievr.core.screen.FormulaFilter
import io.github.akrishna87.sievr.core.screen.IndexFilter
import io.github.akrishna87.sievr.core.screen.IndustryFilter
import io.github.akrishna87.sievr.core.screen.MetricFilter
import io.github.akrishna87.sievr.core.screen.PatternFilter
import io.github.akrishna87.sievr.core.screen.Screen
import io.github.akrishna87.sievr.core.screen.SectorFilter
import io.github.akrishna87.sievr.core.technical.Indicators
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Replays a screen on weekly prices: every [Settings.rebalanceWeeks] it runs the screen as it
 * would have looked then, buys the top [Settings.maxHoldings] equally, and holds them until the
 * next rebalance.
 *
 * Price-based metrics (returns, moving averages, 52-week range, RSI) are recalculated at each past
 * date. Fundamentals are only known as they are today, so filters on them carry look-ahead bias,
 * and the universe is today's listed stocks (survivorship bias). The result lists both caveats.
 */
object Backtester {

    data class Settings(
        val years: Int = 5,
        val rebalanceWeeks: Int = 4,
        val maxHoldings: Int = 20,
        /** Round-trip cost per rebalance on the traded value, % (brokerage, STT, slippage). */
        val costPercent: Double = 0.4,
        val riskFreeRate: Double = 6.5,
    )

    data class Point(val epochDay: Int, val strategy: Double, val benchmark: Double?)

    data class Rebalance(val epochDay: Int, val holdings: List<String>)

    data class Result(
        val curve: List<Point>,
        val rebalances: List<Rebalance>,
        val cagr: Double,
        val benchmarkCagr: Double?,
        val totalReturn: Double,
        val benchmarkTotalReturn: Double?,
        val maxDrawdown: Double,
        val volatility: Double,
        val sharpe: Double?,
        /** Share of rebalance periods that beat the benchmark, %. */
        val winRate: Double?,
        val averageHoldings: Double,
        /** Metrics used with today's values at past dates. */
        val lookAheadMetrics: List<String>,
        val benchmarkName: String,
    )

    /** Metrics that can be recalculated from weekly closes at a past date. */
    val historicalMetrics: Set<String> = Metrics.all.filter { it.historical }.map { it.id }.toSet()

    fun run(screen: Screen, market: Market, settings: Settings = Settings()): Result? {
        val dates = market.snapshot.weeklyDates
        if (dates.size < 30) return null
        val weeks = minOf(settings.years * 52, dates.size - 53)
        if (weeks < 8) return null
        val start = dates.size - 1 - weeks
        val prices: Map<String, List<Double?>> = market.stocks.associate { it.symbol to it.data.w }

        val benchIndex = market.snapshot.indices.firstOrNull { it.name.equals("NIFTY 50", true) }
        val bench: List<Double?>? = benchIndex?.w?.takeIf { it.size == dates.size && it[start] != null }

        val lookAhead = screen.filters.flatMap { it.metricIds() }
            .filter { it !in historicalMetrics }.distinct()
            .mapNotNull { Metrics[it]?.label }

        var equity = 1.0
        var holdings: List<String> = emptyList()
        val curve = mutableListOf<Point>()
        val rebalances = mutableListOf<Rebalance>()
        val periodReturns = mutableListOf<Double>()
        val periodWins = mutableListOf<Boolean>()
        var periodStartEquity = 1.0
        var periodStartBench: Double? = bench?.get(start)
        val benchBase = bench?.get(start)

        for (t in start until dates.size) {
            if (t > start && holdings.isNotEmpty()) {
                // Equal-weight portfolio's return this week; a stock with a missing price is held flat.
                val r = holdings.map { sym ->
                    val p = prices[sym]
                    val a = p?.getOrNull(t - 1)
                    val b = p?.getOrNull(t)
                    if (a != null && b != null && a > 0) b / a - 1 else 0.0
                }.average()
                equity *= 1 + r
            }
            if ((t - start) % settings.rebalanceWeeks == 0 && t < dates.size - 1) {
                if (t > start) {
                    val br = bench?.get(t)
                    if (br != null && periodStartBench != null) periodWins += equity / periodStartEquity > br / periodStartBench!!
                    periodStartEquity = equity
                    periodStartBench = br
                }
                val chosen = select(screen, market.stocks, prices, t, settings.maxHoldings)
                val turnover = if (holdings.isEmpty()) 1.0 else chosen.count { it !in holdings }.toDouble() / maxOf(1, chosen.size)
                if (chosen.isNotEmpty()) equity *= 1 - settings.costPercent / 100 * turnover
                holdings = chosen
                rebalances += Rebalance(dates[t], chosen)
            }
            val b = bench?.get(t)
            curve += Point(dates[t], equity, if (b != null && benchBase != null) b / benchBase else null)
        }

        val years = weeks / 52.0
        val weekly = (1 until curve.size).map { curve[it].strategy / curve[it - 1].strategy - 1 }
        val mean = weekly.average()
        val sd = sqrt(weekly.sumOf { (it - mean) * (it - mean) } / maxOf(1, weekly.size - 1))
        val vol = sd * sqrt(52.0) * 100
        val cagr = (equity.pow(1 / years) - 1) * 100
        var peak = 0.0
        var maxDd = 0.0
        for (p in curve) {
            peak = maxOf(peak, p.strategy)
            maxDd = maxOf(maxDd, 1 - p.strategy / peak)
        }
        val benchEnd = curve.last().benchmark
        return Result(
            curve = curve,
            rebalances = rebalances,
            cagr = cagr,
            benchmarkCagr = benchEnd?.let { (it.pow(1 / years) - 1) * 100 },
            totalReturn = (equity - 1) * 100,
            benchmarkTotalReturn = benchEnd?.let { (it - 1) * 100 },
            maxDrawdown = maxDd * 100,
            volatility = vol,
            sharpe = if (vol > 0) (cagr - settings.riskFreeRate) / vol else null,
            winRate = if (periodWins.isNotEmpty()) periodWins.count { it } * 100.0 / periodWins.size else null,
            averageHoldings = rebalances.map { it.holdings.size }.average(),
            lookAheadMetrics = lookAhead,
            benchmarkName = if (bench != null) "Nifty 50" else "—",
        )
    }

    private fun select(
        screen: Screen, stocks: List<Stock>, prices: Map<String, List<Double?>>, t: Int, max: Int
    ): List<String> {
        val candidates = stocks.mapNotNull { stock ->
            val series = prices[stock.symbol] ?: return@mapNotNull null
            if (series.getOrNull(t) == null) return@mapNotNull null
            val past = pastMetrics(series, t) ?: return@mapNotNull null
            val lookup = { id: String -> if (id in historicalMetrics) past[id] else stock[id] }
            if (screen.filters.all { matchesAt(it, stock, lookup) }) stock to lookup(screen.sortBy) else null
        }
        val sorted = candidates.sortedWith(compareBy<Pair<Stock, Double?>> { it.second == null }.thenBy {
            val v = it.second ?: 0.0
            if (screen.sortDescending) -v else v
        })
        val limit = if (screen.limit > 0) minOf(screen.limit, max) else max
        return sorted.take(limit).map { it.first.symbol }
    }

    private fun matchesAt(filter: Filter, stock: Stock, lookup: (String) -> Double?): Boolean = when (filter) {
        is MetricFilter -> filter.test(lookup(filter.metric))
        is FormulaFilter -> filter.matches(lookup)
        is SectorFilter, is IndustryFilter, is IndexFilter, is ExchangeFilter -> filter.matches(stock)
        // Candlestick patterns need daily bars, which only exist for recent sessions.
        is PatternFilter -> false
    }

    /** Price metrics at week [t] from weekly closes (52 weeks ≈ 1 year, 10 ≈ 50 days, 40 ≈ 200 days). */
    internal fun pastMetrics(series: List<Double?>, t: Int): Map<String, Double>? {
        val from = maxOf(0, t - 60)
        val window = (from..t).mapNotNull { series[it] }
        if (window.size < 14) return null
        val price = window.last()
        val year = window.takeLast(53)
        val out = HashMap<String, Double>()
        out["price"] = price
        Indicators.change(window, 4)?.let { out["ret1m"] = it }
        Indicators.change(window, 13)?.let { out["ret3m"] = it }
        Indicators.change(window, 26)?.let { out["ret6m"] = it }
        Indicators.change(window, 52)?.let { out["ret1y"] = it }
        out["high52"] = year.max()
        out["low52"] = year.min()
        out["fromHigh52"] = (1 - price / year.max()) * 100
        out["fromLow52"] = (price / year.min() - 1) * 100
        Indicators.sma(window, 10)?.let { out["sma50"] = it; out["priceVsSma50"] = (price / it - 1) * 100 }
        Indicators.sma(window, 40)?.let { out["sma200"] = it; out["priceVsSma200"] = (price / it - 1) * 100 }
        Indicators.rsi(window, 14)?.let { out["rsi14"] = it }
        return out
    }
}
