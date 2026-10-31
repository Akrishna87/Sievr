package io.github.akrishna87.sievr.core

import io.github.akrishna87.sievr.core.backtest.Backtester
import io.github.akrishna87.sievr.core.formula.Formula
import io.github.akrishna87.sievr.core.metrics.Enricher
import io.github.akrishna87.sievr.core.metrics.Metrics
import io.github.akrishna87.sievr.core.model.Snapshot
import io.github.akrishna87.sievr.core.nl.PlainEnglish
import io.github.akrishna87.sievr.core.portfolio.Holding
import io.github.akrishna87.sievr.core.portfolio.PortfolioAnalyzer
import io.github.akrishna87.sievr.core.screen.Filter
import io.github.akrishna87.sievr.core.screen.FormulaFilter
import io.github.akrishna87.sievr.core.screen.MetricFilter
import io.github.akrishna87.sievr.core.screen.Op
import io.github.akrishna87.sievr.core.screen.Presets
import io.github.akrishna87.sievr.core.screen.Screen
import io.github.akrishna87.sievr.core.screen.ScreenEngine
import io.github.akrishna87.sievr.core.screen.SectorFilter
import io.github.akrishna87.sievr.core.technical.CandlePattern
import io.github.akrishna87.sievr.core.technical.Indicators
import io.github.akrishna87.sievr.core.valuation.Valuation
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FormulaTest {
    private val values = mapOf("roe" to 22.0, "pe" to 12.0, "industryPe" to 20.0, "de" to 0.2, "price" to 100.0, "sma200" to 90.0)
    private fun eval(s: String) = Formula.parse(s).matches { values[it] }

    @Test fun comparisonsAndLogic() {
        assertTrue(eval("ROE > 20 AND PE < Industry_PE"))
        assertTrue(eval("roe>20 && pe<industry_pe"))
        assertFalse(eval("ROE > 25 OR Debt_to_Equity > 1"))
        assertTrue(eval("NOT (de > 1)"))
        assertTrue(eval("Price > SMA_200 * 1.1 - 0.5"))
        assertTrue(eval("PE BETWEEN 10 AND 15"))
        assertTrue(eval("ROE > 15% and max(pe, 5) = 12"))
    }

    @Test fun missingMetricIsFalse() {
        assertFalse(eval("Dividend_Yield > 1"))
        assertTrue(eval("NOT (Dividend_Yield > 1)"))
    }

    @Test fun errors() {
        assertFailsWith<Formula.ParseException> { Formula.parse("ROE >") }
        assertFailsWith<Formula.ParseException> { Formula.parse("Banana > 3") }
        assertFailsWith<Formula.ParseException> { Formula.parse("(ROE > 3") }
        assertEquals(setOf("roe", "pe"), Formula.parse("roe / pe > 1").metricIds)
    }
}

class IndicatorTest {
    @Test fun basics() {
        val rising = (1..300).map { it.toDouble() }
        assertEquals(298.5, Indicators.sma(rising, 4))
        assertEquals(100.0, Indicators.rsi(rising))
        assertEquals(100.0, Indicators.change(listOf(50.0, 100.0), 1))
        assertEquals(10.0, Indicators.cagr(100.0, 121.0, 2.0)!!, 1e-9)
        assertTrue(Indicators.macd(rising)!!.macd > 0)
    }

    @Test fun goldenCross() {
        // Long fall then a sharp rise: the 50-day average overtakes the 200-day.
        val prices = (0 until 260).map { if (it < 200) 200.0 - it * 0.5 else 100.0 + (it - 200) * 6.0 }
        var crossedAt = -1
        for (end in 211..prices.size) if (Indicators.maCross(prices.subList(0, end), 50, 200, 1) == 1) { crossedAt = end; break }
        assertTrue(crossedAt > 0)
    }

    @Test fun patterns() {
        val falling = listOf(listOf(110.0, 111.0, 104.0, 105.0), listOf(105.0, 106.0, 100.0, 101.0), listOf(101.0, 102.0, 96.0, 97.0))
        val hammer = falling + listOf(listOf(96.0, 97.2, 90.0, 97.0))
        assertTrue(CandlePattern.HAMMER in CandlePattern.detect(hammer))
        val engulf = falling + listOf(listOf(96.0, 103.0, 95.5, 102.5))
        assertTrue(CandlePattern.BULLISH_ENGULFING in CandlePattern.detect(engulf))
    }
}

class ValuationTest {
    @Test fun dcfAndReverse() {
        val r = Valuation.dcf(Valuation.DcfInputs(100.0, 10.0, 5.0, 4.0, 12.0, 50.0, 10.0))!!
        assertTrue(r.valuePerShare > 100)
        val g = Valuation.impliedGrowth(r.equityValue, 100.0, 50.0)!!
        // A single growth rate for 10 years lands between 5 and 10.
        assertTrue(g in 5.0..10.0, "implied $g")
        assertEquals(30.0, Valuation.grahamNumber(4.0, 10.0)!!, 1e-9)
        assertNull(Valuation.grahamNumber(-1.0, 10.0))
    }
}

class EnricherTest {
    private val market = Enricher.enrich(TestData.snapshot())

