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
import androidx.compose.foundation.verticalScroll
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
import io.github.akrishna87.sievr.core.portfolio.Growth
import io.github.akrishna87.sievr.data.FAMILY
import io.github.akrishna87.sievr.data.Member
import io.github.akrishna87.sievr.ui.components.UserDate

/** Mutual fund holdings valued at the latest NAVs from AMFI, which refresh by themselves every business day. */
@Composable
fun MutualFundsTab(view: String, members: List<Member>, target: String, groupName: String, withOwner: (() -> Unit) -> Unit) {
    val app = SievrApp.instance
    val mf by app.mf.state.collectAsStateWithLifecycle()
    val user by app.user.data.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var searching by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Pair<MfScheme?, MfHolding?>?>(null) }
    var importResult by remember { mutableStateOf<MutualFunds.Import?>(null) }
    var importMessage by remember { mutableStateOf<String?>(null) }
    var pdf by remember { mutableStateOf<android.net.Uri?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var reading by remember { mutableStateOf(false) }
    var casResult by remember { mutableStateOf<io.github.akrishna87.sievr.core.mf.CasParser.Result?>(null) }
    var howTo by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { app.mf.start() }

    fun readPdf(password: String) {
        val uri = pdf ?: return
        val schemes = mf.data?.schemes ?: return
        reading = true
        scope.launch {
            when (val o = io.github.akrishna87.sievr.data.CasReader.read(context, uri, password)) {
                is io.github.akrishna87.sievr.data.CasReader.Outcome.Read -> {
                    pdf = null
                    val result = io.github.akrishna87.sievr.core.mf.CasParser.parse(o.text, schemes,
                        app.market.market?.stocks?.filter { it.isEtf }?.mapNotNull { it.data.isin }?.toSet() ?: emptySet())
                    if (result.holdings.isEmpty() && result.unmatched.isEmpty()) {
                        importMessage = "No mutual fund holdings were found in that PDF. Use a Consolidated Account Statement (CAS) " +
                            "from CAMS, KFintech, MF Central, NSDL or CDSL — see \"How do I get my statement?\""
                    } else casResult = result
                }
                io.github.akrishna87.sievr.data.CasReader.Outcome.WrongPassword -> passwordError = "That password didn't open it. Try again."
                is io.github.akrishna87.sievr.data.CasReader.Outcome.Failed -> { pdf = null; importMessage = o.message }
            }
            reading = false
        }
    }

    fun importSpreadsheet(uri: android.net.Uri) {
        val schemes = mf.data?.schemes ?: return
        val text = readSpreadsheet(context, uri)
        val result = text?.let { MutualFunds.parseCsv(it, schemes) }
        if (result == null || result.holdings.isEmpty()) {
            importMessage = "Couldn't find scheme names (or ISINs) and units in that file. Export your mutual fund holdings as Excel or CSV " +
                "from Groww, Kuvera, Coin or your CAMS/KFintech statement and try again, or add funds one by one."
        } else importResult = result
    }

    // One picker for everything: a statement PDF goes to the password step, an Excel or CSV report to the import.
    fun onPicked(uri: android.net.Uri) {
        if (isPdf(context, uri)) { pdf = uri; passwordError = null } else importSpreadsheet(uri)
    }
    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) onPicked(uri) }
    val importer = pdfPicker

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

    val family = view == FAMILY
    val mine = remember(user.mfHoldings, view, members) {
        val ids = members.map { it.id }.toSet()
        if (family) user.mfHoldings.filter { it.owner in ids } else user.mfHoldings.filter { it.owner == view }
    }
    val report = remember(mine, mf.byCode) { MutualFunds.analyze(if (family) MutualFunds.merge(mine) else mine, mf.byCode) }
    // CAGR from each member's own funds and dates.
    val growth = remember(mine, mf.byCode) {
        val positions = if (family) MutualFunds.analyze(mine, mf.byCode).positions else report.positions
        Growth.portfolio(positions.mapNotNull { p -> Growth.parse(p.holding.since)?.let { Growth.Lot(it, p.holding.invested, p.value) } },
            positions.sumOf { it.holding.invested })
    }
    val breakdown = remember(mine, mf.byCode, members) {
        if (!family) emptyList() else members.map { m ->
            val r = MutualFunds.analyze(mine.filter { it.owner == m.id }, mf.byCode)
            MemberTotals(m.name, r.value, r.invested)
        }
    }
    val label = when {
        members.size < 2 -> "Current value"
        family -> "$groupName total · current value"
        else -> (members.firstOrNull { it.id == view }?.name ?: "") + " · current value"
    }
    val targetName = members.firstOrNull { it.id == target }?.name?.takeIf { members.size > 1 }
    val onAdd = { withOwner { searching = true } }
    val onImport = { withOwner { pdfPicker.launch("*/*") } }

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
        if (mine.isEmpty()) {
            item {
                EmptyState(
                    "Track your mutual funds",
                    "Import your mutual fund statement (CAS PDF) to bring in every fund at once, or add funds one by one. Values update " +
                        "with the latest NAV every business day. Your statement is read on this phone and never uploaded.",
                )
            }
            item { Actions(onAdd = onAdd, onPdf = onImport, onCsv = onImport, onHowTo = { howTo = true }) }
        } else {
            item { Summary(report, label, growth) }
            if (family) item { MemberBreakdown(breakdown) }
            item { Actions(onAdd = onAdd, onPdf = onImport, onCsv = onImport, onHowTo = { howTo = true }) }
            item { SectionHeader("Funds (${report.positions.size})") }
            items(report.positions, key = { it.holding.code }) { p ->
                FundRow(p, cagr = if (family) null else Growth.cagr(p.holding.invested, p.value, Growth.parse(p.holding.since))) {
                    if (!family) editing = p.scheme to p.holding
                }
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
        editing = s to user.mfHoldings.firstOrNull { it.code == s.code && it.owner == target }
    }
    editing?.let { (scheme, holding) -> FundDialog(scheme, holding, holding?.owner ?: target) { editing = null } }
    importMessage?.let {
        AlertDialog(onDismissRequest = { importMessage = null }, title = { Text("Import") }, text = { Text(it) },
            confirmButton = { TextButton(onClick = { importMessage = null }) { Text("OK") } })
    }
    if (pdf != null) PasswordDialog(passwordError, reading, onDismiss = { pdf = null; passwordError = null }) { readPdf(it) }
    casResult?.let { r ->
        CasResultDialog(r, onDismiss = { casResult = null }) { replace ->
            app.user.importMfHoldings(r.holdings, replace, target); casResult = null
        }
    }
    if (howTo) HowToDialog { howTo = false }
    importResult?.let { r ->
        AlertDialog(
            onDismissRequest = { importResult = null },
            title = { Text("Import ${r.holdings.size} funds" + (targetName?.let { " into $it's portfolio" } ?: "") + "?") },
            text = {
                Text(
                    r.holdings.take(6).joinToString("\n") { "${it.name.take(40)}: ${Fmt.units(it.units)} units" } +
                        (if (r.holdings.size > 6) "\n…" else "") +
                        (if (r.unmatched.isNotEmpty()) "\n\nNot found (add these by hand): " + r.unmatched.take(5).joinToString(", ") else ""),
                )
            },
            confirmButton = { TextButton(onClick = { app.user.importMfHoldings(r.holdings, replace = true, owner = target); importResult = null }) { Text("Replace my funds") } },
            dismissButton = { TextButton(onClick = { app.user.importMfHoldings(r.holdings, replace = false, owner = target); importResult = null }) { Text("Add to them") } },
        )
    }
}

