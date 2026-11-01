package io.github.akrishna87.sievr.data

import android.content.Context
import io.github.akrishna87.sievr.BuildConfig
import io.github.akrishna87.sievr.core.model.SnapshotIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

/** What CI publishes as version.json next to Sievr.apk on the app-latest release. */
@Serializable
data class AppVersion(
    val versionCode: Int,
    val versionName: String = "",
    val apk: String = "Sievr.apk",
    val notes: String = "",
)

/**
 * Checks the app-latest release for a newer build than the one installed. Sievr isn't on the Play Store,
 * so this is how friends and family hear about updates.
 */
class UpdateChecker(context: Context) {
    private val prefs = context.getSharedPreferences("updates", Context.MODE_PRIVATE)
    private val _available = MutableStateFlow<AppVersion?>(null)
    /** A newer version, or null when this build is the latest (or the check hasn't run). */
    val available: StateFlow<AppVersion?> = _available.asStateFlow()

    val apkUrl: String get() = RELEASE + (available.value?.apk ?: "Sievr.apk")

    /** Returns the newer version, if there is one. Network errors just mean "nothing new". */
    suspend fun check(): AppVersion? = withContext(Dispatchers.IO) {
        val latest = runCatching {
            SnapshotIO.json.decodeFromString(AppVersion.serializer(), String(Http.get(RELEASE + "version.json")))
        }.getOrNull()
        val newer = latest?.takeIf { it.versionCode > BuildConfig.VERSION_CODE }
        _available.value = newer
        newer
    }

    /** True the first time a given version is seen, so it's only notified once. */
    fun shouldNotify(version: AppVersion): Boolean {
        if (prefs.getInt("notified", 0) >= version.versionCode) return false
        prefs.edit().putInt("notified", version.versionCode).apply()
        return true
    }

    companion object {
        const val RELEASE = "https://github.com/Akrishna87/Sievr/releases/download/app-latest/"
    }
}
