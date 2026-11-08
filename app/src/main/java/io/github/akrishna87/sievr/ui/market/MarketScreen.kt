package io.github.akrishna87.sievr.ui.market

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.akrishna87.sievr.SievrApp
import io.github.akrishna87.sievr.core.model.IndexData
import io.github.akrishna87.sievr.core.model.Market
import io.github.akrishna87.sievr.core.screen.Presets
import io.github.akrishna87.sievr.data.MarketState
import io.github.akrishna87.sievr.ui.LocalNav
import io.github.akrishna87.sievr.ui.Routes
import io.github.akrishna87.sievr.ui.components.ChangeText
import io.github.akrishna87.sievr.ui.components.Fmt
import io.github.akrishna87.sievr.ui.components.LineChart
import io.github.akrishna87.sievr.ui.components.RefreshBar
import io.github.akrishna87.sievr.ui.components.SectionHeader
import io.github.akrishna87.sievr.ui.components.Series
import io.github.akrishna87.sievr.ui.components.SievrCard
import io.github.akrishna87.sievr.ui.components.Sparkline
import io.github.akrishna87.sievr.ui.components.StockRow
import io.github.akrishna87.sievr.ui.components.StockSearch
import io.github.akrishna87.sievr.ui.components.WithMarket
import io.github.akrishna87.sievr.ui.components.changeColor
import io.github.akrishna87.sievr.ui.theme.LocalMarketColors
import kotlinx.coroutines.launch

@Composable
fun MarketScreen() {
    val app = SievrApp.instance
    val scope = rememberCoroutineScope()
    var searching by remember { mutableStateOf(false) }
    val user by app.user.data.collectAsStateWithLifecycle()
    val nav = LocalNav.current
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Sievr") },
            actions = {
                IconButton(onClick = { searching = true }) { Icon(Icons.Default.Search, "Search stocks") }
                IconButton(onClick = { scope.launch { app.market.refresh(force = false) } }) { Icon(Icons.Default.Refresh, "Check for new data") }
            },
        )
    }) { padding ->
        Column(Modifier.padding(padding)) {
            RefreshBar()
            WithMarket { market ->
                MarketBody(market)
                if (searching) StockSearch(market, onDismiss = { searching = false }, recent = user.recentSymbols) {
                    searching = false
                    nav.stock(it.symbol)
                }
            }
        }
    }
}


