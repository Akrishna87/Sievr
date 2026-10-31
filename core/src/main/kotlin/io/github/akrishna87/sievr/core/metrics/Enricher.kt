package io.github.akrishna87.sievr.core.metrics

import io.github.akrishna87.sievr.core.model.AnnualFinancials
import io.github.akrishna87.sievr.core.model.Market
import io.github.akrishna87.sievr.core.model.Snapshot
import io.github.akrishna87.sievr.core.model.Stock
import io.github.akrishna87.sievr.core.model.StockData
import io.github.akrishna87.sievr.core.technical.CandlePattern
import io.github.akrishna87.sievr.core.technical.Indicators
import io.github.akrishna87.sievr.core.util.finiteOrNull
import io.github.akrishna87.sievr.core.util.median
import io.github.akrishna87.sievr.core.util.percentileRanks
import io.github.akrishna87.sievr.core.valuation.Valuation

/**
 * Turns the raw [Snapshot] into a [Market]: works out technicals from the price history,
 * ratios from the financial statements, then the market-wide figures (industry P/E,
 * cap categories, Magic Formula rank) and the 0–100 scores.
 */
object Enricher {

    const val BENCHMARK = "NIFTY 50"

    fun enrich(snapshot: Snapshot): Market {
        val benchmark = snapshot.indices.firstOrNull { it.name.equals(BENCHMARK, ignoreCase = true) }
        val benchmarkRet1y = benchmark?.c?.filterNotNull()?.let { Indicators.change(it, minOf(252, it.size - 1)) }

        val perStock = snapshot.stocks.map { data ->
            val metrics = HashMap<String, Double>()
            val patterns = CandlePattern.detect(data.ohlc)
            stockMetrics(data, metrics)
            for (p in CandlePattern.entries) metrics[p.metricId] = if (p in patterns) 1.0 else 0.0
            if (benchmarkRet1y != null) metrics["ret1y"]?.let { metrics["relStrength"] = it - benchmarkRet1y }
            Triple(data, metrics, patterns.map { it.metricId })
        }
        val allMetrics = perStock.map { it.second }
        marketMetrics(perStock.map { it.first }, allMetrics)
        Scores.apply(allMetrics)
        val stocks = perStock.map { (data, metrics, patterns) ->
            Stock(data, metrics.filterValues { it.isFinite() }, patterns)
        }
        return Market(snapshot, stocks)
    }

    // ---------------------------------------------------------------- per stock

