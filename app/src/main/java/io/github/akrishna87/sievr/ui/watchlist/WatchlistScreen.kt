package io.github.akrishna87.sievr.ui.watchlist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.akrishna87.sievr.SievrApp
import io.github.akrishna87.sievr.core.metrics.Metrics
import io.github.akrishna87.sievr.core.screen.ScreenEngine
import io.github.akrishna87.sievr.ui.LocalNav
import io.github.akrishna87.sievr.ui.components.EmptyState
import io.github.akrishna87.sievr.ui.components.MetricPicker
import io.github.akrishna87.sievr.ui.components.StockSearch
import io.github.akrishna87.sievr.ui.components.WithMarket
import io.github.akrishna87.sievr.ui.components.stockTable

@Composable
fun WatchlistScreen() {
    val nav = LocalNav.current
    val store = SievrApp.instance.user
    val user by store.data.collectAsStateWithLifecycle()
    var selectedId by rememberSaveable { mutableStateOf(user.watchlists.first().id) }
    val list = user.watchlists.firstOrNull { it.id == selectedId } ?: user.watchlists.first()
    var adding by remember { mutableStateOf(false) }
    var columns by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var naming by remember { mutableStateOf<String?>(null) }
    var newList by remember { mutableStateOf(false) }
    var sortBy by rememberSaveable { mutableStateOf<String?>(null) }
    var desc by rememberSaveable { mutableStateOf(true) }
    var removing by remember { mutableStateOf<String?>(null) }
    var addMenu by remember { mutableStateOf(false) }
    // 0 everything, 1 stocks, 2 ETFs, 3 mutual funds
    var kind by rememberSaveable { mutableStateOf(0) }
    var addingFund by remember { mutableStateOf(false) }
    var fundInfo by remember { mutableStateOf<io.github.akrishna87.sievr.core.mf.MfScheme?>(null) }
    val app = SievrApp.instance
    val mf by app.mf.state.collectAsStateWithLifecycle()
    // Fund NAVs load only when someone watches or holds a fund.
    androidx.compose.runtime.LaunchedEffect(list.funds.isNotEmpty() || addingFund) {
        if (list.funds.isNotEmpty() || addingFund) app.mf.start()
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Watchlists") }, actions = {
            IconButton(onClick = { addMenu = true }) { Icon(Icons.Default.Add, "Add stock, ETF or fund") }
            DropdownMenu(addMenu, { addMenu = false }) {
                DropdownMenuItem({ Text("Stock or ETF") }, onClick = { addMenu = false; adding = true })
                DropdownMenuItem({ Text("Mutual fund") }, onClick = { addMenu = false; addingFund = true })
            }
            IconButton(onClick = { columns = true }) { Icon(Icons.Default.ViewColumn, "Columns") }
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem({ Text("New watchlist") }, onClick = { menu = false; newList = true })
                DropdownMenuItem({ Text("Rename this list") }, onClick = { menu = false; naming = list.name })
                if (user.watchlists.size > 1) DropdownMenuItem({ Text("Delete this list") }, onClick = {
                    menu = false; store.deleteWatchlist(list.id); selectedId = user.watchlists.first().id
                })
                DropdownMenuItem({ Text("Reset columns") }, onClick = { menu = false; store.update { it.copy(watchColumns = listOf("price", "chg1d", "pe", "roe", "sievrScore")) } })
                if (list.symbols.size >= 2) DropdownMenuItem({ Text("Compare these stocks") }, onClick = { menu = false; nav.compare(list.symbols.take(4)) })
            }
        })
    }) { padding ->
        Column(Modifier.padding(padding)) {
            WithMarket { market ->
                val stocks = remember(market, list.symbols, sortBy, desc, kind) {
                    val s = list.symbols.mapNotNull { market[it] }.filter {
                        when (kind) { 1 -> !it.isEtf; 2 -> it.isEtf; 3 -> false; else -> true }
                    }
                    sortBy?.let { ScreenEngine.sort(s, it, desc) } ?: s
                }
                val hScroll = rememberScrollState()
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    if (user.watchlists.size > 1) item {
                        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp)) {
                            items(user.watchlists, key = { it.id }) { w ->
                                FilterChip(w.id == list.id, { selectedId = w.id }, { Text("${w.name} (${w.symbols.size + w.funds.size})") }, Modifier.padding(4.dp))
                            }
                        }
                    }
                    item {
                        val all = list.symbols.mapNotNull { market[it] }
                        val nEtf = all.count { it.isEtf }
                        androidx.compose.material3.ScrollableTabRow(selectedTabIndex = kind, edgePadding = 8.dp) {
                            listOf("All (${all.size + list.funds.size})", "Stocks (${all.size - nEtf})", "ETFs ($nEtf)", "Mutual funds (${list.funds.size})")
                                .forEachIndexed { i, label ->
                                    androidx.compose.material3.Tab(kind == i, { kind = i }, text = { Text(label) })
                                }
                        }
                    }
                    val showFunds = kind == 0 || kind == 3
                    if (kind != 0 && stocks.isEmpty() && !(showFunds && list.funds.isNotEmpty())) item {
                        Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            EmptyState(
                                when (kind) { 1 -> "No stocks here yet"; 2 -> "No ETFs here yet"; else -> "No mutual funds here yet" },
                                when (kind) { 3 -> "Follow a fund's NAV and daily move."; 2 -> "Search an ETF like NIFTYBEES or GOLDBEES."; else -> "Search by name or symbol, or speak it." },
                            )
                            androidx.compose.material3.Button(onClick = { if (kind == 3) addingFund = true else adding = true }) {
                                Text(if (kind == 3) "Add mutual funds" else if (kind == 2) "Add ETFs" else "Add stocks")
                            }
                        }
                    } else if (stocks.isEmpty() && list.funds.isEmpty()) item {
                        Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            EmptyState("Start your ${list.name}", "Follow stocks, ETFs and mutual funds here, with today's price or NAV and " +
                                "the day's move. Tip: tap the star on any stock page to add it. Long-press a row to remove it.")
                            androidx.compose.material3.Button(onClick = { adding = true }, Modifier.padding(horizontal = 32.dp).fillMaxWidth()) {
                                Icon(io.github.akrishna87.sievr.ui.watchlist.WatchIcons.stock, null)
                                Text("  Add stocks & ETFs")
                            }
                            androidx.compose.material3.OutlinedButton(onClick = { addingFund = true },
                                Modifier.padding(horizontal = 32.dp, vertical = 8.dp).fillMaxWidth()) {
                                Icon(io.github.akrishna87.sievr.ui.watchlist.WatchIcons.fund, null)
                                Text("  Add mutual funds")
                            }
                        }
                    } else if (stocks.isNotEmpty()) stockTable(
                        stocks, user.watchColumns, hScroll, sortBy, desc,
                        onSort = { id -> if (sortBy == id) desc = !desc else { sortBy = id; desc = Metrics[id]?.higherIsBetter != false } },
                        onOpen = { nav.stock(it.symbol) },
                        onLongPress = { removing = it.symbol },
                    )
                    if (showFunds && list.funds.isNotEmpty()) {
                        if (kind == 0) item { io.github.akrishna87.sievr.ui.components.SectionHeader("Mutual funds (${list.funds.size})") }
                        val byCode = mf.byCode
                        if (byCode.isEmpty()) item {
                            Text(if (mf.error != null) "Couldn't load fund NAVs: ${mf.error}" else "Loading the latest NAVs…",
                                Modifier.padding(16.dp), style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                        } else items(list.funds, key = { "mf$it" }) { code ->
                            val s = byCode[code]
                            FundWatchRow(s, code, onOpen = { fundInfo = s }, onRemove = { store.toggleWatchFund(code, list.id) })
                        }
                    }
                }
                if (adding) StockSearch(market, onDismiss = { adding = false }, title = "Add to ${list.name}", recent = user.recentSymbols,
                    added = list.symbols.toSet(), onPickMany = { picked ->
                        picked.forEach { if (it.symbol !in list.symbols) store.toggleWatch(it.symbol, list.id) }
                        adding = false
                    })
            }
        }
    }
    if (addingFund) {
        val schemes = mf.data?.schemes
        if (schemes == null) {
            AlertDialog(onDismissRequest = { addingFund = false }, title = { Text("Mutual funds") },
                text = { Text(mf.error ?: "Downloading the latest NAVs…") },
                confirmButton = { TextButton(onClick = { addingFund = false }) { Text("Close") } })
        } else io.github.akrishna87.sievr.ui.portfolio.SchemeSearch(schemes, onDismiss = { addingFund = false }, title = "Add funds to ${list.name}",
            added = list.funds.toSet(), onPickMany = { picked ->
                picked.forEach { if (it.code !in list.funds) store.toggleWatchFund(it.code, list.id) }
                addingFund = false
            })
    }
    fundInfo?.let { s -> FundInfoDialog(s, onDismiss = { fundInfo = null }) { store.toggleWatchFund(s.code, list.id); fundInfo = null } }
    if (columns) MetricPicker("Add a column", onDismiss = { columns = false }, exclude = user.watchColumns.toSet(), includePatterns = false) { m ->
        store.update { it.copy(watchColumns = it.watchColumns + m.id) }
        columns = false
    }
    removing?.let { sym ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove $sym from ${list.name}?") },
            confirmButton = { TextButton(onClick = { store.toggleWatch(sym, list.id); removing = null }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } },
        )
    }
    if (newList || naming != null) {
        var name by remember { mutableStateOf(naming ?: "") }
        AlertDialog(
            onDismissRequest = { newList = false; naming = null },
            title = { Text(if (newList) "New watchlist" else "Rename watchlist") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true) },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    if (newList) selectedId = store.addWatchlist(name.trim()) else store.renameWatchlist(list.id, name.trim())
                    newList = false; naming = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { newList = false; naming = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun FundWatchRow(s: io.github.akrishna87.sievr.core.mf.MfScheme?, code: Int, onOpen: () -> Unit, onRemove: () -> Unit) {
    val ty = androidx.compose.material3.MaterialTheme.typography
    val muted = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth().combinedClickable(onClick = onOpen, onLongClick = onRemove).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(s?.name ?: "Scheme $code", style = ty.bodyMedium, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text(s?.let { "${it.subCategory} · ${io.github.akrishna87.sievr.ui.components.Fmt.isoDate(it.date)}" } ?: "No longer in AMFI's list",
                style = ty.bodySmall, color = muted, maxLines = 1)
        }
        Column(horizontalAlignment = androidx.compose.ui.Alignment.End, modifier = Modifier.padding(start = 12.dp)) {
            Text(s?.let { io.github.akrishna87.sievr.ui.components.Fmt.nav(it.nav) } ?: "—", style = ty.bodyMedium)
            val d = s?.dayChangePercent
            Text(io.github.akrishna87.sievr.ui.components.Fmt.percent(d, true, 2), style = ty.bodySmall,
                color = io.github.akrishna87.sievr.ui.components.changeColor(d))
        }
    }
    androidx.compose.material3.HorizontalDivider()
}

@Composable
internal fun FundInfoDialog(s: io.github.akrishna87.sievr.core.mf.MfScheme, onDismiss: () -> Unit, actionLabel: String = "Remove from list", onAction: () -> Unit) {
    val ty = androidx.compose.material3.MaterialTheme.typography
    val F = io.github.akrishna87.sievr.ui.components.Fmt
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s.name, style = ty.titleMedium) },
        text = {
            Column {
                Text("NAV ${F.nav(s.nav)} on ${F.isoDate(s.date)}", style = ty.bodyLarge)
                s.prevNav?.let { p ->
                    Text("Previous ${F.nav(p)}" + (s.prevDate?.let { " on ${F.isoDate(it)}" } ?: "") + " · " + F.percent(s.dayChangePercent, true, 2),
                        style = ty.bodyMedium, color = io.github.akrishna87.sievr.ui.components.changeColor(s.dayChangePercent))
                }
                Text("\n${s.assetClass} · ${s.subCategory}" + (if (s.amc.isNotBlank()) "\n${s.amc}" else ""), style = ty.bodySmall)
                if (s.isin.isNotEmpty()) Text("ISIN ${s.isin.joinToString(", ")} · AMFI code ${s.code}", style = ty.bodySmall)
                Text("\nTo track your units and gains, add it under Portfolio → Mutual funds.", style = ty.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        dismissButton = { TextButton(onClick = onAction) { Text(actionLabel) } },
    )
}

internal object WatchIcons {
    val stock = Icons.Default.ShowChart
    val fund = Icons.Default.AccountBalance
}
