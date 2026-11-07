package io.github.akrishna87.sievr.ui.portfolio

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.Description
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.akrishna87.sievr.SievrApp
import io.github.akrishna87.sievr.core.model.Market
import io.github.akrishna87.sievr.core.model.Stock
import io.github.akrishna87.sievr.core.portfolio.Holding
import io.github.akrishna87.sievr.core.portfolio.PortfolioAnalyzer
import io.github.akrishna87.sievr.ui.LocalNav
import io.github.akrishna87.sievr.ui.components.BarList
import io.github.akrishna87.sievr.ui.components.ChangeText
import io.github.akrishna87.sievr.ui.components.EmptyState
import io.github.akrishna87.sievr.ui.components.Fmt
import io.github.akrishna87.sievr.ui.components.MetricGrid
import io.github.akrishna87.sievr.ui.components.ScoreBar
import io.github.akrishna87.sievr.ui.components.SectionHeader
import io.github.akrishna87.sievr.ui.components.SievrCard
import io.github.akrishna87.sievr.ui.components.StockSearch
import io.github.akrishna87.sievr.ui.components.WithMarket
import io.github.akrishna87.sievr.ui.components.changeColor
import io.github.akrishna87.sievr.ui.theme.LocalMarketColors

@Composable
fun PortfolioScreen() {
    val store = SievrApp.instance.user
    val user by store.data.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var picking by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Pair<String, Holding?>?>(null) }
    var importMessage by remember { mutableStateOf<String?>(null) }
    var pendingImport by remember { mutableStateOf<List<Holding>>(emptyList()) }
    var pendingUnmatched by remember { mutableStateOf<List<String>>(emptyList()) }
    var tab by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(0) }
    var pdf by remember { mutableStateOf<android.net.Uri?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var reading by remember { mutableStateOf(false) }
    var shareResult by remember { mutableStateOf<io.github.akrishna87.sievr.core.portfolio.ShareStatement.Result?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    fun readPdf(password: String) {
        val uri = pdf ?: return
        val market = SievrApp.instance.market.market ?: return
        reading = true
        scope.launch {
            when (val o = io.github.akrishna87.sievr.data.CasReader.read(context, uri, password)) {
                is io.github.akrishna87.sievr.data.CasReader.Outcome.Read -> {
                    pdf = null
                    val r = io.github.akrishna87.sievr.core.portfolio.ShareStatement.parse(o.text, market)
                    if (r.holdings.isEmpty() && r.unmatched.isEmpty()) {
                        importMessage = "No shares were found in that PDF. Use the NSDL or CDSL Consolidated Account Statement " +
                            "(CAS) that's emailed to you every month; it lists the shares in all your demat accounts."
                    } else shareResult = r
                }
                io.github.akrishna87.sievr.data.CasReader.Outcome.WrongPassword -> passwordError = "That password didn't open it. Try again."
                is io.github.akrishna87.sievr.data.CasReader.Outcome.Failed -> { pdf = null; importMessage = o.message }
            }
            reading = false
        }
    }

    fun importSpreadsheet(uri: android.net.Uri) {
        val text = readSpreadsheet(context, uri)
        val result = text?.let { PortfolioAnalyzer.parseCsv(it, SievrApp.instance.market.market) }
        if (result == null || result.holdings.isEmpty()) {
            importMessage = "Couldn't find stock names (or symbols or ISINs), quantities and buy prices in that file. Download the holdings " +
                "report from your broker (Groww: Profile → Reports → Holdings; Zerodha: Console → Portfolio → Holdings → Download) as Excel or CSV and try again." +
                (if (!result?.unmatched.isNullOrEmpty()) "\n\nNot recognised: " + result!!.unmatched.take(5).joinToString(", ") else "")
        } else {
            pendingImport = result.holdings
            pendingUnmatched = result.unmatched
        }
    }

    // One picker for everything: a statement PDF goes to the password step, an Excel or CSV report to the import.
    fun onPicked(uri: android.net.Uri) {
        if (isPdf(context, uri)) { pdf = uri; passwordError = null } else importSpreadsheet(uri)
    }
    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) onPicked(uri) }
    val importer = pdfPicker

    Scaffold(topBar = {
        TopAppBar(title = { Text("Portfolio") }, actions = {
            if (tab == 0) {
                IconButton(onClick = { pdfPicker.launch("*/*") }) { Icon(Icons.Default.FileUpload, "Import a statement PDF or broker report") }
                IconButton(onClick = { importer.launch("*/*") }) { Icon(Icons.Default.Description, "Import broker report (Excel or CSV)") }
                IconButton(onClick = { picking = true }) { Icon(Icons.Default.Add, "Add holding") }
            }
        })
    }) { padding ->
        Column(Modifier.padding(padding)) {
            androidx.compose.material3.TabRow(selectedTabIndex = tab) {
                androidx.compose.material3.Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Stocks") })
                androidx.compose.material3.Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Mutual funds") })
            }
            if (tab == 1) MutualFundsTab()
            else WithMarket { market ->
                if (user.holdings.isEmpty()) {
                    Column {
                        EmptyState("Track your shares", "Import your demat statement (NSDL or CDSL CAS PDF) to bring in every share at once, " +
                            "or import your broker's holdings report (Excel or CSV, e.g. Groww's Stocks_Holdings file), or add shares by hand. Sievr shows your gains, sector mix, weighted P/E and ROE, " +
                            "expected dividends and red flags. Everything stays on this phone.")
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                            androidx.compose.material3.Button(onClick = { pdfPicker.launch("*/*") }, Modifier.weight(1f)) { Text("Import statement") }
                            androidx.compose.material3.OutlinedButton(onClick = { picking = true }, Modifier.weight(1f)) { Text("Add stock") }
                        }
                        TextButton(onClick = { importer.launch("*/*") }, Modifier.padding(horizontal = 8.dp)) { Text("Use your broker's report, Excel or CSV (has your buy prices)") }
                    }
                } else PortfolioBody(user.holdings, market) { h -> editing = h.symbol to h }
                if (picking) StockSearch(market, onDismiss = { picking = false }, title = "Add a holding", recent = user.recentSymbols) {
                    picking = false
                    editing = it.symbol to user.holdings.firstOrNull { h -> h.symbol == it.symbol }
                }
                editing?.let { (sym, existing) -> HoldingDialog(sym, existing, market[sym]) { editing = null } }
            }
        }
    }
    if (pdf != null) PasswordDialog(passwordError, reading, onDismiss = { pdf = null; passwordError = null }) { readPdf(it) }
    shareResult?.let { r -> ShareResultDialog(r, onDismiss = { shareResult = null }) { replace -> store.importHoldings(r.holdings, replace); shareResult = null } }
    importMessage?.let {
        AlertDialog(onDismissRequest = { importMessage = null }, title = { Text("Import") }, text = { Text(it) },
            confirmButton = { TextButton(onClick = { importMessage = null }) { Text("OK") } })
    }
    if (pendingImport.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { pendingImport = emptyList() },
            title = { Text("Import ${pendingImport.size} holdings?") },
            text = {
                Text(pendingImport.take(8).joinToString("\n") { "${it.symbol}: ${Fmt.number(it.quantity, 0)} @ ${Fmt.price(it.averageCost)}" } +
                    (if (pendingImport.size > 8) "\n…and ${pendingImport.size - 8} more" else "") +
                    (if (pendingUnmatched.isNotEmpty()) "\n\nNot in Sievr's market data, so skipped: " + pendingUnmatched.take(5).joinToString(", ") else ""))
            },
            confirmButton = { TextButton(onClick = { store.importHoldings(pendingImport, replace = true); pendingImport = emptyList() }) { Text("Replace portfolio") } },
            dismissButton = { TextButton(onClick = { store.importHoldings(pendingImport, replace = false); pendingImport = emptyList() }) { Text("Add to it") } },
        )
    }
}