    internal fun stockMetrics(data: StockData, out: MutableMap<String, Double>) {
        val raw = data.f
        fun put(id: String, value: Double?) { value?.finiteOrNull()?.let { out[id] = it } }
        fun rawOf(id: String) = raw[id]?.finiteOrNull()

        // Prices
        val closes = data.c.filterNotNull().filter { it > 0 }
        val price = closes.lastOrNull()
        put("price", price)
        technicals(closes, out)
        val weekly = data.w.filterNotNull().filter { it > 0 }
        if (weekly.size > 156) put("ret3y", Indicators.cagr(weekly[weekly.size - 157], weekly.last(), 3.0))
        if (weekly.size > 260) put("ret5y", Indicators.cagr(weekly[weekly.size - 261], weekly.last(), 5.0))

        val vols = data.v.filterNotNull()
        if (vols.isNotEmpty()) {
            put("volume", vols.last())
            if (price != null) put("turnover", vols.last() * price / 1e7)
            if (vols.size >= 21) {
                val avg = vols.subList(vols.size - 21, vols.size - 1).average()
                put("avgVolume20", avg)
                if (avg > 0) put("volumeRatio", vols.last() / avg)
            }
        }
        put("atrPct", Indicators.atrPercent(data.ohlc))

        // Size and valuation, using today's price where the source figure may be stale.
        val shares = rawOf("shares") ?: data.fin.lastOrNull()?.shares
        val mcap = if (price != null && shares != null && shares > 0) price * shares else rawOf("mcap")
        put("mcap", mcap)
        val eps = rawOf("eps") ?: data.fin.lastOrNull()?.eps
        put("eps", eps)
        put("pe", if (price != null && eps != null) (if (eps > 0) price / eps else null) else rawOf("pe"))
        put("fpe", rawOf("fpe"))
        val bvps = rawOf("bookValue")
            ?: data.fin.lastOrNull()?.let { f -> if (f.equity != null && shares != null && shares > 0) f.equity / shares else null }
        put("bookValue", bvps)
        put("pb", if (price != null && bvps != null && bvps > 0) price / bvps else rawOf("pb"))

        val last = data.fin.lastOrNull()
        val prev = data.fin.getOrNull(data.fin.size - 2)
        val revenue = rawOf("revenue") ?: last?.revenue
        val netIncome = rawOf("netIncome") ?: last?.netIncome
        put("revenue", revenue)
        put("netIncome", netIncome)
        if (mcap != null && revenue != null && revenue > 0) put("ps", mcap / revenue)

        val debt = rawOf("debt") ?: last?.debt
        val cash = rawOf("cash") ?: last?.cash
        put("debt", debt)
        if (debt != null && cash != null) put("netCash", cash - debt)
        val ev = if (mcap != null && debt != null && cash != null) mcap + debt - cash else rawOf("ev")
        put("ev", ev)
        val ebitda = rawOf("ebitda") ?: last?.ebitda
        put("evEbitda", if (ev != null && ebitda != null && ebitda > 0) ev / ebitda else rawOf("evEbitda"))

        val ebit = last?.ebit
        val pe = out["pe"]
        put("earningsYield", when {
            ebit != null && ev != null && ev > 0 -> ebit / ev * 100
            pe != null && pe > 0 -> 100 / pe
            else -> null
        })

        // Profitability
        put("roe", rawOf("roe") ?: ratio(last?.netIncome, last?.equity))
        put("roa", rawOf("roa") ?: ratio(last?.netIncome, last?.totalAssets))
        put("opm", rawOf("opm") ?: ratio(last?.ebit, last?.revenue))
        put("npm", rawOf("npm") ?: ratio(last?.netIncome, last?.revenue))
        put("gpm", rawOf("gpm") ?: ratio(last?.grossProfit, last?.revenue))
        put("roce", roce(last))
        val recent = data.fin.takeLast(3)
        recent.mapNotNull { roce(it) }.takeIf { it.size == recent.size && it.isNotEmpty() }?.let { put("roceMin3", it.min()) }
        recent.mapNotNull { ratio(it.netIncome, it.equity) }.takeIf { it.isNotEmpty() }?.let { put("roe3", it.average()) }
        if (last != null && prev != null) {
            val m1 = ratio(last.ebit, last.revenue)
            val m0 = ratio(prev.ebit, prev.revenue)
            if (m1 != null && m0 != null) put("opmChange", m1 - m0)
        }

        // Growth
        put("revenueGrowth", rawOf("revenueGrowth"))
        put("earningsGrowth", rawOf("earningsGrowth"))
        growth(data.fin, out)
        val profitCagr = out["profitCagr3"]
        if (pe != null && pe > 0 && profitCagr != null && profitCagr > 0) put("peg", pe / profitCagr)

        // Health
        val equity = last?.equity
        put("de", rawOf("de") ?: if (debt != null && equity != null && equity > 0) debt / equity else null)
        put("currentRatio", rawOf("currentRatio") ?: ratioPlain(last?.currentAssets, last?.currentLiabilities))
        put("quickRatio", rawOf("quickRatio"))
        if (last?.ebit != null && last.interest != null && last.interest > 0) put("interestCoverage", last.ebit / last.interest)
        put("beta", rawOf("beta"))
        put("piotroski", piotroski(data.fin)?.toDouble())
        put("altmanZ", altmanZ(last, mcap))

        // Cash flow
        val fcf = rawOf("fcf") ?: last?.let { f -> if (f.operatingCashFlow != null && f.capex != null) f.operatingCashFlow - f.capex else null }
        put("fcf", fcf)
        put("ocf", rawOf("ocf") ?: last?.operatingCashFlow)
        if (fcf != null && mcap != null && mcap > 0) put("fcfYield", fcf / mcap * 100)
        val ocfSum = recent.mapNotNull { it.operatingCashFlow }
        val niSum = recent.mapNotNull { it.netIncome }
        if (ocfSum.size == recent.size && niSum.size == recent.size && niSum.sum() > 0) put("cashConversion", ocfSum.sum() / niSum.sum() * 100)

        // Dividends
        put("divYield", rawOf("divYield"))
        put("payout", rawOf("payout"))

        // Ownership
        val h = data.holding
        put("promoter", h?.promoter ?: rawOf("insiders"))
        put("pledged", h?.pledged)
        put("fii", h?.fii)
        put("dii", h?.dii)
        put("institutions", if (h?.fii != null && h.dii != null) h.fii + h.dii else rawOf("institutions"))
        put("promoterChange", h?.promoterChange)
        put("fiiChange", h?.fiiChange)
        put("diiChange", h?.diiChange)

        // Analysts
        val target = rawOf("targetPrice")
        put("targetPrice", target)
        if (target != null && price != null && price > 0) put("targetUpside", (target / price - 1) * 100)
        put("analysts", rawOf("analysts"))
        put("rating", rawOf("rating"))

        // Fair value
        val graham = Valuation.grahamNumber(eps, bvps)
        put("grahamNumber", graham)
        if (graham != null && price != null) put("grahamUpside", (graham / price - 1) * 100)
        defaultDcf(data, out, fcf, shares, price)
    }

