package com.folio.viewer.viewer.office

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.folio.viewer.R
import com.folio.viewer.ui.common.ErrorState
import com.folio.viewer.ui.common.LoadingState
import com.folio.viewer.ui.viewer.LocalSearchState
import com.folio.viewer.util.SearchHighlight
import com.folio.viewer.util.UriCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.hslf.usermodel.HSLFSlideShow
import org.apache.poi.xslf.usermodel.XMLSlideShow
import java.io.FileInputStream

data class ParsedSlide(
    val index: Int, val title: String, val bullets: List<String>, val notes: String?
) {
    val flatText: String get() =
        title + "\n" + bullets.joinToString("\n") + (notes?.let { "\n$it" } ?: "")
}

@Composable
fun PowerPointViewer(uri: Uri, displayName: String) {
    val context = LocalContext.current
    var slides by remember { mutableStateOf<List<ParsedSlide>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val search = LocalSearchState.current

    LaunchedEffect(uri) {
        try {
            val ext = displayName.substringAfterLast('.', "pptx").lowercase()
            val file = withContext(Dispatchers.IO) { UriCache.materialize(context, uri, ".$ext") }
            slides = withContext(Dispatchers.IO) {
                if (ext == "ppt") parseHslf(file) else parseXslf(file)
            }
        } catch (t: Throwable) { error = t.message }
    }

    LaunchedEffect(slides, search.query) {
        val s = slides ?: return@LaunchedEffect
        val q = search.query
        if (q.isBlank()) { search.totalHits = 0; return@LaunchedEffect }
        val qL = q.lowercase()
        var total = 0
        s.forEach { total += countIn(it.flatText.lowercase(), qL) }
        search.totalHits = total
        if (search.currentHit >= total) search.currentHit = 0
    }

    when {
        error != null -> ErrorState(stringResource(R.string.viewer_error_open))
        slides == null -> LoadingState(stringResource(R.string.viewer_loading))
        else -> SlideDeck(slides!!, search.query, search.currentHit)
    }
}

private fun countIn(text: String, q: String): Int {
    if (q.isEmpty()) return 0
    var i = 0; var n = 0
    while (true) {
        val idx = text.indexOf(q, i); if (idx < 0) break
        n++; i = idx + q.length
    }
    return n
}

@Composable
private fun SlideDeck(slides: List<ParsedSlide>, query: String, currentHit: Int) {
    val pager = rememberPagerState(pageCount = { slides.size })
    val scope = rememberCoroutineScope()

    LaunchedEffect(query, currentHit, slides) {
        if (query.isBlank()) return@LaunchedEffect
        val q = query.lowercase()
        var seen = 0; var target = -1
        for (s in slides) {
            val n = countIn(s.flatText.lowercase(), q)
            if (currentHit < seen + n) { target = s.index; break }
            seen += n
        }
        if (target >= 0) scope.launch { pager.animateScrollToPage(target) }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxWidth().weight(1f),
            pageSpacing = 16.dp,
            contentPadding = PaddingValues(horizontal = 12.dp)
        ) { page -> SlideCard(slides[page], query) }

        Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
            val scroll = rememberScrollState()
            Row(Modifier.horizontalScroll(scroll).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                slides.forEachIndexed { i, _ ->
                    val selected = i == pager.currentPage
                    Box(
                        Modifier
                            .padding(horizontal = 4.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { scope.launch { pager.animateScrollToPage(i) } }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            "${i + 1}",
                            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
        Text(
            "${pager.currentPage + 1} / ${slides.size}",
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SlideCard(slide: ParsedSlide, query: String) {
    Box(
        Modifier.fillMaxSize().padding(vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp)).background(Color.White)
    ) {
        LazyColumn(Modifier.fillMaxSize().padding(28.dp)) {
            item {
                Text(
                    text = highlight(AnnotatedString(slide.title.ifBlank { "Slide ${slide.index + 1}" }), query),
                    color = Color(0xFF12141C), fontWeight = FontWeight.Bold, fontSize = 28.sp,
                    maxLines = 3, overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(20.dp))
            }
            items(slide.bullets) { bullet ->
                Row(Modifier.padding(vertical = 4.dp)) {
                    Text("•  ", color = Color(0xFF3B4BE0), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(text = highlight(AnnotatedString(bullet), query), color = Color(0xFF1B1A17), fontSize = 18.sp)
                }
            }
            if (!slide.notes.isNullOrBlank()) {
                item {
                    Spacer(Modifier.height(24.dp))
                    HorizontalDivider(color = Color(0xFFEAE3D2))
                    Spacer(Modifier.height(8.dp))
                    Text("Speaker notes", color = Color(0xFF8B7B5D), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(text = highlight(AnnotatedString(slide.notes), query), color = Color(0xFF4F4A3D), fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun highlight(source: AnnotatedString, query: String): AnnotatedString =
    if (query.isBlank()) source else SearchHighlight.apply(source, query, -1)

private fun parseXslf(file: java.io.File): List<ParsedSlide> =
    FileInputStream(file).use { input ->
        XMLSlideShow(input).use { ppt ->
            ppt.slides.mapIndexed { idx, slide ->
                val texts = slide.shapes.mapNotNull { s ->
                    try {
                        (s as? org.apache.poi.sl.usermodel.TextShape<*, *>)?.text
                            ?.trim()?.takeIf { it.isNotEmpty() }
                    } catch (_: Throwable) { null }
                }
                val title = texts.firstOrNull().orEmpty()
                val bullets = texts.drop(1).flatMap { it.lines().map(String::trim).filter { it.isNotEmpty() } }
                val notes = try {
                    slide.notes?.shapes?.joinToString("\n") { s ->
                        (s as? org.apache.poi.sl.usermodel.TextShape<*, *>)?.text.orEmpty()
                    }?.trim()?.takeIf { it.isNotEmpty() }
                } catch (_: Throwable) { null }
                ParsedSlide(idx, title, bullets, notes)
            }
        }
    }

private fun parseHslf(file: java.io.File): List<ParsedSlide> =
    FileInputStream(file).use { input ->
        HSLFSlideShow(input).use { ppt ->
            ppt.slides.mapIndexed { idx, slide ->
                val texts = slide.shapes.mapNotNull { s ->
                    try {
                        (s as? org.apache.poi.sl.usermodel.TextShape<*, *>)?.text
                            ?.trim()?.takeIf { it.isNotEmpty() }
                    } catch (_: Throwable) { null }
                }
                val title = texts.firstOrNull().orEmpty()
                val bullets = texts.drop(1).flatMap { it.lines().map(String::trim).filter { it.isNotEmpty() } }
                val notes = try {
                    slide.notes?.shapes?.joinToString("\n") { s ->
                        (s as? org.apache.poi.sl.usermodel.TextShape<*, *>)?.text.orEmpty()
                    }?.trim()?.takeIf { it.isNotEmpty() }
                } catch (_: Throwable) { null }
                ParsedSlide(idx, title, bullets, notes)
            }
        }
    }