@Composable
internal fun PasswordDialog(error: String?, reading: Boolean, onDismiss: () -> Unit, onOpen: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!reading) onDismiss() },
        title = { Text("Statement password") },
        text = {
            Column {
                OutlinedTextField(password, { password = it }, label = { Text("PDF password") }, singleLine = true, isError = error != null,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    supportingText = { if (error != null) Text(error) })
                Text(
                    "Leave it blank if the PDF has no password (e.g. downloaded from DigiLocker).\n" +
                        "CAMS / KFintech / MF Central: the password you chose when requesting it.\n" +
                        "NSDL / CDSL: usually your PAN in capitals, sometimes followed by your date of birth (DDMMYYYY). The statement email says which.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (reading) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Reading your statement…", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = !reading, onClick = { onOpen(password) }) { Text("Open") } },
        dismissButton = { TextButton(enabled = !reading, onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun CasResultDialog(r: io.github.akrishna87.sievr.core.mf.CasParser.Result, onDismiss: () -> Unit, onImport: (Boolean) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Found ${r.holdings.size} fund${if (r.holdings.size == 1) "" else "s"}") },
        text = {
            Column(Modifier.androidxVerticalScroll()) {
                if (r.statementValue > 0) Text("Worth ${Fmt.price(r.statementValue)} on the statement.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                r.holdings.take(12).forEach { h ->
                    Text("• ${h.name}", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("   ${Fmt.units(h.units)} units · invested ${Fmt.price(h.invested)}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (r.holdings.size > 12) Text("…and ${r.holdings.size - 12} more", style = MaterialTheme.typography.bodySmall)
                if (r.etfsSkipped > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text("${r.etfsSkipped} ETF(s) left out here: ETFs are tracked under Stocks. Import the same statement there to add them.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                }
                if (r.missingCost > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text("${r.missingCost} fund(s) had no cost on the statement, so the current value is used as the amount invested. You can edit them afterwards.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                }
                if (r.unmatched.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Not in today's NAV list (merged or closed), so not imported: " +
                        r.unmatched.take(5).joinToString(", ") { it.name.ifBlank { it.isin } }, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { TextButton(enabled = r.holdings.isNotEmpty(), onClick = { onImport(true) }) { Text("Replace my funds") } },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                TextButton(enabled = r.holdings.isNotEmpty(), onClick = { onImport(false) }) { Text("Add to them") }
            }
        },
    )
}

@Composable
private fun HowToDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Get your statement (CAS)") },
        text = {
            Column(Modifier.androidxVerticalScroll()) {
                Text("One PDF lists every mutual fund you hold, whichever app, bank or broker you bought through. It's free.",
                    style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(10.dp))
                Text("CAMS (covers all fund houses)", style = MaterialTheme.typography.titleSmall)
                Text("1. Open camsonline.com → Investors → Statements → CAS – CAMS+KFintech.\n" +
                    "2. Choose Summary (or Detailed), your PAN and the email registered with your funds.\n" +
                    "3. Set a password. The PDF arrives by email in a few minutes.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(10.dp))
                Text("Or MF Central", style = MaterialTheme.typography.titleSmall)
                Text("Log in at mfcentral.com with your PAN and download the CAS.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(10.dp))
                Text("Funds held in a demat account", style = MaterialTheme.typography.titleSmall)
                Text("The monthly NSDL or CDSL CAS email works too (password: PAN + date of birth).", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(10.dp))
                Text("Then tap \"Import statement\", pick the PDF and type its password. Re-import any time to update your units after new SIP instalments.",
                    style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Got it") } },
    )
}

@Composable
private fun Modifier.androidxVerticalScroll(): Modifier = this.then(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()))

@Composable
private fun Summary(r: MutualFunds.Report, label: String, growth: Growth.Summary?) {
    // A day's change needs yesterday's NAV; until the second night of NAVs there isn't one.
    val hasDay = r.positions.any { it.scheme?.prevNav != null }
    io.github.akrishna87.sievr.ui.components.ReturnsCard(
        value = r.value, invested = r.invested, gain = r.gain, gainPercent = r.gainPercent,
        dayChange = if (hasDay) r.dayChange else null, dayPercent = if (hasDay) r.dayChangePercent else null,
        footnote = if (hasDay) null else "1-day returns appear once two days of NAVs have been collected.",
        label = label, cagr = growth, showCagr = true,
    )
}

@Composable
private fun Actions(onAdd: () -> Unit, onPdf: () -> Unit, onCsv: () -> Unit, onHowTo: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.Button(onClick = onPdf, Modifier.weight(1f)) {
                Icon(Icons.Default.FileUpload, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Import statement")
            }
            OutlinedButton(onClick = onAdd, Modifier.weight(1f)) {
                Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Add fund")
            }
        }
        Row {
            TextButton(onClick = onHowTo) { Text("How do I get my statement?") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onCsv) { Text("Use Excel or CSV") }
        }
    }
}

@Composable
private fun FundRow(p: MutualFunds.Position, cagr: Double?, onClick: () -> Unit) {
    io.github.akrishna87.sievr.ui.components.HoldingRow(
        title = p.name,
        detail = "${Fmt.units(p.holding.units)} units · NAV ${p.scheme?.let { Fmt.nav(it.nav) } ?: "—"}" +
            (p.averageNav?.let { " · avg ${Fmt.nav(it)}" } ?: ""),
        invested = p.holding.invested, value = p.value, gain = p.gain, gainPercent = p.gainPercent,
        dayPercent = p.scheme?.dayChangePercent, onClick = onClick, cagr = cagr,
    )
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
                        Text("${s.subCategory} · NAV ${Fmt.nav(s.nav)} on ${Fmt.isoDate(s.date)}", style = MaterialTheme.typography.bodySmall,
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
private fun FundDialog(scheme: MfScheme?, existing: MfHolding?, owner: String, onDismiss: () -> Unit) {
    val store = SievrApp.instance.user
    var units by remember { mutableStateOf(existing?.units?.let { Fmt.units(it) }?.replace(",", "") ?: "") }
    var invested by remember { mutableStateOf(existing?.invested?.let { "%.2f".format(java.util.Locale.US, it) } ?: "") }
    var bought by remember { mutableStateOf(UserDate.show(existing?.since)) }
    val u = units.replace(",", "").toDoubleOrNull()
    val inv = invested.replace(",", "").toDoubleOrNull()
    val since = UserDate.parse(bought)
    val dateOk = since != UserDate.INVALID
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
                OutlinedTextField(bought, { bought = it }, label = { Text("First invested on (DD-MM-YYYY)") }, singleLine = true,
                    isError = !dateOk, supportingText = { Text(if (dateOk) "Optional. Used for CAGR (for SIPs, the first instalment)" else "Use a past date like 15-04-2021") })
                Text(
                    "Units and amount are on your account statement or fund app. For SIPs, use the total units and total amount invested so far.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (scheme != null && u != null && u > 0) {
                    Spacer(Modifier.height(8.dp))
                    val value = scheme.nav * u
                    Text("Worth ${Fmt.price(value)} at NAV ${Fmt.nav(scheme.nav)}" +
                        (if (inv != null && inv > 0) " · ${Fmt.percent((value / inv - 1) * 100, true)}" else ""),
                        style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = u != null && u > 0 && inv != null && inv >= 0 && dateOk, onClick = {
                store.setMfHolding(MfHolding(code, u!!, inv!!, name, owner, since)); onDismiss()
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (existing != null) TextButton(onClick = { store.removeMfHolding(code, owner); onDismiss() }) { Text("Remove") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
