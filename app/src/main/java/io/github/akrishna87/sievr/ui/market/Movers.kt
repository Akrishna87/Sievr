package io.github.akrishna87.sievr.ui.market

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.akrishna87.sievr.core.mf.MfScheme
import io.github.akrishna87.sievr.core.model.Market
import io.github.akrishna87.sievr.core.model.Stock
import io.github.akrishna87.sievr.core.screen.ScreenEngine
import io.github.akrishna87.sievr.ui.components.Fmt
import io.github.akrishna87.sievr.ui.components.SortButton
import io.github.akrishna87.sievr.ui.components.StockRow
import io.github.akrishna87.sievr.ui.components.changeColor

enum class Asset(val label: String) { STOCKS("Stocks"), ETFS("ETFs"), FUNDS("Mutual funds") }

enum class Movers(val label: String) { GAINERS("Gainers"), LOSERS("Losers"), VOLUME("Volume"), HIGHS("52W highs"), LOWS("52W lows") }

val periods = listOf("chg1d" to "1D", "ret1w" to "1W", "ret1m" to "1M", "ret3m" to "3M", "ret1y" to "1Y")
private val caps = listOf(0 to "All sizes", 1 to "Large", 2 to "Mid", 3 to "Small", 4 to "Micro")
private val mfClasses = listOf("All", "Equity", "Debt", "Hybrid", "Gold & silver")
private val counts = listOf(10, 25, 50)

/** Every choice on the Top movers card, kept across screen changes. */
@Stable
class MoversState(
    asset: Asset, kind: Movers, period: String, cap: Int, mfClass: String, count: Int, sort: String, desc: Boolean,
) {
    var asset by mutableStateOf(asset)
    var kind by mutableStateOf(kind)
    var period by mutableStateOf(period)
    var cap by mutableStateOf(cap)
    var mfClass by mutableStateOf(mfClass)
    var count by mutableStateOf(count)
    /** "rank" keeps the list in gainer/loser order; anything else re-sorts the top N by that column. */
    var sort by mutableStateOf(sort)
    var desc by mutableStateOf(desc)
}

private val moversSaver = androidx.compose.runtime.saveable.listSaver<MoversState, Any>(
    save = { listOf(it.asset.name, it.kind.name, it.period, it.cap, it.mfClass, it.count, it.sort, it.desc) },
    restore = {
        MoversState(Asset.valueOf(it[0] as String), Movers.valueOf(it[1] as String), it[2] as String, it[3] as Int,
            it[4] as String, it[5] as Int, it[6] as String, it[7] as Boolean)
    },
)

/** The Top movers choices, kept when you leave the Market tab and come back. */
@Composable
fun rememberMoversState(): MoversState = rememberSaveable(saver = moversSaver) {
    MoversState(Asset.STOCKS, Movers.GAINERS, "chg1d", 0, "All", 10, "rank", true)
}

/** Gainers, losers, volume and 52-week lists for stocks or ETFs. */
fun stockMovers(market: Market, st: MoversState): List<Stock> {
    val etf = st.asset == Asset.ETFS
    val pool = market.stocks.filter { s ->
        if (etf) s.isEtf && s["chg1d"] != null && (s["turnover"] ?: 0.0) >= 0.05
        else !s.isEtf && (s["mcap"] ?: 0.0) >= 500 && s["chg1d"] != null && (st.cap == 0 || s["capCategory"]?.toInt() == st.cap)
    }
    val p = st.period
    val ranked = when (st.kind) {
        Movers.GAINERS -> pool.filter { it[p] != null }.sortedByDescending { it[p] }
        Movers.LOSERS -> pool.filter { it[p] != null }.sortedBy { it[p] }
        Movers.VOLUME -> pool.filter { (it["turnover"] ?: 0.0) >= (if (etf) 0.05 else 1.0) }.sortedByDescending { it["volumeRatio"] ?: 0.0 }
        Movers.HIGHS -> pool.filter { it["newHigh52"] == 1.0 }.sortedByDescending { it["turnover"] ?: 0.0 }
        Movers.LOWS -> pool.filter { it["newLow52"] == 1.0 }.sortedByDescending { it["turnover"] ?: 0.0 }
    }.take(st.count)
    return when (st.sort) {
        "rank" -> ranked
        "name" -> if (st.desc) ranked.sortedByDescending { it.name.lowercase() } else ranked.sortedBy { it.name.lowercase() }
        else -> ScreenEngine.sort(ranked, st.sort, st.desc)
    }
}

