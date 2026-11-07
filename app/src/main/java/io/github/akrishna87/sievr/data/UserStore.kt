package io.github.akrishna87.sievr.data

import android.content.Context
import io.github.akrishna87.sievr.core.alerts.PriceAlert
import io.github.akrishna87.sievr.core.mf.MfHolding
import io.github.akrishna87.sievr.core.portfolio.Holding
import io.github.akrishna87.sievr.core.screen.Screen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

@Serializable
data class Watchlist(val id: String, val name: String, val symbols: List<String> = emptyList())

/** A family member whose stocks and funds are tracked separately. */
@Serializable
data class Member(val id: String, val name: String)

/** The Portfolio view that shows everyone together. */
const val FAMILY = "all"

@Serializable
data class UserData(
    val screens: List<Screen> = emptyList(),
    val watchlists: List<Watchlist> = listOf(Watchlist("default", "Watchlist")),
    val holdings: List<Holding> = emptyList(),
    val mfHoldings: List<MfHolding> = emptyList(),
    /** Family members; holdings belong to one by id. Starts with just "Me". */
    val members: List<Member> = listOf(Member(io.github.akrishna87.sievr.core.portfolio.DEFAULT_OWNER, "Me")),
    /** The Portfolio view last chosen: a member's id, or FAMILY for everyone together. */
    val portfolioView: String = FAMILY,
    val priceAlerts: List<PriceAlert> = emptyList(),
    /** Last results of each screen with alerts on, to find what entered and left. */
    val screenMatches: Map<String, Set<String>> = emptyMap(),
    val recentSymbols: List<String> = emptyList(),
    val watchColumns: List<String> = listOf("price", "chg1d", "pe", "roe", "sievrScore"),
    /** "system", "light" or "dark". */
    val theme: String = "system",
    /** Text size multiplier: 0.9 small, 1.0 default, 1.15 large, 1.3 extra large. */
    val fontScale: Float = 1f,
    /** "system", "serif", "condensed", "mono" or "handwritten". */
    val fontFamily: String = "system",
    /** Notify when a watchlist stock moves this much in a day, %; 0 = off. */
    val watchMoveAlert: Double = 5.0,
    val dailyDigest: Boolean = true,
    val acceptedDisclaimer: Boolean = false,
    /** Snapshot (generatedAt) that alerts were last evaluated for. */
    val alertsCheckedFor: String = "",
)

