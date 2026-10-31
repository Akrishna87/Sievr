package io.github.akrishna87.sievr.core

import io.github.akrishna87.sievr.core.model.AnnualFinancials
import io.github.akrishna87.sievr.core.model.IndexData
import io.github.akrishna87.sievr.core.model.Snapshot
import io.github.akrishna87.sievr.core.model.StockData
import kotlin.math.exp
import kotlin.math.sin

/** A small, made-up market for tests. */
object TestData {
    const val DAYS = 300
    const val WEEKS = 300

    fun series(n: Int, start: Double, dailyGrowth: Double, wobble: Double = 0.0): List<Double?> =
        (0 until n).map { i -> start * exp(dailyGrowth * i) * (1 + wobble * sin(i / 3.0)) }

    fun fin(rev0: Double, growth: Double, margin: Double, equity: Double, debt: Double, years: Int = 4) =
        (0 until years).map { i ->
            val rev = rev0 * Math.pow(1 + growth, i.toDouble())
            val ebit = rev * margin
            val ni = ebit * 0.75
            AnnualFinancials(
                year = 2022 + i, revenue = rev, grossProfit = rev * (0.4 + i * 0.01), ebit = ebit, ebitda = ebit * 1.2,
                interest = debt * 0.09 + 0.01, netIncome = ni, eps = ni / 10, totalAssets = equity + debt + rev * 0.3,
                currentAssets = rev * 0.4, currentLiabilities = rev * 0.2, totalLiabilities = debt + rev * 0.3,
                equity = equity * (1 + 0.1 * i), debt = debt * (1 - 0.1 * i), cash = rev * 0.1, retainedEarnings = equity * 0.5,
                operatingCashFlow = ni * 1.1, capex = ni * 0.3, shares = 10.0,
            )
        }

    fun snapshot(): Snapshot {
        val stocks = listOf(
            StockData("GOOD", "Good Co", sector = "Technology", industry = "Software", indices = listOf("NIFTY 50"),
                c = series(DAYS, 100.0, 0.002), v = List(30) { 1000.0 } + listOf(5000.0), w = series(WEEKS, 50.0, 0.01),
                fin = fin(1000.0, 0.2, 0.25, 800.0, 10.0)),
            StockData("CHEAP", "Cheap Co", sector = "Technology", industry = "Software",
                c = series(DAYS, 50.0, 0.0), w = series(WEEKS, 50.0, 0.0), fin = fin(2000.0, 0.05, 0.15, 3000.0, 100.0),
                f = mapOf("divYield" to 4.0, "payout" to 40.0)),
            StockData("DEBT", "Debt Co", sector = "Industrials", industry = "Construction",
                c = series(DAYS, 200.0, -0.001), w = series(WEEKS, 300.0, -0.003), fin = fin(500.0, -0.05, 0.05, 200.0, 900.0)),
            StockData("MID", "Mid Co", sector = "Technology", industry = "Software",
                c = series(DAYS, 80.0, 0.0008, 0.02), w = series(WEEKS, 60.0, 0.003, 0.02), fin = fin(800.0, 0.1, 0.18, 600.0, 50.0)),
        )
        return Snapshot(
            asOf = "2026-10-30",
            dailyDates = (0 until DAYS).map { 20000 + it },
            weeklyDates = (0 until WEEKS).map { 18000 + it * 7 },
            stocks = stocks,
            indices = listOf(IndexData("NIFTY 50", c = series(DAYS, 20000.0, 0.0005), w = series(WEEKS, 15000.0, 0.002))),
        )
    }
}
