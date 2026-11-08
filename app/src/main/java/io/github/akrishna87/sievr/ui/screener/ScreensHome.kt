package io.github.akrishna87.sievr.ui.screener

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.akrishna87.sievr.SievrApp
import io.github.akrishna87.sievr.core.model.Market
import io.github.akrishna87.sievr.core.nl.PlainEnglish
import io.github.akrishna87.sievr.core.screen.Presets
import io.github.akrishna87.sievr.core.screen.Screen
import io.github.akrishna87.sievr.core.screen.ScreenEngine
import io.github.akrishna87.sievr.ui.LocalNav
import io.github.akrishna87.sievr.ui.components.SectionHeader
import io.github.akrishna87.sievr.ui.components.SievrCard
import io.github.akrishna87.sievr.ui.components.WithMarket
import java.util.UUID

private val examples = listOf(
    "Profitable small caps with low debt and ROE above 20",
    "Undervalued large caps with high dividend",
    "Pharma stocks with rising margins near 52 week high",
    "Debt free IT companies with sales growth above 15",
    "Oversold quality stocks in Nifty 500",
    "Golden cross with high volume",
)

@Composable
fun ScreensHome() {
    val nav = LocalNav.current
    Scaffold(topBar = {
        TopAppBar(title = { Text("Screens") }, actions = {
            IconButton(onClick = { nav.screen(Screen("my-" + UUID.randomUUID(), "New screen"), saved = false) }) {
                Icon(Icons.Default.Add, "New screen")
            }
        })
    }) { padding ->
        Column(Modifier.padding(padding)) {
            WithMarket { market -> ScreensBody(market) }
        }
    }
}

@Composable
private fun ScreensBody(market: Market) {
    val nav = LocalNav.current
    val user by SievrApp.instance.user.data.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val counts = remember(market, user.screens) {
        (Presets.all + user.screens).associate { it.id to ScreenEngine.run(it, market.stocks).size }
    }

    fun ask(text: String) {
        if (text.isBlank()) return
        val result = PlainEnglish.parse(text, market.sectors)
        nav.screen(
            Screen(
                id = "my-" + UUID.randomUUID(),
                name = text.trim().replaceFirstChar { it.uppercase() }.take(60),
                description = if (result.ignored.isEmpty()) "" else "Skipped: " + result.ignored.joinToString(" "),
                filters = result.filters,
                sortBy = result.sortBy ?: "mcap",
                sortDescending = result.sortDescending,
            ),
            saved = false,
        )
    }

    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            SievrCard(Modifier.fillMaxWidth().padding(16.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Describe the stocks you want", style = MaterialTheme.typography.titleSmall)
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        query, { query = it }, Modifier.fillMaxWidth(),
                        placeholder = { Text("e.g. profitable small caps with low debt") },
                        trailingIcon = {
                            Row {
                                io.github.akrishna87.sievr.ui.components.MicButton("Describe the stocks you want") { query = it; ask(it) }
                                IconButton(onClick = { ask(query) }) { Icon(Icons.AutoMirrored.Filled.Send, "Build screen") }
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { ask(query) }),
                        maxLines = 3,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("Works offline on your phone. Try:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow {
                        examples.forEach { e -> AssistChip(onClick = { ask(e) }, label = { Text(e, maxLines = 1, overflow = TextOverflow.Ellipsis) }, modifier = Modifier.padding(end = 6.dp)) }
                    }
                }
            }
        }
        if (user.screens.isNotEmpty()) {
            item { SectionHeader("My screens") }
            items(user.screens, key = { it.id }) { s -> ScreenRow(s, counts[s.id] ?: 0) { nav.screen(s, saved = true) } }
        }
        for (category in Presets.categories) {
            item(key = "h-$category") { SectionHeader(category) }
            items(Presets.all.filter { it.category == category }, key = { it.id }) { s ->
                ScreenRow(s, counts[s.id] ?: 0) { nav.screen(s, saved = false) }
            }
        }
    }
}

@Composable
private fun ScreenRow(screen: Screen, count: Int, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(screen.name, style = MaterialTheme.typography.bodyLarge)
                if (screen.alerts) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Default.NotificationsActive, "Alerts on", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
            val sub = screen.description.ifBlank { screen.filters.joinToString(" · ") { it.describe() } }
            if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(12.dp))
        Text("$count", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    }
}
