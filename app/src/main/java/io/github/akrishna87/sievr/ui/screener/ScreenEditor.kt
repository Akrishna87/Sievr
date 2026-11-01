package io.github.akrishna87.sievr.ui.screener

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import io.github.akrishna87.sievr.SievrApp
import io.github.akrishna87.sievr.core.formula.Formula
import io.github.akrishna87.sievr.core.metrics.Metric
import io.github.akrishna87.sievr.core.metrics.Metrics
import io.github.akrishna87.sievr.core.model.Market
import io.github.akrishna87.sievr.core.screen.ExchangeFilter
import io.github.akrishna87.sievr.core.screen.Filter
import io.github.akrishna87.sievr.core.screen.FormulaFilter
import io.github.akrishna87.sievr.core.screen.IndexFilter
import io.github.akrishna87.sievr.core.screen.IndustryFilter
import io.github.akrishna87.sievr.core.screen.MetricFilter
import io.github.akrishna87.sievr.core.screen.Op
import io.github.akrishna87.sievr.core.screen.PatternFilter
import io.github.akrishna87.sievr.core.screen.Screen
import io.github.akrishna87.sievr.core.screen.ScreenEngine
import io.github.akrishna87.sievr.core.screen.SectorFilter
import io.github.akrishna87.sievr.core.technical.CandlePattern
import io.github.akrishna87.sievr.core.util.Csv
import io.github.akrishna87.sievr.ui.LocalNav
import io.github.akrishna87.sievr.ui.Routes
import io.github.akrishna87.sievr.ui.ScreenDraft
import io.github.akrishna87.sievr.ui.components.Fmt
import io.github.akrishna87.sievr.ui.components.MetricPicker
import io.github.akrishna87.sievr.ui.components.WithMarket
import io.github.akrishna87.sievr.ui.components.stockTable
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

private const val SHOW_MAX = 300

