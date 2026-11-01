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
            OutlinedTextField(
                query, { query = it }, Modifier.fillMaxWidth().padding(16.dp, 8.dp),
                placeholder = { Text("Search ${Metrics.all.size} metrics") },
                leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true,
            )
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

/** Finds stocks by symbol or name. */
@Composable
fun StockSearch(market: Market, onDismiss: () -> Unit, title: String = "Find a stock", recent: List<String> = emptyList(), onPick: (Stock) -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember(query, market) { search(market, query, recent) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.fillMaxHeight(0.9f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))
            OutlinedTextField(
                query, { query = it }, Modifier.fillMaxWidth().padding(16.dp, 8.dp),
                placeholder = { Text("Symbol, company name or BSE code") },
                leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true,
            )
            LazyColumn {
                items(results, key = { it.symbol }) { s ->
                    StockRow(s, onClick = { onPick(s) }, showSparkline = false, subtitle = s.name + " · " + s.sector)
                }
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