@Composable
private fun MarketBody(market: Market) {
    val nav = LocalNav.current
    val state by SievrApp.instance.market.state.collectAsStateWithLifecycle()
    val st = rememberMoversState()
    var chartIndex by rememberSaveable { mutableStateOf(0) }
    val app = SievrApp.instance
    val mf by app.mf.state.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(st.asset) { if (st.asset == Asset.FUNDS) app.mf.start() }
    var fundInfo by remember { mutableStateOf<io.github.akrishna87.sievr.core.mf.MfScheme?>(null) }
    val moverList = remember(market, st.asset, st.kind, st.period, st.cap, st.count, st.sort, st.desc) {
        if (st.asset == Asset.FUNDS) emptyList() else stockMovers(market, st)
    }
    val fundList = remember(mf.data, st.asset, st.kind, st.mfClass, st.count, st.sort, st.desc) {
        if (st.asset != Asset.FUNDS) emptyList() else fundMovers(mf.data?.schemes.orEmpty(), st)
    }

    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text("Closing data for ${Fmt.isoDate(market.snapshot.asOf)}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                (state as? MarketState.Ready)?.error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
        if (market.snapshot.indices.isNotEmpty()) item {
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                items(market.snapshot.indices.size) { i ->
                    IndexCard(market.snapshot.indices[i], selected = i == chartIndex) { chartIndex = i }
                }
            }
        }
        market.snapshot.indices.getOrNull(chartIndex)?.let { index ->
            item { IndexChart(index, market.snapshot.dailyDates) }
        }
        item { Breadth(market) }
        item { SectionHeader("Top movers") }
        item { MoversControls(st) }
        if (st.asset == Asset.FUNDS) {
            if (fundList.isEmpty()) item {
                Text(
                    when {
                        mf.data == null && mf.error != null -> "Couldn't load fund NAVs: ${mf.error}"
                        mf.data == null -> "Loading the latest NAVs…"
                        else -> "No changes yet: a day's move needs two days of NAVs."
                    },
                    Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                item {
                    Text("Growth plans, by the last day's NAV change · NAVs for ${Fmt.isoDate(fundList.first().date)}",
                        Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(fundList, key = { "f" + it.code }) { s -> FundMoverRow(s) { fundInfo = s } }
            }
        } else {
            if (moverList.isEmpty()) item {
                Text("None today.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(moverList, key = { "m" + it.symbol }) { s ->
                StockRow(s, onClick = { nav.stock(s.symbol) }, trailingMetric = trailingFor(st))
            }
        }
        item { SectionHeader("Sectors today", action = "All sectors", onAction = { nav.go(Routes.SECTORS) }) }
        item { SectorHeatmap(market) }
        item { SectionHeader("Popular screens", action = "See all", onAction = { nav.go(Routes.SCREENS) }) }
        item {
            val picks = listOf("magic-formula", "coffee-can", "undervalued-quality", "breakout-volume", "golden-cross", "dividend-steady")
                .mapNotNull { Presets[it] }
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp)) {
                items(picks, key = { it.id }) { screen ->
                    val count = remember(market, screen) {
                        io.github.akrishna87.sievr.core.screen.ScreenEngine.run(screen, market.stocks).size
                    }
                    SievrCard(Modifier.width(200.dp).padding(4.dp), onClick = { nav.screen(screen, saved = false) }) {
                        Column(Modifier.padding(12.dp)) {
                            Text(screen.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(screen.description, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(6.dp))
                            Text("$count stocks", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
    fundInfo?.let { s ->
        val user by app.user.data.collectAsStateWithLifecycle()
        val list = user.watchlists.first()
        val watched = s.code in list.funds
        io.github.akrishna87.sievr.ui.watchlist.FundInfoDialog(s, onDismiss = { fundInfo = null },
            actionLabel = if (watched) "Remove from ${list.name}" else "Add to ${list.name}") {
            app.user.toggleWatchFund(s.code, list.id); fundInfo = null
        }
    }
}

@Composable
private fun IndexCard(index: IndexData, selected: Boolean, onClick: () -> Unit) {
    val closes = index.c.filterNotNull()
    val last = closes.lastOrNull()
    val chg = if (closes.size >= 2) (closes.last() / closes[closes.size - 2] - 1) * 100 else null
    SievrCard(Modifier.width(170.dp).padding(4.dp), onClick = onClick) {
        Column(Modifier.padding(12.dp)) {
            Text(index.name, style = MaterialTheme.typography.labelSmall,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Text(last?.let { Fmt.indian(it, 2) } ?: "—", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChangeText(chg, Modifier.weight(1f))
                Sparkline(closes.takeLast(30), Modifier.width(48.dp).height(20.dp))
            }
        }
    }
}

@Composable
private fun IndexChart(index: IndexData, dates: List<Int>) {
    var scrub by remember { mutableStateOf<Int?>(null) }
    val values = index.c
    val first = values.firstOrNull { it != null }
    val last = values.lastOrNull { it != null }
    val color = if (first != null && last != null && last >= first) LocalMarketColors.current.up else LocalMarketColors.current.down
    Column(Modifier.padding(horizontal = 16.dp)) {
        val i = scrub
        val label = if (i != null && values.getOrNull(i) != null) "${Fmt.epochDay(dates[i])}: ${Fmt.indian(values[i]!!, 2)}"
        else "${index.name}, last ${values.size} sessions" + (if (first != null && last != null) " · " + Fmt.percent((last / first - 1) * 100, true) else "")
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LineChart(listOf(Series(values, color)), height = 160.dp, onScrub = { scrub = it })
    }
}

private data class BreadthStats(val up: Int, val down: Int, val highs: Int, val lows: Int, val above200: Double)

@Composable
private fun Breadth(market: Market) {
    val stats = remember(market) {
        val s = market.stocks
        BreadthStats(
            up = s.count { (it["chg1d"] ?: 0.0) > 0 },
            down = s.count { (it["chg1d"] ?: 0.0) < 0 },
            highs = s.count { it["newHigh52"] == 1.0 },
            lows = s.count { it["newLow52"] == 1.0 },
            above200 = s.count { (it["priceVsSma200"] ?: -1.0) > 0 } * 100.0 / maxOf(1, s.count { it["priceVsSma200"] != null }),
        )
    }
    SievrCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text("Market breadth", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            val total = (stats.up + stats.down).coerceAtLeast(1)
            Row(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp))) {
                if (stats.up > 0) Box(Modifier.weight(stats.up.toFloat() / total).height(10.dp).background(LocalMarketColors.current.up))
                if (stats.down > 0) Box(Modifier.weight(stats.down.toFloat() / total).height(10.dp).background(LocalMarketColors.current.down))
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth()) {
                Text("${stats.up} advancing", style = MaterialTheme.typography.bodySmall, color = LocalMarketColors.current.up, modifier = Modifier.weight(1f))
                Text("${stats.down} declining", style = MaterialTheme.typography.bodySmall, color = LocalMarketColors.current.down)
            }
            Spacer(Modifier.height(4.dp))
            Text("${stats.highs} new 52-week highs · ${stats.lows} new lows · ${"%.0f".format(stats.above200)}% above their 200-day average",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Market-cap-weighted performance of each sector over a period. */
fun sectorPerformance(market: Market, metric: String = "chg1d"): List<Triple<String, Double, Int>> =
    market.stocks.filter { it[metric] != null && it["mcap"] != null }
        .groupBy { it.sector }
        .map { (sector, list) ->
            val w = list.sumOf { it["mcap"]!! }
            Triple(sector, list.sumOf { it[metric]!! * it["mcap"]!! } / w, list.size)
        }
        .filter { it.third >= 3 }
        .sortedByDescending { it.second }

@Composable
private fun SectorHeatmap(market: Market) {
    val nav = LocalNav.current
    val sectors = remember(market) { sectorPerformance(market) }
    FlowRow(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.Start) {
        sectors.forEach { (name, chg, count) ->
            Box(
                Modifier.padding(4.dp).clip(RoundedCornerShape(8.dp)).background(heat(chg))
                    .clickable { nav.go(Routes.SECTORS) }.padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Column {
                    Text(name, style = MaterialTheme.typography.labelSmall, color = Color.White, maxLines = 1)
                    Text(Fmt.percent(chg, signed = true, decimals = 2), style = MaterialTheme.typography.bodySmall,
                        color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun heat(change: Double): Color {
    val t = (kotlin.math.abs(change) / 3.0).coerceIn(0.0, 1.0).toFloat()
    val base = Color(0xFF64748B)
    return lerp(base, if (change >= 0) Color(0xFF15803D) else Color(0xFFB91C1C), 0.35f + 0.65f * t)
}

/** Sector table with returns over several periods; tap a sector to screen it. */
@Composable
fun SectorsScreen() {
    val nav = LocalNav.current
    var period by rememberSaveable { mutableStateOf("chg1d") }
    val periods = listOf("chg1d" to "1D", "ret1w" to "1W", "ret1m" to "1M", "ret3m" to "3M", "ret1y" to "1Y")
    Scaffold(topBar = {
        TopAppBar(title = { Text("Sectors") }, navigationIcon = {
            IconButton(onClick = { nav.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        })
    }) { padding ->
        Column(Modifier.padding(padding)) {
            WithMarket { market ->
                val rows = remember(market, period) { sectorPerformance(market, period) }
                LazyColumn {
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp)) {
                            items(periods) { (id, label) -> FilterChip(period == id, { period = id }, { Text(label) }, Modifier.padding(4.dp)) }
                        }
                    }
                    items(rows, key = { it.first }) { (sector, chg, count) ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                nav.screen(
                                    io.github.akrishna87.sievr.core.screen.Screen(
                                        id = "sector-" + sector.lowercase().replace(' ', '-'), name = sector,
                                        filters = listOf(io.github.akrishna87.sievr.core.screen.SectorFilter(setOf(sector))),
                                        sortBy = period, sortDescending = true,
                                    ), saved = false,
                                )
                            }.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(sector, style = MaterialTheme.typography.bodyLarge)
                                Text("$count stocks", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(Fmt.percent(chg, true, 2), color = changeColor(chg), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}
