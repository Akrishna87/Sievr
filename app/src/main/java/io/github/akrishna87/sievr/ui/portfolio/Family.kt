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
import io.github.akrishna87.sievr.data.GROUP_COLORS
import io.github.akrishna87.sievr.data.Group
import io.github.akrishna87.sievr.data.Member
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import io.github.akrishna87.sievr.ui.components.Fmt
import io.github.akrishna87.sievr.ui.components.SectionHeader
import io.github.akrishna87.sievr.ui.components.changeColor
import io.github.akrishna87.sievr.ui.components.signedMoney

/** One member's totals, for the family breakdown. */
data class MemberTotals(val name: String, val value: Double, val invested: Double) {
    val gain: Double get() = value - invested
    val gainPercent: Double? get() = if (invested > 0) (value / invested - 1) * 100 else null
}

/** The folders (e.g. Family, Parents), each in its own colour, plus adding and editing folders. */
@Composable
fun GroupBar(groups: List<Group>, current: Group) {
    val store = SievrApp.instance.user
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Group?>(null) }
    LazyRow(contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        items(groups, key = { it.id }) { g ->
            val color = Color(g.color)
            val selected = g.id == current.id
            FilterChip(
                selected, { if (selected) editing = g else store.setPortfolioGroup(g.id) },
                { Text(g.name, fontWeight = if (selected) FontWeight.SemiBold else null) },
                Modifier.padding(4.dp),
                leadingIcon = { Icon(Icons.Default.Folder, null, tint = if (selected) Color.White else color) },
                trailingIcon = if (selected) ({ Icon(Icons.Default.Edit, "Edit folder", Modifier.size(16.dp), tint = Color.White) }) else null,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = color, selectedLabelColor = Color.White,
                ),
                border = FilterChipDefaults.filterChipBorder(true, selected, borderColor = color.copy(alpha = 0.6f)),
            )
        }
        item {
            AssistChip({ adding = true }, { Text("New folder") }, Modifier.padding(4.dp), leadingIcon = { Icon(Icons.Default.Add, null) })
        }
    }
    if (adding) GroupDialog(null, groups.size, canDelete = false, onDismiss = { adding = false }) { name, color, tint, first ->
        store.addGroup(name, color, first ?: "Member", tint); adding = false
    }
    editing?.let { g ->
        GroupDialog(g, groups.size, canDelete = groups.size > 1, onDismiss = { editing = null }) { name, color, tint, _ ->
            store.editGroup(g.id, name, color, tint); editing = null
        }
    }
}

