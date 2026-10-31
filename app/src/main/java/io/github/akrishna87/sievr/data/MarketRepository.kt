package io.github.akrishna87.sievr.data

import android.content.Context
import io.github.akrishna87.sievr.BuildConfig
import io.github.akrishna87.sievr.core.metrics.Enricher
import io.github.akrishna87.sievr.core.model.Market
import io.github.akrishna87.sievr.core.model.SnapshotIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

sealed interface MarketState {
    data object Loading : MarketState
    data class Ready(
        val market: Market,
        val refreshing: Boolean = false,
        val error: String? = null,
    ) : MarketState
    data class Failed(val message: String, val refreshing: Boolean = false) : MarketState
}

@Serializable
private data class Meta(val asOf: String = "", val generatedAt: String = "", val stocks: Int = 0, val bytes: Long = 0)

/**
 * Keeps the market data: the last downloaded snapshot on disk, enriched in memory, and a check
 * for a newer one on the release page. All screening then happens on the phone.
 */
class MarketRepository(private val context: Context) {
    private val file = File(context.filesDir, "snapshot.json.gz")
    private val prefs = context.getSharedPreferences("market", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val _state = MutableStateFlow<MarketState>(MarketState.Loading)
    val state: StateFlow<MarketState> = _state.asStateFlow()

    val market: Market? get() = (state.value as? MarketState.Ready)?.market

    val lastChecked: Long get() = prefs.getLong("checkedAt", 0)

    /** Loads what's on disk, then looks for newer data. */
    suspend fun start() {
        if (market == null && file.exists()) {
            runCatching { load() }.onFailure { file.delete() }
        }
        refresh(force = false)
    }

    /**
     * Downloads the latest snapshot if it is newer than the one on disk.
     * Returns true when new data was loaded.
     */
    suspend fun refresh(force: Boolean): Boolean = mutex.withLock {
        setRefreshing(true)
        try {
            val meta = withContext(Dispatchers.IO) {
                SnapshotIO.json.decodeFromString(Meta.serializer(), String(fetch(BuildConfig.DATA_URL + "meta.json")))
            }
            prefs.edit().putLong("checkedAt", System.currentTimeMillis()).apply()
            val have = prefs.getString("generatedAt", null)
            if (!force && have == meta.generatedAt && file.exists()) {
                if (market == null) load()
                setRefreshing(false)
                return false
            }
            withContext(Dispatchers.IO) {
                val tmp = File(context.filesDir, "snapshot.download")
                tmp.writeBytes(fetch(BuildConfig.DATA_URL + "snapshot.json.gz"))
                // Make sure it parses before replacing the good copy.
                tmp.inputStream().use { SnapshotIO.read(it) }
                if (!tmp.renameTo(file)) {
                    tmp.copyTo(file, overwrite = true)
                    tmp.delete()
                }
            }
            prefs.edit().putString("generatedAt", meta.generatedAt).apply()
            load()
            true
        } catch (e: Exception) {
            val message = when (e) {
                is IOException -> "Couldn't reach the market data server. Check your connection."
                else -> "Market data couldn't be read (${e.javaClass.simpleName})."
            }
            _state.value = when (val s = _state.value) {
                is MarketState.Ready -> s.copy(refreshing = false, error = message)
                else -> MarketState.Failed(message)
            }
            false
        }
    }

    private suspend fun load() {
        val market = withContext(Dispatchers.Default) {
            val snapshot = file.inputStream().use { SnapshotIO.read(it) }
            Enricher.enrich(snapshot)
        }
        _state.value = MarketState.Ready(market)
    }

    private fun setRefreshing(on: Boolean) {
        _state.value = when (val s = _state.value) {
            is MarketState.Ready -> s.copy(refreshing = on, error = null)
            is MarketState.Failed -> s.copy(refreshing = on)
            MarketState.Loading -> MarketState.Loading
        }
    }

    private fun fetch(url: String, redirects: Int = 5): ByteArray {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 20_000
        conn.readTimeout = 60_000
        conn.instanceFollowRedirects = false
        conn.setRequestProperty("User-Agent", "Sievr/${BuildConfig.VERSION_NAME} (Android)")
        try {
            val code = conn.responseCode
            if (code in 300..399 && redirects > 0) {
                val next = conn.getHeaderField("Location") ?: throw IOException("Redirect without location")
                return fetch(URL(URL(url), next).toString(), redirects - 1)
            }
            if (code != 200) throw IOException("HTTP $code for $url")
            return conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }
}
