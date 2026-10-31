package io.github.akrishna87.sievr.ui.stock

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAlert
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.akrishna87.sievr.SievrApp
import io.github.akrishna87.sievr.core.alerts.PriceAlert
import io.github.akrishna87.sievr.core.alerts.PriceCondition
import io.github.akrishna87.sievr.core.metrics.Metrics
import io.github.akrishna87.sievr.core.model.Market
import io.github.akrishna87.sievr.core.model.Stock
import io.github.akrishna87.sievr.core.technical.CandlePattern
import io.github.akrishna87.sievr.core.valuation.Valuation
import io.github.akrishna87.sievr.ui.LocalNav
import io.github.akrishna87.sievr.ui.components.BarList
import io.github.akrishna87.sievr.ui.components.ChangeText
import io.github.akrishna87.sievr.ui.components.EmptyState
import io.github.akrishna87.sievr.ui.components.Fmt
import io.github.akrishna87.sievr.ui.components.LineChart
import io.github.akrishna87.sievr.ui.components.MetricGrid
import io.github.akrishna87.sievr.ui.components.Pill
import io.github.akrishna87.sievr.ui.components.ScoreBar
import io.github.akrishna87.sievr.ui.components.SectionHeader
import io.github.akrishna87.sievr.ui.components.Series
import io.github.akrishna87.sievr.ui.components.SievrCard
import io.github.akrishna87.sievr.ui.components.stockTable
import io.github.akrishna87.sievr.ui.components.WithMarket
import io.github.akrishna87.sievr.ui.components.changeColor
import io.github.akrishna87.sievr.ui.theme.LocalMarketColors
import java.util.UUID

private val tabs = listOf("Overview", "Valuation", "Financials", "Technicals", "Peers", "Ownership")

@Composable
fun StockScreen(symbol: String) {
    val nav = LocalNav.current
    val store = SievrApp.instance.user
    val user by store.data.collectAsStateWithLifecycle()
    val watched = user.watchlists.any { symbol in it.symbols }
    var alertDialog by remember { mutableStateOf(false) }
    LaunchedEffect(symbol) { store.addRecent(symbol) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(symbol) },
            navigationIcon = { IconButton(onClick = { nav.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = {
                IconButton(onClick = { nav.compare(listOf(symbol)) }) { Icon(Icons.Default.CompareArrows, "Compare") }
                IconButton(onClick = { alertDialog = true }) { Icon(Icons.Default.AddAlert, "Price alert") }
                IconButton(onClick = { store.toggleWatch(symbol) }) {
                    Icon(if (watched) Icons.Default.Star else Icons.Default.StarBorder, if (watched) "Remove from watchlist" else "Add to watchlist",
                        tint = if (watched) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurface)
                }
            },
        )
    }) { padding ->
        Column(Modifier.padding(padding)) {
            WithMarket { market ->
                val stock = market[symbol]
                if (stock == null) EmptyState("Not found", "$symbol isn't in today's market data.")
                else {
                    StockBody(stock, market)
                    if (alertDialog) AlertDialogFor(stock) { alertDialog = false }
                }
            }
        }
    }
}

@Composable
private fun StockBody(stock: Stock, market: Market) {
    var tab by rememberSaveable { mutableStateOf(0) }
    Column {
        Header(stock)
        ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
            tabs.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) }) }
        }
        when (tab) {
            0 -> Overview(stock, market)
            1 -> ValuationTab(stock)
            2 -> FinancialsTab(stock)
            3 -> TechnicalsTab(stock, market)
            4 -> PeersTab(stock, market)
            5 -> OwnershipTab(stock)
        }
    }
}

@Composable
private fun Header(stock: Stock) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(stock.name, style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(Fmt.price(stock["price"]), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(8.dp))
            ChangeText(stock["chg1d"], Modifier.padding(bottom = 3.dp), bold = true)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill(stock.sector)
            if (stock.industry != stock.sector) Pill(stock.industry)
            Pill(Fmt.capCategory(stock["capCategory"]))
            stock.data.indices.filter { it == "NIFTY 50" || it == "NIFTY NEXT 50" }.forEach { Pill(it) }
        }
    }
}

