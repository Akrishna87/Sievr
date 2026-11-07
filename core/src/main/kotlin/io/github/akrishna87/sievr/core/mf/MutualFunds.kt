package io.github.akrishna87.sievr.core.mf

import io.github.akrishna87.sievr.core.portfolio.PortfolioAnalyzer
import kotlinx.serialization.Serializable

/** One scheme's latest NAV, from AMFI via the nightly mf.json.gz. */
@Serializable
data class MfScheme(
    val code: Int,
    val name: String,
    val amc: String = "",
    val category: String = "",
    val nav: Double,
    /** NAV date, yyyy-MM-dd. */
    val date: String,
    val isin: List<String> = emptyList(),
    val prevNav: Double? = null,
    val prevDate: String? = null,
) {
    /**
     * Equity, Debt, Hybrid, Gold & silver or Other, from AMFI's category ("Equity Scheme - Large Cap Fund").
     * AMFI files index funds, ETFs and fund-of-funds under "Other Scheme", so those are sorted by name.
     */
    val assetClass: String get() {
        val c = category.lowercase()
        val n = name.lowercase()
        return when {
            c.startsWith("hybrid") -> "Hybrid"
            c.startsWith("equity") || c.startsWith("elss") || c.startsWith("growth") -> "Equity"
            c.startsWith("debt") || c.startsWith("income") || c.startsWith("gilt") -> "Debt"
            preciousMetals.any { it in n } -> "Gold & silver"
            // Index funds, ETFs and fund-of-funds: by what they hold, judged from the name.
            listOf("other", "index", "etf", "exchange traded", "fund of funds").any { it in c } ->
                if (debtWords.any { it in n } || "debt" in c) "Debt" else "Equity"
            "equity" in c -> "Equity"
            else -> "Other"
        }
    }
    /** The part after "Equity Scheme - ", e.g. "Large Cap Fund". */
    val subCategory: String get() = category.substringAfter(" - ", category).ifBlank { "Other" }
    val dayChangePercent: Double? get() = prevNav?.takeIf { it > 0 }?.let { (nav / it - 1) * 100 }
}

private val preciousMetals = listOf("gold", "silver")
private val debtWords = listOf("sdl", "gilt", "g-sec", "gsec", "bond", "crisil ibx", "ibx", "t-bill", "treasury", "liquid",
    "overnight", "money market", "debt", "corporate", "psu ", "target maturity", "state development")

@Serializable
data class MfData(
    val version: Int = 1,
    val asOf: String = "",
    val generatedAt: String = "",
    val source: String = "",
    val schemes: List<MfScheme> = emptyList(),
)

/** A fund the user holds: units and the total amount they paid for them. */
@Serializable
data class MfHolding(
    val code: Int,
    val units: Double,
    val invested: Double,
    /** Kept so the holding still shows sensibly if a scheme stops appearing in AMFI's list. */
    val name: String = "",
    /** Whose holding this is (a family member's id); older saved data belongs to "me". */
    val owner: String = io.github.akrishna87.sievr.core.portfolio.DEFAULT_OWNER,
    /** When it was first bought, "yyyy-MM-dd", for CAGR. Optional. */
    val since: String? = null,
)

object MutualFunds {

    data class Position(
        val holding: MfHolding,
        val scheme: MfScheme?,
        val value: Double,
        val gain: Double,
        val gainPercent: Double?,
        val dayChange: Double,
        val weight: Double,
    ) {
        val name: String get() = scheme?.name ?: holding.name.ifBlank { "Scheme ${holding.code}" }
        val averageNav: Double? get() = if (holding.units > 0) holding.invested / holding.units else null
    }

    data class Report(
        val positions: List<Position>,
        val value: Double,
        val invested: Double,
        val gain: Double,
        val gainPercent: Double?,
        val dayChange: Double,
        val dayChangePercent: Double?,
        /** Equity / Debt / Hybrid / Other → share of value, %. */
        val assetMix: List<Pair<String, Double>>,
        val byCategory: List<Pair<String, Double>>,
        val byAmc: List<Pair<String, Double>>,
        /** Latest NAV date among the holdings. */
        val navDate: String?,
        val missing: List<MfHolding>,
    )

    /** Everyone's units of the same scheme as one holding. */
    fun merge(holdings: List<MfHolding>): List<MfHolding> = holdings.groupBy { it.code }.map { (code, list) ->
        MfHolding(code, list.sumOf { it.units }, list.sumOf { it.invested }, list.first().name, owner = "all",
            since = list.mapNotNull { it.since }.minOrNull())
    }

