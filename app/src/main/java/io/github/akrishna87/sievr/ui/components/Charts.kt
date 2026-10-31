package io.github.akrishna87.sievr.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.akrishna87.sievr.ui.theme.LocalMarketColors

/** A line series; [values] may contain nulls (gaps are skipped). */
data class Series(val values: List<Double?>, val color: Color, val width: Float = 2f, val dashed: Boolean = false)

/**
 * A price chart. Long-press and drag to read a value: [onScrub] receives the index under the
 * finger (null when released) so the caller can show the date and price.
 */
@Composable
fun LineChart(
    series: List<Series>,
    modifier: Modifier = Modifier,
    height: Dp = 200.dp,
    fill: Boolean = true,
    baseline: Double? = null,
    onScrub: ((Int?) -> Unit)? = null,
) {
    var scrub by remember { mutableStateOf<Int?>(null) }
    val n = series.maxOfOrNull { it.values.size } ?: 0
    val all = series.flatMap { it.values.filterNotNull() } + listOfNotNull(baseline)
    if (n < 2 || all.isEmpty()) {
        Box(modifier.fillMaxWidth().height(height))
        return
    }
    val min = all.min()
    val max = all.max()
    val range = (max - min).takeIf { it > 0 } ?: 1.0
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val cursorColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .pointerInput(n) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { o -> scrub = indexAt(o.x, size.width.toFloat(), n); onScrub?.invoke(scrub) },
                    onDrag = { change, _ -> scrub = indexAt(change.position.x, size.width.toFloat(), n); onScrub?.invoke(scrub) },
                    onDragEnd = { scrub = null; onScrub?.invoke(null) },
                    onDragCancel = { scrub = null; onScrub?.invoke(null) },
                )
            }
            .pointerInput(n) {
                detectTapGestures(onPress = {
                    scrub = indexAt(it.x, size.width.toFloat(), n); onScrub?.invoke(scrub)
                    tryAwaitRelease()
                    scrub = null; onScrub?.invoke(null)
                })
            },
    ) {
        val w = size.width
        val h = size.height
        val pad = 6f
        fun x(i: Int) = i * w / (n - 1)
        fun y(v: Double) = (pad + (1 - (v - min) / range) * (h - 2 * pad)).toFloat()

        for (k in 1..3) {
            val gy = h * k / 4
            drawLine(gridColor, Offset(0f, gy), Offset(w, gy), strokeWidth = 1f)
        }
        baseline?.let {
            drawLine(cursorColor, Offset(0f, y(it)), Offset(w, y(it)), strokeWidth = 1.5f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
        }
        series.forEachIndexed { si, s ->
            val path = Path()
            var started = false
            var firstX = 0f
            var lastX = 0f
            s.values.forEachIndexed { i, v ->
                if (v == null) return@forEachIndexed
                if (!started) { path.moveTo(x(i), y(v)); started = true; firstX = x(i) } else path.lineTo(x(i), y(v))
                lastX = x(i)
            }
            if (!started) return@forEachIndexed
            if (fill && si == 0) {
                val area = Path().apply {
                    addPath(path)
                    lineTo(lastX, h)
                    lineTo(firstX, h)
                    close()
                }
                drawPath(area, Brush.verticalGradient(listOf(s.color.copy(alpha = 0.25f), s.color.copy(alpha = 0f))))
            }
            drawPath(path, s.color, style = Stroke(width = s.width * density / 2, cap = StrokeCap.Round,
                pathEffect = if (s.dashed) PathEffect.dashPathEffect(floatArrayOf(10f, 8f)) else null))
        }
        scrub?.let { i ->
            drawLine(cursorColor, Offset(x(i), 0f), Offset(x(i), h), strokeWidth = 2f)
            series.firstOrNull()?.values?.getOrNull(i)?.let { v ->
                drawCircle(series.first().color, radius = 5f * density / 2, center = Offset(x(i), y(v)))
            }
        }
    }
}

private fun indexAt(x: Float, width: Float, n: Int): Int = ((x / width) * (n - 1)).toInt().coerceIn(0, n - 1)

/** A tiny trend line for lists, green when the last value is above the first. */
@Composable
fun Sparkline(values: List<Double?>, modifier: Modifier = Modifier) {
    val v = values.filterNotNull()
    if (v.size < 2) return
    val up = v.last() >= v.first()
    val color = if (up) LocalMarketColors.current.up else LocalMarketColors.current.down
    val min = v.min()
    val range = (v.max() - min).takeIf { it > 0 } ?: 1.0
    Canvas(modifier) {
        val path = Path()
        v.forEachIndexed { i, p ->
            val px = i * size.width / (v.size - 1)
            val py = ((1 - (p - min) / range) * size.height).toFloat()
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        drawPath(path, color, style = Stroke(width = 1.5f * density / 2 + 1f, cap = StrokeCap.Round))
    }
}

/** Horizontal bars, e.g. sector allocation or revenue by year. */
@Composable
fun BarList(items: List<Pair<String, Double>>, format: (Double) -> String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    val max = items.maxOfOrNull { kotlin.math.abs(it.second) }?.takeIf { it > 0 } ?: 1.0
    Column(modifier) {
        for ((label, value) in items) {
            androidx.compose.foundation.layout.Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Text(label, Modifier.weight(0.38f), style = MaterialTheme.typography.bodySmall, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Box(Modifier.weight(0.42f).height(14.dp)) {
                    Canvas(Modifier.fillMaxWidth().height(14.dp)) {
                        val frac = (kotlin.math.abs(value) / max).toFloat()
                        drawRoundRect(
                            if (value < 0) Color(0xFFDC2626) else color,
                            size = Size(size.width * frac, size.height),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f),
                        )
                    }
                }
                Text(format(value), Modifier.weight(0.2f), style = MaterialTheme.typography.bodySmall,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End)
            }
        }
    }
}

