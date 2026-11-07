package io.github.akrishna87.sievr.data

import android.content.Context
import io.github.akrishna87.sievr.BuildConfig
import io.github.akrishna87.sievr.core.mf.MfData
import io.github.akrishna87.sievr.core.mf.MfScheme
import io.github.akrishna87.sievr.core.model.SnapshotIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.zip.GZIPInputStream

/** Latest NAVs for every mutual fund scheme (from AMFI, rebuilt nightly), kept in one file that each update replaces. */
class MfRepository(private val context: Context) {
    data class State(
        val data: MfData? = null,
        val byCode: Map<Int, MfScheme> = emptyMap(),
        val loading: Boolean = false,
        val error: String? = null,
    )

    private val file = File(context.filesDir, "mf.json.gz")
    private val prefs = context.getSharedPreferences("mf", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    /** Loads the saved NAVs, then downloads new ones if the saved copy is a few hours old. */
    suspend fun start() {
        if (_state.value.data == null && file.exists()) runCatching { load() }.onFailure { file.delete() }
        if (System.currentTimeMillis() - prefs.getLong("checkedAt", 0) > 3 * 3600_000L || _state.value.data == null) refresh()
    }

    suspend fun refresh(): Boolean = mutex.withLock {
        _state.value = _state.value.copy(loading = true, error = null)
        try {
            withContext(Dispatchers.IO) {
                val bytes = Http.get(BuildConfig.DATA_URL + "mf.json.gz")
                val tmp = File(context.filesDir, "mf.download")
                tmp.writeBytes(bytes)
                read(tmp) // make sure it parses before replacing the good copy
                if (!tmp.renameTo(file)) { tmp.copyTo(file, overwrite = true); tmp.delete() }
            }
            prefs.edit().putLong("checkedAt", System.currentTimeMillis()).apply()
            load()
            true
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                loading = false,
                error = if (e is IOException) "Couldn't download the latest NAVs. Check your connection." else "NAV data couldn't be read.",
            )
            false
        }
    }

    private suspend fun load() {
        val data = withContext(Dispatchers.IO) { read(file) }
        _state.value = State(data, data.schemes.associateBy { it.code })
    }

    private fun read(f: File): MfData = GZIPInputStream(f.inputStream().buffered()).use {
        SnapshotIO.json.decodeFromString(MfData.serializer(), it.readBytes().decodeToString())
    }
}
