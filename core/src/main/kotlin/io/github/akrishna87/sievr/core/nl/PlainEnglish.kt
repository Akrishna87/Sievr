package io.github.akrishna87.sievr.core.nl

import io.github.akrishna87.sievr.core.metrics.Metrics
import io.github.akrishna87.sievr.core.screen.Filter
import io.github.akrishna87.sievr.core.screen.FormulaFilter
import io.github.akrishna87.sievr.core.screen.IndexFilter
import io.github.akrishna87.sievr.core.screen.MetricFilter
import io.github.akrishna87.sievr.core.screen.Op
import io.github.akrishna87.sievr.core.screen.PatternFilter
import io.github.akrishna87.sievr.core.screen.SectorFilter
import io.github.akrishna87.sievr.core.technical.CandlePattern

/**
 * Turns a plain-English request into screen filters, entirely on the phone:
 * "profitable small caps with low debt, ROE above 20 and rising margins in pharma".
 *
 * It recognises numeric conditions ("pe below 15", "roe > 20%", "market cap over 10000"),
 * a vocabulary of common investing phrases, sector names and index names.
 */
object PlainEnglish {

    data class Understood(val phrase: String, val filter: Filter)

    data class Result(
        val filters: List<Filter>,
        /** What was recognised, in order, to show as chips the user can remove. */
        val understood: List<Understood>,
        /** Words that weren't used, so the UI can say what it skipped. */
        val ignored: List<String>,
        val sortBy: String? = null,
        val sortDescending: Boolean = true,
    )

    private fun f(metric: String, op: Op, value: Double, value2: Double? = null) = MetricFilter(metric, op, value, value2)

