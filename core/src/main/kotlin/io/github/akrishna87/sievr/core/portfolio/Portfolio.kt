package io.github.akrishna87.sievr.core.portfolio

import io.github.akrishna87.sievr.core.model.Market
import io.github.akrishna87.sievr.core.model.Stock
import kotlinx.serialization.Serializable

@Serializable
data class Holding(val symbol: String, val quantity: Double, val averageCost: Double)

/** Portfolio X-ray: value, gains, sector mix, weighted fundamentals, scores and risk. */
object PortfolioAnalyzer {

    data class Position(
        val holding: Holding,
        val stock: Stock?,
        val price: Double?,
        val value: Double,
        val cost: Double,
        val gain: Double,
        val gainPercent: Double?,
        val dayChange: Double,
        val weight: Double,
    )

    data class Report(
        val positions: List<Position>,
        val value: Double,
        val cost: Double,
        val gain: Double,
        val gainPercent: Double?,
        val dayChange: Double,
        val dayChangePercent: Double?,
        /** Sector → share of value, 0–100, largest first. */
        val sectors: List<Pair<String, Double>>,
        /** Cap category (1 large … 4 micro) → share of value. */
        val capMix: Map<Int, Double>,
        /** Value-weighted averages of each metric over holdings that have it. */
        val weighted: Map<String, Double>,
        /** Expected dividends over the next year at today's yields, ₹. */
        val annualDividends: Double,
        /** Largest single holding, %. */
        val topWeight: Double,
        /** Herfindahl concentration, 0–10,000. Above 2,500 is concentrated. */
        val concentration: Double,
        /** Effective number of equally sized holdings (10,000 / HHI). */
        val effectiveHoldings: Double,
        val warnings: List<String>,
    )

    private val weightedMetrics = listOf(
        "pe", "pb", "roe", "roce", "de", "divYield", "beta", "salesCagr3", "profitCagr3",
        "qualityScore", "valueScore", "growthScore", "momentumScore", "safetyScore", "sievrScore", "ret1y", "volatility",
    )

    fun analyze(holdings: List<Holding>, market: Market): Report {
        val raw = holdings.map { h ->
            val stock = market[h.symbol]
            val price = stock?.get("price")
            val value = (price ?: h.averageCost) * h.quantity
            val chg = stock?.get("chg1d")
            val dayChange = if (chg != null) value - value / (1 + chg / 100) else 0.0
            Triple(h, stock, Triple(price, value, dayChange))
        }
        val total = raw.sumOf { it.third.second }
        val positions = raw.map { (h, stock, t) ->
            val (price, value, day) = t
            val cost = h.averageCost * h.quantity
            Position(h, stock, price, value, cost, value - cost,
                if (cost > 0) (value / cost - 1) * 100 else null, day, if (total > 0) value / total * 100 else 0.0)
        }.sortedByDescending { it.value }
        val cost = positions.sumOf { it.cost }
        val day = positions.sumOf { it.dayChange }

        val sectors = positions.groupBy { it.stock?.sector ?: "Unknown" }
            .mapValues { (_, p) -> p.sumOf { it.weight } }
            .toList().sortedByDescending { it.second }
        val capMix = positions.groupBy { (it.stock?.get("capCategory") ?: 4.0).toInt() }
            .mapValues { (_, p) -> p.sumOf { it.weight } }

        val weighted = buildMap {
            for (id in weightedMetrics) {
                val withValue = positions.filter { it.stock?.get(id) != null }
                val w = withValue.sumOf { it.weight }
                if (w <= 0) continue
                if (id == "pe" || id == "pb") {
                    // Harmonic mean, the right way to average multiples.
                    val positive = withValue.filter { (it.stock!![id] ?: 0.0) > 0 }
                    val pw = positive.sumOf { it.weight }
                    if (pw > 0) put(id, pw / positive.sumOf { it.weight / it.stock!![id]!! })
                } else {
                    put(id, withValue.sumOf { it.weight * it.stock!![id]!! } / w)
                }
            }
        }
        val dividends = positions.sumOf { p -> p.value * (p.stock?.get("divYield") ?: 0.0) / 100 }
        val hhi = positions.sumOf { it.weight * it.weight }

        val warnings = buildList {
            positions.firstOrNull()?.let { if (it.weight > 25) add("${it.holding.symbol} is ${"%.0f".format(it.weight)}% of the portfolio.") }
            sectors.firstOrNull()?.let { if (it.second > 40) add("${"%.0f".format(it.second)}% is in ${it.first}.") }
            positions.filter { (it.stock?.get("pledged") ?: 0.0) > 25 }.forEach { add("${it.holding.symbol}: promoters have pledged ${"%.0f".format(it.stock!!["pledged"])}% of their shares.") }
            positions.filter { (it.stock?.get("altmanZ") ?: 3.0) < 1.8 }.forEach { add("${it.holding.symbol}: Altman Z-score is in the distress zone.") }
            positions.filter { it.stock == null }.forEach { add("${it.holding.symbol} wasn't found in today's market data.") }
        }
        return Report(
            positions, total, cost, total - cost, if (cost > 0) (total / cost - 1) * 100 else null,
            day, if (total - day > 0) day / (total - day) * 100 else null,
            sectors, capMix, weighted, dividends,
            positions.firstOrNull()?.weight ?: 0.0, hhi, if (hhi > 0) 10_000 / hhi else 0.0, warnings,
        )
    }

    /**
     * Reads holdings from a broker CSV export. Finds the symbol, quantity and average price
     * columns by their headers (works with Zerodha, Groww, Upstox and most others).
     */
    fun parseCsv(text: String): List<Holding> {
        val lines = text.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()
        val header = splitCsv(lines.first()).map { it.lowercase().trim() }
        fun col(vararg names: String) = header.indexOfFirst { h -> names.any { h == it } }.takeIf { it >= 0 }
            ?: header.indexOfFirst { h -> names.any { h.contains(it) } }
        val sym = col("symbol", "instrument", "tradingsymbol", "stock", "scrip", "ticker")
        val qty = col("quantity", "qty", "qty.", "shares", "units")
        val avg = col("avg. cost", "average price", "avg price", "avg cost", "average cost", "buy price", "avg")
        if (sym < 0 || qty < 0 || avg < 0) return emptyList()
        return lines.drop(1).mapNotNull { line ->
            val cells = splitCsv(line)
            val s = cells.getOrNull(sym)?.trim()?.uppercase()?.removeSuffix(".NS")?.removeSuffix("-EQ") ?: return@mapNotNull null
            val q = cells.getOrNull(qty)?.replace(",", "")?.trim()?.toDoubleOrNull() ?: return@mapNotNull null
            val a = cells.getOrNull(avg)?.replace(",", "")?.replace("₹", "")?.trim()?.toDoubleOrNull() ?: return@mapNotNull null
            if (s.isBlank() || q <= 0) null else Holding(s, q, a)
        }
    }

    internal fun splitCsv(line: String): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && quoted && i + 1 < line.length && line[i + 1] == '"' -> { cur.append('"'); i++ }
                c == '"' -> quoted = !quoted
                c == ',' && !quoted -> { out += cur.toString(); cur.clear() }
                else -> cur.append(c)
            }
            i++
        }
        out += cur.toString()
        return out
    }
}