/** Mutual fund gainers and losers by the last day's NAV change: growth plans only, so IDCW payouts don't look like crashes. */
fun fundMovers(schemes: List<MfScheme>, st: MoversState): List<MfScheme> {
    val pool = schemes.filter { s ->
        val n = s.name.lowercase()
        val d = s.dayChangePercent
        d != null && kotlin.math.abs(d) < 15 && "growth" in n && "idcw" !in n && "dividend" !in n &&
            !("exchange traded" in s.category.lowercase() || Regex("""\betf\b""").containsMatchIn(n)) &&
            (st.mfClass == "All" || s.assetClass == st.mfClass)
    }
    val ranked = (if (st.kind == Movers.LOSERS) pool.sortedBy { it.dayChangePercent } else pool.sortedByDescending { it.dayChangePercent })
        .take(st.count)
    return when (st.sort) {
        "rank" -> ranked
        "name" -> if (st.desc) ranked.sortedByDescending { it.name.lowercase() } else ranked.sortedBy { it.name.lowercase() }
        "nav" -> if (st.desc) ranked.sortedByDescending { it.nav } else ranked.sortedBy { it.nav }
        else -> if (st.desc) ranked.sortedByDescending { it.dayChangePercent } else ranked.sortedBy { it.dayChangePercent }
    }
}

private fun stockSorts(st: MoversState) = listOf("rank" to (if (st.kind == Movers.LOSERS) "Biggest fall" else "Rank"),
    "chg1d" to "1D change", "ret1w" to "1W return", "ret1m" to "1M return", "ret1y" to "1Y return",
    "price" to "Price", "mcap" to "Market cap", "volumeRatio" to "Volume vs average", "turnover" to "Value traded", "name" to "Name")
private val fundSorts = listOf("rank" to "Rank", "chg" to "1D change", "nav" to "NAV", "name" to "Name")

/** The tabs and filters above the list. */
@Composable
fun MoversControls(st: MoversState) = Column {
    TabRow(selectedTabIndex = st.asset.ordinal) {
        Asset.entries.forEach { a ->
            Tab(st.asset == a, {
                st.asset = a; st.sort = "rank"; st.desc = true
                if (a == Asset.FUNDS && st.kind !in listOf(Movers.GAINERS, Movers.LOSERS)) st.kind = Movers.GAINERS
            }, text = { Text(a.label) })
        }
    }
    val kinds = if (st.asset == Asset.FUNDS) listOf(Movers.GAINERS, Movers.LOSERS) else Movers.entries
    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
        items(kinds) { m -> FilterChip(st.kind == m, { st.kind = m; st.sort = "rank"; st.desc = true }, { Text(m.label) }, Modifier.padding(4.dp)) }
    }
    when {
        st.asset == Asset.FUNDS -> LazyRow(contentPadding = PaddingValues(horizontal = 12.dp)) {
            items(mfClasses) { c -> FilterChip(st.mfClass == c, { st.mfClass = c }, { Text(c) }, Modifier.padding(4.dp)) }
        }
        st.kind == Movers.GAINERS || st.kind == Movers.LOSERS -> LazyRow(contentPadding = PaddingValues(horizontal = 12.dp)) {
            items(periods) { (id, label) -> FilterChip(st.period == id, { st.period = id }, { Text(label) }, Modifier.padding(4.dp)) }
        }
    }
    if (st.asset == Asset.STOCKS) LazyRow(contentPadding = PaddingValues(horizontal = 12.dp)) {
        items(caps) { (id, label) -> FilterChip(st.cap == id, { st.cap = id }, { Text(label) }, Modifier.padding(4.dp)) }
    }
    Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Show", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 4.dp, end = 4.dp))
        counts.forEach { n -> FilterChip(st.count == n, { st.count = n }, { Text("$n") }, Modifier.padding(horizontal = 3.dp)) }
        Spacer(Modifier.weight(1f))
        SortButton(if (st.asset == Asset.FUNDS) fundSorts else stockSorts(st), st.sort, st.desc) { id, d -> st.sort = id; st.desc = d }
    }
}

/** Which number a stock row shows on the right: whatever the list is ranked or sorted by. */
fun trailingFor(st: MoversState): String? = when {
    st.sort != "rank" && st.sort != "name" && st.sort != "chg1d" -> st.sort
    st.kind == Movers.VOLUME -> "volumeRatio"
    (st.kind == Movers.GAINERS || st.kind == Movers.LOSERS) && st.period != "chg1d" -> st.period
    else -> null
}

@Composable
fun FundMoverRow(s: MfScheme, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(s.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${s.subCategory} · ${s.amc}".trimEnd(' ', '·'), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 12.dp)) {
            Text(Fmt.nav(s.nav), style = MaterialTheme.typography.bodyMedium)
            Text(Fmt.percent(s.dayChangePercent, true, 2), style = MaterialTheme.typography.bodySmall, color = changeColor(s.dayChangePercent))
        }
    }
    HorizontalDivider()
}