    /** Phrases, longest first so "very low debt" wins over "low debt". */
    private val phrases: List<Pair<String, Filter>> = listOf(
        "large cap" to f("capCategory", Op.EQ, 1.0),
        "largecap" to f("capCategory", Op.EQ, 1.0),
        "blue chip" to f("capCategory", Op.EQ, 1.0),
        "mid cap" to f("capCategory", Op.EQ, 2.0),
        "midcap" to f("capCategory", Op.EQ, 2.0),
        "small cap" to f("capCategory", Op.EQ, 3.0),
        "smallcap" to f("capCategory", Op.EQ, 3.0),
        "micro cap" to f("capCategory", Op.EQ, 4.0),
        "microcap" to f("capCategory", Op.EQ, 4.0),
        "penny" to f("price", Op.LT, 20.0),
        "debt free" to f("de", Op.LT, 0.05),
        "no debt" to f("de", Op.LT, 0.05),
        "zero debt" to f("de", Op.LT, 0.05),
        "very low debt" to f("de", Op.LT, 0.1),
        "low debt" to f("de", Op.LT, 0.3),
        "high debt" to f("de", Op.GT, 1.5),
        "net cash" to f("netCash", Op.GT, 0.0),
        "cash rich" to f("netCash", Op.GT, 0.0),
        "profitable" to f("netIncome", Op.GT, 0.0),
        "loss making" to f("netIncome", Op.LT, 0.0),
        "loss-making" to f("netIncome", Op.LT, 0.0),
        "high roe" to f("roe", Op.GT, 18.0),
        "high roce" to f("roce", Op.GT, 18.0),
        "high return on capital" to f("roce", Op.GT, 18.0),
        "high margin" to f("opm", Op.GT, 20.0),
        "high margins" to f("opm", Op.GT, 20.0),
        "rising margins" to f("opmChange", Op.GT, 0.0),
        "expanding margins" to f("opmChange", Op.GT, 0.0),
        "improving margins" to f("opmChange", Op.GT, 0.0),
        "consistent" to f("salesGrowthMin3", Op.GT, 5.0),
        "undervalued" to FormulaFilter("PE > 0 AND PE < Industry_PE"),
        "cheaper than peers" to FormulaFilter("PE > 0 AND PE < Industry_PE"),
        "cheap" to f("valueScore", Op.GT, 70.0),
        "value" to f("valueScore", Op.GT, 70.0),
        "low pe" to f("pe", Op.BETWEEN, 0.1, 15.0),
        "low p/e" to f("pe", Op.BETWEEN, 0.1, 15.0),
        "low pb" to f("pb", Op.BETWEEN, 0.1, 1.5),
        "below book" to f("pb", Op.BETWEEN, 0.01, 1.0),
        "below graham" to f("grahamUpside", Op.GT, 0.0),
        "margin of safety" to f("dcfUpside", Op.GT, 25.0),
        "below intrinsic value" to f("dcfUpside", Op.GT, 0.0),
        "high dividend" to f("divYield", Op.GT, 3.0),
        "dividend" to f("divYield", Op.GT, 1.5),
        "dividends" to f("divYield", Op.GT, 1.5),
        "high yield" to f("divYield", Op.GT, 4.0),
        "fast growing" to f("salesCagr3", Op.GT, 20.0),
        "high growth" to f("salesCagr3", Op.GT, 20.0),
        "growing" to f("salesCagr3", Op.GT, 10.0),
        "growth" to f("growthScore", Op.GT, 70.0),
        "profit growth" to f("profitCagr3", Op.GT, 15.0),
        "earnings growth" to f("profitCagr3", Op.GT, 15.0),
        "earnings surge" to f("earningsGrowth", Op.GT, 25.0),
        "quality" to f("qualityScore", Op.GT, 70.0),
        "high quality" to f("qualityScore", Op.GT, 80.0),
        "safe" to f("safetyScore", Op.GT, 70.0),
        "momentum" to f("momentumScore", Op.GT, 70.0),
        "uptrend" to FormulaFilter("Price > SMA50 AND SMA50 > SMA200"),
        "downtrend" to FormulaFilter("Price < SMA50 AND SMA50 < SMA200"),
        "above 200 dma" to f("priceVsSma200", Op.GT, 0.0),
        "above 200dma" to f("priceVsSma200", Op.GT, 0.0),
        "below 200 dma" to f("priceVsSma200", Op.LT, 0.0),
        "oversold" to f("rsi14", Op.LT, 30.0),
        "overbought" to f("rsi14", Op.GT, 70.0),
        "near 52 week high" to f("fromHigh52", Op.LT, 5.0),
        "near 52-week high" to f("fromHigh52", Op.LT, 5.0),
        "near highs" to f("fromHigh52", Op.LT, 5.0),
        "new high" to f("newHigh52", Op.EQ, 1.0),
        "breakout" to f("newHigh52", Op.EQ, 1.0),
        "near 52 week low" to f("fromLow52", Op.LT, 10.0),
        "near 52-week low" to f("fromLow52", Op.LT, 10.0),
        "near lows" to f("fromLow52", Op.LT, 10.0),
        "golden cross" to f("goldenCross", Op.EQ, 1.0),
        "death cross" to f("deathCross", Op.EQ, 1.0),
        "macd crossover" to f("macdBullCross", Op.EQ, 1.0),
        "volume spike" to f("volumeRatio", Op.GT, 2.0),
        "high volume" to f("volumeRatio", Op.GT, 2.0),
        "beating nifty" to f("relStrength", Op.GT, 0.0),
        "outperforming" to f("relStrength", Op.GT, 0.0),
        "high promoter" to f("promoter", Op.GT, 50.0),
        "promoter holding" to f("promoter", Op.GT, 50.0),
        "no pledge" to FormulaFilter("NOT (Pledged > 0)"),
        "promoters buying" to f("promoterChange", Op.GT, 0.0),
        "fii buying" to f("fiiChange", Op.GT, 0.0),
        "fiis buying" to f("fiiChange", Op.GT, 0.0),
        "dii buying" to f("diiChange", Op.GT, 0.0),
        "institutional" to f("institutions", Op.GT, 20.0),
        "analyst upside" to f("targetUpside", Op.GT, 15.0),
        "hammer" to PatternFilter(setOf(CandlePattern.HAMMER.metricId)),
        "bullish engulfing" to PatternFilter(setOf(CandlePattern.BULLISH_ENGULFING.metricId)),
        "bearish engulfing" to PatternFilter(setOf(CandlePattern.BEARISH_ENGULFING.metricId)),
        "doji" to PatternFilter(setOf(CandlePattern.DOJI.metricId)),
        "piotroski" to f("piotroski", Op.GTE, 7.0),
        "nifty 50" to IndexFilter(setOf("NIFTY 50")),
        "nifty50" to IndexFilter(setOf("NIFTY 50")),
        "nifty next 50" to IndexFilter(setOf("NIFTY NEXT 50")),
        "nifty 500" to IndexFilter(setOf("NIFTY 500")),
    ).sortedByDescending { it.first.length }

