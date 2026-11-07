package io.github.akrishna87.sievr.core.mf

import kotlin.math.abs

/**
 * Reads mutual fund holdings from the text of a Consolidated Account Statement (CAS): the PDF that
 * CAMS/KFintech (or MF Central) and NSDL/CDSL email on request, listing every fund a PAN holds.
 *
 * Statements come in several layouts, so this doesn't depend on one:
 *  - Detailed CAMS/KFintech CAS: per scheme, "ISIN: INF…", then transactions, then
 *    "Closing Unit Balance: 1,234.567 … Total Cost Value: 1,00,000.00 … Market Value on 31-Oct-2026: INR 1,15,000.00".
 *  - Summary CAS and NSDL/CDSL statements: one row per folio with the ISIN and its numbers.
 * Each ISIN starts a block that runs to the next ISIN. Labelled figures are used when present; otherwise the
 * block's numbers are checked against each other: units × NAV must equal the market value, which tells
 * units, NAV and value apart in any column order. The cost is then the figure that, with the gain or loss,
 * adds up to the value, or the one labelled as cost.
 */
object CasParser {

    data class Entry(
        val isin: String,
        val name: String,
        val units: Double,
        /** Amount invested; null when the statement doesn't show it. */
        val cost: Double?,
        val nav: Double?,
        val value: Double?,
    )

    data class Result(
        /** One per scheme, folios added together. */
        val holdings: List<MfHolding>,
        /** Entries whose ISIN isn't in AMFI's list (e.g. a merged scheme), with what the statement says. */
        val unmatched: List<Entry>,
        /** Number of holdings where the statement had no cost, so the current value was used. */
        val missingCost: Int,
        val statementValue: Double,
    )

    private val isinRegex = Regex("""\bINF[0-9A-Z]{9}\b""")
    private val numberRegex = Regex("""(?<![\w/.-])-?\(?\d{1,3}(?:,\d{2,3})*(?:\.\d+)\)?|(?<![\w/.-])-?\d+\.\d+""")
    private val dateRegex = Regex("""\b\d{1,2}[-/ ](?:[A-Za-z]{3}|\d{1,2})[-/ ]\d{2,4}\b""")

    fun parse(text: String, schemes: List<MfScheme>): Result {
        val entries = entries(text)
        val byIsin = schemes.flatMap { s -> s.isin.map { it to s } }.toMap()
        val holdings = LinkedHashMap<Int, MfHolding>()
        val unmatched = mutableListOf<Entry>()
        var missingCost = 0
        for (e in entries) {
            val scheme = byIsin[e.isin]
            if (scheme == null) {
                unmatched += e
                continue
            }
            val cost = e.cost ?: e.value ?: (e.units * scheme.nav).also { missingCost++ }
            if (e.cost == null && e.value != null) missingCost++
            val prev = holdings[scheme.code]
            holdings[scheme.code] = MfHolding(scheme.code, (prev?.units ?: 0.0) + e.units, (prev?.invested ?: 0.0) + cost, scheme.name)
        }
        return Result(holdings.values.toList(), unmatched, missingCost, entries.sumOf { it.value ?: 0.0 })
    }

    /** Every fund block in the statement with positive units. */
    fun entries(text: String): List<Entry> {
        val clean = text.replace(' ', ' ').replace("\r", "")
        val hits = isinRegex.findAll(clean).toList()
        val out = mutableListOf<Entry>()
        for ((i, hit) in hits.withIndex()) {
            // A detailed statement repeats nothing between ISINs; skip a second ISIN in the same line (reinvestment ISINs).
            val end = hits.getOrNull(i + 1)?.range?.first ?: clean.length
            val lineStart = clean.lastIndexOf('\n', hit.range.first).let { if (it < 0) 0 else it + 1 }
            val block = clean.substring(hit.range.first, end)
            val before = clean.substring(lineStart, hit.range.first)
            val name = schemeName(before, block)
            val entry = labelled(hit.value, name, block) ?: rowEntry(hit.value, name, before, block)
            if (entry != null && entry.units > 0) out += entry
        }
        return out
    }

    // ---------------------------------------------------------------- detailed statements

