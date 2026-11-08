package io.github.akrishna87.sievr.ui.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.akrishna87.sievr.BuildConfig
import io.github.akrishna87.sievr.SievrApp
import io.github.akrishna87.sievr.core.alerts.AlertEngine
import io.github.akrishna87.sievr.core.metrics.Category
import io.github.akrishna87.sievr.core.metrics.Metrics
import io.github.akrishna87.sievr.ui.LocalNav
import io.github.akrishna87.sievr.ui.Routes
import io.github.akrishna87.sievr.ui.components.EmptyState
import io.github.akrishna87.sievr.ui.components.Fmt
import io.github.akrishna87.sievr.ui.components.LineChart
import io.github.akrishna87.sievr.ui.components.SectionHeader
import io.github.akrishna87.sievr.ui.components.SievrCard
import io.github.akrishna87.sievr.ui.components.Series
import io.github.akrishna87.sievr.ui.components.StockSearch
import io.github.akrishna87.sievr.ui.components.WithMarket
import io.github.akrishna87.sievr.ui.components.formulaName
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.launch

@Composable
private fun BackScaffold(title: String, content: @Composable (Modifier) -> Unit) {
    val nav = LocalNav.current
    Scaffold(topBar = {
        TopAppBar(title = { Text(title) }, navigationIcon = { IconButton(onClick = { nav.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } })
    }) { padding -> content(Modifier.padding(padding)) }
}

@Composable
fun MoreScreen() {
    val nav = LocalNav.current
    val market = SievrApp.instance.market
    val state by market.state.collectAsStateWithLifecycle()
    Scaffold(topBar = { TopAppBar(title = { Text("More") }) }) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            item { MoreItem(Icons.Default.Notifications, "Alerts", "Price, RSI and 52-week alerts; screen alerts") { nav.go(Routes.ALERTS) } }
            item { MoreItem(Icons.AutoMirrored.Filled.CompareArrows, "Compare stocks", "Up to four side by side") { nav.compare() } }
            item { MoreItem(Icons.Default.ViewModule, "Sectors", "Performance by sector over 1 day to 1 year") { nav.go(Routes.SECTORS) } }
            item { MoreItem(Icons.Default.Functions, "Formula guide", "Every metric name for custom screens") { nav.go(Routes.FORMULA_HELP) } }
            item { MoreItem(Icons.Default.Settings, "Settings", "Theme, notifications, data") { nav.go(Routes.SETTINGS) } }
            item { MoreItem(Icons.Default.Info, "About Sievr", "Data sources, disclaimer, version") { nav.go(Routes.ABOUT) } }
            item { UpdateItem() }
            item {
                val m = (state as? io.github.akrishna87.sievr.data.MarketState.Ready)?.market
                Text(
                    if (m != null) "Data for ${Fmt.isoDate(m.snapshot.asOf)} · ${m.stocks.size} stocks" else "No market data yet",
                    Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MoreItem(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, null) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

// ------------------------------------------------------------------ alerts

@Composable
fun AlertsScreen() {
    val nav = LocalNav.current
    val store = SievrApp.instance.user
    val user by store.data.collectAsStateWithLifecycle()
    BackScaffold("Alerts") { modifier ->
        LazyColumn(modifier, contentPadding = PaddingValues(bottom = 24.dp)) {
            item { SectionHeader("Price alerts") }
            if (user.priceAlerts.isEmpty()) item {
                Text("Create one from the bell on any stock page.", Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(user.priceAlerts, key = { it.id }) { a ->
                ListItem(
                    headlineContent = { Text("${a.symbol} ${AlertEngine.describe(a)}") },
                    supportingContent = { Text(if (a.active) "Active" else "Triggered; switch on to arm again") },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(a.active, { store.setAlertActive(a.id, it) })
                            IconButton(onClick = { store.removeAlert(a.id) }) { Icon(Icons.Default.Close, "Delete") }
                        }
                    },
                    modifier = Modifier.clickable { nav.stock(a.symbol) },
                )
            }
            item { SectionHeader("Screen alerts") }
            val withAlerts = user.screens.filter { it.alerts }
            if (withAlerts.isEmpty()) item {
                Text("Open a screen and tap its bell to hear when stocks enter or leave it.", Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(withAlerts, key = { "s" + it.id }) { s ->
                ListItem(
                    headlineContent = { Text(s.name) },
                    supportingContent = { Text("${user.screenMatches[s.id]?.size ?: 0} stocks last time") },
                    trailingContent = { Switch(true, { store.saveScreen(s.copy(alerts = false)) }) },
                    modifier = Modifier.clickable { nav.screen(s, saved = true) },
                )
            }
            item { SectionHeader("Watchlist moves") }
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text("Tell me when a watchlist stock moves at least…", style = MaterialTheme.typography.bodyMedium)
                    FlowRow {
                        listOf(0.0 to "Off", 3.0 to "3%", 5.0 to "5%", 8.0 to "8%", 10.0 to "10%").forEach { (v, label) ->
                            FilterChip(user.watchMoveAlert == v, { store.update { it.copy(watchMoveAlert = v) } }, { Text(label) }, Modifier.padding(end = 6.dp))
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ compare

private val compareMetrics = listOf(
    "price", "chg1d", "mcap", "pe", "industryPe", "pb", "evEbitda", "peg", "divYield", "roe", "roce", "opm", "npm",
    "salesCagr3", "profitCagr3", "de", "interestCoverage", "fcfYield", "piotroski", "altmanZ", "promoter", "institutions",
    "ret1m", "ret6m", "ret1y", "ret3y", "volatility", "rsi14", "qualityScore", "valueScore", "growthScore", "momentumScore", "safetyScore", "sievrScore",
)
private val lineColors = listOf(Color(0xFF0F766E), Color(0xFFF59E0B), Color(0xFF6366F1), Color(0xFFEC4899))

@Composable
fun CompareScreen(initial: List<String>) {
    val nav = LocalNav.current
    var symbols by remember { mutableStateOf(initial.take(4)) }
    var adding by remember { mutableStateOf(false) }
    val user by SievrApp.instance.user.data.collectAsStateWithLifecycle()
    BackScaffold("Compare") { modifier ->
        Column(modifier) {
            WithMarket { market ->
                val stocks = symbols.mapNotNull { market[it] }
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    item {
                        FlowRow(Modifier.padding(horizontal = 12.dp)) {
                            stocks.forEachIndexed { i, s ->
                                AssistChip({ symbols = symbols - s.symbol }, { Text(s.symbol, color = lineColors[i]) }, Modifier.padding(4.dp),
                                    trailingIcon = { Icon(Icons.Default.Close, "Remove") })
                            }
                            if (symbols.size < 4) TextButton(onClick = { adding = true }) { Text("+ Add stock") }
                        }
                    }
                    if (stocks.isEmpty()) item { EmptyState("Pick stocks to compare", "Add up to four.", "Add a stock") { adding = true } }
                    if (stocks.isNotEmpty()) {
                        item {
                            SectionHeader("1-year performance (rebased to 100)")
                            LineChart(
                                stocks.mapIndexed { i, s ->
                                    val c = s.data.c.takeLast(252)
                                    val base = c.firstOrNull { it != null }
                                    Series(c.map { v -> if (v != null && base != null) v / base * 100 else null }, lineColors[i])
                                },
                                Modifier.padding(horizontal = 16.dp), height = 180.dp, fill = false, baseline = 100.0,
                            )
                        }
                        item {
                            SectionHeader("Side by side")
                            val scroll = rememberScrollState()
                            Row(Modifier.padding(horizontal = 16.dp)) {
                                Column(Modifier.width(130.dp)) {
                                    Text("", Modifier.padding(vertical = 6.dp), style = MaterialTheme.typography.labelSmall)
                                    compareMetrics.forEach { id ->
                                        Text(Metrics[id]?.label ?: id, Modifier.padding(vertical = 6.dp), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                    }
                                }
                                Row(Modifier.horizontalScroll(scroll)) {
                                    stocks.forEachIndexed { i, s ->
                                        Column(Modifier.width(96.dp).clickable { nav.stock(s.symbol) }, horizontalAlignment = Alignment.End) {
                                            Text(s.symbol, Modifier.padding(vertical = 6.dp), style = MaterialTheme.typography.labelSmall, color = lineColors[i], maxLines = 1)
                                            compareMetrics.forEach { id ->
                                                val best = bestOf(stocks.mapNotNull { st -> st[id] }, Metrics[id]?.higherIsBetter)
                                                val v = s[id]
                                                Text(Fmt.metric(id, v), Modifier.padding(vertical = 6.dp), style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = if (v != null && v == best && stocks.size > 1) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
                                            }
                                        }
                                    }
                                }
                            }
                            Text("Bold marks the best value where higher or lower is clearly better.", Modifier.padding(16.dp),
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (adding) StockSearch(market, onDismiss = { adding = false }, title = "Add to comparison", recent = user.recentSymbols) {
                    if (it.symbol !in symbols) symbols = symbols + it.symbol
                    adding = false
                }
            }
        }
    }
}

private fun bestOf(values: List<Double>, higherIsBetter: Boolean?): Double? = when (higherIsBetter) {
    true -> values.maxOrNull()
    false -> values.filter { it > 0 }.minOrNull()
    null -> null
}

// ------------------------------------------------------------------ settings

@Composable
fun SettingsScreen() {
    val store = SievrApp.instance.user
    val repo = SievrApp.instance.market
    val user by store.data.collectAsStateWithLifecycle()
    val state by repo.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    BackScaffold("Settings") { modifier ->
        Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text("Theme", style = MaterialTheme.typography.titleSmall)
            FlowRow {
                listOf("system" to "Follow phone", "light" to "Light", "dark" to "Dark", "black" to "Pure black").forEach { (id, label) ->
                    FilterChip(user.theme == id, { store.update { it.copy(theme = id) } }, { Text(label) }, Modifier.padding(end = 6.dp))
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("App colour", style = MaterialTheme.typography.titleSmall)
            Text("Changes the colour of the whole app. Portfolio folders can still have their own.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(Modifier.padding(top = 6.dp)) {
                io.github.akrishna87.sievr.ui.theme.skinChoices.forEach { (c, label) ->
                    val selected = user.skinColor == c
                    Column(Modifier.padding(end = 10.dp, bottom = 8.dp).clickable { store.update { it.copy(skinColor = c) } },
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        androidx.compose.foundation.layout.Box(
                            Modifier.size(40.dp).clip(androidx.compose.foundation.shape.CircleShape)
                                .background(io.github.akrishna87.sievr.ui.theme.skinSwatch(c))
                                .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, androidx.compose.foundation.shape.CircleShape) else Modifier),
                            contentAlignment = Alignment.Center,
                        ) { if (selected) Icon(Icons.Default.Check, "Selected", tint = androidx.compose.ui.graphics.Color.White) }
                        Text(label, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Text("Background tint", style = MaterialTheme.typography.labelMedium)
            FlowRow {
                listOf(0 to "Off", 1 to "Light", 2 to "Strong").forEach { (v, label) ->
                    FilterChip(user.skinTint == v, { store.update { it.copy(skinTint = v) } }, { Text(label) }, Modifier.padding(end = 6.dp))
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Text size", style = MaterialTheme.typography.titleSmall)
            FlowRow {
                io.github.akrishna87.sievr.ui.theme.textSizeChoices.forEach { (label, scale) ->
                    FilterChip(kotlin.math.abs(user.fontScale - scale) < 0.01f, { store.update { it.copy(fontScale = scale) } },
                        { Text(label) }, Modifier.padding(end = 6.dp))
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Font", style = MaterialTheme.typography.titleSmall)
            FlowRow {
                io.github.akrishna87.sievr.ui.theme.fontChoices.forEach { (id, label) ->
                    FilterChip(user.fontFamily == id, { store.update { it.copy(fontFamily = id) } },
                        { Text(label, fontFamily = io.github.akrishna87.sievr.ui.theme.fontFamilyFor(id)) }, Modifier.padding(end = 6.dp))
                }
            }
            SievrCard(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Preview", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Reliance Industries", style = MaterialTheme.typography.titleMedium)
                    Text("₹1,234.50  +1.25%  ·  P/E 24.3  ·  ROE 18.2%", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Daily digest", style = MaterialTheme.typography.titleSmall)
                    Text("A short market summary when new data arrives", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(user.dailyDigest, { v -> store.update { it.copy(dailyDigest = v) } })
            }
            Spacer(Modifier.height(16.dp))
            Text("Market data", style = MaterialTheme.typography.titleSmall)
            val ready = state as? io.github.akrishna87.sievr.data.MarketState.Ready
            Text(
                (ready?.market?.let { "Closing data for ${Fmt.isoDate(it.snapshot.asOf)}, built ${it.snapshot.generatedAt.take(16).replace('T', ' ')} UTC. " } ?: "") +
                    "New data is published every weekday evening and downloaded in the background.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { scope.launch { repo.refresh(force = true) } }) { Text(if (ready?.refreshing == true) "Downloading…" else "Download again now") }
            Spacer(Modifier.height(16.dp))
            Text("Your data", style = MaterialTheme.typography.titleSmall)
            Text("${user.screens.size} screens, ${user.watchlists.sumOf { it.symbols.size }} watched stocks, ${user.holdings.size} holdings and " +
                "${user.priceAlerts.size} alerts, stored only on this phone.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ------------------------------------------------------------------ about

@Composable
fun AboutScreen() {
    BackScaffold("About Sievr") { modifier ->
        val state by SievrApp.instance.market.state.collectAsStateWithLifecycle()
        val source = (state as? io.github.akrishna87.sievr.data.MarketState.Ready)?.market?.snapshot?.source
        Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text("Sievr", style = MaterialTheme.typography.headlineSmall)
            Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Text(
                "A free stock screener for India. Every ratio, score, screen, backtest and alert is calculated on your phone from one " +
                    "end-of-day data file, so there is no account, no paywall and no tracking. Your screens, watchlists and portfolio never leave the device.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(16.dp))
            Text("Data", style = MaterialTheme.typography.titleSmall)
            Text(
                (source ?: "NSE listings and index constituents; Yahoo Finance prices and fundamentals") +
                    ". Prices are end-of-day and adjusted for splits and dividends. Fundamentals refresh on a rolling basis, so a company's figures can be a few days behind its latest filing.",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(16.dp))
            Text("Disclaimer", style = MaterialTheme.typography.titleSmall)
            Text(
                "Sievr is an information and research tool. It does not give investment advice or recommendations, and it is not registered " +
                    "with SEBI as an investment adviser or research analyst. Scores, fair values and backtests are mechanical calculations that can be wrong. " +
                    "Verify figures with company filings on NSE/BSE and consult a SEBI-registered adviser before investing. Investments in securities are subject to market risks.",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(16.dp))
            Text("Scores", style = MaterialTheme.typography.titleSmall)
            listOf("qualityScore", "valueScore", "growthScore", "momentumScore", "safetyScore", "sievrScore").forEach { id ->
                Metrics[id]?.let { Text("${it.label}: ${it.description}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp)) }
            }
        }
    }
}

// ------------------------------------------------------------------ formula help

@Composable
fun FormulaHelpScreen() {
    BackScaffold("Formula guide") { modifier ->
        LazyColumn(modifier, contentPadding = PaddingValues(16.dp)) {
            item {
                Text("Write conditions with metric names, numbers and operators:", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                listOf(
                    "ROE > 20 AND PE < Industry_PE",
                    "Price > SMA200 AND RSI BETWEEN 40 AND 60",
                    "(FCF_Yield > 5 OR Dividend_Yield > 3) AND Debt_to_Equity < 0.5",
                    "Price < Graham_Number * 0.8",
                    "Sales_CAGR > 15 AND Profit_CAGR > Sales_CAGR",
                    "NOT (Pledged > 10) AND Promoter > 50",
                    "max(ROE, ROCE) > 25",
                ).forEach { Text(it, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp)) }
                Spacer(Modifier.height(8.dp))
                Text("Operators: + − * /  > >= < <= = !=  AND OR NOT  BETWEEN … AND …  and the functions abs, min, max, sqrt, log. " +
                    "Names ignore case and underscores. Percentages are plain numbers (ROE > 15 means 15%). Money is in ₹ crore.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Category.entries.forEach { c ->
                item(key = c.name) { Text(c.label, Modifier.padding(top = 16.dp, bottom = 4.dp), style = MaterialTheme.typography.titleSmall) }
                items(Metrics.inCategory(c), key = { it.id }) { m ->
                    Column(Modifier.padding(vertical = 4.dp)) {
                        Text(formulaName(m), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        Text(m.label + " — " + m.description, style = MaterialTheme.typography.bodySmall)
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun UpdateItem() {
    val updates = SievrApp.instance.updates
    val available by updates.available.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var checked by remember { mutableStateOf(false) }
    val v = available
    ListItem(
        headlineContent = { Text(if (v != null) "Update to ${v.versionName}" else "Check for updates") },
        supportingContent = {
            Text(
                when {
                    v != null -> "Tap to download, then open the file. Your data is kept."
                    checking -> "Checking…"
                    checked -> "You have the latest version (${BuildConfig.VERSION_NAME})."
                    else -> "Installed: ${BuildConfig.VERSION_NAME}"
                },
            )
        },
        leadingContent = { Icon(Icons.Default.SystemUpdate, null) },
        modifier = Modifier.clickable {
            if (v != null) context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(updates.apkUrl)))
            else if (!checking) scope.launch { checking = true; updates.check(); checking = false; checked = true }
        },
    )
}
