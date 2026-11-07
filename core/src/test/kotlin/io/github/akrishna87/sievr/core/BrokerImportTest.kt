package io.github.akrishna87.sievr.core

import io.github.akrishna87.sievr.core.metrics.Enricher
import io.github.akrishna87.sievr.core.portfolio.Holding
import io.github.akrishna87.sievr.core.portfolio.PortfolioAnalyzer
import io.github.akrishna87.sievr.core.util.Xlsx
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BrokerImportTest {
    private val market = Enricher.enrich(TestData.snapshot().let { s ->
        s.copy(stocks = s.stocks.mapIndexed { i, st ->
            st.copy(isin = listOf("INE002A01018", "INE009A01021", "INE467B01029", "INE040A01034")[i],
                name = listOf("Good Co Limited", "Cheap Co Ltd", "Debt Co Ltd", "Mid Co Limited")[i])
        })
    })

    /** A minimal .xlsx: shared strings for text cells, numbers inline, a first row of account details, as Excel writes them. */
    private fun xlsx(rows: List<List<Any>>): ByteArray {
        val strings = mutableListOf<String>()
        fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;")
        val sheet = StringBuilder("""<?xml version="1.0" encoding="UTF-8"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
        rows.forEachIndexed { r, row ->
            sheet.append("""<row r="${r + 1}">""")
            row.forEachIndexed { c, v ->
                if (v == "") return@forEachIndexed  // empty cells are simply absent, as in real files
                val ref = ('A' + c).toString() + (r + 1)
                if (v is Number) sheet.append("""<c r="$ref"><v>$v</v></c>""")
                else { strings += v.toString(); sheet.append("""<c r="$ref" t="s"><v>${strings.size - 1}</v></c>""") }
            }
            sheet.append("</row>")
        }
        sheet.append("</sheetData></worksheet>")
        val shared = """<?xml version="1.0" encoding="UTF-8"?><sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""" +
            strings.joinToString("") { "<si><t>${esc(it)}</t></si>" } + "</sst>"
        val files = mapOf(
            "[Content_Types].xml" to """<?xml version="1.0"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"/>""",
            "xl/workbook.xml" to """<?xml version="1.0"?><workbook xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Holdings" sheetId="1" r:id="rId1"/></sheets></workbook>""",
            "xl/_rels/workbook.xml.rels" to """<?xml version="1.0"?><Relationships><Relationship Id="rId1" Target="worksheets/sheet1.xml"/></Relationships>""",
            "xl/worksheets/sheet1.xml" to sheet.toString(),
            "xl/sharedStrings.xml" to shared,
        )
        return ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { z -> files.forEach { (n, t) -> z.putNextEntry(ZipEntry(n)); z.write(t.toByteArray()); z.closeEntry() } }
        }.toByteArray()
    }

    @Test fun growwStyleExcelHoldings() {
        val bytes = xlsx(listOf(
            listOf("Name", "Aravind"),
            listOf("Unique Client Code", "1234567"),
            listOf("Holdings statement as on 06-11-2026"),
            listOf("Stock Name", "ISIN", "Quantity", "Average buy price", "Buy value", "Closing price", "Closing value", "Unrealised P&L"),
            listOf("GOOD CO LIMITED", "INE002A01018", 10, 1250.5, 12505, 1300, 13000, 495),
            listOf("Cheap Co Ltd.", "", 25, 42, 1050, 50, 1250, 200),             // no ISIN: matched by name
            listOf("Some Unlisted Ltd", "INE999Z01011", 5, 10, 50, 0, 0, 0),
            listOf("Total", "", "", "", 13605, "", 14250, 645),
        ))
        assertTrue(Xlsx.looksLikeXlsx(bytes))
        val rows = Xlsx.firstSheet(bytes)
        assertEquals("Stock Name", rows[3][0])
        assertEquals("", rows[5][1])  // the missing ISIN cell keeps the columns aligned
        val r = PortfolioAnalyzer.parseCsv(Xlsx.toCsv(bytes), market)
        assertEquals(listOf(Holding("GOOD", 10.0, 1250.5), Holding("CHEAP", 25.0, 42.0)), r.holdings)
        assertEquals(listOf("Some Unlisted Ltd"), r.unmatched)
    }

    @Test fun zerodhaStyleCsvAndSymbolMatching() {
        val csv = "Instrument,Qty.,Avg. cost,LTP,Cur. val,P&L\nGOOD,4,1000,1300,5200,1200\nMID-BE,2,80,90,180,20\nNOTREAL,1,5,5,5,0\n"
        val r = PortfolioAnalyzer.parseCsv(csv, market)
        assertEquals(listOf(Holding("GOOD", 4.0, 1000.0), Holding("MID", 2.0, 80.0)), r.holdings)
        assertEquals(listOf("NOTREAL"), r.unmatched)
    }

    @Test fun buyValueWhenNoAveragePrice() {
        val csv = "Company Name,ISIN Code,No. of Shares,Invested Value\nDebt Co Ltd,INE467B01029,10,\"1,500.00\"\n"
        assertEquals(listOf(Holding("DEBT", 10.0, 150.0)), PortfolioAnalyzer.parseCsv(csv, market).holdings)
    }
}