    /** Common words for sectors, mapped to words that appear in sector names. */
    private val sectorWords = mapOf(
        "bank" to "financial", "banks" to "financial", "banking" to "financial", "nbfc" to "financial",
        "finance" to "financial", "financial" to "financial", "insurance" to "financial",
        "pharma" to "health", "pharmaceutical" to "health", "healthcare" to "health", "hospital" to "health",
        "it" to "technology", "tech" to "technology", "software" to "technology", "technology" to "technology",
        "auto" to "consumer cyclical", "automobile" to "consumer cyclical", "fmcg" to "consumer defensive",
        "consumer" to "consumer", "energy" to "energy", "oil" to "energy", "power" to "utilities",
        "utility" to "utilities", "utilities" to "utilities", "metal" to "basic materials", "metals" to "basic materials",
        "chemical" to "basic materials", "chemicals" to "basic materials", "cement" to "basic materials",
        "infra" to "industrials", "infrastructure" to "industrials", "industrial" to "industrials", "capital goods" to "industrials",
        "defence" to "industrials", "realty" to "real estate", "real estate" to "real estate",
        "telecom" to "communication", "media" to "communication",
    )

    private val comparators = listOf(
        "greater than or equal to" to Op.GTE, "at least" to Op.GTE, "more than" to Op.GT, "greater than" to Op.GT,
        "higher than" to Op.GT, "above" to Op.GT, "over" to Op.GT, "exceeding" to Op.GT, ">=" to Op.GTE, ">" to Op.GT,
        "less than or equal to" to Op.LTE, "at most" to Op.LTE, "less than" to Op.LT, "lower than" to Op.LT,
        "below" to Op.LT, "under" to Op.LT, "<=" to Op.LTE, "<" to Op.LT, "between" to Op.BETWEEN,
    )

    private val stopWords = setOf(
        "show", "me", "find", "list", "stocks", "stock", "companies", "company", "shares", "with", "and", "that",
        "have", "has", "are", "is", "the", "a", "an", "in", "of", "which", "where", "whose", "good", "great",
        "strong", "best", "top", "sector", "india", "indian", "nse", "bse", "but", "also", "plus", "or", "to", "for",
        "&", "very", "ratio", "than", "&amp;",
    )

