package com.folio.viewer.viewer.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.folio.viewer.R
import com.folio.viewer.ui.common.ErrorState
import com.folio.viewer.ui.common.LoadingState
import com.folio.viewer.ui.viewer.LocalSearchState
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * PDF renderer. Bitmaps come from the stock `android.graphics.pdf.PdfRenderer`;
 * text (for search) comes from PdfBox-Android on the same underlying file.
 */
@Composable
fun PdfViewer(uri: Uri) {
    val context = LocalContext.current
    var renderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var pageCount by remember { mutableIntStateOf(0) }
    var pageTexts by remember { mutableStateOf<List<String>>(emptyList()) }
    val search = LocalSearchState.current

    DisposableEffect(uri) {
        var pfd: ParcelFileDescriptor? = null
        var localTemp: File? = null
        try {
            PDFBoxResourceLoader.init(context.applicationContext)
            // Try direct fd first — but PdfBox requires a File, so we also cache a copy.
            localTemp = File.createTempFile("pdf-", ".pdf", context.cacheDir)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(localTemp).use { out -> input.copyTo(out) }
            } ?: error("Cannot open URI")
            pfd = ParcelFileDescriptor.open(localTemp, ParcelFileDescriptor.MODE_READ_ONLY)
            val r = PdfRenderer(pfd)
            renderer = r
            pageCount = r.pageCount
        } catch (t: Throwable) {
            error = t.message
        }
        val tempForCleanup = localTemp
        onDispose {
            try { renderer?.close() } catch (_: Throwable) {}
            try { pfd?.close() } catch (_: Throwable) {}
            tempForCleanup?.delete()
        }
    }

    // Extract page text lazily once — off the main thread.
    LaunchedEffect(renderer) {
        val r = renderer ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            try {
                val text = mutableListOf<String>()
                val cache = File.createTempFile("pdftext-", ".pdf", context.cacheDir)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(cache).use { out -> input.copyTo(out) }
                }
                PDDocument.load(cache).use { doc ->
                    val stripper = PDFTextStripper()
                    for (i in 1..doc.numberOfPages) {
                        stripper.startPage = i
                        stripper.endPage = i
                        text.add(stripper.getText(doc))
                    }
                }
                cache.delete()
                pageTexts = text
            } catch (_: Throwable) { /* text extraction is best-effort */ }
        }
    }

    // Update hit count when query or text changes.
    LaunchedEffect(pageTexts, search.query) {
        if (search.query.isBlank()) { search.totalHits = 0; return@LaunchedEffect }
        val q = search.query.lowercase()
        var total = 0
        pageTexts.forEach { p ->
            var i = 0
            val lower = p.lowercase()
            while (true) {
                val idx = lower.indexOf(q, i)
                if (idx < 0) break
                total++; i = idx + q.length
            }
        }
        search.totalHits = total
        if (search.currentHit >= total) search.currentHit = 0
    }

    when {
        error != null -> ErrorState(stringResource(R.string.viewer_error_open))
        renderer == null -> LoadingState(stringResource(R.string.viewer_loading))
        else -> PdfPageList(renderer!!, pageCount, pageTexts, search.query, search.currentHit)
    }
}

@Composable
private fun PdfPageList(
    renderer: PdfRenderer,
    pageCount: Int,
    pageTexts: List<String>,
    query: String,
    currentHit: Int
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Jump to the page containing the current hit.
    LaunchedEffect(query, currentHit, pageTexts) {
        if (query.isBlank() || pageTexts.isEmpty()) return@LaunchedEffect
        val q = query.lowercase()
        var seen = 0
        var target = -1
        for ((page, text) in pageTexts.withIndex()) {
            val n = countOccurrences(text.lowercase(), q)
            if (currentHit < seen + n) { target = page; break }
            seen += n
        }
        if (target >= 0) scope.launch { listState.animateScrollToItem(target) }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF262626))
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 4f)
                    if (scale > 1f) { offsetX += pan.x; offsetY += pan.y }
                    else { offsetX = 0f; offsetY = 0f }
                }
            }
            .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offsetX, translationY = offsetY),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        items(count = pageCount, key = { it }) { index -> PdfPage(renderer, index) }
    }
}

private fun countOccurrences(text: String, q: String): Int {
    if (q.isEmpty()) return 0
    var i = 0
    var n = 0
    while (true) {
        val idx = text.indexOf(q, i)
        if (idx < 0) break
        n++; i = idx + q.length
    }
    return n
}

@Composable
private fun PdfPage(renderer: PdfRenderer, index: Int) {
    val density = LocalDensity.current
    val widthPx = with(density) { (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp - 16.dp).toPx() }.toInt()
    var bitmap by remember(index) { mutableStateOf<Bitmap?>(null) }
    var aspect by remember(index) { mutableFloatStateOf(1f / 1.4142f) }

    LaunchedEffect(index) {
        bitmap = withContext(Dispatchers.IO) {
            synchronized(renderer) {
                if (index >= renderer.pageCount) return@withContext null
                renderer.openPage(index).use { page ->
                    val pageAspect = page.width.toFloat() / page.height.toFloat()
                    aspect = pageAspect
                    val w = widthPx.coerceAtLeast(200)
                    val h = (w / pageAspect).toInt().coerceAtLeast(200)
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    Canvas(bmp).drawColor(AndroidColor.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bmp
                }
            }
        }
    }

    val bm = bitmap
    if (bm != null) {
        Image(
            bitmap = bm.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
        )
    } else {
        Box(
            Modifier.fillMaxWidth().aspectRatio(aspect).padding(horizontal = 8.dp)
                .background(Color.White.copy(alpha = 0.05f))
        )
    }
}