private enum class Range(val label: String, val days: Int, val weekly: Boolean) {
    M1("1M", 21, false), M3("3M", 63, false), M6("6M", 126, false), Y1("1Y", 252, false), Y3("3Y", 156, true), Y5("5Y", 260, true), MAX("Max", 100_000, true)
}

@Composable
fun PriceChart(stock: Stock, market: Market, showAverages: Boolean = false) {
    var range by rememberSaveable { mutableStateOf(Range.Y1) }
    var scrub by remember { mutableStateOf<Int?>(null) }
    val snap = market.snapshot
    val (values, dates) = if (range.weekly) stock.data.w.takeLast(range.days) to snap.weeklyDates.takeLast(range.days)
    else stock.data.c.takeLast(range.days) to snap.dailyDates.takeLast(range.days)
    val first = values.firstOrNull { it != null }
    val last = values.lastOrNull { it != null }
    val color = if (first != null && last != null && last >= first) LocalMarketColors.current.up else LocalMarketColors.current.down
    val series = mutableListOf(Series(values, color))
    if (showAverages && !range.weekly) {
        val closes = stock.data.c
        fun sma(p: Int) = closes.indices.map { i ->
            if (i + 1 < p) null else closes.subList(i + 1 - p, i + 1).let { w -> if (w.any { it == null }) null else w.sumOf { it!! } / p }
        }.takeLast(range.days)
        series += Series(sma(50), Color(0xFF3B82F6), 1.5f)
        series += Series(sma(200), Color(0xFFF59E0B), 1.5f)
    }
    Column(Modifier.padding(horizontal = 16.dp)) {
        val i = scrub
        val caption = if (i != null && values.getOrNull(i) != null) "${Fmt.epochDay(dates[i])} · ${Fmt.price(values[i])}"
        else if (first != null && last != null) "${range.label}: " + Fmt.percent((last / first - 1) * 100, true) else ""
        Text(caption, style = MaterialTheme.typography.bodySmall, color = if (i == null && first != null && last != null) changeColor(last - first) else MaterialTheme.colorScheme.onSurfaceVariant)
        LineChart(series, height = 190.dp, onScrub = { scrub = it })
        if (showAverages && !range.weekly) Text("Blue: 50-day average · Amber: 200-day average", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            Range.entries.forEach { r -> FilterChip(range == r, { range = r }, { Text(r.label) }, Modifier.padding(end = 6.dp)) }
        }
    }
}

