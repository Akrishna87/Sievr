package io.github.akrishna87.sievr.core.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.io.InputStream
import java.util.zip.GZIPInputStream

object SnapshotIO {
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    /** Reads a snapshot, gzip-compressed or plain JSON. */
    @OptIn(ExperimentalSerializationApi::class)
    fun read(input: InputStream): Snapshot {
        val buffered = input.buffered()
        buffered.mark(2)
        val b0 = buffered.read()
        val b1 = buffered.read()
        buffered.reset()
        val stream = if (b0 == 0x1f && b1 == 0x8b) GZIPInputStream(buffered, 64 * 1024) else buffered
        return stream.use { json.decodeFromStream(Snapshot.serializer(), it) }
    }
}