@Composable
private fun PortfolioBody(holdings: List<Holding>, market: Market, onEdit: (Holding) -> Unit) {
    val nav = LocalNav.current
    val r = remember(holdings, market) { PortfolioAnalyzer.analyze(holdings, market) }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            SievrCard(Modifier.fillMaxWidth().padding(16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Current value", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(Fmt.price(r.value), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Row {
                        Text("Total ${if (r.gain >= 0) "gain" else "loss"} ${Fmt.price(kotlin.math.abs(r.gain))} ", color = changeColor(r.gain))
                        ChangeText(r.gainPercent)
                    }
                    Row {
                        Text("Today ${if (r.dayChange >= 0) "+" else "−"}${Fmt.price(kotlin.math.abs(r.dayChange))} ", color = changeColor(r.dayChange),
                            style = MaterialTheme.typography.bodySmall)
                        Text(Fmt.percent(r.dayChangePercent, true, 2), color = changeColor(r.dayChangePercent), style = MaterialTheme.typography.bodySmall)
                    }
                    Text("Invested ${Fmt.price(r.cost)} · Dividends ≈ ${Fmt.price(r.annualDividends)} a year", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (r.warnings.isNotEmpty()) item {
            SectionHeader("Worth a look")
            Column(Modifier.padding(horizontal = 16.dp)) {
                r.warnings.forEach { Text("!  $it", style = MaterialTheme.typography.bodyMedium, color = LocalMarketColors.current.down) }
            }
        }
        item { SectionHeader("Holdings") }
        items(r.positions, key = { it.holding.symbol }) { p ->
            Row(
                Modifier.fillMaxWidth().clickable { if (p.stock != null) nav.stock(p.holding.symbol) else onEdit(p.holding) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(p.holding.symbol, style = MaterialTheme.typography.titleSmall)
                    Text("${Fmt.number(p.holding.quantity, 0)} @ ${Fmt.price(p.holding.averageCost)} · ${Fmt.percent(p.weight, decimals = 1)}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(Fmt.price(p.value), style = MaterialTheme.typography.bodyMedium)
                    Text(Fmt.percent(p.gainPercent, true), style = MaterialTheme.typography.bodySmall, color = changeColor(p.gainPercent))
                }
                TextButton(onClick = { onEdit(p.holding) }) { Text("Edit") }
            }
            HorizontalDivider()
        }
        item {
            SectionHeader("Sector mix")
            Column(Modifier.padding(horizontal = 16.dp)) { BarList(r.sectors.take(10), { Fmt.percent(it, decimals = 0) }) }
            Spacer(Modifier.height(8.dp))
            Text(
                listOf(1 to "Large", 2 to "Mid", 3 to "Small", 4 to "Micro").filter { (r.capMix[it.first] ?: 0.0) > 0 }
                    .joinToString(" · ") { "${it.second} ${Fmt.percent(r.capMix[it.first], decimals = 0)}" },
                Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall,
            )
        }
        item {
            SectionHeader("Portfolio X-ray")
            Column(Modifier.padding(horizontal = 16.dp)) {
                ScoreBar("Sievr score", r.weighted["sievrScore"])
                ScoreBar("Quality", r.weighted["qualityScore"])
                ScoreBar("Value", r.weighted["valueScore"])
                ScoreBar("Growth", r.weighted["growthScore"])
                ScoreBar("Momentum", r.weighted["momentumScore"])
                ScoreBar("Safety", r.weighted["safetyScore"])
            }
            MetricGrid(listOf(
                "Weighted P/E" to Fmt.metric("pe", r.weighted["pe"]),
                "Weighted P/B" to Fmt.metric("pb", r.weighted["pb"]),
                "Weighted ROE" to Fmt.percent(r.weighted["roe"]),
                "Weighted ROCE" to Fmt.percent(r.weighted["roce"]),
                "Debt / equity" to Fmt.number(r.weighted["de"]),
                "Dividend yield" to Fmt.percent(r.weighted["divYield"], decimals = 2),
                "Beta" to Fmt.number(r.weighted["beta"]),
                "Holdings' 1Y return" to Fmt.percent(r.weighted["ret1y"], true),
                "Sales growth (3Y)" to Fmt.percent(r.weighted["salesCagr3"]),
                "Largest holding" to Fmt.percent(r.topWeight, decimals = 0),
                "Effective no. of stocks" to Fmt.number(r.effectiveHoldings, 1),
                "Concentration (HHI)" to Fmt.number(r.concentration, 0),
            ))
            Text("Averages are weighted by each holding's value; P/E and P/B use the harmonic mean.", Modifier.padding(16.dp),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Start)
        }
    }
}

@Composable
private fun HoldingDialog(symbol: String, existing: Holding?, stock: Stock?, onDismiss: () -> Unit) {
    val store = SievrApp.instance.user
    var qty by remember { mutableStateOf(existing?.quantity?.let { Fmt.number(it, 0) } ?: "") }
    var avg by remember { mutableStateOf(existing?.averageCost?.let { "%.2f".format(it) } ?: stock?.get("price")?.let { "%.2f".format(it) } ?: "") }
    val q = qty.replace(",", "").toDoubleOrNull()
    val a = avg.replace(",", "").toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(symbol) },
        text = {
            Column {
                OutlinedTextField(qty, { qty = it }, label = { Text("Quantity") }, singleLine = true)
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(avg, { avg = it }, label = { Text("Average buy price (₹)") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(enabled = q != null && q > 0 && a != null && a >= 0, onClick = { store.setHolding(Holding(symbol, q!!, a!!)); onDismiss() }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (existing != null) TextButton(onClick = { store.removeHolding(symbol); onDismiss() }) { Text("Remove") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun ShareResultDialog(r: io.github.akrishna87.sievr.core.portfolio.ShareStatement.Result, onDismiss: () -> Unit, onImport: (Boolean) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Found ${r.holdings.size} stock${if (r.holdings.size == 1) "" else "s"}") },
        text = {
            Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                if (r.statementValue > 0) Text("Worth ${Fmt.price(r.statementValue)} on the statement.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                r.holdings.take(15).forEach { h ->
                    Text("• ${h.symbol}: ${Fmt.number(h.quantity, 0)} shares" + if (h.symbol in r.priceIsPlaceholder) "" else " @ ${Fmt.price(h.averageCost)}",
                        style = MaterialTheme.typography.bodySmall)
                }
                if (r.holdings.size > 15) Text("…and ${r.holdings.size - 15} more", style = MaterialTheme.typography.bodySmall)
                if (r.priceIsPlaceholder.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Demat statements don't show what you paid, so today's price is used as the buy price for " +
                        "${r.priceIsPlaceholder.size} stock(s). Gains will show from today until you set the real price: tap the stock under " +
                        "Holdings → Edit. A broker CSV includes buy prices if you'd rather import that.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                }
                if (r.unmatched.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Not in Sievr's market data (unlisted or suspended), so not imported: " + r.unmatched.take(5).joinToString(", "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { TextButton(enabled = r.holdings.isNotEmpty(), onClick = { onImport(true) }) { Text("Replace my stocks") } },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                TextButton(enabled = r.holdings.isNotEmpty(), onClick = { onImport(false) }) { Text("Add to them") }
            }
        },
    )
}

/** A spreadsheet file's contents as CSV text, whether it's Excel (.xlsx) or already CSV. */
internal fun readSpreadsheet(context: android.content.Context, uri: android.net.Uri): String? = runCatching {
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    if (io.github.akrishna87.sievr.core.util.Xlsx.looksLikeXlsx(bytes)) io.github.akrishna87.sievr.core.util.Xlsx.toCsv(bytes) else bytes.decodeToString()
}.getOrNull()

/** True when the file is a PDF (starts with "%PDF"), whatever its name or type says. */
internal fun isPdf(context: android.content.Context, uri: android.net.Uri): Boolean = runCatching {
    context.contentResolver.openInputStream(uri)?.use { input -> val b = ByteArray(5); input.read(b) == 5 && String(b) == "%PDF-" } ?: false
}.getOrDefault(false)
