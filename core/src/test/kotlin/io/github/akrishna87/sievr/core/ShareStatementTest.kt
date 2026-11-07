package io.github.akrishna87.sievr.core

import io.github.akrishna87.sievr.core.metrics.Enricher
import io.github.akrishna87.sievr.core.mf.CasParser
import io.github.akrishna87.sievr.core.portfolio.ShareStatement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShareStatementTest {
    private val market = Enricher.enrich(TestData.snapshot().let { s ->
        s.copy(stocks = s.stocks.mapIndexed { i, st -> st.copy(isin = listOf("INE002A01018", "INE009A01021", "INE467B01029", "INE040A01034")[i]) })
    })

    // NSDL CAS equity table: ISIN, security, current balance, frozen, pledged, free balance, market price, value —
    // followed in the same statement by mutual fund folios, which must not be read as shares.
    private val nsdl = """
        NSDL Consolidated Account Statement
        Equities (E)
        ISIN Security Current Bal. Frozen Bal. Pledge Bal. Free Bal. Market Price / Face Value Value
        INE002A01018 GOOD CO LIMITED.EQ 50 0 0 50 2,500.75 1,25,037.50
        INE009A01021 CHEAP CO LTD 120.000 0.000 0.000 120.000 50.0000 6,000.00
        INE999Z01011 UNLISTED THING LTD 10 0 0 10 100.00 1,000.00
        Mutual Fund Folios (F)
        INF846K01DP8 AXIS LARGE CAP FUND - DIRECT GROWTH 9101112 1,500.000 50.0000 75,000.00 62.4100 93,615.00 18,615.00 12.45
    """.trimIndent()

    // A CDSL statement for another demat account holding the same company.
    private val cdsl = """
        CDSL Holding Statement
        ISIN Code Company Name Current Bal Free Bal Lock-in Bal Pledged Bal Rate Value
        INE002A01018 GOOD CO LTD 25 25 0 0 2500.75 62518.75
    """.trimIndent()

    @Test fun sharesFromDepositoryStatements() {
        val r = ShareStatement.parse(nsdl + "\n" + cdsl, market)
        val bySym = r.holdings.associateBy { it.symbol }
        assertEquals(setOf("GOOD", "CHEAP"), bySym.keys)
        assertEquals(75.0, bySym.getValue("GOOD").quantity, 1e-9)
        assertEquals(2500.75, bySym.getValue("GOOD").averageCost, 1e-6)
        assertEquals(120.0, bySym.getValue("CHEAP").quantity, 1e-9)
        assertEquals(setOf("GOOD", "CHEAP"), r.priceIsPlaceholder)
        assertEquals(1, r.unmatched.size)
        // The fund row in the same statement is still read as a fund, not a share.
        assertEquals(listOf("INF846K01DP8"), CasParser.entries(nsdl).map { it.isin })
        assertTrue(r.holdings.none { it.symbol.startsWith("INF") })
    }
}
