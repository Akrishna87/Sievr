package io.github.akrishna87.sievr.core.util

import io.github.akrishna87.sievr.core.metrics.Metrics
import io.github.akrishna87.sievr.core.model.Stock

object Csv {
    /** Results as CSV with a header row: symbol, name, sector, then each metric column. */
    fun export(stocks: List<Stock>, columns: List<String>): String = buildString {
        val metrics = columns.mapNotNull { Metrics[it] }
        appendLine((listOf("Symbol", "Name", "Sector", "Industry") + metrics.map { it.label }).joinToString(",") { quote(it) })
        for (s in stocks) {
            val cells = listOf(s.symbol, s.name, s.sector, s.industry) +
                metrics.map { m -> s[m.id]?.let { v -> if (v == v.toLong().toDouble()) v.toLong().toString() else "%.4f".format(v).trimEnd('0').trimEnd('.') } ?: "" }
            appendLine(cells.joinToString(",") { quote(it) })
        }
    }

    private fun quote(s: String) = if (s.any { it == ',' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
}
