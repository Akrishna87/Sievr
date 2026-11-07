package io.github.akrishna87.sievr.ui.portfolio

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.akrishna87.sievr.SievrApp
import io.github.akrishna87.sievr.core.mf.MfHolding
import io.github.akrishna87.sievr.core.mf.MfScheme
import io.github.akrishna87.sievr.core.mf.MutualFunds
import io.github.akrishna87.sievr.ui.components.BarList
import io.github.akrishna87.sievr.ui.components.CenterMessage
import io.github.akrishna87.sievr.ui.components.ChangeText
import io.github.akrishna87.sievr.ui.components.EmptyState
import io.github.akrishna87.sievr.ui.components.Fmt
import io.github.akrishna87.sievr.ui.components.SectionHeader
import io.github.akrishna87.sievr.ui.components.SievrCard
import io.github.akrishna87.sievr.ui.components.changeColor
import kotlinx.coroutines.launch

/** Mutual fund holdings valued at the latest NAVs from AMFI, which refresh by themselves every business day. */
@Composable
fun MutualFundsTab() {
    val app = SievrApp.instance
    val mf by app.mf.state.collectAsStateWithLifecycle()
    val user by app.user.data.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var searching by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Pair<MfScheme?, MfHolding?>?>(null) }
    var importResult by remember { mutableStateOf<MutualFunds.Import?>(null) }
    var importMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { app.mf.start() }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val schemes = mf.data?.schemes ?: return@rememberLauncherForActivityResult
        val text = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }.getOrNull()
        val result = text?.let { MutualFunds.parseCsv(it, schemes) }
        if (result == null || result.holdings.isEmpty()) {
            importMessage = "Couldn't find scheme names (or ISINs) and units in that file. Export your mutual fund holdings as CSV " +
                "from Groww, Kuvera, Coin or your CAMS/KFintech statement and try again, or add funds one by one."
        } else importResult = result
    }

    val data = mf.data
    if (data == null) {
        CenterMessage {
            if (mf.loading || mf.error == null) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("Downloading the latest NAVs…")
            } else {
                Text(mf.error ?: "", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = { scope.launch { app.mf.refresh() } }) { Text("Try again") }
            }
        }
        return
    }

    val report = remember(user.mfHoldings, mf.byCode) { MutualFunds.analyze(user.mfHoldings, mf.byCode) }

    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "NAVs as of ${Fmt.isoDate(report.navDate ?: data.asOf)} · updated every business day",
                    Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (mf.loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else androidx.compose.material3.IconButton(onClick = { scope.launch { app.mf.refresh() } }) { Icon(Icons.Default.Refresh, "Refresh NAVs") }
            }
            mf.error?.let { Text(it, Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        }
        if (user.mfHoldings.isEmpty()) {
            item {
                EmptyState(
                    "Track your mutual funds",
                    "Add each fund with its units and the amount you invested, or import a holdings CSV. Values update with the latest NAV " +
                        "every business day. Everything stays on this phone.",
                )
            }
            item { Actions(onAdd = { searching = true }, onImport = { importer.launch("*/*") }) }
        } else {
            item { Summary(report) }
            item { Actions(onAdd = { searching = true }, onImport = { importer.launch("*/*") }) }
            item { SectionHeader("Funds (${report.positions.size})") }
            items(report.positions, key = { it.holding.code }) { p ->
                FundRow(p) { editing = p.scheme to p.holding }
                HorizontalDivider()
            }
            if (report.missing.isNotEmpty()) item {
                Text("${report.missing.size} fund(s) no longer appear in AMFI's NAV list (merged or closed); they're shown at the amount invested.",
                    Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            item {
                SectionHeader("Asset mix")
                Column(Modifier.padding(horizontal = 16.dp)) { BarList(report.assetMix, { Fmt.percent(it, decimals = 0) }) }
            }
            item {
                SectionHeader("By category")
                Column(Modifier.padding(horizontal = 16.dp)) { BarList(report.byCategory.take(10), { Fmt.percent(it, decimals = 0) }) }
            }
            item {
                SectionHeader("By fund house")
                Column(Modifier.padding(horizontal = 16.dp)) { BarList(report.byAmc.take(10), { Fmt.percent(it, decimals = 0) }) }
            }
        }
    }

    if (searching) SchemeSearch(data.schemes, onDismiss = { searching = false }) { s ->
        searching = false
        editing = s to user.mfHoldings.firstOrNull { it.code == s.code }
    }
    editing?.let { (scheme, holding) -> FundDialog(scheme, holding) { editing = null } }
    importMessage?.let {
        AlertDialog(onDismissRequest = { importMessage = null }, title = { Text("Import") }, text = { Text(it) },
            confirmButton = { TextButton(onClick = { importMessage = null }) { Text("OK") } })
    }
    importResult?.let { r ->
        AlertDialog(
            onDismissRequest = { importResult = null },
            title = { Text("Import ${r.holdings.size} funds?") },
            text = {
                Text(
                    r.holdings.take(6).joinToString("\n") { "${it.name.take(40)}: ${Fmt.number(it.units, 3)} units" } +
                        (if (r.holdings.size > 6) "\n…" else "") +
                        (if (r.unmatched.isNotEmpty()) "\n\nNot found (add these by hand): " + r.unmatched.take(5).joinToString(", ") else ""),
                )
            },
            confirmButton = { TextButton(onClick = { app.user.importMfHoldings(r.holdings, replace = true); importResult = null }) { Text("Replace my funds") } },
            dismissButton = { TextButton(onClick = { app.user.importMfHoldings(r.holdings, replace = false); importResult = null }) { Text("Add to them") } },
        )
    }
}

@Composable
private fun Summary(r: MutualFunds.Report) {
    SievrCard(Modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("Current value", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(Fmt.price(r.value), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Row {
                Text("Total ${if (r.gain >= 0) "gain" else "loss"} ${Fmt.price(kotlin.math.abs(r.gain))} ", color = changeColor(r.gain))
                ChangeText(r.gainPercent)
            }
            Row {
                Text("Last day ${if (r.dayChange >= 0) "+" else "−"}${Fmt.price(kotlin.math.abs(r.dayChange))} ", color = changeColor(r.dayChange),
                    style = MaterialTheme.typography.bodySmall)
                Text(Fmt.percent(r.dayChangePercent, true, 2), color = changeColor(r.dayChangePercent), style = MaterialTheme.typography.bodySmall)
            }
            Text("Invested ${Fmt.price(r.invested)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Actions(onAdd: () -> Unit, onImport: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onAdd, Modifier.weight(1f)) {
            Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Add fund")
        }
        OutlinedButton(onClick = onImport, Modifier.weight(1f)) {
            Icon(Icons.Default.FileUpload, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Import CSV")
        }
    }
}

@Composable
private fun FundRow(p: MutualFunds.Position, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(p.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                "${Fmt.number(p.holding.units, 3)} units · NAV ${p.scheme?.let { Fmt.price(it.nav) } ?: "—"}" +
                    (p.averageNav?.let { " · avg ${Fmt.price(it)}" } ?: ""),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(Fmt.price(p.value), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(Fmt.percent(p.gainPercent, true), style = MaterialTheme.typography.bodySmall, color = changeColor(p.gainPercent))
            p.scheme?.dayChangePercent?.let { Text("day ${Fmt.percent(it, true, 2)}", style = MaterialTheme.typography.labelSmall, color = changeColor(it)) }
        }
    }
}

@Composable
private fun SchemeSearch(schemes: List<MfScheme>, onDismiss: () -> Unit, onPick: (MfScheme) -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember(query, schemes) { if (query.trim().length < 2) emptyList() else MutualFunds.search(schemes, query) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.fillMaxHeight(0.9f)) {
            Text("Add a fund", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))
            OutlinedTextField(
                query, { query = it }, Modifier.fillMaxWidth().padding(16.dp, 8.dp),
                placeholder = { Text("Fund name, e.g. parag flexi direct") },
                leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true,
            )
            Text(
                if (query.trim().length < 2) "Type a few words of the fund's name. You can also paste its ISIN or AMFI code."
                else "${results.size} match${if (results.size == 1) "" else "es"}. Pick the exact plan you hold (Direct or Regular, Growth or IDCW).",
                Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyColumn {
                items(results, key = { it.code }) { s ->
                    Column(Modifier.fillMaxWidth().clickable { onPick(s) }.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Text(s.name, style = MaterialTheme.typography.bodyMedium)
                        Text("${s.subCategory} · NAV ${Fmt.price(s.nav)} on ${Fmt.isoDate(s.date)}", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

/** Units and amount invested for one fund; also shows what it's worth at today's NAV. */
@Composable
private fun FundDialog(scheme: MfScheme?, existing: MfHolding?, onDismiss: () -> Unit) {
    val store = SievrApp.instance.user
    var units by remember { mutableStateOf(existing?.units?.let { Fmt.number(it, 3) }?.replace(",", "") ?: "") }
    var invested by remember { mutableStateOf(existing?.invested?.let { "%.2f".format(java.util.Locale.US, it) } ?: "") }
    val u = units.replace(",", "").toDoubleOrNull()
    val inv = invested.replace(",", "").toDoubleOrNull()
    val code = scheme?.code ?: existing?.code ?: return
    val name = scheme?.name ?: existing?.name ?: "Scheme $code"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(name, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column {
                OutlinedTextField(units, { units = it }, label = { Text("Units") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(invested, { invested = it }, label = { Text("Amount invested (₹)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                Spacer(Modifier.height(8.dp))
                Text(
                    "Both are on your account statement or fund app. For SIPs, use the total units and total amount invested so far.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (scheme != null && u != null && u > 0) {
                    Spacer(Modifier.height(8.dp))
                    val value = scheme.nav * u
                    Text("Worth ${Fmt.price(value)} at NAV ${Fmt.price(scheme.nav)}" +
                        (if (inv != null && inv > 0) " · ${Fmt.percent((value / inv - 1) * 100, true)}" else ""),
                        style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = u != null && u > 0 && inv != null && inv >= 0, onClick = {
                store.setMfHolding(MfHolding(code, u!!, inv!!, name)); onDismiss()
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (existing != null) TextButton(onClick = { store.removeMfHolding(code); onDismiss() }) { Text("Remove") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
