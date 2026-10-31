package io.github.akrishna87.sievr.core.technical

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Candlestick patterns recognised on the most recent daily bars. */
enum class CandlePattern(val label: String, val bullish: Boolean?, val description: String) {
    DOJI("Doji", null, "Open and close almost equal: indecision."),
    HAMMER("Hammer", true, "Small body at the top, long lower shadow after a fall: buyers stepped in."),
    INVERTED_HAMMER("Inverted hammer", true, "Small body at the bottom, long upper shadow after a fall."),
    SHOOTING_STAR("Shooting star", false, "Small body at the bottom, long upper shadow after a rise: sellers pushed back."),
    HANGING_MAN("Hanging man", false, "Hammer shape after a rise: possible top."),
    BULLISH_ENGULFING("Bullish engulfing", true, "A green body that swallows the previous red body."),
    BEARISH_ENGULFING("Bearish engulfing", false, "A red body that swallows the previous green body."),
    BULLISH_HARAMI("Bullish harami", true, "A small green body inside the previous long red body."),
    BEARISH_HARAMI("Bearish harami", false, "A small red body inside the previous long green body."),
    PIERCING_LINE("Piercing line", true, "Opens below the previous low and closes above the middle of the previous red body."),
    DARK_CLOUD_COVER("Dark cloud cover", false, "Opens above the previous high and closes below the middle of the previous green body."),
    MORNING_STAR("Morning star", true, "Long red, small body, then long green: a three-day bottom."),
    EVENING_STAR("Evening star", false, "Long green, small body, then long red: a three-day top."),
    THREE_WHITE_SOLDIERS("Three white soldiers", true, "Three long green bodies, each closing higher."),
    THREE_BLACK_CROWS("Three black crows", false, "Three long red bodies, each closing lower."),
    MARUBOZU_BULLISH("Bullish marubozu", true, "A long green body with almost no shadows: strong buying all day."),
    MARUBOZU_BEARISH("Bearish marubozu", false, "A long red body with almost no shadows: strong selling all day.");

    val metricId: String get() = "pat_" + name.lowercase()

    companion object {
        private class Bar(val o: Double, val h: Double, val l: Double, val c: Double) {
            val body get() = abs(c - o)
            val range get() = h - l
            val upper get() = h - max(o, c)
            val lower get() = min(o, c) - l
            val green get() = c > o
            val red get() = c < o
            val mid get() = (o + c) / 2
        }

        /** Detects patterns ending on the last of [ohlc] (oldest first, each [o, h, l, c]). */
        fun detect(ohlc: List<List<Double>>): List<CandlePattern> {
            val bars = ohlc.filter { it.size >= 4 && it[1] >= it[2] }.map { Bar(it[0], it[1], it[2], it[3]) }
            if (bars.isEmpty()) return emptyList()
            val found = mutableListOf<CandlePattern>()
            val b = bars.last()
            if (b.range <= 0) return emptyList()
            val avgBody = bars.map { it.body }.average().coerceAtLeast(b.range * 0.05)
            val longBody = { x: Bar -> x.body >= avgBody * 1.1 && x.body >= x.range * 0.5 }
            val smallBody = { x: Bar -> x.body <= x.range * 0.3 }

            // Trend before the last bar, from closes of the earlier bars.
            val prior = bars.dropLast(1)
            val downtrend = prior.size >= 3 && prior.last().c < prior.first().c
            val uptrend = prior.size >= 3 && prior.last().c > prior.first().c

            if (b.body <= b.range * 0.1) found += DOJI
            val hammerShape = b.lower >= 2 * b.body && b.upper <= b.body.coerceAtLeast(b.range * 0.1) && b.body > 0
            val invertedShape = b.upper >= 2 * b.body && b.lower <= b.body.coerceAtLeast(b.range * 0.1) && b.body > 0
            if (hammerShape && downtrend) found += HAMMER
            if (hammerShape && uptrend) found += HANGING_MAN
            if (invertedShape && downtrend) found += INVERTED_HAMMER
            if (invertedShape && uptrend) found += SHOOTING_STAR
            if (b.body >= b.range * 0.9 && longBody(b)) found += if (b.green) MARUBOZU_BULLISH else MARUBOZU_BEARISH

            if (bars.size >= 2) {
                val p = bars[bars.size - 2]
                if (p.red && b.green && b.o <= p.c && b.c >= p.o && b.body > p.body) found += BULLISH_ENGULFING
                if (p.green && b.red && b.o >= p.c && b.c <= p.o && b.body > p.body) found += BEARISH_ENGULFING
                if (p.red && longBody(p) && b.green && b.o > p.c && b.c < p.o && b.body < p.body * 0.6) found += BULLISH_HARAMI
                if (p.green && longBody(p) && b.red && b.o < p.c && b.c > p.o && b.body < p.body * 0.6) found += BEARISH_HARAMI
                if (p.red && longBody(p) && b.green && b.o < p.l && b.c > p.mid && b.c < p.o) found += PIERCING_LINE
                if (p.green && longBody(p) && b.red && b.o > p.h && b.c < p.mid && b.c > p.o) found += DARK_CLOUD_COVER
            }
            if (bars.size >= 3) {
                val a = bars[bars.size - 3]
                val m = bars[bars.size - 2]
                if (a.red && longBody(a) && smallBody(m) && max(m.o, m.c) < a.c && b.green && b.c > a.mid) found += MORNING_STAR
                if (a.green && longBody(a) && smallBody(m) && min(m.o, m.c) > a.c && b.red && b.c < a.mid) found += EVENING_STAR
                val three = listOf(a, m, b)
                if (three.all { it.green && it.body >= it.range * 0.6 } && a.c < m.c && m.c < b.c && m.o > a.o && b.o > m.o) found += THREE_WHITE_SOLDIERS
                if (three.all { it.red && it.body >= it.range * 0.6 } && a.c > m.c && m.c > b.c && m.o < a.o && b.o < m.o) found += THREE_BLACK_CROWS
            }
            return found
        }
    }
}
