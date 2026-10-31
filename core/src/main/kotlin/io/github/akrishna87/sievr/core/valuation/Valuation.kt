package io.github.akrishna87.sievr.core.valuation

import kotlin.math.pow
import kotlin.math.sqrt

/** Fair-value models. Amounts are in ₹ crore, shares in crore, so per-share values come out in ₹. */
object Valuation {

    data class DcfInputs(
        /** Starting free cash flow, ₹ crore. */
        val baseCashFlow: Double,
        /** Yearly growth for years 1–5, %. */
        val growthYears1to5: Double,
        /** Yearly growth for years 6–10, %. */
        val growthYears6to10: Double,
        /** Growth forever after year 10, %. Must be below [discountRate]. */
        val terminalGrowth: Double = 4.0,
        /** Required return, %. */
        val discountRate: Double = 12.0,
        /** Cash minus debt, ₹ crore. */
        val netCash: Double = 0.0,
        /** Shares outstanding, crore. */
        val shares: Double,
    )

    data class DcfResult(
        val valuePerShare: Double,
        val enterpriseValue: Double,
        val equityValue: Double,
        /** Present value of the terminal value as a share of the enterprise value, 0–1. */
        val terminalShare: Double,
        /** Projected cash flows for years 1–10, ₹ crore. */
        val projected: List<Double>,
    )

    fun dcf(inputs: DcfInputs): DcfResult? = with(inputs) {
        if (shares <= 0 || discountRate <= terminalGrowth || baseCashFlow <= 0) return null
        val r = discountRate / 100
        var cf = baseCashFlow
        var pv = 0.0
        val projected = mutableListOf<Double>()
        for (year in 1..10) {
            val g = (if (year <= 5) growthYears1to5 else growthYears6to10) / 100
            cf *= 1 + g
            projected += cf
            pv += cf / (1 + r).pow(year)
        }
        val tg = terminalGrowth / 100
        val terminal = cf * (1 + tg) / (r - tg)
        val terminalPv = terminal / (1 + r).pow(10)
        val ev = pv + terminalPv
        val equity = ev + netCash
        DcfResult(equity / shares, ev, equity, terminalPv / ev, projected)
    }

    /**
     * Reverse DCF: the yearly growth for years 1–10 that the market price implies, given
     * the other inputs. Returns null if no rate between −50% and +100% explains the price.
     */
    fun impliedGrowth(
        marketCap: Double, baseCashFlow: Double, netCash: Double,
        discountRate: Double = 12.0, terminalGrowth: Double = 4.0,
    ): Double? {
        if (baseCashFlow <= 0 || marketCap <= 0) return null
        fun valueAt(g: Double) = dcf(
            DcfInputs(baseCashFlow, g, g, terminalGrowth, discountRate, netCash, 1.0)
        )?.equityValue ?: Double.NaN
        var lo = -50.0
        var hi = 100.0
        if (valueAt(lo) > marketCap || valueAt(hi) < marketCap) return null
        repeat(80) {
            val mid = (lo + hi) / 2
            if (valueAt(mid) < marketCap) lo = mid else hi = mid
        }
        return (lo + hi) / 2
    }

    /** √(22.5 × EPS × book value per share). Null when either is not positive. */
    fun grahamNumber(eps: Double?, bookValuePerShare: Double?): Double? {
        if (eps == null || bookValuePerShare == null || eps <= 0 || bookValuePerShare <= 0) return null
        return sqrt(22.5 * eps * bookValuePerShare)
    }

    /**
     * Graham's revised growth formula: V = EPS × (8.5 + 2g) × 4.4 / Y, where g is expected
     * growth (%) and Y the current AAA corporate bond yield (%), about 7.5% in India.
     */
    fun grahamGrowthValue(eps: Double?, growth: Double?, bondYield: Double = 7.5): Double? {
        if (eps == null || growth == null || eps <= 0) return null
        return eps * (8.5 + 2 * growth.coerceIn(0.0, 25.0)) * 4.4 / bondYield
    }

    /** Lynch fair value: EPS × growth rate (as a P/E equal to growth), capped at 25. */
    fun lynchValue(eps: Double?, growth: Double?): Double? {
        if (eps == null || growth == null || eps <= 0 || growth <= 0) return null
        return eps * growth.coerceAtMost(25.0)
    }
}