    private fun technicals(closes: List<Double>, out: MutableMap<String, Double>) {
        fun put(id: String, value: Double?) { value?.finiteOrNull()?.let { out[id] = it } }
        if (closes.isEmpty()) return
        val price = closes.last()
        put("chg1d", Indicators.change(closes, 1))
        put("ret1w", Indicators.change(closes, 5))
        put("ret1m", Indicators.change(closes, 21))
        put("ret3m", Indicators.change(closes, 63))
        put("ret6m", Indicators.change(closes, 126))
        put("ret1y", Indicators.change(closes, minOf(252, closes.size - 1).takeIf { closes.size > 240 } ?: Int.MAX_VALUE))
        val year = closes.takeLast(252)
        val hi = year.max()
        val lo = year.min()
        put("high52", hi)
        put("low52", lo)
        put("fromHigh52", (1 - price / hi) * 100)
        put("fromLow52", (price / lo - 1) * 100)
        if (year.size >= 200) {
            val before = year.dropLast(1)
            out["newHigh52"] = if (price > before.max()) 1.0 else 0.0
            out["newLow52"] = if (price < before.min()) 1.0 else 0.0
        }
        val sma20 = Indicators.sma(closes, 20)
        val sma50 = Indicators.sma(closes, 50)
        val sma200 = Indicators.sma(closes, 200)
        put("sma20", sma20)
        put("sma50", sma50)
        put("sma200", sma200)
        sma50?.let { put("priceVsSma50", (price / it - 1) * 100) }
        sma200?.let { put("priceVsSma200", (price / it - 1) * 100) }
        put("rsi14", Indicators.rsi(closes, 14))
        Indicators.macd(closes)?.let {
            put("macd", it.macd); put("macdSignal", it.signal); put("macdHist", it.histogram)
            out["macdBullCross"] = if (it.bullishCross) 1.0 else 0.0
        }
        if (closes.size >= 211) {
            val cross = Indicators.maCross(closes, 50, 200, 10)
            out["goldenCross"] = if (cross == 1) 1.0 else 0.0
            out["deathCross"] = if (cross == -1) 1.0 else 0.0
        }
        put("volatility", Indicators.volatility(year))
    }

    private fun growth(fin: List<AnnualFinancials>, out: MutableMap<String, Double>) {
        if (fin.size < 2) return
        val last = fin.last()
        val baseIndex = maxOf(0, fin.size - 4)
        val base = fin[baseIndex]
        val years = (last.year - base.year).toDouble()
        if (years >= 2) {
            Indicators.cagr(base.revenue, last.revenue, years)?.let { out["salesCagr3"] = it }
            Indicators.cagr(base.netIncome, last.netIncome, years)?.let { out["profitCagr3"] = it }
            Indicators.cagr(base.eps, last.eps, years)?.let { out["epsCagr3"] = it }
        }
        val yearly = (baseIndex + 1 until fin.size).mapNotNull { i ->
            val a = fin[i - 1].revenue; val b = fin[i].revenue
            if (a != null && b != null && a > 0) (b / a - 1) * 100 else null
        }
        if (yearly.size >= 2) out["salesGrowthMin3"] = yearly.min()
    }

