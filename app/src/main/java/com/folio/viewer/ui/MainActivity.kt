package com.folio.viewer.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.lifecycleScope
import com.folio.viewer.FolioApp
import com.folio.viewer.ui.theme.FolioTheme
import com.folio.viewer.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val pendingIntentUri = MutableStateFlow<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as FolioApp
        handleIncoming(intent)

        setContent {
            val themeMode by app.prefs.themeMode.collectAsState(initial = ThemeMode.System)
            val incoming by pendingIntentUri.collectAsState()

            FolioTheme(mode = themeMode) {
                FolioRoot(
                    app = app,
                    incomingUri = incoming,
                    onIncomingConsumed = { pendingIntentUri.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncoming(intent)
    }

    private fun handleIncoming(intent: Intent?) {
        if (intent == null) return
        if (intent.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return
        // Persist read grant when possible (external "Open with…" is usually a one-shot grant).
        val app = application as FolioApp
        lifecycleScope.launch {
            app.documents.takePersistablePermission(uri)
            app.documents.record(uri, intent.type)
            pendingIntentUri.value = uri
        }
    }
}
