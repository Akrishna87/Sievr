package io.github.akrishna87.sievr.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.akrishna87.sievr.SievrApp
import io.github.akrishna87.sievr.core.model.Market
import io.github.akrishna87.sievr.core.model.Stock
import io.github.akrishna87.sievr.data.MarketState
import io.github.akrishna87.sievr.ui.theme.LocalMarketColors
import kotlinx.coroutines.launch

@Composable
fun changeColor(x: Double?): Color = when {
    x == null || x == 0.0 -> LocalMarketColors.current.neutral
    x > 0 -> LocalMarketColors.current.up
    else -> LocalMarketColors.current.down
}

@Composable
fun ChangeText(percent: Double?, modifier: Modifier = Modifier, bold: Boolean = false) {
    Text(
        Fmt.percent(percent, signed = true, decimals = 2),
        modifier,
        color = changeColor(percent),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
    )
}

/** Shows [content] once market data is loaded; otherwise a loading or error state. */
@Composable
fun WithMarket(content: @Composable (Market) -> Unit) {
    val repo = SievrApp.instance.market
    val state by repo.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    when (val s = state) {
        is MarketState.Ready -> content(s.market)
        MarketState.Loading -> CenterMessage {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text("Downloading today's market data…", style = MaterialTheme.typography.bodyMedium)
            Text("About 5–10 MB, once a day.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        is MarketState.Failed -> CenterMessage {
            Icon(Icons.Outlined.CloudOff, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Text(s.message, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            if (s.refreshing) CircularProgressIndicator()
            else Button(onClick = { scope.launch { repo.refresh(force = true) } }) { Text("Try again") }
        }
    }
}

@Composable
fun CenterMessage(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}

@Composable
fun RefreshBar() {
    val state by SievrApp.instance.market.state.collectAsStateWithLifecycle()
    val s = state
    if (s is MarketState.Ready && s.refreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 16.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) TextButton(onClick = onAction) { Text(action) }
    }
}

@Composable
fun SievrCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    if (onClick != null) Card(onClick = onClick, modifier = modifier, colors = colors) { content() }
    else Card(modifier = modifier, colors = colors) { content() }
}

/** One stock in a list: symbol and name, an optional sparkline, then the value columns. */
@Composable
fun StockRow(
    stock: Stock,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingMetric: String? = null,
    showSparkline: Boolean = true,
    subtitle: String? = null,
) {
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stock.symbol, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            Text(subtitle ?: stock.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (showSparkline) {
            Sparkline(stock.data.c.takeLast(60), Modifier.width(56.dp).height(24.dp))
            Spacer(Modifier.width(12.dp))
        }
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(96.dp)) {
            Text(Fmt.price(stock["price"]), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            if (trailingMetric == null) ChangeText(stock["chg1d"])
            else Text(Fmt.metric(trailingMetric, stock[trailingMetric]), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A 0–100 score as a labelled bar. */
@Composable
fun ScoreBar(label: String, score: Double?, modifier: Modifier = Modifier) {
    val color = when {
        score == null -> MaterialTheme.colorScheme.outline
        score >= 70 -> LocalMarketColors.current.up
        score >= 40 -> Color(0xFFF59E0B)
        else -> LocalMarketColors.current.down
    }
    Column(modifier.padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            Text(score?.let { "%.0f".format(it) } ?: "—", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
        }
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            if (score != null) Box(Modifier.fillMaxWidth((score / 100).toFloat().coerceIn(0.02f, 1f)).height(6.dp).background(color))
        }
    }
}

/** A label above a value, for metric grids. */
@Composable
fun MetricTile(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = Color.Unspecified, onClick: (() -> Unit)? = null) {
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = valueColor, maxLines = 1)
    }
}

/** Metric tiles laid out [columns] per row. */
@Composable
fun MetricGrid(items: List<Pair<String, String>>, columns: Int = 3, onClick: ((Int) -> Unit)? = null) {
    Column {
        items.chunked(columns).forEachIndexed { row, chunk ->
            Row(Modifier.fillMaxWidth()) {
                chunk.forEachIndexed { col, (label, value) ->
                    val index = row * columns + col
                    MetricTile(label, value, Modifier.weight(1f), onClick = onClick?.let { { it(index) } })
                }
                repeat(columns - chunk.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
fun EmptyState(title: String, text: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (action != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
fun Pill(text: String, color: Color = MaterialTheme.colorScheme.secondaryContainer, textColor: Color = MaterialTheme.colorScheme.onSecondaryContainer) {
    Text(
        text,
        Modifier.clip(RoundedCornerShape(50)).background(color).padding(horizontal = 8.dp, vertical = 2.dp),
        style = MaterialTheme.typography.labelSmall,
        color = textColor,
    )
}
