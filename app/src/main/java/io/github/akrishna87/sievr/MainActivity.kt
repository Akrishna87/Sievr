package io.github.akrishna87.sievr

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import io.github.akrishna87.sievr.ui.SievrRoot

class MainActivity : ComponentActivity() {
    /** A stock to open, from a notification. */
    private val pendingSymbol = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingSymbol.value = intent?.getStringExtra(EXTRA_SYMBOL)
        setContent { SievrRoot(pendingSymbol) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_SYMBOL)?.let { pendingSymbol.value = it }
    }

    companion object {
        const val EXTRA_SYMBOL = "symbol"
    }
}
