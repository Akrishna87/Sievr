package io.github.akrishna87.sievr.core.portfolio

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.pow

/**
 * Annualised returns. A holding's CAGR needs the date it was bought, which the user enters (statements don't
 * have it). For a whole portfolio bought at different times the right "CAGR" is the money-weighted one (XIRR):
 * each dated holding counts as its invested amount going in on its date and today's value coming out today.
 * Under a year, annualising exaggerates (5% in a month isn't 80% a year), so those show as not yet available.
 */
object Growth {

    /** One holding for the portfolio figure: invested on [since], worth [value] today. */
    data class Lot(val since: LocalDate, val invested: Double, val value: Double)

    data class Summary(
        /** Annualised %, or null when every dated holding is under a year old. */
        val cagr: Double?,
        val earliest: LocalDate,
        /** Share of the invested amount that has a buy date and so is counted, %. */
        val coverage: Double,
    )

    fun parse(date: String?): LocalDate? = date?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    fun years(since: LocalDate, today: LocalDate = LocalDate.now()): Double = ChronoUnit.DAYS.between(since, today) / 365.25

    /** CAGR in %, or null under a year or without sensible amounts. */
    fun cagr(invested: Double, value: Double, since: LocalDate?, today: LocalDate = LocalDate.now()): Double? {
        if (since == null || invested <= 0 || value < 0) return null
        val y = years(since, today)
        if (y < 1.0) return null
        return ((value / invested).pow(1 / y) - 1) * 100
    }

    /** Money-weighted annual return (XIRR) of the dated lots, plus how much of [totalInvested] they cover. */
    fun portfolio(lots: List<Lot>, totalInvested: Double, today: LocalDate = LocalDate.now()): Summary? {
        val dated = lots.filter { it.invested > 0 && !it.since.isAfter(today) }
        if (dated.isEmpty()) return null
        val earliest = dated.minOf { it.since }
        val coverage = if (totalInvested > 0) (dated.sumOf { it.invested } / totalInvested * 100).coerceAtMost(100.0) else 100.0
        // Weighted by money, how long has it been invested? Under a year, don't annualise.
        val avgYears = dated.sumOf { it.invested * years(it.since, today) } / dated.sumOf { it.invested }
        if (avgYears < 1.0) return Summary(null, earliest, coverage)
        val value = dated.sumOf { it.value }
        fun npv(rate: Double) = value - dated.sumOf { it.invested * (1 + rate).pow(years(it.since, today)) }
        // npv falls as rate rises; bisect between −99.9% and +10,000%.
        var lo = -0.999
        var hi = 100.0
        if (npv(lo) < 0 || npv(hi) > 0) return Summary(null, earliest, coverage)
        repeat(200) {
            val mid = (lo + hi) / 2
            if (npv(mid) > 0) lo = mid else hi = mid
            if (abs(hi - lo) < 1e-9) return@repeat
        }
        return Summary((lo + hi) / 2 * 100, earliest, coverage)
    }
}
