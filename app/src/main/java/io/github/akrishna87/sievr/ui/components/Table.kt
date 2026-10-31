package io.github.akrishna87.sievr.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.akrishna87.sievr.core.metrics.Metrics
import io.github.akrishna87.sievr.core.model.Stock

private val NameWidth = 112.dp
private val CellWidth = 92.dp

/**
 * A results table as LazyColumn items: the symbol column stays put and the metric columns
 * scroll sideways together (every row shares [scroll]). Tap a header to sort by it.
 */
fun LazyListScope.stockTable(
    stocks: List<Stock>,
    columns: List<String>,
    scroll: ScrollState,
    sortBy: String?,
    sortDescending: Boolean,
    onSort: (String) -> Unit,
    onOpen: (Stock) -> Unit,
    onLongPress: ((Stock) -> Unit)? = null,
) {
    item(key = "table-header") {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Stock", Modifier.width(NameWidth).padding(start = 16.dp), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.horizontalScroll(scroll)) {
                columns.forEach { id ->
                    val arrow = if (id == sortBy) (if (sortDescending) " ↓" else " ↑") else ""
                    Text(
                        (Metrics[id]?.label ?: id) + arrow,
                        Modifier.width(CellWidth).clickable { onSort(id) }.padding(horizontal = 6.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (id == sortBy) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End, maxLines = 2,
                    )
                }
            }
        }
        HorizontalDivider()
    }
    items(stocks, key = { "row-" + it.symbol }) { s ->
        Column {
            Row(
                Modifier.fillMaxWidth()
                    .then(
                        if (onLongPress != null) Modifier.combinedClickableCompat({ onOpen(s) }, { onLongPress(s) })
                        else Modifier.clickable { onOpen(s) },
                    )
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.width(NameWidth).padding(start = 16.dp, end = 4.dp)) {
                    Text(s.symbol, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(s.name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(Modifier.horizontalScroll(scroll)) {
                    columns.forEach { id ->
                        val v = s[id]
                        val color = if (id == "chg1d" || id.startsWith("ret")) changeColor(v) else Color.Unspecified
                        Text(Fmt.metric(id, v), Modifier.width(CellWidth).padding(horizontal = 6.dp), style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.End, color = color, maxLines = 1)
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
    }
}

private fun Modifier.combinedClickableCompat(onClick: () -> Unit, onLongClick: () -> Unit): Modifier =
    this.then(Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick))