@Composable
fun ScreenEditor() {
    val nav = LocalNav.current
    val store = SievrApp.instance.user
    val initial = remember { ScreenDraft.screen ?: Screen("my-" + UUID.randomUUID(), "New screen") }
    var saved by remember { mutableStateOf(ScreenDraft.saved) }
    var screen by remember { mutableStateOf(initial) }
    var savedVersion by remember { mutableStateOf(if (ScreenDraft.saved) initial else null) }
    var menu by remember { mutableStateOf(false) }
    var naming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val dirty = savedVersion != screen

    fun save(named: String? = null) {
        // Presets and new screens become the user's own copy with a fresh id.
        val id = if (saved) screen.id else if (screen.id.startsWith("my-")) screen.id else "my-" + UUID.randomUUID()
        val toSave = screen.copy(id = id, name = named ?: screen.name, category = "")
        screen = toSave
        store.saveScreen(toSave)
        SievrApp.instance.market.market?.let { m ->
            if (toSave.alerts) store.setMatches(toSave.id, ScreenEngine.run(toSave, m.stocks).map { it.symbol }.toSet())
        }
        saved = true
        savedVersion = toSave
        scope.launch { snackbar.showSnackbar("Saved to My screens") }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(screen.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.clickable { naming = true }) },
                navigationIcon = { IconButton(onClick = { nav.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = {
                        screen = screen.copy(alerts = !screen.alerts)
                        if (saved) save() else naming = true
                    }) {
                        Icon(if (screen.alerts) Icons.Default.Notifications else Icons.Default.NotificationsNone,
                            if (screen.alerts) "Alerts on" else "Alert me when stocks enter or leave")
                    }
                    if (!saved || dirty) IconButton(onClick = { if (saved) save() else naming = true }) { Icon(Icons.Default.Save, "Save") }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem({ Text("Backtest this screen") }, onClick = { menu = false; nav.backtest(screen) })
                        DropdownMenuItem({ Text("Export results (CSV)") }, onClick = {
                            menu = false
                            SievrApp.instance.market.market?.let { exportCsv(context, screen, it) }
                        })
                        DropdownMenuItem({ Text("Rename") }, onClick = { menu = false; naming = true })
                        DropdownMenuItem({ Text("Save as a copy") }, onClick = {
                            menu = false
                            screen = screen.copy(id = "my-" + UUID.randomUUID(), name = screen.name + " (copy)")
                            saved = false
                            naming = true
                        })
                        DropdownMenuItem({ Text("Formula help") }, onClick = { menu = false; nav.go(Routes.FORMULA_HELP) })
                        if (saved) DropdownMenuItem({ Text("Delete") }, onClick = { menu = false; confirmDelete = true })
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            WithMarket { market -> EditorBody(market, screen) { screen = it } }
        }
    }

    if (naming) {
        var name by remember { mutableStateOf(screen.name) }
        AlertDialog(
            onDismissRequest = { naming = false },
            title = { Text("Name this screen") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { naming = false; save(name.trim().ifBlank { "My screen" }) }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { naming = false }) { Text("Cancel") } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete “${screen.name}”?") },
            confirmButton = { TextButton(onClick = { store.deleteScreen(screen.id); confirmDelete = false; nav.back() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

private enum class Adding { METRIC, FORMULA, SECTOR, INDEX, EXCHANGE, PATTERN, COLUMNS, SORT }

@Composable
private fun EditorBody(market: Market, screen: Screen, onChange: (Screen) -> Unit) {
    val nav = LocalNav.current
    var adding by remember { mutableStateOf<Adding?>(null) }
    var editing by remember { mutableStateOf<Int?>(null) }
    var pickedMetric by remember { mutableStateOf<Metric?>(null) }
    val results = remember(market, screen.filters, screen.sortBy, screen.sortDescending, screen.limit) { ScreenEngine.run(screen, market.stocks) }
    val columns = remember(screen) { screen.displayColumns() }
    val hScroll = rememberScrollState()

    fun setFilters(filters: List<Filter>) = onChange(screen.copy(filters = filters))
    fun replace(index: Int?, filter: Filter) =
        setFilters(if (index == null) screen.filters + filter else screen.filters.mapIndexed { i, f -> if (i == index) filter else f })

    LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
        if (screen.description.isNotBlank()) item {
            Text(screen.description, Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Text("Filters", Modifier.padding(start = 16.dp, top = 8.dp), style = MaterialTheme.typography.titleSmall)
            FlowRow(Modifier.padding(horizontal = 12.dp)) {
                screen.filters.forEachIndexed { i, f ->
                    InputChip(
                        selected = false,
                        onClick = { editing = i },
                        label = { Text(f.describe(), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        trailingIcon = {
                            Icon(Icons.Default.Close, "Remove", Modifier.clickable { setFilters(screen.filters.filterIndexed { j, _ -> j != i }) })
                        },
                        modifier = Modifier.padding(4.dp),
                    )
                }
                if (screen.filters.isEmpty()) Text("No filters yet: every stock matches. Add one below.", Modifier.padding(4.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FlowRow(Modifier.padding(horizontal = 12.dp)) {
                AssistChip({ adding = Adding.METRIC }, { Text("Metric") }, Modifier.padding(4.dp), leadingIcon = { Icon(Icons.Default.Add, null) })
                AssistChip({ adding = Adding.FORMULA }, { Text("Formula") }, Modifier.padding(4.dp), leadingIcon = { Icon(Icons.Default.Add, null) })
                AssistChip({ adding = Adding.SECTOR }, { Text("Sector") }, Modifier.padding(4.dp), leadingIcon = { Icon(Icons.Default.Add, null) })
                AssistChip({ adding = Adding.INDEX }, { Text("Index") }, Modifier.padding(4.dp), leadingIcon = { Icon(Icons.Default.Add, null) })
                if (market.hasBse) AssistChip({ adding = Adding.EXCHANGE }, { Text("Exchange") }, Modifier.padding(4.dp), leadingIcon = { Icon(Icons.Default.Add, null) })
                AssistChip({ adding = Adding.PATTERN }, { Text("Candlestick") }, Modifier.padding(4.dp), leadingIcon = { Icon(Icons.Default.Add, null) })
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${results.size} stocks match", style = MaterialTheme.typography.titleMedium)
                    Text("Sorted by ${Metrics[screen.sortBy]?.label ?: screen.sortBy}, ${if (screen.sortDescending) "highest" else "lowest"} first" +
                        (if (screen.limit > 0) " · top ${screen.limit}" else ""),
                        Modifier.clickable { adding = Adding.SORT }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = { adding = Adding.COLUMNS }) { Icon(Icons.Default.ViewColumn, "Columns") }
                TextButton(onClick = { nav.backtest(screen) }) { Text("Backtest") }
            }
        }
        stockTable(
            stocks = results.take(SHOW_MAX),
            columns = columns,
            scroll = hScroll,
            sortBy = screen.sortBy,
            sortDescending = screen.sortDescending,
            onSort = { id -> onChange(screen.copy(sortBy = id, sortDescending = if (id == screen.sortBy) !screen.sortDescending else Metrics[id]?.higherIsBetter != false)) },
            onOpen = { nav.stock(it.symbol) },
        )
        if (results.size > SHOW_MAX) item {
            Text("Showing the first $SHOW_MAX. Export to CSV for all ${results.size}.", Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    // ----- dialogs

    val editingFilter = editing?.let { screen.filters.getOrNull(it) }
    when (editingFilter) {
        is MetricFilter -> Metrics[editingFilter.metric]?.let { m ->
            MetricFilterDialog(m, editingFilter, market, onDismiss = { editing = null }) { replace(editing, it); editing = null }
        }
        is FormulaFilter -> FormulaDialog(editingFilter.expression, market, onDismiss = { editing = null }) { replace(editing, it); editing = null }
        is SectorFilter -> ChoiceDialog("Sectors", market.sectors, editingFilter.sectors, { editing = null }) { replace(editing, SectorFilter(it)); editing = null }
        is IndexFilter -> ChoiceDialog("Indices", market.indexNames, editingFilter.indices, { editing = null }) { replace(editing, IndexFilter(it)); editing = null }
        is IndustryFilter -> ChoiceDialog("Industries", market.industries, editingFilter.industries, { editing = null }) { replace(editing, IndustryFilter(it)); editing = null }
        is PatternFilter -> PatternDialog(editingFilter.patterns, { editing = null }) { replace(editing, PatternFilter(it)); editing = null }
        is ExchangeFilter -> ExchangeDialog({ editing = null }) { replace(editing, it); editing = null }
        null -> Unit
    }

    when (adding) {
        Adding.METRIC -> MetricPicker(onDismiss = { adding = null }, includePatterns = false) { pickedMetric = it; adding = null }
        Adding.FORMULA -> FormulaDialog("", market, onDismiss = { adding = null }) { replace(null, it); adding = null }
        Adding.SECTOR -> ChoiceDialog("Sectors", market.sectors, emptySet(), { adding = null }) { replace(null, SectorFilter(it)); adding = null }
        Adding.INDEX -> ChoiceDialog("Indices", market.indexNames, emptySet(), { adding = null }) { replace(null, IndexFilter(it)); adding = null }
        Adding.PATTERN -> PatternDialog(emptySet(), { adding = null }) { replace(null, PatternFilter(it)); adding = null }
        Adding.EXCHANGE -> ExchangeDialog({ adding = null }) { replace(null, it); adding = null }
        Adding.COLUMNS -> MetricPicker("Add a column", onDismiss = { adding = null }, exclude = columns.toSet(), includePatterns = false) {
            onChange(screen.copy(columns = screen.columns + it.id)); adding = null
        }
        Adding.SORT -> SortDialog(screen, onDismiss = { adding = null }) { onChange(it); adding = null }
        null -> Unit
    }
    pickedMetric?.let { m ->
        MetricFilterDialog(m, null, market, onDismiss = { pickedMetric = null }) { replace(null, it); pickedMetric = null }
    }
}

/** Sets a metric condition, showing how the metric is spread across the market as a guide. */
@Composable
fun MetricFilterDialog(metric: Metric, initial: MetricFilter?, market: Market, onDismiss: () -> Unit, onSave: (MetricFilter) -> Unit) {
    var op by remember { mutableStateOf(initial?.op ?: if (metric.higherIsBetter == false) Op.LT else Op.GT) }
    var v1 by remember { mutableStateOf(initial?.value?.let { trim(it) } ?: "") }
    var v2 by remember { mutableStateOf(initial?.value2?.let { trim(it) } ?: "") }
    val dist = remember(metric, market) {
        val values = market.stocks.mapNotNull { it[metric.id] }.sorted()
        if (values.isEmpty()) null else listOf(0.1, 0.25, 0.5, 0.75, 0.9).map { values[((values.size - 1) * it).toInt()] } + values.size.toDouble()
    }
    if (metric.isFlag) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(metric.label) },
            text = { Text(metric.description) },
            confirmButton = { TextButton(onClick = { onSave(MetricFilter(metric.id, Op.EQ, 1.0)) }) { Text("Yes") } },
            dismissButton = { TextButton(onClick = { onSave(MetricFilter(metric.id, Op.EQ, 0.0)) }) { Text("No") } },
        )
        return
    }
    val a = v1.replace(",", "").toDoubleOrNull()
    val b = v2.replace(",", "").toDoubleOrNull()
    val valid = a != null && (op != Op.BETWEEN || b != null)
    val preview = if (valid) MetricFilter(metric.id, op, a!!, if (op == Op.BETWEEN) b else null) else null
    val count = preview?.let { f -> market.stocks.count { f.matches(it) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(metric.label) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(metric.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.padding(4.dp))
                FlowRow {
                    listOf(Op.GT, Op.GTE, Op.LT, Op.LTE, Op.BETWEEN).forEach { o ->
                        FilterChip(op == o, { op = o }, { Text(o.symbol) }, Modifier.padding(end = 6.dp))
                    }
                }
                Row {
                    OutlinedTextField(v1, { v1 = it }, Modifier.weight(1f), label = { Text(if (op == Op.BETWEEN) "From" else "Value") }, singleLine = true)
                    if (op == Op.BETWEEN) {
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(v2, { v2 = it }, Modifier.weight(1f), label = { Text("To") }, singleLine = true)
                    }
                }
                if (dist != null) {
                    Spacer(Modifier.padding(4.dp))
                    Text("Across ${dist[5].toInt()} stocks: 10% below ${Fmt.metric(metric.id, dist[0])}, median ${Fmt.metric(metric.id, dist[2])}, " +
                        "10% above ${Fmt.metric(metric.id, dist[4])}.", style = MaterialTheme.typography.bodySmall)
                    FlowRow {
                        listOf("Top 25%" to dist[3], "Median" to dist[2], "Bottom 25%" to dist[1]).forEach { (label, v) ->
                            AssistChip({ v1 = trim(v) }, { Text(label) }, Modifier.padding(end = 6.dp))
                        }
                    }
                }
                if (count != null) Text("$count stocks pass this filter on its own.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary)
            }
        },
        confirmButton = { TextButton(enabled = valid, onClick = { preview?.let(onSave) }) { Text("Done") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun FormulaDialog(initial: String, market: Market, onDismiss: () -> Unit, onSave: (FormulaFilter) -> Unit) {
    val nav = LocalNav.current
    var text by remember { mutableStateOf(initial) }
    val parsed = remember(text) { if (text.isBlank()) null else Formula.check(text) }
    val error = parsed?.exceptionOrNull()
    val count = remember(parsed, market) { parsed?.getOrNull()?.let { f -> market.stocks.count { s -> f.matches { s[it] } } } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Formula") },
        text = {
            Column {
                OutlinedTextField(
                    text, { text = it }, Modifier.fillMaxWidth().heightIn(min = 96.dp),
                    placeholder = { Text("ROE > 20 AND PE < Industry_PE") },
                    isError = error != null,
                    supportingText = {
                        Text(
                            when {
                                error != null -> error.message ?: "Can't read that"
                                count != null -> "$count stocks match"
                                else -> "Use metric names, + − × ÷, > < =, AND, OR, NOT, BETWEEN."
                            },
                        )
                    },
                )
                FlowRow {
                    listOf("PE < Industry_PE", "Price > SMA200", "ROCE > 20", "FCF_Yield > 5", "Promoter > 50", "RSI BETWEEN 40 AND 60").forEach { ex ->
                        AssistChip({ text = if (text.isBlank()) ex else "$text AND $ex" }, { Text(ex) }, Modifier.padding(end = 6.dp))
                    }
                }
                TextButton(onClick = { nav.go(Routes.FORMULA_HELP) }) { Text("All metric names and examples") }
            }
        },
        confirmButton = { TextButton(enabled = parsed?.isSuccess == true, onClick = { onSave(FormulaFilter(text.trim())) }) { Text("Done") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun ChoiceDialog(title: String, options: List<String>, initial: Set<String>, onDismiss: () -> Unit, onSave: (Set<String>) -> Unit) {
    var chosen by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { o ->
                    Row(Modifier.fillMaxWidth().clickable { chosen = if (o in chosen) chosen - o else chosen + o }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(o in chosen, { chosen = if (o in chosen) chosen - o else chosen + o })
                        Text(o)
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = chosen.isNotEmpty(), onClick = { onSave(chosen) }) { Text("Done") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PatternDialog(initial: Set<String>, onDismiss: () -> Unit, onSave: (Set<String>) -> Unit) {
    val labels = CandlePattern.entries.associate { it.metricId to (it.label + when (it.bullish) { true -> " (bullish)"; false -> " (bearish)"; null -> "" }) }
    var chosen by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Candlestick patterns today") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                CandlePattern.entries.forEach { p ->
                    val id = p.metricId
                    Row(Modifier.fillMaxWidth().clickable { chosen = if (id in chosen) chosen - id else chosen + id }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(id in chosen, { chosen = if (id in chosen) chosen - id else chosen + id })
                        Column {
                            Text(labels.getValue(id))
                            Text(p.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider()
                }
            }
        },
        confirmButton = { TextButton(enabled = chosen.isNotEmpty(), onClick = { onSave(chosen) }) { Text("Done") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ExchangeDialog(onDismiss: () -> Unit, onSave: (ExchangeFilter) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Exchange") },
        text = {
            Column {
                listOf("NSE" to "Listed on NSE", "BSE" to "Listed on BSE", "BSE_ONLY" to "Only on BSE (not on NSE)").forEach { (id, label) ->
                    Text(label, Modifier.fillMaxWidth().clickable { onSave(ExchangeFilter(id)) }.padding(vertical = 12.dp))
                    HorizontalDivider()
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SortDialog(screen: Screen, onDismiss: () -> Unit, onSave: (Screen) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    var sortBy by remember { mutableStateOf(screen.sortBy) }
    var desc by remember { mutableStateOf(screen.sortDescending) }
    var limit by remember { mutableStateOf(if (screen.limit > 0) screen.limit.toString() else "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sort results") },
        text = {
            Column {
                TextButton(onClick = { picking = true }) { Text("By: " + (Metrics[sortBy]?.label ?: sortBy)) }
                Row {
                    FilterChip(desc, { desc = true }, { Text("Highest first") }, Modifier.padding(end = 6.dp))
                    FilterChip(!desc, { desc = false }, { Text("Lowest first") })
                }
                OutlinedTextField(limit, { limit = it.filter(Char::isDigit) }, label = { Text("Only the top N (blank = all)") }, singleLine = true)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(screen.copy(sortBy = sortBy, sortDescending = desc, limit = limit.toIntOrNull() ?: 0)) }) { Text("Done") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    if (picking) MetricPicker("Sort by", onDismiss = { picking = false }, includePatterns = false) {
        sortBy = it.id
        desc = it.higherIsBetter != false
        picking = false
    }
}

private fun trim(x: Double): String = if (x == x.toLong().toDouble()) x.toLong().toString() else "%.2f".format(java.util.Locale.US, x)

fun exportCsv(context: Context, screen: Screen, market: Market) {
    val results = ScreenEngine.run(screen, market.stocks)
    val dir = File(context.cacheDir, "exports").apply { mkdirs() }
    val safe = screen.name.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().ifBlank { "screen" }
    val file = File(dir, "$safe ${market.snapshot.asOf}.csv")
    file.writeText(Csv.export(results, screen.displayColumns()))
    val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
    val share = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "${screen.name} — Sievr")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(share, "Export ${results.size} stocks"))
}
