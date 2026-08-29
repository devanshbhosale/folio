package com.folio.viewer.viewer.office

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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.folio.viewer.R
import com.folio.viewer.ui.common.ErrorState
import com.folio.viewer.ui.common.LoadingState
import com.folio.viewer.ui.viewer.LocalSearchState
import com.folio.viewer.util.SearchHighlight
import com.folio.viewer.util.UriCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.hwpf.HWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFRun

@Composable
fun WordViewer(uri: Uri, displayName: String) {
    val context = LocalContext.current
    var text by remember { mutableStateOf<AnnotatedString?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val search = LocalSearchState.current

    LaunchedEffect(uri) {
        try {
            val ext = displayName.substringAfterLast('.', "").lowercase()
            val file = withContext(Dispatchers.IO) { UriCache.materialize(context, uri, ".$ext") }
            text = withContext(Dispatchers.IO) {
                if (ext == "doc") parseDoc(file.inputStream()) else parseDocx(file.inputStream())
            }
        } catch (t: Throwable) { error = t.message }
    }

    LaunchedEffect(text, search.query) {
        val t = text ?: return@LaunchedEffect
        val hits = SearchHighlight.findAll(t.text, search.query)
        search.totalHits = hits.size
        if (search.currentHit >= hits.size) search.currentHit = 0
    }

    when {
        error != null -> ErrorState(stringResource(R.string.viewer_error_open))
        text == null -> LoadingState(stringResource(R.string.viewer_loading))
        else -> {
            val hits = remember(text, search.query) {
                SearchHighlight.findAll(text!!.text, search.query)
            }
            val highlighted = remember(text, search.query, search.currentHit) {
                val off = hits.getOrNull(search.currentHit)?.first ?: -1
                SearchHighlight.apply(text!!, search.query, off)
            }
            val scroll = rememberScrollState()
            Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 20.dp, vertical = 24.dp)) {
                Text(
                    text = highlighted,
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp)
                )
            }
        }
    }
}

private fun parseDocx(stream: java.io.InputStream): AnnotatedString =
    XWPFDocument(stream).use { doc ->
        buildAnnotatedString {
            doc.paragraphs.forEach { para ->
                val runs = para.runs
                if (runs.isEmpty()) { append('\n'); return@forEach }
                runs.forEach { run: XWPFRun ->
                    val underline = try {
                        run.underline?.name?.let { it != "NONE" && it != "SINGLE_NONE" } ?: false
                    } catch (_: Throwable) { false }
                    val size = (run.fontSize.takeIf { it > 0 } ?: 11).sp
                    withStyle(
                        SpanStyle(
                            fontWeight = if (run.isBold) FontWeight.Bold else FontWeight.Normal,
                            fontStyle = if (run.isItalic) FontStyle.Italic else FontStyle.Normal,
                            textDecoration = if (underline) TextDecoration.Underline else null,
                            fontSize = size
                        )
                    ) { append(run.text() ?: "") }
                }
                append('\n'); append('\n')
            }
            doc.tables.forEach { table ->
                table.rows.forEach { row ->
                    append(row.tableCells.joinToString(separator = "  |  ") { it.text ?: "" })
                    append('\n')
                }
                append('\n')
            }
        }
    }

private fun parseDoc(stream: java.io.InputStream): AnnotatedString =
    HWPFDocument(stream).use { doc ->
        val range = doc.range
        buildAnnotatedString {
            for (i in 0 until range.numParagraphs()) {
                val p = range.getParagraph(i)
                for (r in 0 until p.numCharacterRuns()) {
                    val cr = p.getCharacterRun(r)
                    withStyle(
                        SpanStyle(
                            fontWeight = if (cr.isBold) FontWeight.Bold else FontWeight.Normal,
                            fontStyle = if (cr.isItalic) FontStyle.Italic else FontStyle.Normal,
                            textDecoration = if (cr.underlineCode != 0) TextDecoration.Underline else null,
                            fontSize = (cr.fontSize / 2).coerceIn(8, 32).sp
                        )
                    ) {
                        val cleaned = (cr.text() ?: "").filter { it >= ' ' || it == '\n' || it == '\t' }
                        append(cleaned)
                    }
                }
                append('\n')
            }
        }
    }
