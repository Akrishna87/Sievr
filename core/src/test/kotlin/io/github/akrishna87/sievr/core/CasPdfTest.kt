package io.github.akrishna87.sievr.core

import io.github.akrishna87.sievr.core.mf.CasParser
import io.github.akrishna87.sievr.core.mf.MfScheme
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.encryption.AccessPermission
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.text.PDFTextStripper
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** The whole path the app takes: a password-protected statement PDF → text → holdings. */
class CasPdfTest {
    private val schemes = listOf(
        MfScheme(122639, "Parag Parikh Flexi Cap Fund - Direct Plan - Growth", "PPFAS Mutual Fund", "Equity Scheme - Flexi Cap Fund", 93.9483, "2026-11-06", listOf("INF879O01027")),
        MfScheme(119551, "Axis Large Cap Fund - Direct Plan - Growth", "Axis Mutual Fund", "Equity Scheme - Large Cap Fund", 62.41, "2026-11-06", listOf("INF846K01DP8")),
    )

    private fun statementPdf(password: String, columns: List<Pair<Float, String>>): ByteArray {
        val doc = PDDocument()
        val page = PDPage()
        doc.addPage(page)
        PDPageContentStream(doc, page).use { cs ->
            cs.setFont(PDType1Font.HELVETICA, 8f)
            var y = 750f
            // Lines are written as separate text runs at x positions, like a real statement's table cells.
            for ((x, line) in columns) {
                if (x == 0f) y -= 12f
                cs.beginText(); cs.newLineAtOffset(40f + x, y); cs.showText(line); cs.endText()
            }
        }
        doc.protect(StandardProtectionPolicy("owner-$password", password, AccessPermission()).apply { encryptionKeyLength = 128 })
        return ByteArrayOutputStream().also { doc.save(it); doc.close() }.toByteArray()
    }

    private fun text(pdf: ByteArray, password: String) =
        PDDocument.load(pdf, password).use { PDFTextStripper().apply { sortByPosition = true }.getText(it) }

    @Test fun detailedStatementPdf() {
        val pdf = statementPdf("secret123", listOf(
            0f to "Consolidated Account Statement",
            0f to "Folio No: 12345678 / 0   PAN: ABCDE1234F",
            0f to "PP001ZG-Parag Parikh Flexi Cap Fund - Direct Plan Growth - ISIN: INF879O01027(Advisor: DIRECT) Registrar : CAMS",
            0f to "Opening Unit Balance: 500.000",
            0f to "05-Apr-2026", 60f to "Purchase - SIP", 180f to "5,000.00", 240f to "59.123", 300f to "84.5700", 360f to "559.123",
            0f to "Closing Unit Balance: 616.994", 150f to "NAV on 31-Oct-2026: INR 93.9483", 300f to "Total Cost Value: 50,000.00",
            440f to "Market Value on 31-Oct-2026: INR 57,965.66",
            0f to "Folio No: 9101112 / 22",
            0f to "128AXEDG-Axis Large Cap Fund - Direct Growth - ISIN: INF846K01DP8 Registrar : KFINTECH",
            0f to "Closing Unit Balance: 1,000.000", 150f to "NAV on 31-Oct-2026: INR 62.4100", 300f to "Total Cost Value: 55,000.00",
            440f to "Market Value on 31-Oct-2026: INR 62,410.00",
        ))
        assertFailsWith<InvalidPasswordException> { text(pdf, "wrong") }
        val r = CasParser.parse(text(pdf, "secret123"), schemes)
        val byCode = r.holdings.associateBy { it.code }
        assertEquals(616.994, byCode.getValue(122639).units, 1e-6)
        assertEquals(50000.0, byCode.getValue(122639).invested, 1e-6)
        assertEquals(1000.0, byCode.getValue(119551).units, 1e-6)
        assertEquals(55000.0, byCode.getValue(119551).invested, 1e-6)
    }

    @Test fun depositoryStatementPdfAsTableCells() {
        // NSDL/CDSL tables: each value is its own cell; the extracted row must still read in column order.
        val cells = listOf(0f, 70f, 230f, 280f, 320f, 360f, 410f, 450f, 500f)
        val row1 = listOf("INF846K01DP8", "AXIS LARGE CAP FUND - DIRECT GROWTH", "9101112", "1,500.000", "50.0000", "75,000.00", "62.4100", "93,615.00", "18,615.00")
        val pdf = statementPdf("ABCDE1234F01011980", listOf(0f to "Mutual Fund Folios (F)") + cells.zip(row1))
        val r = CasParser.parse(text(pdf, "ABCDE1234F01011980"), schemes)
        val h = r.holdings.single()
        assertEquals(119551, h.code)
        assertEquals(1500.0, h.units, 1e-6)
        assertEquals(75000.0, h.invested, 1e-6)
    }

    @Test fun sharesFromDematStatementPdf() {
        val market = io.github.akrishna87.sievr.core.metrics.Enricher.enrich(TestData.snapshot().let { snap ->
            snap.copy(stocks = snap.stocks.mapIndexed { i, st -> st.copy(isin = listOf("INE002A01018", "INE009A01021", "INE467B01029", "INE040A01034")[i]) })
        })
        val cells = listOf(0f, 70f, 230f, 270f, 310f, 350f, 400f, 460f)
        val shares = listOf("INE002A01018", "GOOD CO LIMITED.EQ", "50", "0", "0", "50", "2,500.75", "1,25,037.50")
        val fund = listOf("INF846K01DP8", "AXIS LARGE CAP FUND - DIRECT GROWTH", "9101112", "1,500.000", "50.0000", "75,000.00", "62.4100", "93,615.00")
        val pdf = statementPdf("ABCDE1234F01011980",
            listOf(0f to "Equities (E)") + cells.zip(shares) + listOf(0f to "Mutual Fund Folios (F)") + cells.zip(fund))
        val text = text(pdf, "ABCDE1234F01011980")
        val r = io.github.akrishna87.sievr.core.portfolio.ShareStatement.parse(text, market)
        val h = r.holdings.single()
        assertEquals("GOOD", h.symbol)
        assertEquals(50.0, h.quantity, 1e-9)
        assertEquals(2500.75, h.averageCost, 1e-6)
        // The same PDF's fund row is still read by the fund import.
        assertEquals(1500.0, CasParser.parse(text, schemes).holdings.single().units, 1e-6)
    }
}