    fun analyze(holdings: List<MfHolding>, schemes: Map<Int, MfScheme>): Report {
        val raw = holdings.map { h ->
            val s = schemes[h.code]
            val value = if (s != null) s.nav * h.units else h.invested
            val day = if (s?.prevNav != null) (s.nav - s.prevNav) * h.units else 0.0
            Triple(h, s, value to day)
        }
        val total = raw.sumOf { it.third.first }
        val positions = raw.map { (h, s, vd) ->
            val (value, day) = vd
            Position(h, s, value, value - h.invested, if (h.invested > 0) (value / h.invested - 1) * 100 else null, day,
                if (total > 0) value / total * 100 else 0.0)
        }.sortedByDescending { it.value }
        val invested = positions.sumOf { it.holding.invested }
        val day = positions.sumOf { it.dayChange }
        fun group(key: (Position) -> String) = positions.groupBy(key)
            .mapValues { (_, p) -> p.sumOf { it.weight } }.toList().sortedByDescending { it.second }
        return Report(
            positions = positions,
            value = total,
            invested = invested,
            gain = total - invested,
            gainPercent = if (invested > 0) (total / invested - 1) * 100 else null,
            dayChange = day,
            dayChangePercent = if (total - day > 0) day / (total - day) * 100 else null,
            assetMix = group { it.scheme?.assetClass ?: "Other" },
            byCategory = group { it.scheme?.subCategory ?: "Other" },
            byAmc = group { it.scheme?.amc?.removeSuffix(" Mutual Fund")?.ifBlank { null } ?: "Other" },
            navDate = positions.mapNotNull { it.scheme?.date }.maxOrNull(),
            missing = positions.filter { it.scheme == null }.map { it.holding },
        )
    }

    /**
     * Finds schemes by words in any order ("parag flexi direct"). Direct-growth plans and shorter
     * names rank first, since that's what most people hold and search for.
     */
    fun search(schemes: List<MfScheme>, query: String, limit: Int = 50): List<MfScheme> {
        val words = query.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        words.singleOrNull()?.toIntOrNull()?.let { code -> schemes.firstOrNull { it.code == code }?.let { return listOf(it) } }
        val isin = query.trim().uppercase()
        if (isin.startsWith("INF")) schemes.firstOrNull { isin in it.isin }?.let { return listOf(it) }
        return schemes.filter { s -> val n = s.name.lowercase(); words.all { it in n } }
            .sortedWith(compareBy<MfScheme>(
                { if (it.name.contains("direct", true)) 0 else 1 },
                { if (it.name.contains("growth", true)) 0 else 1 },
                { it.name.length },
            ))
            .take(limit)
    }

    data class Import(val holdings: List<MfHolding>, val unmatched: List<String>)

    /**
     * Reads holdings from a CSV export (Groww, Kuvera, Coin, CAMS/KFintech summaries…): finds the scheme
     * by AMFI code, ISIN or name, and the units and invested-amount columns by their headers.
     */
    fun parseCsv(text: String, schemes: List<MfScheme>): Import {
        val lines = text.lines().filter { it.isNotBlank() }
        val headerIndex = lines.indexOfFirst { l -> val h = l.lowercase(); "unit" in h && ("scheme" in h || "fund" in h || "isin" in h) }
        if (headerIndex < 0) return Import(emptyList(), emptyList())
        val header = PortfolioAnalyzer.splitCsv(lines[headerIndex]).map { it.lowercase().trim() }
        fun col(vararg names: String) = header.indexOfFirst { h -> names.any { h == it } }.takeIf { it >= 0 }
            ?: header.indexOfFirst { h -> names.any { h.contains(it) } }
        val nameCol = col("scheme name", "scheme", "fund name", "fund", "name")
        val isinCol = col("isin")
        val codeCol = col("amfi code", "scheme code", "amfi")
        val unitsCol = col("units", "balance units", "closing units", "unit balance", "quantity")
        val investedCol = col("invested amount", "invested value", "invested", "cost value", "purchase value", "amount invested", "cost")
        if (unitsCol < 0) return Import(emptyList(), emptyList())
        val byCode = schemes.associateBy { it.code }
        val byIsin = schemes.flatMap { s -> s.isin.map { it to s } }.toMap()
        val byName = schemes.groupBy { normalize(it.name) }
        val out = mutableListOf<MfHolding>()
        val unmatched = mutableListOf<String>()
        for (line in lines.drop(headerIndex + 1)) {
            val cells = PortfolioAnalyzer.splitCsv(line)
            fun cell(i: Int) = cells.getOrNull(i)?.trim().orEmpty()
            fun num(i: Int) = cell(i).replace(",", "").replace("₹", "").trim().toDoubleOrNull()
            val units = num(unitsCol) ?: continue
            if (units <= 0) continue
            val name = cell(nameCol)
            val scheme = cell(codeCol).toIntOrNull()?.let { byCode[it] }
                ?: byIsin[cell(isinCol).uppercase()]
                ?: byName[normalize(name)]?.first()
                ?: name.takeIf { it.length >= 8 }?.let { n -> search(schemes, n, 1).firstOrNull() }
            if (scheme == null) {
                unmatched += name.ifBlank { line.take(40) }
                continue
            }
            val invested = num(investedCol) ?: scheme.nav * units
            out += MfHolding(scheme.code, units, invested, scheme.name)
        }
        return Import(out, unmatched)
    }

    private fun normalize(name: String) = name.lowercase().replace(Regex("[^a-z0-9]"), "")
}