    private fun defaultDcf(data: StockData, out: MutableMap<String, Double>, fcf: Double?, shares: Double?, price: Double?) {
        if (shares == null || shares <= 0 || price == null) return
        val history = data.fin.takeLast(3).mapNotNull { f ->
            if (f.operatingCashFlow != null && f.capex != null) f.operatingCashFlow - f.capex else null
        }
        val base = if (history.isNotEmpty()) history.average() else fcf ?: return
        if (base <= 0) return
        val g = (out["salesCagr3"] ?: out["profitCagr3"] ?: 8.0).coerceIn(0.0, 20.0)
        val result = Valuation.dcf(
            Valuation.DcfInputs(base, g, g / 2, 4.0, 12.0, out["netCash"] ?: 0.0, shares)
        ) ?: return
        if (result.valuePerShare > 0) {
            out["dcfValue"] = result.valuePerShare
            out["dcfUpside"] = (result.valuePerShare / price - 1) * 100
        }
    }

    internal fun roce(f: AnnualFinancials?): Double? {
        if (f?.ebit == null || f.totalAssets == null || f.currentLiabilities == null) return null
        val capital = f.totalAssets - f.currentLiabilities
        return if (capital > 0) f.ebit / capital * 100 else null
    }

    /** Piotroski F-score from the last two years; null when fewer than 7 of the 9 tests can be run. */
    internal fun piotroski(fin: List<AnnualFinancials>): Int? {
        if (fin.size < 2) return null
        val c = fin[fin.size - 1]
        val p = fin[fin.size - 2]
        var score = 0
        var tested = 0
        fun test(result: Boolean?) { if (result != null) { tested++; if (result) score++ } }
        val roaC = ratioPlain(c.netIncome, c.totalAssets)
        val roaP = ratioPlain(p.netIncome, p.totalAssets)
        test(c.netIncome?.let { it > 0 })
        test(c.operatingCashFlow?.let { it > 0 })
        test(if (roaC != null && roaP != null) roaC > roaP else null)
        test(if (c.operatingCashFlow != null && c.netIncome != null) c.operatingCashFlow > c.netIncome else null)
        val levC = ratioPlain(c.debt, c.totalAssets)
        val levP = ratioPlain(p.debt, p.totalAssets)
        test(if (levC != null && levP != null) levC <= levP else null)
        val crC = ratioPlain(c.currentAssets, c.currentLiabilities)
        val crP = ratioPlain(p.currentAssets, p.currentLiabilities)
        test(if (crC != null && crP != null) crC > crP else null)
        test(if (c.shares != null && p.shares != null) c.shares <= p.shares * 1.005 else null)
        val gmC = ratioPlain(c.grossProfit, c.revenue)
        val gmP = ratioPlain(p.grossProfit, p.revenue)
        test(if (gmC != null && gmP != null) gmC > gmP else null)
        val atC = ratioPlain(c.revenue, c.totalAssets)
        val atP = ratioPlain(p.revenue, p.totalAssets)
        test(if (atC != null && atP != null) atC > atP else null)
        return if (tested >= 7) score else null
    }

    internal fun altmanZ(f: AnnualFinancials?, mcap: Double?): Double? {
        if (f == null || mcap == null) return null
        val ta = f.totalAssets ?: return null
        val tl = f.totalLiabilities ?: return null
        val ca = f.currentAssets ?: return null
        val cl = f.currentLiabilities ?: return null
        val ebit = f.ebit ?: return null
        val sales = f.revenue ?: return null
        if (ta <= 0 || tl <= 0) return null
        val re = f.retainedEarnings ?: 0.0
        return 1.2 * (ca - cl) / ta + 1.4 * re / ta + 3.3 * ebit / ta + 0.6 * mcap / tl + 1.0 * sales / ta
    }

    private fun ratio(a: Double?, b: Double?): Double? = ratioPlain(a, b)?.times(100)
    private fun ratioPlain(a: Double?, b: Double?): Double? =
        if (a != null && b != null && b > 0) a / b else null

    // ---------------------------------------------------------------- market wide

    private val excludedFromMagic = listOf("financial", "bank", "insurance", "utilit")

