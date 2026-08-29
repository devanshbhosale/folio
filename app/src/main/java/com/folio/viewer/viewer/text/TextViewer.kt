package com.folio.viewer.viewer.text

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.folio.viewer.R
import com.folio.viewer.ui.common.ErrorState
import com.folio.viewer.ui.common.LoadingState
import com.folio.viewer.ui.viewer.LocalSearchState
import com.folio.viewer.util.SearchHighlight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

@Composable
fun TextViewer(uri: Uri) {
    val context = LocalContext.current
    var content by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val search = LocalSearchState.current

    LaunchedEffect(uri) {
        try {
            content = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { it.readText() }
                } ?: error("open failed")
            }
        } catch (t: Throwable) { error = t.message }
    }

    // Recompute hits on query change.
    LaunchedEffect(content, search.query) {
        if (content == null) return@LaunchedEffect
        val hits = SearchHighlight.findAll(content!!, search.query)
        search.totalHits = hits.size
        if (search.currentHit >= hits.size) search.currentHit = 0
    }

    when {
        error != null -> ErrorState(stringResource(R.string.viewer_error_open))
        content == null -> LoadingState(stringResource(R.string.viewer_loading))
        else -> {
            val hits = remember(content, search.query) {
                SearchHighlight.findAll(content!!, search.query)
            }
            val highlighted: AnnotatedString = remember(content, search.query, search.currentHit) {
                val currentOffset = hits.getOrNull(search.currentHit)?.first ?: -1
                SearchHighlight.apply(AnnotatedString(content!!), search.query, currentOffset)
            }
            val scroll = rememberScrollState()
            Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(16.dp)) {
                Text(
                    highlighted,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    }
}
