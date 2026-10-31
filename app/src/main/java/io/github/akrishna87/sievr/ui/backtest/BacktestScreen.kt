package io.github.akrishna87.sievr.ui.backtest

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.akrishna87.sievr.core.backtest.Backtester
import io.github.akrishna87.sievr.core.model.Market
import io.github.akrishna87.sievr.core.screen.Screen
import io.github.akrishna87.sievr.ui.LocalNav
import io.github.akrishna87.sievr.ui.ScreenDraft
import io.github.akrishna87.sievr.ui.components.CenterMessage
import io.github.akrishna87.sievr.ui.components.Fmt
import io.github.akrishna87.sievr.ui.components.LineChart
import io.github.akrishna87.sievr.ui.components.MetricGrid
import io.github.akrishna87.sievr.ui.components.SectionHeader
import io.github.akrishna87.sievr.ui.components.Series
import io.github.akrishna87.sievr.ui.components.WithMarket
import io.github.akrishna87.sievr.ui.components.changeColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun BacktestScreen() {
    val nav = LocalNav.current
    val screen = remember { ScreenDraft.screen }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Backtest: ${screen?.name ?: ""}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = { IconButton(onClick = { nav.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        )
    }) { padding ->
        Column(Modifier.padding(padding)) {
            if (screen == null) CenterMessage { Text("No screen to test.") }
            else WithMarket { market -> BacktestBody(screen, market) }
        }
    }
}

@Composable
private fun BacktestBody(screen: Screen, market: Market) {
    var years by rememberSaveable { mutableStateOf(5) }
    var rebalance by rememberSaveable { mutableStateOf(4) }
    var holdings by rememberSaveable { mutableStateOf(20) }
    var result by remember { mutableStateOf<Backtester.Result?>(null) }
    var running by remember { mutableStateOf(true) }
    var scrub by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(screen, years, rebalance, holdings, market) {
        running = true
        result = withContext(Dispatchers.Default) {
            Backtester.run(screen, market, Backtester.Settings(years = years, rebalanceWeeks = rebalance, maxHoldings = holdings))
        }
        running = false
    }

    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text("Period", style = MaterialTheme.typography.labelSmall)
                FlowRow { listOf(1, 3, 5).forEach { y -> FilterChip(years == y, { years = y }, { Text("${y}Y") }, Modifier.padding(end = 6.dp)) } }
                Text("Rebalance", style = MaterialTheme.typography.labelSmall)
                FlowRow {
                    listOf(1 to "Weekly", 4 to "Monthly", 13 to "Quarterly", 26 to "Half-yearly").forEach { (w, label) ->
                        FilterChip(rebalance == w, { rebalance = w }, { Text(label) }, Modifier.padding(end = 6.dp))
                    }
                }
                Text("Stocks held (equal weight)", style = MaterialTheme.typography.labelSmall)
                FlowRow { listOf(5, 10, 20, 30).forEach { n -> FilterChip(holdings == n, { holdings = n }, { Text("$n") }, Modifier.padding(end = 6.dp)) } }
            }
        }
        if (running) item {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.width(20.dp).height(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text("Replaying ${years * 52} weeks of prices…")
            }
        }
        val r = result
        if (!running && r == null) item {
            Text("Not enough price history to test this screen.", Modifier.padding(16.dp))
        }
        if (r != null) {
            item {
                SectionHeader("Growth of ₹1 lakh")
                val i = scrub
                val point = i?.let { r.curve.getOrNull(it) }
                Text(
                    if (point != null) "${Fmt.epochDay(point.epochDay)}: strategy ${Fmt.price(point.strategy * 1e5)}" +
                        (point.benchmark?.let { " · ${r.benchmarkName} ${Fmt.price(it * 1e5)}" } ?: "")
                    else "Strategy ${Fmt.price(r.curve.last().strategy * 1e5)}" + (r.curve.last().benchmark?.let { " vs ${r.benchmarkName} ${Fmt.price(it * 1e5)}" } ?: ""),
                    Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall,
                )
                LineChart(
                    listOf(
                        Series(r.curve.map { it.strategy }, MaterialTheme.colorScheme.primary, 2.5f),
                        Series(r.curve.map { it.benchmark }, Color(0xFF94A3B8), 1.5f, dashed = true),
                    ),
                    Modifier.padding(horizontal = 16.dp), height = 220.dp, fill = false, baseline = 1.0, onScrub = { scrub = it },
                )
                Text("Solid: this screen · Dashed: ${r.benchmarkName}", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                SectionHeader("Results")
                MetricGrid(listOf(
                    "CAGR" to Fmt.percent(r.cagr, true),
                    "${r.benchmarkName} CAGR" to Fmt.percent(r.benchmarkCagr, true),
                    "Total return" to Fmt.percent(r.totalReturn, true, 0),
                    "Max drawdown" to Fmt.percent(-r.maxDrawdown),
                    "Volatility" to Fmt.percent(r.volatility),
                    "Sharpe ratio" to Fmt.number(r.sharpe),
                    "Periods beating index" to Fmt.percent(r.winRate, decimals = 0),
                    "Avg stocks held" to Fmt.number(r.averageHoldings, 1),
                    "Rebalances" to "${r.rebalances.size}",
                ))
                val beat = r.benchmarkCagr?.let { r.cagr - it }
                if (beat != null) Text(
                    "The screen ${if (beat >= 0) "beat" else "trailed"} the ${r.benchmarkName} by ${Fmt.percent(kotlin.math.abs(beat))} a year, after 0.4% costs per trade.",
                    Modifier.padding(16.dp), color = changeColor(beat), fontWeight = FontWeight.Medium,
                )
            }
            item {
                SectionHeader("Read this before trusting the result")
                Column(Modifier.padding(horizontal = 16.dp)) {
                    if (r.lookAheadMetrics.isNotEmpty()) Text(
                        "• ${r.lookAheadMetrics.joinToString()} ${if (r.lookAheadMetrics.size == 1) "uses its" else "use their"} values as they are today, not as they were then " +
                            "(look-ahead bias). Price-based metrics are worked out as of each past date.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text("• Only stocks listed today are included, so companies that failed or delisted are missing (survivorship bias).", style = MaterialTheme.typography.bodySmall)
                    Text("• Past results don't predict future returns.", style = MaterialTheme.typography.bodySmall)
                }
            }
            item { SectionHeader("Latest picks") }
            items(r.rebalances.takeLast(6).reversed(), key = { it.epochDay }) { rb ->
                Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Text(Fmt.epochDay(rb.epochDay), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text(rb.holdings.ifEmpty { listOf("— nothing passed —") }.joinToString(", "), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