    @Test fun metricsAreWorkedOut() {
        val good = market["GOOD"]!!
        assertNotNull(good["price"]); assertNotNull(good["sma200"]); assertNotNull(good["rsi14"])
        assertEquals(20.0, good["salesCagr3"]!!, 0.01)
        assertNotNull(good["roce"]); assertNotNull(good["piotroski"]); assertNotNull(good["altmanZ"])
        assertNotNull(good["industryPe"]); assertNotNull(good["qualityScore"]); assertNotNull(good["sievrScore"])
        assertEquals(1.0, good["capCategory"])
        assertEquals(5.0, good["volumeRatio"]!!, 1e-9)
        assertTrue(good["ret1y"]!! > 0)
        assertNotNull(good["relStrength"])
        assertTrue(market.stocks.all { s -> s.metrics.values.all { it.isFinite() } })
        // Higher growth and margins should score better on quality and growth than the indebted shrinker.
        val debt = market["DEBT"]!!
        assertTrue(good["growthScore"]!! > debt["growthScore"]!!)
        assertTrue(good["qualityScore"]!! > debt["qualityScore"]!!)
        assertTrue(good["magicRank"] != null)
    }

    @Test fun presetsAllRun() {
        for (screen in Presets.all) {
            ScreenEngine.run(screen, market.stocks)
            screen.filters.filterIsInstance<FormulaFilter>().forEach { assertTrue(it.isValid, "${screen.id}: ${it.expression}") }
            screen.displayColumns().forEach { assertNotNull(Metrics[it], "${screen.id} column $it") }
        }
        assertEquals(Presets.all.size, Presets.all.map { it.id }.distinct().size)
    }

    @Test fun screening() {
        val screen = Screen("t", "t", filters = listOf(MetricFilter("de", Op.LT, 0.5), FormulaFilter("salesCagr3 > 8")), sortBy = "roe")
        val hits = ScreenEngine.run(screen, market.stocks).map { it.symbol }
        assertEquals(listOf("GOOD", "MID"), hits.sorted())
    }

    @Test fun screenSerialization() {
        val json = Json { ignoreUnknownKeys = true }
        val screen = Presets["graham-defensive"]!!.copy(filters = Presets["graham-defensive"]!!.filters + SectorFilter(setOf("Technology")))
        val text = json.encodeToString(Screen.serializer(), screen)
        val back = json.decodeFromString(Screen.serializer(), text)
        assertEquals(screen.filters.map(Filter::describe), back.filters.map(Filter::describe))
        val snap = json.decodeFromString(Snapshot.serializer(), json.encodeToString(Snapshot.serializer(), TestData.snapshot()))
        assertEquals(4, snap.stocks.size)
    }

    @Test fun backtest() {
        val screen = Screen("m", "Momentum", filters = listOf(MetricFilter("ret6m", Op.GT, 0.0)), sortBy = "ret1y", limit = 2)
        val r = Backtester.run(screen, market, Backtester.Settings(years = 4))!!
        assertTrue(r.curve.size > 100)
        assertTrue(r.cagr > 0, "cagr ${r.cagr}")
        assertTrue(r.lookAheadMetrics.isEmpty())
        assertNotNull(r.benchmarkCagr)
        val withFundamentals = Backtester.run(screen.copy(filters = screen.filters + MetricFilter("roe", Op.GT, 0.0)), market)!!
        assertEquals(listOf("ROE"), withFundamentals.lookAheadMetrics)
    }

    @Test fun portfolio() {
        val report = PortfolioAnalyzer.analyze(listOf(Holding("GOOD", 10.0, 100.0), Holding("CHEAP", 20.0, 40.0), Holding("NOPE", 1.0, 5.0)), market)
        assertEquals(100.0, report.positions.sumOf { it.weight }, 1e-6)
        assertTrue(report.warnings.any { it.contains("NOPE") })
        assertNotNull(report.weighted["roe"])
        val csv = "Instrument,Qty.,Avg. cost,LTP\nGOOD,10,\"1,000.50\",1100\nCHEAP-EQ,5,40,41\n"
        assertEquals(listOf(Holding("GOOD", 10.0, 1000.5), Holding("CHEAP", 5.0, 40.0)), PortfolioAnalyzer.parseCsv(csv))
    }
}

class PlainEnglishTest {
    private val sectors = listOf("Technology", "Financial Services", "Healthcare", "Industrials")

    @Test fun phrasesAndNumbers() {
        val r = PlainEnglish.parse("Profitable small caps with low debt, ROE above 20 and rising margins in pharma", sectors)
        val metrics = r.filters.filterIsInstance<MetricFilter>().associateBy { it.metric }
        assertEquals(3.0, metrics["capCategory"]!!.value)
        assertEquals(Op.LT, metrics["de"]!!.op)
        assertEquals(MetricFilter("roe", Op.GT, 20.0), metrics["roe"])
        assertNotNull(metrics["opmChange"]); assertNotNull(metrics["netIncome"])
        assertEquals(SectorFilter(setOf("Healthcare")), r.filters.filterIsInstance<SectorFilter>().single())
        assertTrue(r.ignored.isEmpty(), "ignored ${r.ignored}")
    }

    @Test fun moreNumbers() {
        val r = PlainEnglish.parse("pe below 15, market cap over 10,000 cr and dividend yield between 2 and 5 in banks")
        val metrics = r.filters.filterIsInstance<MetricFilter>().associateBy { it.metric }
        assertEquals(MetricFilter("pe", Op.LT, 15.0), metrics["pe"])
        assertEquals(MetricFilter("mcap", Op.GT, 10000.0), metrics["mcap"])
        assertEquals(MetricFilter("divYield", Op.BETWEEN, 2.0, 5.0), metrics["divYield"])
    }
}