    internal fun marketMetrics(data: List<StockData>, metrics: List<MutableMap<String, Double>>) {
        // Industry P/E: median of profitable peers, falling back to the sector when an industry is small.
        fun pes(groupOf: (StockData) -> String?) = data.indices
            .groupBy { groupOf(data[it]) ?: "" }
            .mapValues { (_, idx) -> idx.mapNotNull { metrics[it]["pe"] }.filter { it in 0.0..300.0 } }
        val byIndustry = pes { it.industry ?: it.sector }
        val bySector = pes { it.sector }
        data.indices.forEach { i ->
            val ind = byIndustry[data[i].industry ?: data[i].sector ?: ""].orEmpty()
            val peers = if (ind.size >= 3) ind else bySector[data[i].sector ?: ""].orEmpty()
            val industryPe = median(peers) ?: return@forEach
            metrics[i]["industryPe"] = industryPe
            metrics[i]["pe"]?.let { pe -> if (pe > 0) metrics[i]["peVsIndustry"] = pe / industryPe }
        }

        // Cap category by market-cap rank, as SEBI defines it (top 100 large, 101–250 mid, 251–500 small).
        data.indices.filter { metrics[it]["mcap"] != null }
            .sortedByDescending { metrics[it]["mcap"] }
            .forEachIndexed { rank, i ->
                metrics[i]["capCategory"] = when {
                    rank < 100 -> 1.0
                    rank < 250 -> 2.0
                    rank < 500 -> 3.0
                    else -> 4.0
                }
            }

        // Greenblatt's Magic Formula: rank by earnings yield and by return on capital, add the ranks.
        val eligible = data.indices.filter { i ->
            val sector = (data[i].sector ?: "").lowercase()
            excludedFromMagic.none { sector.contains(it) } &&
                (metrics[i]["earningsYield"] ?: 0.0) > 0 && (metrics[i]["roce"] ?: 0.0) > 0
        }
        val eyRank = eligible.sortedByDescending { metrics[it]["earningsYield"] }.withIndex().associate { it.value to it.index }
        val roceRank = eligible.sortedByDescending { metrics[it]["roce"] }.withIndex().associate { it.value to it.index }
        eligible.sortedBy { eyRank.getValue(it) + roceRank.getValue(it) }
            .forEachIndexed { pos, i -> metrics[i]["magicRank"] = (pos + 1).toDouble() }
    }
}

/** The 0–100 factor scores: percentile ranks against the whole market, averaged per factor. */
object Scores {
    private class Part(val id: String, val higherIsBetter: Boolean, val valid: (Double) -> Boolean = { true })

    private val quality = listOf(
        Part("roe", true), Part("roce", true), Part("opm", true), Part("piotroski", true),
        Part("cashConversion", true), Part("de", false) { it >= 0 },
    )
    private val value = listOf(
        Part("pe", false) { it > 0 }, Part("pb", false) { it > 0 }, Part("evEbitda", false) { it > 0 },
        Part("earningsYield", true), Part("fcfYield", true), Part("divYield", true),
    )
    private val growth = listOf(
        Part("salesCagr3", true), Part("profitCagr3", true), Part("revenueGrowth", true), Part("earningsGrowth", true),
    )
    private val momentum = listOf(
        Part("ret3m", true), Part("ret6m", true), Part("ret1y", true), Part("priceVsSma200", true), Part("fromHigh52", false),
    )
    private val safety = listOf(
        Part("de", false) { it >= 0 }, Part("interestCoverage", true), Part("altmanZ", true),
        Part("volatility", false), Part("pledged", false), Part("currentRatio", true),
    )

    private val factors = listOf(
        "qualityScore" to quality, "valueScore" to value, "growthScore" to growth,
        "momentumScore" to momentum, "safetyScore" to safety,
    )

    fun apply(metrics: List<MutableMap<String, Double>>) {
        val ranks = HashMap<String, Map<Int, Double>>()
        for (part in factors.flatMap { it.second }.distinctBy { it.id + it.higherIsBetter }) {
            val values = metrics.indices.mapNotNull { i ->
                metrics[i][part.id]?.takeIf { it.isFinite() && part.valid(it) }?.let { i to (if (part.higherIsBetter) it else -it) }
            }.toMap()
            ranks[part.id + part.higherIsBetter] = percentileRanks(values)
        }
        for (i in metrics.indices) {
            val scores = mutableListOf<Double>()
            for ((id, parts) in factors) {
                val got = parts.mapNotNull { ranks[it.id + it.higherIsBetter]?.get(i) }
                // Need at least half the inputs, so a single figure can't decide a score.
                if (got.size * 2 >= parts.size) {
                    val score = got.average()
                    metrics[i][id] = score
                    scores += score
                }
            }
            if (scores.size >= 3) metrics[i]["sievrScore"] = scores.average()
        }
    }
}
