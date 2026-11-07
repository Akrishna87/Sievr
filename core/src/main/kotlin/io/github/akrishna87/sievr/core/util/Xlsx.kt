package io.github.akrishna87.sievr.core.util

import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Reads the first worksheet of an Excel .xlsx file (a zip of XML files) into rows of cell text, enough for the
 * holdings reports brokers and fund platforms export (Groww, Zerodha Console, Kuvera…). No formulas are
 * evaluated: a cell shows its stored value, which is what these exports contain.
 */
object Xlsx {

    /** True when the bytes look like a zip (and so possibly .xlsx) rather than text. */
    fun looksLikeXlsx(bytes: ByteArray): Boolean = bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()

    fun firstSheet(bytes: ByteArray): List<List<String>> {
        val files = HashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                if (!e.isDirectory && (e.name.endsWith(".xml") || e.name.endsWith(".rels"))) files[e.name.trimStart('/')] = zip.readBytes()
            }
        }
        val shared = files["xl/sharedStrings.xml"]?.let { sharedStrings(it) }.orEmpty()
        val sheet = files[firstSheetPath(files)] ?: files.keys.filter { it.startsWith("xl/worksheets/") && it.endsWith(".xml") }.sorted().firstOrNull()?.let { files[it] }
            ?: return emptyList()
        return rows(sheet, shared)
    }

    /** The rows as CSV text, so CSV importers can read Excel files too. */
    fun toCsv(bytes: ByteArray): String = firstSheet(bytes).joinToString("\n") { row ->
        row.joinToString(",") { c -> if (c.any { it == ',' || it == '"' || it == '\n' }) "\"" + c.replace("\"", "\"\"") + "\"" else c }
    }

    private fun parse(bytes: ByteArray) = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = false
        runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        isExpandEntityReferences = false
    }.newDocumentBuilder().parse(ByteArrayInputStream(bytes))

    private fun firstSheetPath(files: Map<String, ByteArray>): String? {
        val workbook = files["xl/workbook.xml"] ?: return null
        val rels = files["xl/_rels/workbook.xml.rels"] ?: return null
        val first = (parse(workbook).getElementsByTagName("sheet").item(0) as? Element) ?: return null
        val id = first.getAttribute("r:id").ifBlank { return null }
        val relNodes = parse(rels).getElementsByTagName("Relationship")
        for (i in 0 until relNodes.length) {
            val r = relNodes.item(i) as Element
            if (r.getAttribute("Id") == id) {
                val target = r.getAttribute("Target").trimStart('/')
                return if (target.startsWith("xl/")) target else "xl/$target"
            }
        }
        return null
    }

    private fun sharedStrings(bytes: ByteArray): List<String> {
        val items = parse(bytes).getElementsByTagName("si")
        return (0 until items.length).map { i ->
            val ts = (items.item(i) as Element).getElementsByTagName("t")
            (0 until ts.length).joinToString("") { ts.item(it).textContent }
        }
    }

    private fun rows(bytes: ByteArray, shared: List<String>): List<List<String>> {
        val rowNodes = parse(bytes).getElementsByTagName("row")
        val out = mutableListOf<List<String>>()
        for (i in 0 until rowNodes.length) {
            val cells = (rowNodes.item(i) as Element).getElementsByTagName("c")
            val row = mutableListOf<String>()
            for (j in 0 until cells.length) {
                val c = cells.item(j) as Element
                val col = column(c.getAttribute("r")) ?: row.size
                while (row.size < col) row += ""
                val type = c.getAttribute("t")
                val v = c.getElementsByTagName("v").item(0)?.textContent
                val text = when (type) {
                    "s" -> v?.toIntOrNull()?.let { shared.getOrNull(it) }.orEmpty()
                    "inlineStr" -> c.getElementsByTagName("t").let { ts -> (0 until ts.length).joinToString("") { ts.item(it).textContent } }
                    "b" -> if (v == "1") "TRUE" else "FALSE"
                    else -> v.orEmpty()
                }
                row += text.trim()
            }
            if (row.any { it.isNotBlank() }) out += row
        }
        return out
    }

    /** "C12" → 2 (zero-based column), from the cell reference's letters. */
    private fun column(ref: String): Int? {
        val letters = ref.takeWhile { it.isLetter() }.uppercase()
        if (letters.isEmpty()) return null
        return letters.fold(0) { acc, ch -> acc * 26 + (ch - 'A' + 1) } - 1
    }
}