    fun parse(query: String, sectors: List<String> = emptyList()): Result {
        var text = " " + query.lowercase().replace(Regex("(?<![0-9]),|,(?![0-9])|[;!?]"), " ").replace(Regex("\\s+"), " ") + " "
        val understood = mutableListOf<Pair<Int, Understood>>()

        fun consume(range: IntRange, phrase: String, filter: Filter) {
            understood += range.first to Understood(phrase.trim(), filter)
            text = text.replaceRange(range, " ".repeat(range.last - range.first + 1))
        }

        // 1. Numeric conditions: "<metric words> <comparator> <number> [and <number>]".
        val compAlternation = comparators.joinToString("|") { Regex.escape(it.first) }
        val numeric = Regex("([a-z][a-z0-9 /&_.-]{0,40}?)\\s*(?:is\\s+|of\\s+)?($compAlternation)\\s*(-?[0-9][0-9,]*\\.?[0-9]*)\\s*(%|cr|crore|crores)?(?:\\s*(?:and|to|-)\\s*(-?[0-9][0-9,]*\\.?[0-9]*))?")
        while (true) {
            val match = numeric.findAll(text).firstNotNullOfOrNull { m ->
                metricFromWords(m.groupValues[1])?.let { m to it }
            } ?: break
            val (m, found) = match
            val (metricId, metricWords) = found
            val op = comparators.first { it.first == m.groupValues[2] }.second
            val v1 = m.groupValues[3].replace(",", "").toDoubleOrNull() ?: break
            val v2 = m.groupValues[5].replace(",", "").toDoubleOrNull()
            val filter = if (op == Op.BETWEEN && v2 != null) MetricFilter(metricId, Op.BETWEEN, v1, v2) else MetricFilter(metricId, if (op == Op.BETWEEN) Op.GTE else op, v1)
            // Only blank out the metric words actually used, so earlier words stay available.
            val start = m.range.first + m.groupValues[1].lastIndexOf(metricWords).coerceAtLeast(0)
            consume(start..m.range.last, text.substring(start, m.range.last + 1), filter)
        }

        // 2. Known phrases.
        for ((phrase, filter) in phrases) {
            while (true) {
                // Plurals count too: "small caps", "dividends".
                val idx = Regex("(?<![a-z0-9])" + Regex.escape(phrase) + "s?(?![a-z0-9])").find(text) ?: break
                consume(idx.range, phrase, filter)
            }
        }

        // 3. Sectors.
        val sectorHits = mutableSetOf<String>()
        for ((word, key) in sectorWords.entries.sortedByDescending { it.key.length }) {
            val m = Regex("(?<![a-z0-9])" + Regex.escape(word) + "s?(?![a-z0-9])").find(text) ?: continue
            val hit = sectors.filter { it.lowercase().contains(key) }
            if (hit.isNotEmpty()) {
                sectorHits += hit
                understood += m.range.first to Understood(word, SectorFilter(hit.toSet()))
                text = text.replaceRange(m.range, " ".repeat(m.value.length))
            }
        }
        for (sector in sectors) {
            val m = Regex("(?<![a-z0-9])" + Regex.escape(sector.lowercase()) + "(?![a-z0-9])").find(text) ?: continue
            sectorHits += sector
            understood += m.range.first to Understood(sector, SectorFilter(setOf(sector)))
            text = text.replaceRange(m.range, " ".repeat(m.value.length))
        }

        val ordered = understood.sortedBy { it.first }.map { it.second }
        // Merge sector mentions into one OR filter.
        val nonSector = ordered.filter { it.filter !is SectorFilter }.distinctBy { it.filter }
        val filters = nonSector.map { it.filter }.toMutableList()
        if (sectorHits.isNotEmpty()) filters += SectorFilter(sectorHits)
        val ignored = text.split(" ").map { it.trim('.', '-') }.filter { it.isNotBlank() && it !in stopWords }

        val sort = filters.filterIsInstance<MetricFilter>().firstOrNull { Metrics[it.metric]?.higherIsBetter != null }
        return Result(
            filters = filters,
            understood = ordered.distinctBy { it.filter },
            ignored = ignored,
            sortBy = sort?.metric,
            sortDescending = sort?.let { Metrics[it.metric]?.higherIsBetter } ?: true,
        )
    }

    /** The metric named by the longest tail of [words], e.g. "small caps with roe" → roe. */
    private fun metricFromWords(words: String): Pair<String, String>? {
        val tokens = words.trim().split(" ").filter { it.isNotBlank() }
        for (take in minOf(4, tokens.size) downTo 1) {
            val tail = tokens.takeLast(take)
            val candidate = tail.joinToString(" ")
            val metric = Metrics.find(candidate) ?: Metrics.find(spoken[candidate] ?: continue) ?: continue
            return metric.id to candidate
        }
        return null
    }

    /** Spoken names that differ from metric labels and aliases. */
    private val spoken = mapOf(
        "market cap" to "mcap", "market capitalisation" to "mcap", "market capitalization" to "mcap", "mcap" to "mcap",
        "debt to equity" to "de", "debt equity" to "de", "debt/equity" to "de", "d/e" to "de",
        "price to earnings" to "pe", "p/e" to "pe", "pe" to "pe", "p/b" to "pb", "price to book" to "pb",
        "dividend yield" to "divYield", "yield" to "divYield", "sales growth" to "salesCagr3", "revenue growth" to "salesCagr3",
        "profit growth" to "profitCagr3", "earnings growth" to "profitCagr3", "promoter holding" to "promoter", "promoters" to "promoter",
        "pledge" to "pledged", "rsi" to "rsi14", "price" to "price", "share price" to "price", "volume" to "volumeRatio",
        "1 year return" to "ret1y", "one year return" to "ret1y", "6 month return" to "ret6m", "3 month return" to "ret3m",
        "operating margin" to "opm", "net margin" to "npm", "margin" to "opm", "f score" to "piotroski",
        "fcf yield" to "fcfYield", "free cash flow yield" to "fcfYield", "beta" to "beta", "upside" to "targetUpside",
    )
}
