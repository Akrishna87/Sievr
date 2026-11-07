package io.github.akrishna87.sievr.ui.portfolio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.akrishna87.sievr.SievrApp
import io.github.akrishna87.sievr.data.FAMILY
import io.github.akrishna87.sievr.data.Member
import io.github.akrishna87.sievr.ui.components.Fmt
import io.github.akrishna87.sievr.ui.components.SectionHeader
import io.github.akrishna87.sievr.ui.components.changeColor
import io.github.akrishna87.sievr.ui.components.signedMoney

/** One member's totals, for the family breakdown. */
data class MemberTotals(val name: String, val value: Double, val invested: Double) {
    val gain: Double get() = value - invested
    val gainPercent: Double? get() = if (invested > 0) (value / invested - 1) * 100 else null
}

/** Family total and each member, plus adding and managing members. Shared by the Stocks and Mutual funds tabs. */
@Composable
fun MemberBar(members: List<Member>, view: String) {
    val store = SievrApp.instance.user
    var adding by remember { mutableStateOf(false) }
    var managing by remember { mutableStateOf(false) }
    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (members.size > 1) item {
            FilterChip(view == FAMILY, { store.setPortfolioView(FAMILY) }, { Text("Family total") }, Modifier.padding(4.dp))
        }
        items(members, key = { it.id }) { m ->
            FilterChip(view == m.id || (members.size == 1), { store.setPortfolioView(m.id) }, { Text(m.name) }, Modifier.padding(4.dp))
        }
        item {
            AssistChip({ adding = true }, { Text("Add member") }, Modifier.padding(4.dp), leadingIcon = { Icon(Icons.Default.Add, null) })
        }
        item { IconButton(onClick = { managing = true }) { Icon(Icons.Default.Edit, "Rename or remove members") } }
    }
    if (adding) NameDialog("Add a family member", "", "e.g. Wife, Amma, Appa", onDismiss = { adding = false }) {
        store.addMember(it); adding = false
    }
    if (managing) ManageMembersDialog(members) { managing = false }
}

@Composable
private fun NameDialog(title: String, initial: String, hint: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(name, { name = it }, singleLine = true, placeholder = { Text(hint) }) },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onSave(name.trim()) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ManageMembersDialog(members: List<Member>, onDismiss: () -> Unit) {
    val store = SievrApp.instance.user
    var renaming by remember { mutableStateOf<Member?>(null) }
    var removing by remember { mutableStateOf<Member?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Family members") },
        text = {
            Column {
                members.forEach { m ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(m.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        TextButton(onClick = { renaming = m }) { Text("Rename") }
                        if (members.size > 1) TextButton(onClick = { removing = m }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                    }
                    HorizontalDivider()
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
    renaming?.let { m -> NameDialog("Rename", m.name, "Name", onDismiss = { renaming = null }) { store.renameMember(m.id, it); renaming = null } }
    removing?.let { m ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove ${m.name}?") },
            text = { Text("${m.name}'s stocks and mutual funds will be deleted from this phone.") },
            confirmButton = { TextButton(onClick = { store.removeMember(m.id); removing = null }) { Text("Remove", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } },
        )
    }
}

/** Asked when adding or importing from the Family total view: whose portfolio does it go into? */
@Composable
fun OwnerPickerDialog(members: List<Member>, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Whose portfolio?") },
        text = {
            Column {
                members.forEach { m ->
                    Text(m.name, Modifier.fillMaxWidth().clickable { onPick(m.id) }.padding(vertical = 14.dp), style = MaterialTheme.typography.bodyLarge)
                    HorizontalDivider()
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Each member's value, invested and returns, shown under the Family total. */
@Composable
fun MemberBreakdown(rows: List<MemberTotals>) {
    if (rows.size < 2) return
    SectionHeader("By member")
    Column(Modifier.padding(horizontal = 16.dp)) {
        rows.forEach { m ->
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(m.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Text("Invested ${Fmt.price(m.invested)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(Fmt.price(m.value), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(signedMoney(m.gain) + (m.gainPercent?.let { "  " + Fmt.percent(it, true, 2) } ?: ""),
                        style = MaterialTheme.typography.bodySmall, color = changeColor(m.gain))
                }
            }
            HorizontalDivider()
        }
        Spacer(Modifier.height(4.dp))
    }
}