/** The user's own data, saved as one JSON file on the phone. Nothing leaves the device. */
class UserStore(context: Context) {
    private val file = File(context.filesDir, "user.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeLock = Mutex()
    private val _data = MutableStateFlow(read())
    val data: StateFlow<UserData> = _data.asStateFlow()

    private fun read(): UserData =
        runCatching { json.decodeFromString(UserData.serializer(), file.readText()) }.getOrDefault(UserData())

    fun update(change: (UserData) -> UserData) {
        _data.update(change)
        val snapshot = _data.value
        scope.launch {
            writeLock.withLock {
                val tmp = File(file.parentFile, "user.json.tmp")
                tmp.writeText(json.encodeToString(UserData.serializer(), snapshot))
                if (!tmp.renameTo(file)) { tmp.copyTo(file, true); tmp.delete() }
            }
        }
    }

    // ---- screens

    fun saveScreen(screen: Screen) = update { d ->
        val exists = d.screens.any { it.id == screen.id }
        d.copy(screens = if (exists) d.screens.map { if (it.id == screen.id) screen else it } else d.screens + screen)
    }

    fun deleteScreen(id: String) = update { d ->
        d.copy(screens = d.screens.filterNot { it.id == id }, screenMatches = d.screenMatches - id)
    }

    fun setMatches(id: String, symbols: Set<String>) = update { it.copy(screenMatches = it.screenMatches + (id to symbols)) }

    // ---- watchlists

    fun isWatched(symbol: String) = data.value.watchlists.any { symbol in it.symbols }

    fun toggleWatch(symbol: String, listId: String = data.value.watchlists.first().id) = update { d ->
        d.copy(watchlists = d.watchlists.map { w ->
            if (w.id != listId) w
            else if (symbol in w.symbols) w.copy(symbols = w.symbols - symbol)
            else w.copy(symbols = w.symbols + symbol)
        })
    }

    fun addWatchlist(name: String): String {
        val id = UUID.randomUUID().toString()
        update { it.copy(watchlists = it.watchlists + Watchlist(id, name)) }
        return id
    }

    fun renameWatchlist(id: String, name: String) = update { d -> d.copy(watchlists = d.watchlists.map { if (it.id == id) it.copy(name = name) else it }) }

    fun deleteWatchlist(id: String) = update { d ->
        val rest = d.watchlists.filterNot { it.id == id }
        d.copy(watchlists = rest.ifEmpty { listOf(Watchlist("default", "Watchlist")) })
    }

    fun addRecent(symbol: String) = update { d -> d.copy(recentSymbols = (listOf(symbol) + d.recentSymbols.filterNot { it == symbol }).take(12)) }

    // ---- portfolio

    fun setHolding(holding: Holding) = update { d ->
        d.copy(holdings = d.holdings.filterNot { it.symbol == holding.symbol && it.owner == holding.owner } + holding)
    }

    fun removeHolding(symbol: String, owner: String) = update { d ->
        d.copy(holdings = d.holdings.filterNot { it.symbol == symbol && it.owner == owner })
    }

    /** Imports into one member's portfolio; "replace" clears only that member's stocks. */
    fun importHoldings(holdings: List<Holding>, replace: Boolean, owner: String) = update { d ->
        // A fresh statement has no buy dates, so keep the ones typed in before.
        val incoming = holdings.map { h -> h.copy(owner = owner, since = h.since ?: d.holdings.firstOrNull { it.owner == owner && it.symbol == h.symbol }?.since) }
        val others = d.holdings.filterNot { it.owner == owner }
        val mine = if (replace) emptyList() else d.holdings.filter { h -> h.owner == owner && incoming.none { it.symbol == h.symbol } }
        d.copy(holdings = others + mine + incoming)
    }

    // ---- mutual funds

    fun setMfHolding(holding: MfHolding) = update { d ->
        d.copy(mfHoldings = d.mfHoldings.filterNot { it.code == holding.code && it.owner == holding.owner } + holding)
    }

    fun removeMfHolding(code: Int, owner: String) = update { d ->
        d.copy(mfHoldings = d.mfHoldings.filterNot { it.code == code && it.owner == owner })
    }

    /** Imports into one member's funds; "replace" clears only that member's funds. */
    fun importMfHoldings(holdings: List<MfHolding>, replace: Boolean, owner: String) = update { d ->
        val incoming = holdings.map { h -> h.copy(owner = owner, since = h.since ?: d.mfHoldings.firstOrNull { it.owner == owner && it.code == h.code }?.since) }
        val others = d.mfHoldings.filterNot { it.owner == owner }
        val mine = if (replace) emptyList() else d.mfHoldings.filter { h -> h.owner == owner && incoming.none { it.code == h.code } }
        d.copy(mfHoldings = others + mine + incoming)
    }

    // ---- family members

    fun addMember(name: String): String {
        val id = UUID.randomUUID().toString()
        update { it.copy(members = it.members + Member(id, name), portfolioView = id) }
        return id
    }

    fun renameMember(id: String, name: String) = update { d -> d.copy(members = d.members.map { if (it.id == id) it.copy(name = name) else it }) }

    /** Removes a member and their holdings; the last member can't be removed. */
    fun removeMember(id: String) = update { d ->
        if (d.members.size <= 1) d
        else d.copy(
            members = d.members.filterNot { it.id == id },
            holdings = d.holdings.filterNot { it.owner == id },
            mfHoldings = d.mfHoldings.filterNot { it.owner == id },
            portfolioView = FAMILY,
        )
    }

    fun setPortfolioView(view: String) = update { it.copy(portfolioView = view) }

    // ---- alerts

    fun addAlert(alert: PriceAlert) = update { it.copy(priceAlerts = it.priceAlerts + alert) }
    fun removeAlert(id: String) = update { d -> d.copy(priceAlerts = d.priceAlerts.filterNot { it.id == id }) }
    fun setAlertActive(id: String, active: Boolean) = update { d ->
        d.copy(priceAlerts = d.priceAlerts.map { if (it.id == id) it.copy(active = active) else it })
    }
}