    private fun labelled(isin: String, name: String, block: String): Entry? {
        val units = label(block, """Closing\s+Unit\s+Balance\s*:?""") ?: return null
        val cost = label(block, """(?:Total\s+)?Cost\s+Value\s*:?""")
        val nav = label(block, """NAV\s+on\s+[\w-]+\s*:?\s*(?:INR|Rs\.?|₹)?""")
        val value = label(block, """(?:Market\s+Value|Valuation)\s+on\s+[\w-]+\s*:?\s*(?:INR|Rs\.?|₹)?""")
        return Entry(isin, name, units, cost, nav, value)
    }

    private fun label(block: String, prefix: String): Double? =
        Regex(prefix + """\s*(?:INR|Rs\.?|₹)?\s*(-?[\d,]+\.?\d*)""", RegexOption.IGNORE_CASE).findAll(block).lastOrNull()
            ?.groupValues?.get(1)?.let { number(it) }

    // ---------------------------------------------------------------- summary / depository statements

    private class Num(val value: Double, val decimals: Int)

    /** The fund's own row; a row wrapped onto following lines is joined with them only if it needs to be. */
    private fun rowEntry(isin: String, name: String, before: String, block: String): Entry? {
        val lines = block.lines()
        for (n in 1..minOf(3, lines.size)) {
            unlabelled(isin, name, before + " " + lines.take(n).joinToString(" "))?.let { return it }
        }
        return null
    }

    private fun unlabelled(isin: String, name: String, row: String): Entry? {
        val text = dateRegex.replace(row.replace(isin, " "), " ")
        // Only figures with a decimal point: folio numbers, years and codes are whole numbers.
        val nums = numberRegex.findAll(text).mapNotNull { m ->
            if ('.' !in m.value) null else number(m.value)?.let { Num(it, m.value.substringAfterLast('.').count(Char::isDigit)) }
        }.toList()
        if (nums.size < 3) return null
        // Units u and NAV n (u listed first) with u × n ≈ value v. Units × average cost = total cost passes the
        // same test, but the market value always comes after the cost columns, so the furthest-right result wins.
        var pick: Triple<Int, Int, Int>? = null
        var bestErr = 0.0
        for (i in nums.indices) for (j in nums.indices) for (k in nums.indices) {
            if (i >= j || k == i || k == j) continue
            val u = nums[i].value; val n = nums[j].value; val v = nums[k].value
            if (u <= 0 || n <= 0 || v <= 0) continue
            val err = abs(u * n - v) / v
            if (err >= 0.001) continue  // statement figures are exact to the paisa
            val current = pick
            if (current == null || k > current.third || (k == current.third && err < bestErr)) { pick = Triple(i, j, k); bestErr = err }
        }
        var (ui, ni, vi) = pick ?: return null
        // NAVs are quoted to 4 decimals and units to 3; trust that over the column order when they differ.
        if (nums[ui].decimals == 4 && nums[ni].decimals == 3) { val t = ui; ui = ni; ni = t }
        val value = nums[vi].value
        val rest = nums.indices.filter { it != ui && it != ni && it != vi }
        // Cost: listed before the gain/loss, and the two add up to the value.
        var cost: Double? = null
        loop@ for (a in rest) for (b in rest) {
            if (a < b && abs(nums[a].value + nums[b].value - value) <= 0.002 * value + 1 && nums[a].value > 0) {
                cost = nums[a].value; break@loop
            }
        }
        if (cost == null) cost = Regex("""(?:Cost|Invested|Investment)[^\d]{0,40}([\d,]+\.\d+)""", RegexOption.IGNORE_CASE)
            .find(row)?.groupValues?.get(1)?.let { number(it) }
        if (cost == null && rest.size == 1 && nums[rest[0]].value in (value * 0.2)..(value * 5)) cost = nums[rest[0]].value
        return Entry(isin, name, nums[ui].value, cost, nums[ni].value, value)
    }

    // ---------------------------------------------------------------- helpers

    private fun schemeName(before: String, block: String): String {
        val fromBefore = before.replace(Regex("""ISIN\s*:?\s*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""^[\w/ ]*?\d{4,}[\d/ ]*"""), "").trim(' ', '-', ':', '(')
        val fromBlock = block.substringAfter(" ").lineSequence().firstOrNull().orEmpty()
            .replace(Regex("""\(Advisor.*|Registrar.*""", RegexOption.IGNORE_CASE), "").trim(' ', '-', ':', ')')
        return (if (fromBefore.length >= 6) fromBefore else fromBlock).take(80)
    }

    private fun number(s: String): Double? {
        val negative = s.startsWith("-") || s.startsWith("(")
        val v = s.replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: return null
        return if (negative) -v else v
    }
}
