package io.github.akrishna87.sievr.ui.watchlist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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

    Scaffold(topBar = {
        TopAppBar(title = { Text("Watchlists") }, actions = {
            IconButton(onClick = { adding = true }) { Icon(Icons.Default.Add, "Add stock") }
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
                val stocks = remember(market, list.symbols, sortBy, desc) {
                    val s = list.symbols.mapNotNull { market[it] }
                    sortBy?.let { ScreenEngine.sort(s, it, desc) } ?: s
                }
                val hScroll = rememberScrollState()
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    if (user.watchlists.size > 1) item {
                        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp)) {
                            items(user.watchlists, key = { it.id }) { w ->
                                FilterChip(w.id == list.id, { selectedId = w.id }, { Text("${w.name} (${w.symbols.size})") }, Modifier.padding(4.dp))
                            }
                        }
                    }
                    if (stocks.isEmpty()) item {
                        EmptyState("Nothing here yet", "Add stocks with + or the star on any stock page. Long-press a row to remove it.", "Add a stock") { adding = true }
                    } else stockTable(
                        stocks, user.watchColumns, hScroll, sortBy, desc,
                        onSort = { id -> if (sortBy == id) desc = !desc else { sortBy = id; desc = Metrics[id]?.higherIsBetter != false } },
                        onOpen = { nav.stock(it.symbol) },
                        onLongPress = { removing = it.symbol },
                    )
                }
                if (adding) StockSearch(market, onDismiss = { adding = false }, title = "Add to ${list.name}", recent = user.recentSymbols) {
                    if (it.symbol !in list.symbols) store.toggleWatch(it.symbol, list.id)
                    adding = false
                }
            }
        }
    }
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
