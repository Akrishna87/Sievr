package io.github.akrishna87.sievr.core.util

internal fun median(values: List<Double>): Double? {
    if (values.isEmpty()) return null
    val s = values.sorted()
    val n = s.size
    return if (n % 2 == 1) s[n / 2] else (s[n / 2 - 1] + s[n / 2]) / 2
}

internal fun Double.finiteOrNull(): Double? = if (isFinite()) this else null

/**
 * Percentile rank (0–100) of every value among [values]; ties share their average rank.
 * Keys with null values are left out.
 */
internal fun <K> percentileRanks(values: Map<K, Double>): Map<K, Double> {
    if (values.isEmpty()) return emptyMap()
    if (values.size == 1) return values.mapValues { 50.0 }
    val sorted = values.entries.sortedBy { it.value }
    val out = HashMap<K, Double>(values.size)
    var i = 0
    while (i < sorted.size) {
        var j = i
        while (j + 1 < sorted.size && sorted[j + 1].value == sorted[i].value) j++
        val rank = (i + j) / 2.0 / (sorted.size - 1) * 100
        for (k in i..j) out[sorted[k].key] = rank
        i = j + 1
    }
    return out
}
