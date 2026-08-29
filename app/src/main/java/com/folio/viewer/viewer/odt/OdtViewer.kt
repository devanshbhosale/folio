package com.folio.viewer.viewer.odt

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
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
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
import java.util.zip.ZipInputStream
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler

/**
 * ODT viewer — parses content.xml directly from the .odt ZIP (an ODT is just
 * a ZIP of XML). We keep dependencies minimal here rather than pulling all
 * of ODF Toolkit's runtime graph, which adds tens of MB.
 */
@Composable
fun OdtViewer(uri: Uri) {
    val context = LocalContext.current
    var text by remember { mutableStateOf<AnnotatedString?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val search = LocalSearchState.current

    LaunchedEffect(uri) {
        try {
            val file = withContext(Dispatchers.IO) { UriCache.materialize(context, uri, ".odt") }
            text = withContext(Dispatchers.IO) { parseOdt(file) }
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
            val hits = remember(text, search.query) { SearchHighlight.findAll(text!!.text, search.query) }
            val highlighted = remember(text, search.query, search.currentHit) {
                val off = hits.getOrNull(search.currentHit)?.first ?: -1
                SearchHighlight.apply(text!!, search.query, off)
            }
            val scroll = rememberScrollState()
            Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(20.dp)) {
                Text(highlighted, style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp),
                    color = MaterialTheme.colorScheme.onBackground)
            }
        }
    }
}

private fun parseOdt(file: java.io.File): AnnotatedString {
    val xml = file.inputStream().use { input ->
        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "content.xml") return@use zip.readBytes()
                entry = zip.nextEntry
            }
            null
        }
    } ?: return AnnotatedString("")

    val builder = AnnotatedString.Builder()
    val parser = SAXParserFactory.newInstance().apply {
        isNamespaceAware = true
        setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
    }.newSAXParser()
    parser.parse(xml.inputStream(), object : DefaultHandler() {
        private val stack = ArrayDeque<String>()
        private var inHeading = false
        private var headingLevel = 1
        override fun startElement(uri: String?, localName: String, qName: String, attrs: Attributes) {
            stack.addLast(localName)
            if (localName == "h") {
                inHeading = true
                headingLevel = attrs.getValue("text:outline-level")?.toIntOrNull() ?: 1
            }
        }
        override fun endElement(uri: String?, localName: String, qName: String) {
            when (localName) {
                "h" -> { inHeading = false; builder.append('\n'); builder.append('\n') }
                "p" -> { builder.append('\n'); builder.append('\n') }
                "tab" -> builder.append('\t')
                "line-break" -> builder.append('\n')
            }
            stack.removeLastOrNull()
        }
        override fun characters(ch: CharArray, start: Int, length: Int) {
            val s = String(ch, start, length)
            if (inHeading) {
                val size = when (headingLevel) { 1 -> 26.sp; 2 -> 22.sp; 3 -> 18.sp; else -> 16.sp }
                builder.withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = size)) {
                    builder.append(s)
                }
            } else builder.append(s)
        }
    })
    return builder.toAnnotatedString()
}

private inline fun <T> AnnotatedString.Builder.withStyle(style: SpanStyle, block: () -> T): T {
    val idx = pushStyle(style)
    return try { block() } finally { pop(idx) }
}
