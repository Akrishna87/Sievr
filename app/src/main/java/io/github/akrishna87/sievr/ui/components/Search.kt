package io.github.akrishna87.sievr.ui.components

import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Speech-to-text through the phone's own recogniser (Google's on most phones), tuned for Indian English.
 * Returns null when the phone has none, so callers simply don't show a mic.
 */
@Composable
fun rememberVoiceInput(prompt: String, onResult: (String) -> Unit): (() -> Unit)? {
    val context = LocalContext.current
    val latest = rememberUpdatedState(onResult)
    val available = remember {
        runCatching { context.packageManager.queryIntentActivities(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0).isNotEmpty() }
            .getOrDefault(false)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { latest.value(cleanSpoken(it)) }
    }
    if (!available) return null
    return {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            .putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
        try { launcher.launch(intent) } catch (_: ActivityNotFoundException) {}
    }
}

/** "Tata Motors." → "Tata Motors": recognisers add full stops that would spoil a search. */
fun cleanSpoken(s: String): String = s.trim().trimEnd('.', '?', '!').trim()

@Composable
fun MicButton(prompt: String = "Say what to search for", onResult: (String) -> Unit) {
    val start = rememberVoiceInput(prompt, onResult) ?: return
    IconButton(onClick = start) { Icon(Icons.Default.Mic, "Search by voice", tint = MaterialTheme.colorScheme.primary) }
}

/** The search box used across Sievr: a search icon, a clear button, and a mic for voice search. */
@Composable
fun SearchField(query: String, onQuery: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, voicePrompt: String = placeholder) {
    OutlinedTextField(
        query, onQuery, modifier,
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Default.Search, null) },
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) { Icon(Icons.Default.Clear, "Clear") }
                MicButton(voicePrompt) { onQuery(it) }
            }
        },
        singleLine = true,
    )
}

/** The bar under a multi-select search: how many are ticked, Clear, and Add. */
@Composable
fun AddSelectedBar(count: Int, verb: String = "Add", onClear: () -> Unit, onAdd: () -> Unit) {
    if (count == 0) return
    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("$count selected", style = MaterialTheme.typography.titleSmall)
            }
            TextButton(onClick = onClear) { Text("Clear") }
            Button(onClick = onAdd) { Text("$verb $count") }
        }
    }
}
