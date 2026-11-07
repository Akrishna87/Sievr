package io.github.akrishna87.sievr.core.portfolio

import io.github.akrishna87.sievr.core.model.Market
import io.github.akrishna87.sievr.core.model.Stock
import kotlinx.serialization.Serializable

@Serializable
data class Holding(
    val symbol: String,
    val quantity: Double,
    val averageCost: Double,
    /** Whose holding this is (a family member's id). Older saved data has none, so it belongs to "me". */
    val owner: String = DEFAULT_OWNER,
    /** When it was first bought, "yyyy-MM-dd", for CAGR. Optional: statements and broker reports don't carry it. */
    val since: String? = null,
)

/** The member everything belongs to until more family members are added. */
const val DEFAULT_OWNER = "me"

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

    /** Everyone's holdings of the same stock as one: quantities added, average cost weighted by quantity. */
    fun merge(holdings: List<Holding>): List<Holding> = holdings.groupBy { it.symbol }.map { (sym, list) ->
        val q = list.sumOf { it.quantity }
        Holding(sym, q, if (q > 0) list.sumOf { it.quantity * it.averageCost } / q else 0.0, owner = "all",
            since = list.mapNotNull { it.since }.minOrNull())
    }

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

    data class Import(val holdings: List<Holding>, val unmatched: List<String>)

    /**
     * Reads holdings from a broker export (CSV text, or an Excel file converted with Xlsx.toCsv): Zerodha
     * Console, Groww, Upstox, Angel and most others. The header row is found wherever it is (exports often
     * start with account details), then each row is matched to a stock by ISIN, by symbol, or by company name.
     * Without [market], only a symbol column can be used.
     */
    fun parseCsv(text: String, market: Market? = null): Import {
        val lines = text.lines().filter { it.isNotBlank() }
        val qtyNames = arrayOf("quantity", "qty", "qty.", "shares", "units", "no. of shares", "holding quantity")
        val headerIndex = lines.indexOfFirst { l ->
            val cells = splitCsv(l).map { it.lowercase().trim() }
            cells.any { c -> qtyNames.any { c == it || c.contains(it) } } &&
                cells.any { c -> listOf("symbol", "instrument", "stock", "scrip", "ticker", "isin", "company", "name").any { c.contains(it) } }
        }
        if (headerIndex < 0) return Import(emptyList(), emptyList())
        val header = splitCsv(lines[headerIndex]).map { it.lowercase().trim() }
        fun col(vararg names: String) = header.indexOfFirst { h -> names.any { h == it } }.takeIf { it >= 0 }
            ?: header.indexOfFirst { h -> names.any { h.contains(it) } }
        val sym = col("symbol", "tradingsymbol", "trading symbol", "instrument", "scrip", "ticker", "nse symbol", "bse symbol")
        val isin = col("isin")
        val name = col("stock name", "company name", "security name", "company", "stock", "name", "security")
        val qty = col(*qtyNames)
        val avg = col("avg. cost", "average price", "avg price", "avg cost", "average cost", "average buy price", "avg. buy price",
            "buy average", "buy avg", "buy price", "avg")
        val buyValue = col("buy value", "invested value", "invested amount", "investment value", "cost value")
        if (qty < 0 || (sym < 0 && isin < 0 && name < 0)) return Import(emptyList(), emptyList())

        val byIsin = market?.stocks?.mapNotNull { s -> s.data.isin?.let { it to s.symbol } }?.toMap().orEmpty()
        val bySymbol = market?.stocks?.associate { it.symbol to it.symbol }.orEmpty()
        val byName = market?.stocks?.groupBy { normalizeName(it.name) }?.mapValues { it.value.first().symbol }.orEmpty()

        val out = LinkedHashMap<String, Holding>()
        val unmatched = mutableListOf<String>()
        for (line in lines.drop(headerIndex + 1)) {
            val cells = splitCsv(line)
            fun cell(i: Int) = if (i < 0) "" else cells.getOrNull(i)?.trim().orEmpty()
            fun num(i: Int) = cell(i).replace(",", "").replace("₹", "").replace("Rs.", "").trim().toDoubleOrNull()
            val q = num(qty) ?: continue
            if (q <= 0) continue
            val rawSym = cell(sym).uppercase().removeSuffix(".NS").removeSuffix(".BO").removeSuffix("-EQ").removeSuffix("-BE")
            val symbol = byIsin[cell(isin).uppercase()]
                ?: bySymbol[rawSym]
                ?: byName[normalizeName(cell(name))]
                ?: rawSym.takeIf { it.isNotBlank() && market == null }
            if (symbol == null) {
                unmatched += cell(name).ifBlank { cell(sym).ifBlank { cell(isin) } }
                continue
            }
            val a = num(avg) ?: num(buyValue)?.let { it / q } ?: continue
            val prev = out[symbol]
            out[symbol] = if (prev == null) Holding(symbol, q, a)
            else Holding(symbol, prev.quantity + q, (prev.quantity * prev.averageCost + q * a) / (prev.quantity + q))
        }
        return Import(out.values.toList(), unmatched.filter { it.isNotBlank() && !it.lowercase().startsWith("total") })
    }

    /** "Reliance Industries Ltd." and "RELIANCE INDUSTRIES LIMITED" both become "relianceindustries". */
    internal fun normalizeName(name: String): String =
        name.lowercase().replace("&", "and").replace(Regex("""\b(limited|ltd|ltd\.|the|co|company|corporation|corp|inc)\b"""), "")
            .replace(Regex("[^a-z0-9]"), "")

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
