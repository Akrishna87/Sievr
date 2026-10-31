package io.github.akrishna87.sievr.core.tools

import io.github.akrishna87.sievr.core.metrics.Enricher
import io.github.akrishna87.sievr.core.model.SnapshotIO
import io.github.akrishna87.sievr.core.screen.Presets
import io.github.akrishna87.sievr.core.screen.ScreenEngine
import java.io.File
import kotlin.system.exitProcess

/**
 * Loads a snapshot exactly as the app does and runs every preset screen, so a broken data file
 * is caught before it's published. Usage: checkSnapshot <snapshot.json.gz> [minimum stocks]
 */
fun main(args: Array<String>) {
    val file = File(args.getOrNull(0) ?: error("snapshot path required"))
    val minimum = args.getOrNull(1)?.toInt() ?: 100
    val started = System.currentTimeMillis()
    val snapshot = file.inputStream().use { SnapshotIO.read(it) }
    val market = Enricher.enrich(snapshot)
    val took = System.currentTimeMillis() - started
    println("Snapshot ${snapshot.asOf}: ${market.stocks.size} stocks, ${snapshot.dailyDates.size} days, ${snapshot.weeklyDates.size} weeks, ${snapshot.indices.size} indices; loaded in ${took} ms")

    val coverage = listOf("price", "mcap", "pe", "roe", "roce", "salesCagr3", "de", "divYield", "promoter", "rsi14", "sma200", "qualityScore", "sievrScore")
    for (id in coverage) {
        val n = market.stocks.count { it[id] != null }
        println("  %-14s %5d (%.0f%%)".format(id, n, n * 100.0 / maxOf(1, market.stocks.size)))
    }
    println("Sectors: ${market.sectors.size}; indices: ${market.indexNames.joinToString()}")
    for (screen in Presets.all) {
        println("  %-34s %4d".format(screen.name, ScreenEngine.run(screen, market.stocks).size))
    }
    val priced = market.stocks.count { it["price"] != null }
    if (priced < minimum) {
        System.err.println("Only $priced stocks have prices (need $minimum)")
        exitProcess(1)
    }
}
