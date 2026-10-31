package io.github.akrishna87.sievr.data

import android.content.Context
import io.github.akrishna87.sievr.core.alerts.PriceAlert
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

@Serializable
data class UserData(
    val screens: List<Screen> = emptyList(),
    val watchlists: List<Watchlist> = listOf(Watchlist("default", "Watchlist")),
    val holdings: List<Holding> = emptyList(),
    val priceAlerts: List<PriceAlert> = emptyList(),
    /** Last results of each screen with alerts on, to find what entered and left. */
    val screenMatches: Map<String, Set<String>> = emptyMap(),
    val recentSymbols: List<String> = emptyList(),
    val watchColumns: List<String> = listOf("price", "chg1d", "pe", "roe", "sievrScore"),
    /** "system", "light" or "dark". */
    val theme: String = "system",
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
        d.copy(holdings = d.holdings.filterNot { it.symbol == holding.symbol } + holding)
    }

    fun removeHolding(symbol: String) = update { d -> d.copy(holdings = d.holdings.filterNot { it.symbol == symbol }) }

    fun importHoldings(holdings: List<Holding>, replace: Boolean) = update { d ->
        val base = if (replace) emptyList() else d.holdings.filterNot { h -> holdings.any { it.symbol == h.symbol } }
        d.copy(holdings = base + holdings)
    }

    // ---- alerts

    fun addAlert(alert: PriceAlert) = update { it.copy(priceAlerts = it.priceAlerts + alert) }
    fun removeAlert(id: String) = update { d -> d.copy(priceAlerts = d.priceAlerts.filterNot { it.id == id }) }
    fun setAlertActive(id: String, active: Boolean) = update { d ->
        d.copy(priceAlerts = d.priceAlerts.map { if (it.id == id) it.copy(active = active) else it })
    }
}
