package io.github.akrishna87.sievr.core.portfolio

import io.github.akrishna87.sievr.core.mf.CasParser
import io.github.akrishna87.sievr.core.model.Market

/**
 * Reads the shares in a depository statement (the NSDL or CDSL CAS): every company's ISIN, quantity,
 * price and value. Depository statements don't show what was paid, so when no cost column is found the
 * statement's price is used as the buy price, and the import reports which holdings need their real one.
 */
object ShareStatement {

    data class Result(
        val holdings: List<Holding>,
        /** Symbols whose buy price is today's price because the statement had no cost. */
        val priceIsPlaceholder: Set<String>,
        /** Shares whose ISIN isn't in Sievr's market data (unlisted, suspended, renamed), as name or ISIN. */
        val unmatched: List<String>,
        val statementValue: Double,
    )

    fun parse(text: String, market: Market): Result {
        val bySin = market.stocks.mapNotNull { s -> s.data.isin?.let { it to s } }.toMap()
        val qty = LinkedHashMap<String, Double>()
        val cost = HashMap<String, Double>()
        val placeholder = mutableSetOf<String>()
        val unmatched = mutableListOf<String>()
        // Company shares, plus ETFs (fund ISINs) that Sievr tracks; other fund units belong to the Mutual funds tab.
        val etfRows = CasParser.entries(text, CasParser.Kind.ETFS)
        val entries = CasParser.entries(text, CasParser.Kind.SHARES) +
            etfRows.filter { bySin[it.isin]?.isEtf == true || (bySin[it.isin] == null && CasParser.etfName(it.name)) }
        for (e in entries) {
            val stock = bySin[e.isin]
            if (stock == null) {
                unmatched += e.name.ifBlank { e.isin }
                continue
            }
            val price = e.nav ?: stock["price"] ?: 0.0
            val paid = e.cost ?: (price * e.units).also { placeholder += stock.symbol }
            qty[stock.symbol] = (qty[stock.symbol] ?: 0.0) + e.units
            cost[stock.symbol] = (cost[stock.symbol] ?: 0.0) + paid
        }
        val holdings = qty.map { (sym, q) -> Holding(sym, q, if (q > 0) (cost[sym] ?: 0.0) / q else 0.0) }
        return Result(holdings, placeholder, unmatched, entries.sumOf { it.value ?: 0.0 })
    }
}
