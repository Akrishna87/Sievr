package io.github.akrishna87.sievr.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * "Sort: Change % ↓": a menu of [options] (id to label). Picking the current option again flips the order;
 * a new option starts high-to-low (or A–Z for names).
 */
@Composable
fun SortButton(options: List<Pair<String, String>>, selected: String, descending: Boolean, onChange: (String, Boolean) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val label = options.firstOrNull { it.first == selected }?.second ?: options.first().second
    Box {
        TextButton(onClick = { open = true }) {
            Icon(Icons.AutoMirrored.Filled.Sort, null)
            Text(" $label ")
            Icon(if (descending) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward, if (descending) "High to low" else "Low to high")
        }
        DropdownMenu(open, { open = false }) {
            options.forEach { (id, text) ->
                DropdownMenuItem(
                    { Text(text) },
                    onClick = {
                        open = false
                        if (id == selected) onChange(id, !descending) else onChange(id, id != "name")
                    },
                    trailingIcon = if (id == selected) ({
                        Icon(if (descending) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward, null)
                    }) else null,
                )
            }
        }
    }
}
