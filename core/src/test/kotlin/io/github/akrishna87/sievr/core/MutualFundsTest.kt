package io.github.akrishna87.sievr.core

import io.github.akrishna87.sievr.core.mf.MfHolding
import io.github.akrishna87.sievr.core.mf.MfScheme
import io.github.akrishna87.sievr.core.mf.MutualFunds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MutualFundsTest {
    private val schemes = listOf(
        MfScheme(122639, "Parag Parikh Flexi Cap Fund - Direct Plan - Growth", "PPFAS Mutual Fund", "Equity Scheme - Flexi Cap Fund", 90.0, "2026-11-06", listOf("INF879O01027"), 88.0, "2026-11-05"),
        MfScheme(122640, "Parag Parikh Flexi Cap Fund - Regular Plan - Growth", "PPFAS Mutual Fund", "Equity Scheme - Flexi Cap Fund", 80.0, "2026-11-06"),
        MfScheme(119091, "HDFC Liquid Fund - Direct Plan - Growth Option", "HDFC Mutual Fund", "Debt Scheme - Liquid Fund", 5000.0, "2026-11-06", listOf("INF179KB1HP9"), 4999.0, "2026-11-05"),
    )

    @Test fun portfolio() {
        val r = MutualFunds.analyze(listOf(MfHolding(122639, 100.0, 8000.0), MfHolding(119091, 2.0, 9900.0), MfHolding(1, 5.0, 500.0, "Old fund")),
            schemes.associateBy { it.code })
        assertEquals(9000.0 + 10000.0 + 500.0, r.value, 1e-9)
        assertEquals(18400.0, r.invested, 1e-9)
        assertEquals(200.0 + 2.0, r.dayChange, 1e-9)
        assertEquals("Debt", r.assetMix.first().first)
        assertEquals(listOf(1), r.missing.map { it.code })
        assertEquals(80.0, r.positions.first { it.holding.code == 122639 }.averageNav!!, 1e-9)
    }

    @Test fun search() {
        assertEquals(122639, MutualFunds.search(schemes, "parag flexi").first().code)
        assertEquals(119091, MutualFunds.search(schemes, "119091").single().code)
        assertEquals(119091, MutualFunds.search(schemes, "INF179KB1HP9").single().code)
        assertTrue(MutualFunds.search(schemes, "nothing like this").isEmpty())
    }

    @Test fun importCsv() {
        val csv = """
            Holdings as on 06-11-2026
            Scheme Name,AMC,Category,Units,Invested Value,Current Value
            Parag Parikh Flexi Cap Fund - Direct Plan - Growth,PPFAS,Equity,"1,234.567","1,00,000",111111
            HDFC Liquid Fund Direct Growth,HDFC,Debt,2.5,12000,12500
            Some Fund That Closed,X,Y,10,1000,1000
        """.trimIndent()
        val r = MutualFunds.parseCsv(csv, schemes)
        assertEquals(listOf(122639, 119091), r.holdings.map { it.code })
        assertEquals(1234.567, r.holdings[0].units, 1e-9)
        assertEquals(100000.0, r.holdings[0].invested, 1e-9)
        assertEquals(listOf("Some Fund That Closed"), r.unmatched)
    }

    @Test fun assetClasses() {
        fun cls(name: String, cat: String) = MfScheme(1, name, "", cat, 10.0, "2026-11-06").assetClass
        assertEquals("Equity", cls("SBI Nifty 50 Index Fund - Direct Plan - Growth", "Other Scheme - Index Funds"))
        assertEquals("Debt", cls("Bharat Bond ETF - April 2030", "Other Scheme - Other  ETFs"))
        assertEquals("Debt", cls("Edelweiss CRISIL IBX 50:50 Gilt Plus SDL Index Fund", "Other Scheme - Index Funds"))
        assertEquals("Gold & silver", cls("Nippon India Gold BeES", "Other Scheme - Gold ETF"))
        assertEquals("Equity", cls("HDFC Large and Mid Cap Fund", "Equity Schemes - Large & Mid Cap Fund"))
        assertEquals("Debt", cls("Some FMP Series 12", "Income"))
        assertEquals("Hybrid", cls("ICICI Balanced Advantage", "Hybrid Scheme - Dynamic Asset Allocation or Balanced Advantage"))
    }
}
