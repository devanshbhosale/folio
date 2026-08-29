package com.folio.viewer.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.folio.viewer.FolioApp
import com.folio.viewer.R
import com.folio.viewer.data.db.DocumentEntity
import com.folio.viewer.domain.DocumentFormat
import com.folio.viewer.ui.common.FormatBadge
import com.folio.viewer.util.ThumbnailGenerator
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    app: FolioApp,
    onOpenSettings: () -> Unit,
    onOpenDocument: (String) -> Unit
) {
    val scope = rememberCoroutineScope()

    val mimeFilter = remember {
        arrayOf(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "text/plain", "text/csv",
            "application/rtf", "text/rtf",
            "application/vnd.oasis.opendocument.text",
            "application/x-iwork-numbers-sffnumbers",
            "application/x-iwork-pages-sffpages",
            "*/*" // last-resort fallback so pickers show iWork/etc. too
        )
    }
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        val u = uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            app.documents.takePersistablePermission(u)
            app.documents.record(u, null)
            onOpenDocument(u.toString())
        }
    }

    val recents by app.documents.observeRecents().collectAsState(initial = emptyList())
    val favorites by app.documents.observeFavorites().collectAsState(initial = emptyList())
    var tab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text("Folio", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                        Text(
                            stringResource(R.string.tagline),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.tab_settings))
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { picker.launch(mimeFilter) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.cd_open_file)) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            SecondaryTabRow(selectedTabIndex = tab, containerColor = Color.Transparent) {
                Tab(selected = tab == 0, onClick = { tab = 0 },
                    text = { Text(stringResource(R.string.tab_recents)) })
                Tab(selected = tab == 1, onClick = { tab = 1 },
                    text = { Text(stringResource(R.string.tab_favorites)) })
            }
            AnimatedContent(
                targetState = tab,
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(180)) },
                label = "tab"
            ) { current ->
                val list = if (current == 0) recents else favorites
                if (list.isEmpty()) EmptyState() else DocumentGrid(
                    list = list,
                    onOpen = { onOpenDocument(it.uri) },
                    onToggleFavorite = { d ->
                        scope.launch { app.documents.toggleFavorite(d.uri, !d.isFavorite) }
                    }
                )
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Filled.Description,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.empty_recents_title), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.empty_recents_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DocumentGrid(
    list: List<DocumentEntity>,
    onOpen: (DocumentEntity) -> Unit,
    onToggleFavorite: (DocumentEntity) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(list, key = { it.uri }) { doc -> DocumentCard(doc, onOpen, onToggleFavorite) }
    }
}

@Composable
private fun DocumentCard(
    doc: DocumentEntity,
    onOpen: (DocumentEntity) -> Unit,
    onToggleFavorite: (DocumentEntity) -> Unit
) {
    val fmt = DocumentFormat.fromMimeOrExtension(doc.mime, doc.displayName)
    val context = LocalContext.current
    var thumb by remember(doc.uri) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(doc.uri) {
        try {
            val uri = android.net.Uri.parse(doc.uri)
            val file = ThumbnailGenerator.ensure(context, uri, doc.displayName, doc.mime)
            if (file != null && file.exists()) {
                thumb = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
            } else {
                // fall back to synthetic cover in memory
                thumb = ThumbnailGenerator.syntheticCover(fmt, doc.displayName)
            }
        } catch (_: Throwable) {
            thumb = ThumbnailGenerator.syntheticCover(fmt, doc.displayName)
        }
    }
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen(doc) },
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(12.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(fmt.accent().copy(alpha = 0.12f))
            ) {
                val bmp = thumb
                if (bmp != null) {
                    androidx.compose.foundation.Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(120.dp)
                    )
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                    ) { FormatBadge(fmt, big = false) }
                } else {
                    Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) { FormatBadge(fmt, big = true) }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                doc.displayName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(doc.lastOpenedAt)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { onToggleFavorite(doc) }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        if (doc.isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                        contentDescription = stringResource(R.string.cd_favorite),
                        tint = if (doc.isFavorite) MaterialTheme.colorScheme.tertiary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun DocumentFormat.accent(): Color = when (this) {
    DocumentFormat.PDF -> Color(0xFFD64545)
    DocumentFormat.WORD -> Color(0xFF2B579A)
    DocumentFormat.EXCEL -> Color(0xFF217346)
    DocumentFormat.POWERPOINT -> Color(0xFFB7472A)
    DocumentFormat.CSV -> Color(0xFF3B7A57)
    DocumentFormat.TEXT -> Color(0xFF555555)
    DocumentFormat.RTF -> Color(0xFF6E4B8B)
    DocumentFormat.ODT -> Color(0xFF0E7C86)
    DocumentFormat.NUMBERS -> Color(0xFF35B34A)
    DocumentFormat.PAGES -> Color(0xFFE07C24)
    DocumentFormat.UNSUPPORTED -> Color(0xFF888888)
}

@Composable
private fun stringResource(id: Int) = androidx.compose.ui.res.stringResource(id = id)
