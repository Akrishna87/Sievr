package io.github.akrishna87.sievr.core

import io.github.akrishna87.sievr.core.mf.CasParser
import io.github.akrishna87.sievr.core.mf.MfScheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CasParserTest {
    private val schemes = listOf(
        MfScheme(122639, "Parag Parikh Flexi Cap Fund - Direct Plan - Growth", "PPFAS Mutual Fund", "Equity Scheme - Flexi Cap Fund", 93.9483, "2026-11-06", listOf("INF879O01027")),
        MfScheme(119551, "Axis Large Cap Fund - Direct Plan - Growth", "Axis Mutual Fund", "Equity Scheme - Large Cap Fund", 62.41, "2026-11-06", listOf("INF846K01DP8")),
        MfScheme(119091, "HDFC Liquid Fund - Direct Plan - Growth Option", "HDFC Mutual Fund", "Debt Scheme - Liquid Fund", 5100.25, "2026-11-06", listOf("INF179KB1HP9")),
    )

    // Detailed CAMS/KFintech CAS: header, transactions, closing line. Same Parag Parikh fund in two folios;
    // a fully redeemed fund; and a scheme AMFI no longer lists.
    private val detailed = """
        Consolidated Account Statement
        01-Apr-2026 To 31-Oct-2026
        PPFAS Mutual Fund
        Folio No: 12345678 / 0 PAN: ABCDE1234F KYC: OK PAN: OK
        PP001ZG-Parag Parikh Flexi Cap Fund - Direct Plan Growth - ISIN: INF879O01027(Advisor: DIRECT) Registrar : CAMS
        Opening Unit Balance: 500.000
        05-Apr-2026 Purchase - SIP 5,000.00 59.123 84.5700 559.123
        05-May-2026 Purchase - SIP 5,000.00 57.871 86.4000 616.994
        Closing Unit Balance: 616.994 NAV on 31-Oct-2026: INR 93.9483 Total Cost Value: 50,000.00 Market Value on 31-Oct-2026: INR 57,965.66
        Folio No: 12345678 / 1 PAN: ABCDE1234F KYC: OK PAN: OK
        PP001ZG-Parag Parikh Flexi Cap Fund - Direct Plan Growth - ISIN: INF879O01027(Advisor: DIRECT) Registrar : CAMS
        Opening Unit Balance: 100.000
        Closing Unit Balance: 100.000 NAV on 31-Oct-2026: INR 93.9483 Total Cost Value: 7,000.00 Market Value on 31-Oct-2026: INR 9,394.83
        Axis Mutual Fund
        Folio No: 9101112 / 22
        128AXEDG-Axis Large Cap Fund - Direct Growth - ISIN: INF846K01DP8 Registrar : KFINTECH
        Opening Unit Balance: 1,000.000
        10-Jun-2026 Redemption (1,000.000) (61,000.00) 61.0000 0.000
        Closing Unit Balance: 0.000 NAV on 31-Oct-2026: INR 62.4100 Total Cost Value: 0.00 Market Value on 31-Oct-2026: INR 0.00
        Folio No: 5555 / 1
        OLD1-Some Merged Fund - Growth - ISIN: INF000X01011 Registrar : CAMS
        Closing Unit Balance: 10.000 NAV on 31-Oct-2026: INR 20.0000 Total Cost Value: 150.00 Market Value on 31-Oct-2026: INR 200.00
    """.trimIndent()

    @Test fun detailedStatement() {
        val r = CasParser.parse(detailed, schemes)
        val pp = r.holdings.single()
        assertEquals(122639, pp.code)
        assertEquals(716.994, pp.units, 1e-6)
        assertEquals(57000.0, pp.invested, 1e-6)
        assertEquals(listOf("INF000X01011"), r.unmatched.map { it.isin })
        assertEquals(10.0, r.unmatched.single().units, 1e-9)
        assertEquals(0, r.missingCost)
    }

    // Summary CAS: one row per folio — folio, ISIN, scheme, cost, units, NAV date, NAV, market value, registrar.
    @Test fun summaryStatement() {
        val text = """
            Folio No. ISIN Scheme Name Cost Value Unit Balance NAV Date NAV Market Value Registrar
            12345678/0 INF879O01027 PP001ZG-Parag Parikh Flexi Cap Fund - Direct Plan Growth 50,000.00 616.994 31-Oct-2026 93.9483 57,965.66 CAMS
            9101112/22 INF179KB1HP9 H-HDFC Liquid Fund - Direct Plan - Growth 20,000.00 3.900 31-Oct-2026 5,100.2500 19,890.98 CAMS
            Total 70,000.00 77,856.64
        """.trimIndent()
        val r = CasParser.parse(text, schemes)
        val byCode = r.holdings.associateBy { it.code }
        assertEquals(616.994, byCode.getValue(122639).units, 1e-6)
        assertEquals(50000.0, byCode.getValue(122639).invested, 1e-6)
        assertEquals(3.9, byCode.getValue(119091).units, 1e-6)
        assertEquals(20000.0, byCode.getValue(119091).invested, 1e-6)
    }

    // NSDL/CDSL CAS mutual fund table: ISIN, description, folio, units, avg cost/unit, total cost, NAV, value, P/L, return %.
    @Test fun depositoryStatement() {
        val text = """
            ISIN ISIN Description Folio No. No. of Units Average Cost Per Unit Total Cost Current NAV per unit Current Value Unrealised Profit/(Loss) Annualised Return
            INF846K01DP8 AXIS LARGE CAP FUND - DIRECT GROWTH 9101112 1,500.000 50.0000 75,000.00 62.4100 93,615.00 18,615.00 12.45
            INF879O01027 PARAG PARIKH FLEXI CAP FUND - DIRECT PLAN GROWTH 12345678 200.000 100.0000 20,000.00 93.9483 18,789.66 (1,210.34) -3.10
        """.trimIndent()
        val r = CasParser.parse(text, schemes)
        val byCode = r.holdings.associateBy { it.code }
        assertEquals(1500.0, byCode.getValue(119551).units, 1e-6)
        assertEquals(75000.0, byCode.getValue(119551).invested, 1e-6)
        assertEquals(200.0, byCode.getValue(122639).units, 1e-6)
        assertEquals(20000.0, byCode.getValue(122639).invested, 1e-6)
    }

    @Test fun nothingFound() {
        val r = CasParser.parse("This is not a statement. Account 12345 dated 01-01-2026.", schemes)
        assertTrue(r.holdings.isEmpty() && r.unmatched.isEmpty())
        val e = CasParser.entries("INF879O01027 Parag Parikh 616.994 31-Oct-2026 93.9483 57,965.66")
        assertNull(e.single().cost)
    }
}
