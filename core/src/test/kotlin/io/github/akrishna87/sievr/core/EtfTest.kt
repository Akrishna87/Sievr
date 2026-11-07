package io.github.akrishna87.sievr.core

import io.github.akrishna87.sievr.core.metrics.Enricher
import io.github.akrishna87.sievr.core.mf.CasParser
import io.github.akrishna87.sievr.core.mf.MfScheme
import io.github.akrishna87.sievr.core.model.StockData
import io.github.akrishna87.sievr.core.portfolio.ShareStatement
import io.github.akrishna87.sievr.core.screen.Screen
import io.github.akrishna87.sievr.core.screen.ScreenEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EtfTest {
    private val market = Enricher.enrich(TestData.snapshot().let { s ->
        val good = s.stocks.first().copy(isin = "INE002A01018")
        val etf = s.stocks.first().copy(symbol = "NIFTYBEES", name = "Nippon India ETF Nifty 50 BeES", isin = "INF204KB14I2",
            type = "ETF", sector = "ETF", industry = "Nifty 50", f = emptyMap(), fin = emptyList())
        s.copy(stocks = listOf(good, etf) + s.stocks.drop(1))
    })

    private val statement = """
        CDSL Holding Statement
        ISIN Code Company Name Current Bal Free Bal Lock-in Bal Pledged Bal Rate Value
        INE002A01018 GOOD CO LTD 25 25 0 0 2500.75 62518.75
        INF204KB14I2 NIPPON INDIA ETF NIFTY 50 BEES 40 40 0 0 280.50 11220.00
        Mutual Fund Folios (F)
        INF846K01DP8 AXIS LARGE CAP FUND - DIRECT GROWTH 9101112 1,500.000 50.0000 75,000.00 62.4100 93,615.00 18,615.00 12.45
    """.trimIndent()

    @Test fun statementImportsEtfsWithShares() {
        val r = ShareStatement.parse(statement, market)
        val bySym = r.holdings.associateBy { it.symbol }
        assertEquals(setOf("GOOD", "NIFTYBEES"), bySym.keys)
        assertEquals(40.0, bySym.getValue("NIFTYBEES").quantity, 1e-9)
        assertEquals(280.50, bySym.getValue("NIFTYBEES").averageCost, 1e-6)
        assertTrue(r.unmatched.isEmpty(), "the mutual fund folio is not an unmatched share: ${r.unmatched}")
    }

    @Test fun fundImportLeavesEtfsForStocks() {
        val schemes = listOf(
            MfScheme(1, "Nippon India ETF Nifty 50 BeES", category = "Other Scheme - Exchange Traded Fund", nav = 280.5, date = "2026-11-06", isin = listOf("INF204KB14I2")),
            MfScheme(2, "Axis Large Cap Fund - Direct Growth", category = "Equity Scheme - Large Cap Fund", nav = 62.41, date = "2026-11-06", isin = listOf("INF846K01DP8")),
        )
        val r = CasParser.parse(statement, schemes)
        assertEquals(listOf(2), r.holdings.map { it.code })
        assertEquals(1, r.etfsSkipped)
    }

    @Test fun screensLeaveOutEtfs() {
        val all = ScreenEngine.run(Screen("all", "All"), market.stocks)
        assertFalse(all.any { it.isEtf })
        assertTrue(market["NIFTYBEES"]!!.isEtf)
    }
}