/** New folder (name, colour, first member) or edit one (name, colour, or delete it). */
@Composable
private fun GroupDialog(existing: Group?, count: Int, canDelete: Boolean, onDismiss: () -> Unit, onSave: (String, Long, Int, String?) -> Unit) {
    val store = SievrApp.instance.user
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var first by remember { mutableStateOf("") }
    var color by remember { mutableStateOf(existing?.color ?: GROUP_COLORS[count % GROUP_COLORS.size]) }
    var tint by remember { mutableStateOf(existing?.tint ?: 1) }
    var deleting by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New folder" else "Edit folder") },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Folder name") },
                    placeholder = { Text("e.g. Parents, Brother's family") })
                if (existing == null) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(first, { first = it }, singleLine = true, label = { Text("First member") },
                        placeholder = { Text("e.g. Appa") })
                }
                Spacer(Modifier.height(12.dp))
                Text("Colour", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(6.dp))
                GROUP_COLORS.chunked(5).forEach { row ->
                    Row {
                        row.forEach { c ->
                            Box(
                                Modifier.padding(4.dp).size(36.dp).clip(CircleShape).background(Color(c))
                                    .then(if (c == color) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                                    .clickable { color = c },
                                contentAlignment = Alignment.Center,
                            ) { if (c == color) Icon(Icons.Default.Check, "Selected", tint = Color.White) }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("Page colour", style = MaterialTheme.typography.labelMedium)
                Row {
                    listOf(0 to "Off", 1 to "Light", 2 to "Strong").forEach { (v, label) ->
                        FilterChip(tint == v, { tint = v }, { Text(label) }, Modifier.padding(end = 6.dp),
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(color), selectedLabelColor = Color.White))
                    }
                }
                Text("Tints the whole Portfolio page in this colour while the folder is open.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (existing != null && canDelete) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { deleting = true }) { Text("Delete this folder", color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && (existing != null || first.isNotBlank()),
                onClick = { onSave(name.trim(), color, tint, first.trim().ifBlank { null }) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    if (deleting && existing != null) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text("Delete ${existing.name}?") },
            text = { Text("Everyone in this folder, with their stocks and mutual funds, will be deleted from this phone.") },
            confirmButton = { TextButton(onClick = { store.removeGroup(existing.id); deleting = false; onDismiss() }) {
                Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancel") } },
        )
    }
}

/** The folder's total and each member in it, plus adding and managing members. Shared by the Stocks and Mutual funds tabs. */
@Composable
fun MemberBar(members: List<Member>, view: String, group: Group, groups: List<Group>) {
    val store = SievrApp.instance.user
    var adding by remember { mutableStateOf(false) }
    var managing by remember { mutableStateOf(false) }
    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (members.size > 1) item {
            FilterChip(view == FAMILY, { store.setPortfolioView(FAMILY) }, { Text("${group.name} total") }, Modifier.padding(4.dp))
        }
        items(members, key = { it.id }) { m ->
            FilterChip(view == m.id || (members.size == 1), { store.setPortfolioView(m.id) }, { Text(m.name) }, Modifier.padding(4.dp))
        }
        item {
            AssistChip({ adding = true }, { Text("Add member") }, Modifier.padding(4.dp), leadingIcon = { Icon(Icons.Default.Add, null) })
        }
        item { IconButton(onClick = { managing = true }) { Icon(Icons.Default.Edit, "Rename or remove members") } }
    }
    if (adding) NameDialog("Add to ${group.name}", "", "e.g. Wife, Amma, Appa", onDismiss = { adding = false }) {
        store.addMember(it, group.id); adding = false
    }
    if (managing) ManageMembersDialog(members, groups, group) { managing = false }
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
private fun ManageMembersDialog(members: List<Member>, groups: List<Group>, group: Group, onDismiss: () -> Unit) {
    val store = SievrApp.instance.user
    var renaming by remember { mutableStateOf<Member?>(null) }
    var moving by remember { mutableStateOf<Member?>(null) }
    var removing by remember { mutableStateOf<Member?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${group.name} members") },
        text = {
            Column {
                members.forEach { m ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(m.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        TextButton(onClick = { renaming = m }) { Text("Rename") }
                        if (groups.size > 1 && members.size > 1) TextButton(onClick = { moving = m }) { Text("Move") }
                        if (members.size > 1) TextButton(onClick = { removing = m }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                    }
                    HorizontalDivider()
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
    moving?.let { m ->
        AlertDialog(
            onDismissRequest = { moving = null },
            title = { Text("Move ${m.name} to…") },
            text = {
                Column {
                    groups.filter { it.id != group.id }.forEach { g ->
                        Row(Modifier.fillMaxWidth().clickable { store.moveMember(m.id, g.id); moving = null; onDismiss() }.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Folder, null, tint = Color(g.color))
                            Spacer(Modifier.width(10.dp))
                            Text(g.name, style = MaterialTheme.typography.bodyLarge)
                        }
                        HorizontalDivider()
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { moving = null }) { Text("Cancel") } },
        )
    }
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

/**
 * The open folder's "skin": its colour becomes the page's accent, and with a tint the backgrounds, cards and
 * bars take on a wash of it, so Family and Parents look different at a glance. Works in light and dark mode.
 */
@Composable
fun FolderSkin(color: Long, tint: Int, content: @Composable () -> Unit) {
    val scheme = io.github.akrishna87.sievr.ui.theme.skinned(MaterialTheme.colorScheme, Color(color), tint)
    MaterialTheme(colorScheme = scheme, typography = MaterialTheme.typography, shapes = MaterialTheme.shapes) {
        androidx.compose.material3.Surface(color = scheme.background, contentColor = scheme.onBackground) { content() }
    }
}