@Composable
private fun Overview(stock: Stock, market: Market) {
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Spacer(Modifier.height(8.dp)); PriceChart(stock, market) }
        item {
            SectionHeader("Sievr scores")
            Column(Modifier.padding(horizontal = 16.dp)) {
                ScoreBar("Overall", stock["sievrScore"])
                ScoreBar("Quality", stock["qualityScore"])
                ScoreBar("Value", stock["valueScore"])
                ScoreBar("Growth", stock["growthScore"])
                ScoreBar("Momentum", stock["momentumScore"])
                ScoreBar("Safety", stock["safetyScore"])
                Text("Percentile ranks against every stock in the market, 0–100.", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item { Highlights(stock) }
        item {
            SectionHeader("Key metrics")
            MetricGrid(metricPairs(stock, listOf("mcap", "pe", "industryPe", "pb", "roe", "roce", "de", "divYield", "eps", "high52", "low52", "beta",
                "salesCagr3", "profitCagr3", "opm", "fcfYield", "piotroski", "altmanZ")))
        }
        item {
            SectionHeader("Returns")
            MetricGrid(metricPairs(stock, listOf("ret1w", "ret1m", "ret3m", "ret6m", "ret1y", "ret3y", "ret5y", "relStrength", "volatility")))
        }
        if (stock["targetPrice"] != null) item {
            SectionHeader("Analysts")
            MetricGrid(metricPairs(stock, listOf("targetPrice", "targetUpside", "analysts", "rating")), columns = 2)
        }
        item {
            Text("Source: ${market.snapshot.source}. Data as of ${market.snapshot.asOf}. Not investment advice.",
                Modifier.padding(16.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Plain-language strengths and warnings, like a quick analyst checklist. */
@Composable
private fun Highlights(stock: Stock) {
    val good = mutableListOf<String>()
    val bad = mutableListOf<String>()
    fun v(id: String) = stock[id]
    v("roce")?.let { if (it >= 20) good += "High return on capital (${Fmt.percent(it)})" else if (it < 8) bad += "Low return on capital (${Fmt.percent(it)})" }
    v("de")?.let { if (it < 0.1) good += "Almost debt-free" else if (it > 1.5) bad += "High debt: ${Fmt.number(it)}× equity" }
    v("salesCagr3")?.let { if (it >= 15) good += "Sales growing ${Fmt.percent(it)} a year" else if (it < 0) bad += "Sales shrinking over 3 years" }
    v("profitCagr3")?.let { if (it >= 15) good += "Profit growing ${Fmt.percent(it)} a year" else if (it < 0) bad += "Profit falling over 3 years" }
    v("peVsIndustry")?.let { if (it < 0.8) good += "Cheaper than its industry on P/E" else if (it > 1.5) bad += "Expensive against its industry on P/E" }
    v("piotroski")?.let { if (it >= 7) good += "Strong Piotroski F-score (${it.toInt()}/9)" else if (it <= 3) bad += "Weak Piotroski F-score (${it.toInt()}/9)" }
    v("altmanZ")?.let { if (it < 1.8) bad += "Altman Z-score in the distress zone" }
    v("interestCoverage")?.let { if (it < 2) bad += "Interest barely covered by profits (${Fmt.number(it)}×)" }
    v("cashConversion")?.let { if (it >= 90) good += "Profits turn into cash" else if (it < 50) bad += "Weak cash conversion: ${Fmt.percent(it, decimals = 0)} of profit" }
    v("pledged")?.let { if (it > 20) bad += "Promoters have pledged ${Fmt.percent(it, decimals = 0)} of their shares" }
    v("promoter")?.let { if (it >= 60) good += "Promoters own ${Fmt.percent(it, decimals = 0)}" }
    v("divYield")?.let { if (it >= 3) good += "Dividend yield ${Fmt.percent(it)}" }
    v("priceVsSma200")?.let { if (it > 0) good += "Above its 200-day average" else bad += "Below its 200-day average" }
    v("opmChange")?.let { if (it >= 2) good += "Margins expanding" else if (it <= -3) bad += "Margins shrinking" }
    if (good.isEmpty() && bad.isEmpty()) return
    SectionHeader("At a glance")
    Column(Modifier.padding(horizontal = 16.dp)) {
        good.forEach { Text("✓  $it", style = MaterialTheme.typography.bodyMedium, color = LocalMarketColors.current.up) }
        bad.forEach { Text("!  $it", style = MaterialTheme.typography.bodyMedium, color = LocalMarketColors.current.down) }
    }
}

fun metricPairs(stock: Stock, ids: List<String>): List<Pair<String, String>> =
    ids.mapNotNull { id -> Metrics[id]?.let { it.label to Fmt.metric(id, stock[id]) } }

// ------------------------------------------------------------------ valuation

@Composable
private fun ValuationTab(stock: Stock) {
    val price = stock["price"]
    val shares = stock["mcap"]?.let { m -> price?.let { if (it > 0) m / it else null } }
    val fin = stock.data.fin
    val fcfHistory = fin.takeLast(3).mapNotNull { f -> if (f.operatingCashFlow != null && f.capex != null) f.operatingCashFlow!! - f.capex!! else null }
    val baseFcf = (if (fcfHistory.isNotEmpty()) fcfHistory.average() else stock["fcf"]) ?: 0.0
    val defaultGrowth = (stock["salesCagr3"] ?: stock["profitCagr3"] ?: 8.0).coerceIn(0.0, 20.0)

    var g1 by remember(stock.symbol) { mutableFloatStateOf(defaultGrowth.toFloat()) }
    var g2 by remember(stock.symbol) { mutableFloatStateOf((defaultGrowth / 2).toFloat()) }
    var terminal by remember(stock.symbol) { mutableFloatStateOf(4f) }
    var discount by remember(stock.symbol) { mutableFloatStateOf(12f) }
    var base by remember(stock.symbol) { mutableStateOf(if (baseFcf > 0) "%.0f".format(baseFcf) else "") }

    val baseValue = base.toDoubleOrNull()
    val dcf = if (baseValue != null && shares != null) Valuation.dcf(
        Valuation.DcfInputs(baseValue, g1.toDouble(), g2.toDouble(), terminal.toDouble(), discount.toDouble(), stock["netCash"] ?: 0.0, shares),
    ) else null
    val implied = if (baseValue != null && stock["mcap"] != null) Valuation.impliedGrowth(stock["mcap"]!!, baseValue, stock["netCash"] ?: 0.0, discount.toDouble(), terminal.toDouble()) else null
    val graham = stock["grahamNumber"]
    val grahamGrowth = Valuation.grahamGrowthValue(stock["eps"], stock["profitCagr3"] ?: stock["epsCagr3"])
    val lynch = Valuation.lynchValue(stock["eps"], stock["profitCagr3"] ?: stock["epsCagr3"])

    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        item {
            Text("Fair value estimates", style = MaterialTheme.typography.titleMedium)
            Text("Each model is a rough guide built on assumptions; change them below.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            FairValueRow("Your DCF", dcf?.valuePerShare, price)
            FairValueRow("Graham number", graham, price)
            FairValueRow("Graham growth formula", grahamGrowth, price)
            FairValueRow("Peter Lynch (P/E = growth)", lynch, price)
            FairValueRow("Analyst target", stock["targetPrice"], price)
        }
        item {
            Spacer(Modifier.height(16.dp))
            SievrCard {
                Column(Modifier.padding(12.dp)) {
                    Text("Discounted cash flow", style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(base, { base = it.filter { c -> c.isDigit() || c == '.' } }, Modifier.fillMaxWidth(),
                        label = { Text("Starting free cash flow (₹ Cr/yr)") }, singleLine = true,
                        supportingText = { Text(if (fcfHistory.isNotEmpty()) "Default: 3-year average free cash flow" else "No cash-flow history; enter your own") })
                    SliderRow("Growth, years 1–5", g1, -10f..40f) { g1 = it }
                    SliderRow("Growth, years 6–10", g2, -10f..30f) { g2 = it }
                    SliderRow("Growth after year 10", terminal, 0f..8f) { terminal = it }
                    SliderRow("Discount rate (your required return)", discount, 8f..20f) { discount = it }
                    if (dcf != null) {
                        Text("Value per share ${Fmt.price(dcf.valuePerShare)} · ${Fmt.percent(dcf.terminalShare * 100, decimals = 0)} of it comes after year 10",
                            style = MaterialTheme.typography.bodySmall)
                    } else Text("Needs positive cash flow and a discount rate above long-term growth.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error)
                }
            }
        }
        item {
            Spacer(Modifier.height(16.dp))
            SievrCard {
                Column(Modifier.padding(12.dp)) {
                    Text("Reverse DCF: what the price expects", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (implied != null) "At today's price the market expects free cash flow to grow about ${Fmt.percent(implied)} a year for 10 years " +
                            "(at a ${discount.toInt()}% discount rate). Compare that with the company's past growth of ${Fmt.percent(stock["salesCagr3"])} in sales."
                        else "Not enough data to work this out.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        item {
            SectionHeader("Valuation ratios")
            MetricGrid(metricPairs(stock, listOf("pe", "fpe", "industryPe", "peVsIndustry", "pb", "ps", "evEbitda", "peg", "earningsYield", "fcfYield", "dcfValue", "magicRank")))
        }
    }
}

@Composable
private fun FairValueRow(label: String, value: Double?, price: Double?) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(Fmt.price(value), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        Spacer(Modifier.width(8.dp))
        val upside = if (value != null && price != null && price > 0) (value / price - 1) * 100 else null
        Text(upside?.let { Fmt.percent(it, true, 0) } ?: "", Modifier.width(64.dp), textAlign = TextAlign.End, color = changeColor(upside),
            style = MaterialTheme.typography.bodySmall)
    }
    HorizontalDivider()
}

@Composable
private fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column(Modifier.padding(top = 6.dp)) {
        Row {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            Text("%.1f%%".format(value), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
        }
        Slider(value, { onChange((it * 2).toInt() / 2f) }, valueRange = range)
    }
}

// ------------------------------------------------------------------ financials

@Composable
private fun FinancialsTab(stock: Stock) {
    val fin = stock.data.fin
    if (fin.isEmpty()) {
        EmptyState("No statements yet", "Annual financial statements for ${stock.symbol} haven't been fetched. They fill in over the next few evenings.")
        return
    }
    val rows: List<Pair<String, (io.github.akrishna87.sievr.core.model.AnnualFinancials) -> Double?>> = listOf(
        "Revenue" to { it.revenue }, "Gross profit" to { it.grossProfit }, "EBITDA" to { it.ebitda }, "EBIT" to { it.ebit },
        "Interest" to { it.interest }, "Net profit" to { it.netIncome }, "EPS (₹)" to { it.eps },
        "Total assets" to { it.totalAssets }, "Equity" to { it.equity }, "Debt" to { it.debt }, "Cash" to { it.cash },
        "Operating cash flow" to { it.operatingCashFlow }, "Capex" to { it.capex },
        "Free cash flow" to { f -> if (f.operatingCashFlow != null && f.capex != null) f.operatingCashFlow!! - f.capex!! else null },
        "Dividends paid" to { it.dividendsPaid },
    )
    val scroll = rememberScrollState()
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            SectionHeader("Revenue and profit (₹ Cr)")
            Column(Modifier.padding(horizontal = 16.dp)) {
                BarList(fin.mapNotNull { f -> f.revenue?.let { "FY${f.year % 100} sales" to it } }, { Fmt.indian(it, 0) })
                BarList(fin.mapNotNull { f -> f.netIncome?.let { "FY${f.year % 100} profit" to it } }, { Fmt.indian(it, 0) },
                    color = MaterialTheme.colorScheme.tertiary)
            }
        }
        item {
            SectionHeader("Annual statements (₹ Cr)")
            Row(Modifier.padding(horizontal = 16.dp)) {
                Column(Modifier.width(140.dp)) {
                    Text("", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(vertical = 6.dp))
                    rows.forEach { (label, _) -> Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 6.dp), maxLines = 1) }
                }
                Row(Modifier.horizontalScroll(scroll)) {
                    fin.forEach { f ->
                        Column(Modifier.width(88.dp), horizontalAlignment = Alignment.End) {
                            Text("FY${f.year}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(vertical = 6.dp),
                                color = MaterialTheme.colorScheme.primary)
                            rows.forEach { (label, get) ->
                                val v = get(f)
                                Text(v?.let { if (label.startsWith("EPS")) Fmt.number(it) else Fmt.indian(it, 0) } ?: "—",
                                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 6.dp), maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
        item {
            SectionHeader("Ratios")
            MetricGrid(metricPairs(stock, listOf("roe", "roe3", "roce", "roceMin3", "roa", "opm", "npm", "gpm", "opmChange",
                "salesCagr3", "profitCagr3", "epsCagr3", "revenueGrowth", "earningsGrowth", "cashConversion",
                "currentRatio", "interestCoverage", "netCash", "piotroski", "altmanZ", "payout")))
        }
    }
}

// ------------------------------------------------------------------ technicals

@Composable
private fun TechnicalsTab(stock: Stock, market: Market) {
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Spacer(Modifier.height(8.dp)); PriceChart(stock, market, showAverages = true) }
        item {
            SectionHeader("Signals")
            Column(Modifier.padding(horizontal = 16.dp)) {
                val rsi = stock["rsi14"]
                Signal("RSI (14)", Fmt.number(rsi, 1), when { rsi == null -> null; rsi < 30 -> "Oversold"; rsi > 70 -> "Overbought"; else -> "Neutral" })
                Signal("Trend", Fmt.percent(stock["priceVsSma200"], true) + " vs 200-day",
                    if ((stock["priceVsSma200"] ?: 0.0) > 0 && (stock["priceVsSma50"] ?: 0.0) > 0) "Uptrend"
                    else if ((stock["priceVsSma200"] ?: 0.0) < 0 && (stock["priceVsSma50"] ?: 0.0) < 0) "Downtrend" else "Mixed")
                Signal("MACD", Fmt.number(stock["macdHist"]) + " histogram", if ((stock["macdHist"] ?: 0.0) > 0) "Bullish" else "Bearish")
                if (stock["goldenCross"] == 1.0) Signal("Golden cross", "50-day crossed above 200-day", "Bullish")
                if (stock["deathCross"] == 1.0) Signal("Death cross", "50-day crossed below 200-day", "Bearish")
                Signal("Volume", Fmt.metric("volumeRatio", stock["volumeRatio"]) + " the 20-day average", null)
            }
        }
        item {
            SectionHeader("Candlestick patterns today")
            val found = CandlePattern.entries.filter { it.metricId in stock.patterns }
            Column(Modifier.padding(horizontal = 16.dp)) {
                if (found.isEmpty()) Text("No pattern on the last bar.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                found.forEach { p ->
                    Text(p.label + when (p.bullish) { true -> " · bullish"; false -> " · bearish"; null -> "" }, style = MaterialTheme.typography.bodyLarge,
                        color = when (p.bullish) { true -> LocalMarketColors.current.up; false -> LocalMarketColors.current.down; null -> MaterialTheme.colorScheme.onSurface })
                    Text(p.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
        item {
            SectionHeader("Indicators")
            MetricGrid(metricPairs(stock, listOf("sma20", "sma50", "sma200", "priceVsSma50", "priceVsSma200", "rsi14", "macd", "macdSignal",
                "high52", "low52", "fromHigh52", "fromLow52", "volume", "avgVolume20", "turnover", "volatility", "atrPct", "beta")))
        }
    }
}

@Composable
private fun Signal(label: String, value: String, verdict: String?) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (verdict != null) Pill(verdict,
            color = when (verdict) {
                "Uptrend", "Bullish", "Oversold" -> LocalMarketColors.current.up.copy(alpha = 0.15f)
                "Downtrend", "Bearish", "Overbought" -> LocalMarketColors.current.down.copy(alpha = 0.15f)
                else -> MaterialTheme.colorScheme.secondaryContainer
            })
    }
}

// ------------------------------------------------------------------ peers

@Composable
private fun PeersTab(stock: Stock, market: Market) {
    val nav = LocalNav.current
    val peers = remember(stock, market) { market.peers(stock, 15) }
    val all = listOf(stock) + peers
    val cols = listOf("mcap", "pe", "pb", "roe", "roce", "salesCagr3", "de", "divYield", "ret1y", "sievrScore")
    val scroll = rememberScrollState()
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Text("${stock.industry}: ${peers.size} peers by market cap", Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { nav.compare(all.take(4).map { it.symbol }) }, Modifier.padding(start = 8.dp)) { Text("Compare side by side") }
        }
        stockTable(all, cols, scroll, null, true, onSort = {}, onOpen = { if (it.symbol != stock.symbol) nav.stock(it.symbol) })
        item {
            SectionHeader("Industry medians")
            val med = cols.map { id ->
                val v = all.mapNotNull { it[id] }.sorted()
                Metrics[id]!!.label to Fmt.metric(id, if (v.isEmpty()) null else v[v.size / 2])
            }
            MetricGrid(med)
        }
    }
}

// ------------------------------------------------------------------ ownership

@Composable
private fun OwnershipTab(stock: Stock) {
    val h = stock.data.holding
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            SectionHeader("Shareholding")
            val parts = listOfNotNull(
                (if (h?.promoter != null) "Promoters" else "Insiders / promoters") to stock["promoter"],
                "FIIs" to stock["fii"], "DIIs" to stock["dii"],
                "Institutions (all)" to (if (h?.fii == null) stock["institutions"] else null),
                "Public" to h?.public,
            ).filter { it.second != null }.map { it.first to it.second!! }
            if (parts.isEmpty()) Text("No shareholding data yet.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            else Column(Modifier.padding(horizontal = 16.dp)) { BarList(parts, { Fmt.percent(it) }) }
        }
        item {
            SectionHeader("Changes last quarter")
            MetricGrid(metricPairs(stock, listOf("promoterChange", "fiiChange", "diiChange", "pledged")), columns = 2)
            if (h == null) Text(
                "Quarterly promoter, FII, DII and pledge figures come from NSE shareholding filings and are being added to the data feed. " +
                    "Until then Sievr shows insider and institutional holdings.",
                Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ------------------------------------------------------------------ alerts

@Composable
private fun AlertDialogFor(stock: Stock, onDismiss: () -> Unit) {
    val store = SievrApp.instance.user
    var condition by remember { mutableStateOf(PriceCondition.ABOVE) }
    val price = stock["price"] ?: 0.0
    var value by remember { mutableStateOf("%.0f".format(price * 1.05)) }
    val needsValue = condition !in setOf(PriceCondition.NEW_52W_HIGH, PriceCondition.NEW_52W_LOW)
    val labels = mapOf(
        PriceCondition.ABOVE to "Price above", PriceCondition.BELOW to "Price below",
        PriceCondition.MOVE_UP_PERCENT to "Up % in a day", PriceCondition.MOVE_DOWN_PERCENT to "Down % in a day",
        PriceCondition.RSI_BELOW to "RSI below", PriceCondition.RSI_ABOVE to "RSI above",
        PriceCondition.NEW_52W_HIGH to "New 52W high", PriceCondition.NEW_52W_LOW to "New 52W low",
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Alert for ${stock.symbol}") },
        text = {
            Column {
                FlowRow {
                    PriceCondition.entries.forEach { c ->
                        FilterChip(condition == c, {
                            condition = c
                            value = when (c) {
                                PriceCondition.ABOVE -> "%.0f".format(price * 1.05)
                                PriceCondition.BELOW -> "%.0f".format(price * 0.95)
                                PriceCondition.MOVE_UP_PERCENT, PriceCondition.MOVE_DOWN_PERCENT -> "5"
                                PriceCondition.RSI_BELOW -> "30"
                                PriceCondition.RSI_ABOVE -> "70"
                                else -> ""
                            }
                        }, { Text(labels.getValue(c)) }, Modifier.padding(end = 6.dp))
                    }
                }
                if (needsValue) OutlinedTextField(value, { value = it }, label = { Text("Value") }, singleLine = true)
                Text("Checked each evening when new data arrives. Last close ${Fmt.price(price)}.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(enabled = !needsValue || value.toDoubleOrNull() != null, onClick = {
                store.addAlert(PriceAlert(UUID.randomUUID().toString(), stock.symbol, condition, value.toDoubleOrNull() ?: 0.0))
                onDismiss()
            }) { Text("Create alert") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
