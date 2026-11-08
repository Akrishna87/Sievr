package io.github.akrishna87.sievr.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Checkbox
import androidx.compose.ui.unit.dp
import io.github.akrishna87.sievr.core.metrics.Category
import io.github.akrishna87.sievr.core.metrics.Metric
import io.github.akrishna87.sievr.core.metrics.Metrics
import io.github.akrishna87.sievr.core.model.Market
import io.github.akrishna87.sievr.core.model.Stock

/** Search and browse every metric by category. */
@Composable
fun MetricPicker(
    title: String = "Choose a metric",
    onDismiss: () -> Unit,
    exclude: Set<String> = emptySet(),
    includePatterns: Boolean = true,
    onPick: (Metric) -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by rememberSaveable { mutableStateOf("") }
    var category by remember { mutableStateOf<Category?>(null) }
    val categories = Category.entries.filter { includePatterns || it != Category.PATTERNS }
    val list = Metrics.all.filter { m ->
        m.id !in exclude && (includePatterns || m.category != Category.PATTERNS) &&
            (category == null || m.category == category) &&
            (query.isBlank() || listOf(m.label, m.id, m.description).any { it.contains(query, ignoreCase = true) } ||
                m.aliases.any { it.contains(query.replace(' ', '_'), ignoreCase = true) })
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.fillMaxHeight(0.9f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))
            SearchField(query, { query = it }, "Search ${Metrics.all.size} metrics", Modifier.fillMaxWidth().padding(16.dp, 8.dp),
                voicePrompt = "Say a metric, e.g. return on equity")
            LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)) {
                item { FilterChip(category == null, { category = null }, { Text("All") }, Modifier.padding(4.dp)) }
                items(categories) { c -> FilterChip(category == c, { category = if (category == c) null else c }, { Text(c.label) }, Modifier.padding(4.dp)) }
            }
            LazyColumn(Modifier.fillMaxWidth()) {
                items(list, key = { it.id }) { m ->
                    Column(Modifier.fillMaxWidth().clickable { onPick(m) }.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Text(m.label, style = MaterialTheme.typography.bodyLarge)
                        Text(m.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(m.category.label + " · " + formulaName(m), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

/** The name to type in formulas: the first alias, or the id. */
fun formulaName(m: Metric): String = (m.aliases.firstOrNull { it.contains('_') || it.length > 2 } ?: m.id).let {
    it.split('_').joinToString("_") { w -> if (w.length <= 3) w.uppercase() else w.replaceFirstChar { c -> c.uppercase() } }
}

/**
 * Finds stocks and ETFs by symbol, name or BSE code, typed or spoken. With [onPickMany], rows get tick boxes
 * so several can be added at once ([added] are already in the list; [max] caps how many can be ticked).
 */
@Composable
fun StockSearch(
    market: Market,
    onDismiss: () -> Unit,
    title: String = "Find a stock",
    recent: List<String> = emptyList(),
    added: Set<String> = emptySet(),
    max: Int = Int.MAX_VALUE,
    onPickMany: ((List<Stock>) -> Unit)? = null,
    onPick: (Stock) -> Unit = {},
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by rememberSaveable { mutableStateOf("") }
    var picked by remember { mutableStateOf(listOf<String>()) }
    val results = remember(query, market) { search(market, query, recent) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.fillMaxHeight(0.9f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))
            SearchField(query, { query = it }, "Symbol, company name or BSE code", Modifier.fillMaxWidth().padding(16.dp, 8.dp),
                voicePrompt = "Say a company or ETF name")
            if (onPickMany != null) Text(
                if (max < Int.MAX_VALUE) "Tick up to $max, then tap Add." else "Tick as many as you like, then tap Add. Search again to add more.",
                Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyColumn(Modifier.weight(1f)) {
                items(results, key = { it.symbol }) { s ->
                    if (onPickMany == null) StockRow(s, onClick = { onPick(s) }, showSparkline = false, subtitle = s.name + " · " + s.sector)
                    else {
                        val already = s.symbol in added
                        val ticked = already || s.symbol in picked
                        val toggle: () -> Unit = {
                            if (!already) {
                                picked = if (s.symbol in picked) picked - s.symbol else if (picked.size < max) picked + s.symbol else picked
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(ticked, { toggle() }, enabled = !already, modifier = Modifier.padding(start = 4.dp))
                            Box(Modifier.weight(1f)) {
                                StockRow(s, onClick = toggle, showSparkline = false,
                                    subtitle = (if (already) "Already added · " else "") + s.name + " · " + s.sector)
                            }
                        }
                    }
                }
            }
            if (onPickMany != null) AddSelectedBar(picked.size, onClear = { picked = emptyList() }) {
                onPickMany(picked.mapNotNull { market[it] })
            }
        }
    }
}

fun search(market: Market, query: String, recent: List<String> = emptyList()): List<Stock> {
    val q = query.trim()
    if (q.isEmpty()) {
        val r = recent.mapNotNull { market[it] }
        return r + market.stocks.filter { it.symbol !in recent }.sortedByDescending { it["mcap"] ?: 0.0 }.take(30)
    }
    return market.stocks.mapNotNull { s ->
        val score = when {
            s.symbol.equals(q, true) || s.data.bseCode == q -> 0
            s.symbol.startsWith(q, true) -> 1
            s.name.startsWith(q, true) -> 2
            s.name.split(' ').any { it.startsWith(q, true) } -> 3
            s.symbol.contains(q, true) || s.name.contains(q, true) -> 4
            else -> return@mapNotNull null
        }
        s to score
    }.sortedWith(compareBy<Pair<Stock, Int>> { it.second }.thenByDescending { it.first["mcap"] ?: 0.0 }).take(50).map { it.first }
}
