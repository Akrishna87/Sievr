package io.github.akrishna87.sievr.data

import io.github.akrishna87.sievr.BuildConfig
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Plain HTTPS GET that follows GitHub's release redirects (to a different host). */
object Http {
    fun get(url: String, redirects: Int = 5): ByteArray {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 20_000
        conn.readTimeout = 60_000
        conn.instanceFollowRedirects = false
        conn.setRequestProperty("User-Agent", "Sievr/${BuildConfig.VERSION_NAME} (Android)")
        try {
            val code = conn.responseCode
            if (code in 300..399 && redirects > 0) {
                val next = conn.getHeaderField("Location") ?: throw IOException("Redirect without location")
                return get(URL(URL(url), next).toString(), redirects - 1)
            }
            if (code != 200) throw IOException("HTTP $code for $url")
            return conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }
}
