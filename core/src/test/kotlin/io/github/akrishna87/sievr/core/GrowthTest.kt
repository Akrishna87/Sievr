package io.github.akrishna87.sievr.core

import io.github.akrishna87.sievr.core.portfolio.Growth
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GrowthTest {
    private val today = LocalDate.of(2026, 11, 7)

    @Test fun doublingInFiveYearsIsAbout15Percent() {
        val c = Growth.cagr(100_000.0, 200_000.0, today.minusDays((5 * 365.25).toLong()), today)!!
        assertEquals(14.87, c, 0.05)
    }

    @Test fun underAYearIsNotAnnualised() {
        assertNull(Growth.cagr(100.0, 110.0, today.minusMonths(6), today))
    }

    @Test fun singleLotXirrMatchesCagr() {
        val since = today.minusDays((3 * 365.25).toLong())
        val s = Growth.portfolio(listOf(Growth.Lot(since, 1000.0, 1331.0)), 1000.0, today)!!
        assertEquals(10.0, s.cagr!!, 0.05)
        assertEquals(100.0, s.coverage, 0.01)
    }

    @Test fun lotsBoughtAtDifferentTimes() {
        // 1000 two years ago at 10% → 1210; 1000 one year ago at 10% → 1100. Together: 10%.
        val s = Growth.portfolio(listOf(
            Growth.Lot(today.minusDays(730 + 1), 1000.0, 1210.0),
            Growth.Lot(today.minusDays(365 + 1), 1000.0, 1100.0),
        ), 4000.0, today)!!
        assertEquals(10.0, s.cagr!!, 0.1)
        assertEquals(50.0, s.coverage, 0.01)
    }

    @Test fun noDatesNoSummary() {
        assertNull(Growth.portfolio(emptyList(), 1000.0, today))
    }
}

class GrowthMergeTest {
    @Test fun mergeKeepsEarliestBuyDate() {
        val m = io.github.akrishna87.sievr.core.portfolio.PortfolioAnalyzer.merge(listOf(
            io.github.akrishna87.sievr.core.portfolio.Holding("TCS", 1.0, 100.0, "me", "2022-01-10"),
            io.github.akrishna87.sievr.core.portfolio.Holding("TCS", 1.0, 200.0, "wife", "2020-05-01"),
            io.github.akrishna87.sievr.core.portfolio.Holding("TCS", 1.0, 300.0, "kid"),
        ))
        assertEquals("2020-05-01", m.single().since)
        assertEquals(200.0, m.single().averageCost, 1e-9)
    }
}
