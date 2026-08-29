package com.folio.viewer.ui.viewer

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.folio.viewer.FolioApp
import com.folio.viewer.R
import com.folio.viewer.domain.DocumentFormat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(
    app: FolioApp,
    uriString: String,
    onBack: () -> Unit
) {
    val uri = remember(uriString) { Uri.parse(uriString) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val recents by app.documents.observeRecents().collectAsState(initial = emptyList())
    val entry = remember(recents, uriString) { recents.firstOrNull { it.uri == uriString } }
    val displayName = entry?.displayName ?: (uri.lastPathSegment ?: "Document")
    val mime = entry?.mime ?: context.contentResolver.getType(uri)
    val fmt = remember(mime, displayName) { DocumentFormat.fromMimeOrExtension(mime, displayName) }

    val searchState = rememberSearchState()
    var chromeVisible by remember { mutableStateOf(true) }
    LaunchedEffect(chromeVisible, searchState.visible) {
        if (chromeVisible && !searchState.visible) { delay(3000); chromeVisible = false }
    }

    val searchSupported = fmt in setOf(
        DocumentFormat.PDF, DocumentFormat.WORD, DocumentFormat.TEXT,
        DocumentFormat.CSV, DocumentFormat.RTF, DocumentFormat.ODT,
        DocumentFormat.POWERPOINT, DocumentFormat.EXCEL
    )

    CompositionLocalProvider(LocalSearchState provides searchState) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                Column {
                    AnimatedVisibility(
                        visible = chromeVisible,
                        enter = slideInVertically { -it } + fadeIn(),
                        exit = slideOutVertically { -it } + fadeOut()
                    ) {
                        TopAppBar(
                            title = {
                                Column {
                                    Text(
                                        displayName,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        fmt.display,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            navigationIcon = {
                                IconButton(onClick = onBack) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back))
                                }
                            },
                            actions = {
                                if (searchSupported) {
                                    IconButton(onClick = { searchState.visible = !searchState.visible }) {
                                        Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.cd_search))
                                    }
                                }
                                IconButton(onClick = {
                                    entry?.let {
                                        scope.launch { app.documents.toggleFavorite(it.uri, !it.isFavorite) }
                                    }
                                }) {
                                    Icon(
                                        if (entry?.isFavorite == true) Icons.Filled.Star else Icons.Filled.StarBorder,
                                        contentDescription = stringResource(R.string.cd_favorite),
                                        tint = if (entry?.isFavorite == true)
                                            MaterialTheme.colorScheme.tertiary else LocalContentColor.current
                                    )
                                }
                                IconButton(onClick = { shareFile(context, uri, mime) }) {
                                    Icon(Icons.Filled.Share, stringResource(R.string.cd_share))
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
                            )
                        )
                    }
                    AnimatedVisibility(visible = searchState.visible && searchSupported) {
                        SearchBar(state = searchState)
                    }
                }
            }
        ) { padding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(padding)
                    .pointerInput(searchState.visible) {
                        detectTapGestures(onTap = {
                            if (!searchState.visible) chromeVisible = !chromeVisible
                        })
                    }
            ) {
                ViewerRouter(uri = uri, format = fmt, displayName = displayName)
            }
        }
    }
}

private fun shareFile(context: android.content.Context, uri: Uri, mime: String?) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = mime ?: "*/*"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, null))
}
