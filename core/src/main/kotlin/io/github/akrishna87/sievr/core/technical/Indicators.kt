package io.github.akrishna87.sievr.core.technical

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/** Price indicators over a list of closes, oldest first. Each returns null when there isn't enough data. */
object Indicators {
    fun sma(values: List<Double>, period: Int, endExclusive: Int = values.size): Double? {
        if (period <= 0 || endExclusive < period) return null
        var sum = 0.0
        for (i in endExclusive - period until endExclusive) sum += values[i]
        return sum / period
    }

    /** Exponential moving average series, same length as [values]; entries before [period] − 1 are null. */
    fun emaSeries(values: List<Double>, period: Int): List<Double?> {
        val out = MutableList<Double?>(values.size) { null }
        if (values.size < period) return out
        val k = 2.0 / (period + 1)
        var ema = values.subList(0, period).average()
        out[period - 1] = ema
        for (i in period until values.size) {
            ema = values[i] * k + ema * (1 - k)
            out[i] = ema
        }
        return out
    }

    /** Wilder's RSI on the last bar. */
    fun rsi(values: List<Double>, period: Int = 14, endExclusive: Int = values.size): Double? {
        if (endExclusive <= period) return null
        var gain = 0.0
        var loss = 0.0
        for (i in 1..period) {
            val d = values[i] - values[i - 1]
            if (d > 0) gain += d else loss -= d
        }
        var avgGain = gain / period
        var avgLoss = loss / period
        for (i in period + 1 until endExclusive) {
            val d = values[i] - values[i - 1]
            avgGain = (avgGain * (period - 1) + (if (d > 0) d else 0.0)) / period
            avgLoss = (avgLoss * (period - 1) + (if (d < 0) -d else 0.0)) / period
        }
        if (avgLoss == 0.0) return if (avgGain == 0.0) 50.0 else 100.0
        val rs = avgGain / avgLoss
        return 100 - 100 / (1 + rs)
    }

    data class Macd(val macd: Double, val signal: Double, val histogram: Double, val bullishCross: Boolean)

    fun macd(values: List<Double>, fast: Int = 12, slow: Int = 26, signal: Int = 9): Macd? {
        if (values.size < slow + signal) return null
        val fastEma = emaSeries(values, fast)
        val slowEma = emaSeries(values, slow)
        val line = (slow - 1 until values.size).map { fastEma[it]!! - slowEma[it]!! }
        val signalSeries = emaSeries(line, signal)
        val last = line.size - 1
        val m = line[last]
        val s = signalSeries[last] ?: return null
        // Crossed up within the last 3 bars.
        var cross = false
        for (i in maxOf(signal, last - 2)..last) {
            val prevHist = line[i - 1] - (signalSeries[i - 1] ?: continue)
            val hist = line[i] - (signalSeries[i] ?: continue)
            if (prevHist <= 0 && hist > 0) cross = true
        }
        return Macd(m, s, m - s, cross)
    }

    /** Percent change from `bars` bars ago to the last bar. */
    fun change(values: List<Double>, bars: Int, endExclusive: Int = values.size): Double? {
        val end = endExclusive - 1
        val start = end - bars
        if (start < 0 || values[start] <= 0) return null
        return (values[end] / values[start] - 1) * 100
    }

    /** Compound yearly growth in percent from [first] to [last] over [years]. */
    fun cagr(first: Double?, last: Double?, years: Double): Double? {
        if (first == null || last == null || years <= 0 || first <= 0 || last <= 0) return null
        return ((last / first).pow(1 / years) - 1) * 100
    }

    /** Annualised volatility (%) of the daily returns. */
    fun volatility(values: List<Double>, periodsPerYear: Int = 252): Double? {
        if (values.size < 20) return null
        val returns = (1 until values.size).mapNotNull { i ->
            if (values[i - 1] > 0) values[i] / values[i - 1] - 1 else null
        }
        val mean = returns.average()
        val variance = returns.sumOf { (it - mean) * (it - mean) } / (returns.size - 1)
        return sqrt(variance) * sqrt(periodsPerYear.toDouble()) * 100
    }

    /** Average true range as % of the last close, over OHLC bars [open, high, low, close]. */
    fun atrPercent(bars: List<List<Double>>): Double? {
        if (bars.size < 2) return null
        val ranges = (1 until bars.size).map { i ->
            val h = bars[i][1]; val l = bars[i][2]; val pc = bars[i - 1][3]
            maxOf(h - l, abs(h - pc), abs(l - pc))
        }
        val close = bars.last()[3]
        if (close <= 0) return null
        return ranges.average() / close * 100
    }

    /**
     * Whether the [fast]-period average crossed the [slow]-period average within the last
     * [within] bars. Returns +1 for a cross up, −1 for a cross down, 0 for none.
     */
    fun maCross(values: List<Double>, fast: Int, slow: Int, within: Int): Int {
        if (values.size < slow + within + 1) return 0
        for (end in values.size - within + 1..values.size) {
            val f0 = sma(values, fast, end - 1) ?: continue
            val s0 = sma(values, slow, end - 1) ?: continue
            val f1 = sma(values, fast, end) ?: continue
            val s1 = sma(values, slow, end) ?: continue
            if (f0 <= s0 && f1 > s1) return 1
            if (f0 >= s0 && f1 < s1) return -1
        }
        return 0
    }
}
